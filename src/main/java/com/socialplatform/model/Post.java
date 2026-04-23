package com.socialplatform.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a post authored by either a User or a Bot.
 * author_id references the id in either the users or bots table,
 * disambiguated by author_type.
 */
@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to users.id or bots.id depending on authorType */
    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, length = 10)
    private AuthorType authorType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** Running like count stored in the DB for persistence */
    @Column(name = "like_count", nullable = false)
    @Builder.Default
    private int likeCount = 0;
}
