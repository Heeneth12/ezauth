package com.ezh.ezauth.tenant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;


@Entity
@Table(name = "tenant_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_type", nullable = false)
    private BusinessType businessType;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(length = 3, nullable = false)
    private String baseCurrency;

    @Column(nullable = false)
    private String timeZone;

    @Column(unique = true)
    private String gstNumber;

    @Column(name = "pan_number")
    private String panNumber;

    @Column(name = "is_gst_verified")
    private boolean gstVerified = false;

    @Column(name = "cin_number", unique = true)
    private String cinNumber;

    @Column(name = "trade_name")
    private String tradeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status")
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "incorporation_date")
    private LocalDateTime incorporationDate;

    @Column(name = "support_email")
    private String supportEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "website")
    private String website;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @CreationTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
