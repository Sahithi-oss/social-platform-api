package com.socialplatform.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a human user of the platform.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "is_premium", nullable = false)
    private boolean isPremium = false;
}
