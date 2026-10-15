package com.empleord.domain.repository;

import com.empleord.domain.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("select u from User u join fetch u.role where u.email = :email and u.deletedAt is null")
    Optional<User> findActiveByEmail(@Param("email") String email);

    @Query("select u from User u join fetch u.role where u.id = :id and u.deletedAt is null")
    Optional<User> findActiveById(@Param("id") UUID id);

    Optional<User> findByEmailVerificationTokenHashAndDeletedAtIsNull(String hash);

    Optional<User> findByPasswordResetTokenHashAndDeletedAtIsNull(String hash);

    long countByDeletedAtIsNull();

    @Query("select count(u) from User u join u.role r where u.deletedAt is null and r.name = :role")
    long countActiveByRole(@Param("role") String role);

    @Query("select count(u) from User u where u.deletedAt is null and u.createdAt >= :from and u.createdAt < :to")
    long countCreatedBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select u from User u
            where u.deletedAt is null
            and (:q is null or lower(u.email) like :q or lower(u.firstName) like :q or lower(u.lastName) like :q)
            """)
    Page<User> search(@Param("q") String q, Pageable pageable);
}
