package com.empleord.domain.repository;

import com.empleord.domain.entity.AdSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AdSlotRepository extends JpaRepository<AdSlot, String> {

    List<AdSlot> findAllByOrderBySlotKeyAsc();

    @Modifying
    @Query("update AdSlot slot set slot.impressions = slot.impressions + 1, slot.updatedAt = :now where slot.slotKey = :key")
    int incrementImpressions(@Param("key") String key, @Param("now") Instant now);
}
