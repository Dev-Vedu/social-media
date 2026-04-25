package com.socialapi.entity;

import jakarta.persistence.*;
import lombok.Data;

// This is the Bot table in PostgreSQL
@Entity
@Table(name = "bots")
@Data
public class Bot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String personaDescription;
}
