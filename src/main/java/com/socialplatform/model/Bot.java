package com.socialplatform.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents an AI Bot that can reply to posts/comments.
 */
@Entity
@Table(name = "bots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "persona_description", columnDefinition = "TEXT")
    private String personaDescription;
}
