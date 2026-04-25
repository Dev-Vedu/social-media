package com.socialapi.entity;

import jakarta.persistence.*;
import lombok.Data;

// This is the User table in PostgreSQL
@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private boolean isPremium;
}
