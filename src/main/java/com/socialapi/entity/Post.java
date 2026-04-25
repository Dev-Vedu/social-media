package com.socialapi.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
@Data
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // who wrote the post - can be a user id or bot id
    private Long authorId;

    // "USER" or "BOT"
    private String authorType;

    private String content;

    private LocalDateTime createdAt;

    private int likeCount = 0;

    // automatically set the time when post is created
    @PrePersist
    public void setCreatedAt() {
        this.createdAt = LocalDateTime.now();
    }
}
