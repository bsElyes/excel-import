package com.example.excelimporter.repository;

import com.example.excelimporter.domain.CustomerRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CustomerRecordRepository extends JpaRepository<CustomerRecord, Long> {

    List<CustomerRecord> findAllByBusinessKeyIn(Collection<String> businessKeys);
}
