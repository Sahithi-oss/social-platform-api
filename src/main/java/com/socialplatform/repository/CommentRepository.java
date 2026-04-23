package com.socialplatform.repository;

import com.socialplatform.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // Existing method
    List<Comment> findByPostId(Long postId);

    // ✅ Cooldown: get last comment by bot on a user's post
    Comment findTopByAuthorIdAndPost_AuthorIdOrderByCreatedAtDesc(Long authorId, Long postAuthorId);
}