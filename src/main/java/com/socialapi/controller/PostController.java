package com.socialapi.controller;

import com.socialapi.entity.Comment;
import com.socialapi.entity.Post;
import com.socialapi.repository.BotRepository;
import com.socialapi.repository.CommentRepository;
import com.socialapi.repository.PostRepository;
import com.socialapi.repository.UserRepository;
import com.socialapi.service.GuardrailService;
import com.socialapi.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final BotRepository botRepository;
    private final GuardrailService guardrailService;
    private final NotificationService notificationService;

    // ------------------------------------------------
    // POST /api/posts  →  Create a new post
    // ------------------------------------------------
    @PostMapping
    public ResponseEntity<Post> createPost(@RequestBody Map<String, Object> body) {

        Long authorId   = Long.valueOf(body.get("authorId").toString());
        String authorType = body.get("authorType").toString(); // "USER" or "BOT"
        String content  = body.get("content").toString();

        Post post = new Post();
        post.setAuthorId(authorId);
        post.setAuthorType(authorType);
        post.setContent(content);

        Post saved = postRepository.save(post);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // ------------------------------------------------
    // POST /api/posts/{postId}/comments  →  Add a comment
    // ------------------------------------------------
    @PostMapping("/{postId}/comments")
    public ResponseEntity<Object> addComment(
            @PathVariable Long postId,
            @RequestBody Map<String, Object> body) {

        // Check post exists
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Post not found");
        }

        Long authorId    = Long.valueOf(body.get("authorId").toString());
        String authorType = body.get("authorType").toString();
        String content   = body.get("content").toString();

        // Figure out depth level
        int depth = 1; // top-level comment
        if (body.containsKey("parentCommentId") && body.get("parentCommentId") != null) {
            Long parentId = Long.valueOf(body.get("parentCommentId").toString());
            Comment parent = commentRepository.findById(parentId).orElse(null);
            if (parent != null) {
                depth = parent.getDepthLevel() + 1;
            }
        }

        // ---- If author is a BOT, run all 3 guardrail checks ----
        if ("BOT".equals(authorType)) {

            // Guardrail 1 - Vertical Cap: depth cannot exceed 20
            if (depth > 20) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body("Rejected: Comment thread is too deep (max 20 levels)");
            }

            // Guardrail 2 - Cooldown Cap: same bot cannot reply to same human twice in 10 min
            if ("USER".equals(post.getAuthorType())) {
                boolean cooldownOk = guardrailService.checkAndSetCooldown(authorId, post.getAuthorId());
                if (!cooldownOk) {
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .body("Rejected: Bot is in cooldown for this user (10 min)");
                }
            }

            // Guardrail 3 - Horizontal Cap: max 100 bot replies per post (atomic Lua script)
            boolean allowed = guardrailService.tryIncrementBotCount(postId);
            if (!allowed) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body("Rejected: Post has reached max 100 bot replies");
            }

            // Update virality score in Redis: bot reply = +1
            guardrailService.incrementVirality(postId, 1);

            // Send notification to post owner (if human)
            if ("USER".equals(post.getAuthorType())) {
                String botName = botRepository.findById(authorId)
                        .map(b -> b.getName()).orElse("Unknown Bot");
                notificationService.notify(post.getAuthorId(), botName, postId);
            }

        } else {
            // Human comment: update virality +50
            guardrailService.incrementVirality(postId, 50);
        }

        // Save the comment to PostgreSQL
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(authorId);
        comment.setAuthorType(authorType);
        comment.setContent(content);
        comment.setDepthLevel(depth);

        if (body.containsKey("parentCommentId") && body.get("parentCommentId") != null) {
            comment.setParentCommentId(Long.valueOf(body.get("parentCommentId").toString()));
        }

        Comment saved = commentRepository.save(comment);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // ------------------------------------------------
    // POST /api/posts/{postId}/like  →  Like a post
    // ------------------------------------------------
    @PostMapping("/{postId}/like")
    public ResponseEntity<Object> likePost(@PathVariable Long postId,
                                           @RequestBody Map<String, Object> body) {

        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Post not found");
        }

        // Only human likes count for virality
        String authorType = body.get("authorType").toString();
        if ("USER".equals(authorType)) {
            post.setLikeCount(post.getLikeCount() + 1);
            postRepository.save(post);
            // Human like = +20 virality points
            guardrailService.incrementVirality(postId, 20);
        }

        return ResponseEntity.ok(post);
    }
}
