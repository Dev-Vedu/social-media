package com.socialapi.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@Data
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long postId;

    private Long authorId;

    // "USER" or "BOT"
    private String authorType;

    private String content;

    // how deep is this comment (1 = top level, 2 = reply to comment, etc.)
    private int depthLevel;

    // if this is a reply, which comment is it replying to
    private Long parentCommentId;

    private LocalDateTime createdAt;

    @PrePersist
    public void setCreatedAt() {
        this.createdAt = LocalDateTime.now();
    }
}
