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

   //for new post
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
//for comment
    @PostMapping("/{postId}/comments")
    public ResponseEntity<Object> addComment(
            @PathVariable Long postId,
            @RequestBody Map<String, Object> body) {
        //post exit
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Post not found");
        }

        Long authorId    = Long.valueOf(body.get("authorId").toString());
        String authorType = body.get("authorType").toString();
        String content   = body.get("content").toString();

        int depth = 1; // top-level comment
        if (body.containsKey("parentCommentId") && body.get("parentCommentId") != null) {
            Long parentId = Long.valueOf(body.get("parentCommentId").toString());
            Comment parent = commentRepository.findById(parentId).orElse(null);
            if (parent != null) {
                depth = parent.getDepthLevel() + 1;
            }
        }

        //3 guardrail
        if ("BOT".equals(authorType)) {

            //vertical
            if (depth > 20) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body("Rejected: Comment thread is too deep (max 20 levels)");
            }

            //cooldown
            if ("USER".equals(post.getAuthorType())) {
                boolean cooldownOk = guardrailService.checkAndSetCooldown(authorId, post.getAuthorId());
                if (!cooldownOk) {
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                            .body("Rejected: Bot is in cooldown for this user (10 min)");
                }
            }

            //horizontal
            boolean allowed = guardrailService.tryIncrementBotCount(postId);
            if (!allowed) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body("Rejected: Post has reached max 100 bot replies");
            }

            //+1point
            guardrailService.incrementVirality(postId, 1);

            //man-notification
            if ("USER".equals(post.getAuthorType())) {
                String botName = botRepository.findById(authorId)
                        .map(b -> b.getName()).orElse("Unknown Bot");
                notificationService.notify(post.getAuthorId(), botName, postId);
            }

        } else {
            //human comment
            guardrailService.incrementVirality(postId, 50);
        }

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

    //like
    @PostMapping("/{postId}/like")
    public ResponseEntity<Object> likePost(@PathVariable Long postId,
                                           @RequestBody Map<String, Object> body) {

        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Post not found");
        }

        String authorType = body.get("authorType").toString();
        if ("USER".equals(authorType)) {
            post.setLikeCount(post.getLikeCount() + 1);
            postRepository.save(post);
            //+20
            guardrailService.incrementVirality(postId, 20);
        }

        return ResponseEntity.ok(post);
    }
}
