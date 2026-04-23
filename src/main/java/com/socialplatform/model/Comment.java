package com.socialplatform.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a comment on a Post.
 * Supports nested threads tracked by depth_level.
 */
@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    /** FK to users.id or bots.id depending on authorType */
    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, length = 10)
    private AuthorType authorType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Depth level in the comment thread.
     * Root comments = 1, replies to root = 2, etc.
     * Hard cap enforced by Redis guardrail at depth > 20.
     */
    @Column(name = "depth_level", nullable = false)
    @Builder.Default
    private int depthLevel = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
