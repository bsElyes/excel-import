package com.example.excelimporter.domain;

import lombok.Getter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "customer_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_customer_records_business_key",
                columnNames = "business_key"
        ),
        indexes = @Index(name = "idx_customer_records_business_key", columnList = "business_key")
)
@Getter
public class CustomerRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_record_seq")
    @SequenceGenerator(name = "customer_record_seq", sequenceName = "customer_record_seq", allocationSize = 50)
    private Long id;

    @Column(name = "business_key", nullable = false, length = 64, unique = true)
    private String businessKey;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "last_import_id", length = 36)
    private String lastImportId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}
