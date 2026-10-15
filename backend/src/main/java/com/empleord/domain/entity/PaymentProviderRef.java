package com.empleord.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "payment_provider_refs")
@Getter
@Setter
@NoArgsConstructor
public class PaymentProviderRef {

    @EmbeddedId
    private Key id;

    @Column(name = "ref_value", nullable = false, length = 80)
    private String refValue;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(length = 32)
        private String provider;
        @Column(name = "ref_key", length = 40)
        private String refKey;

        public Key(String provider, String refKey) {
            this.provider = provider;
            this.refKey = refKey;
        }
    }
}
