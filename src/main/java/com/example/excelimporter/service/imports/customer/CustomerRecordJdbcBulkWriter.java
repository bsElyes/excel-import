package com.example.excelimporter.service.imports.customer;

import com.example.excelimporter.dto.CustomerImportRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CustomerRecordJdbcBulkWriter {

    private static final String UPDATE_SQL =
            "update customer_records "
                    + "set name = ?, email = ?, amount = ?, last_import_id = ?, updated_at = ?, version = version + 1 "
                    + "where business_key = ?";

    private static final String INSERT_SQL =
            "insert into customer_records "
                    + "(id, business_key, name, email, amount, last_import_id, updated_at, version) "
                    + "values (nextval('customer_record_seq'), ?, ?, ?, ?, ?, ?, 0)";

    private final JdbcTemplate jdbcTemplate;

    public Set<String> findExistingBusinessKeys(Collection<String> businessKeys) {
        if (businessKeys.isEmpty()) {
            return Collections.emptySet();
        }

        List<String> keyList = new ArrayList<String>(businessKeys);
        String sql = "select business_key from customer_records where business_key in (" + placeholders(keyList.size()) + ")";
        return jdbcTemplate.query(
                sql,
                ps -> {
                    for (int index = 0; index < keyList.size(); index++) {
                        ps.setString(index + 1, keyList.get(index));
                    }
                },
                rs -> {
                    Set<String> existingKeys = new HashSet<String>();
                    while (rs.next()) {
                        existingKeys.add(rs.getString(1));
                    }
                    return existingKeys;
                }
        );
    }

    public void bulkWrite(List<CustomerImportRow> rows, Set<String> existingBusinessKeys, String importId) {
        if (rows.isEmpty()) {
            return;
        }

        List<CustomerImportRow> inserts = new ArrayList<CustomerImportRow>();
        List<CustomerImportRow> updates = new ArrayList<CustomerImportRow>();
        for (CustomerImportRow row : rows) {
            if (existingBusinessKeys.contains(row.getBusinessKey())) {
                updates.add(row);
            } else {
                inserts.add(row);
            }
        }

        Instant now = Instant.now();
        batchUpdate(updates, importId, now);
        batchInsert(inserts, importId, now);
    }

    public void prepareStaging(String importId) {
        jdbcTemplate.update("delete from customer_import_staging where import_id = ?", importId);
        jdbcTemplate.update("delete from import_issues where import_id = ?", importId);
    }

    public void stageRows(String importId, final List<CustomerImportRow> rows) {
        if (rows.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                "insert into customer_import_staging "
                        + "(import_id, row_number, business_key, name, email, email_invalid, amount, amount_invalid) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int index) throws SQLException {
                        CustomerImportRow row = rows.get(index);
                        ps.setString(1, importId);
                        ps.setInt(2, row.getRowNumber());
                        ps.setString(3, row.getBusinessKey());
                        ps.setString(4, row.getName());
                        ps.setString(5, row.getEmail());
                        ps.setBoolean(6, row.isEmailInvalid());
                        ps.setBigDecimal(7, row.getAmount());
                        ps.setBoolean(8, row.isAmountInvalid());
                    }

                    @Override
                    public int getBatchSize() {
                        return rows.size();
                    }
                }
        );
    }

    public void validateStaging(String importId) {
        jdbcTemplate.update("delete from import_issues where import_id = ?", importId);
        insertRequiredIssue(importId, "business_key", "businessKey", "BUSINESS_KEY_REQUIRED", "Business key is required.");
        insertRequiredIssue(importId, "name", "name", "NAME_REQUIRED", "Name is required.");
        insertRequiredIssue(importId, "email", "email", "EMAIL_REQUIRED", "Email is required.");
        jdbcTemplate.update(
                "insert into import_issues (import_id, severity, row_number, field_name, code, message) "
                        + "select import_id, 'BLOCKING_ERROR', row_number, 'email', 'EMAIL_INVALID', 'Email format is invalid.' "
                        + "from customer_import_staging "
                        + "where import_id = ? and email_invalid = true",
                importId
        );
        jdbcTemplate.update(
                "insert into import_issues (import_id, severity, row_number, field_name, code, message) "
                        + "select import_id, 'BLOCKING_ERROR', row_number, 'amount', 'AMOUNT_INVALID', 'Amount must be numeric.' "
                        + "from customer_import_staging where import_id = ? and amount_invalid = true",
                importId
        );
        jdbcTemplate.update(
                "insert into import_issues (import_id, severity, row_number, field_name, code, message) "
                        + "select import_id, 'BLOCKING_ERROR', row_number, 'amount', 'AMOUNT_REQUIRED', 'Amount is required.' "
                        + "from customer_import_staging where import_id = ? and amount_invalid = false and amount is null",
                importId
        );
        jdbcTemplate.update(
                "insert into import_issues (import_id, severity, row_number, field_name, code, message) "
                        + "select import_id, 'BLOCKING_ERROR', row_number, 'amount', 'AMOUNT_NEGATIVE', 'Amount cannot be negative.' "
                        + "from customer_import_staging where import_id = ? and amount < 0",
                importId
        );
        jdbcTemplate.update(
                "insert into import_issues (import_id, severity, row_number, field_name, code, message) "
                        + "select s.import_id, 'BLOCKING_ERROR', s.row_number, 'businessKey', 'DUPLICATE_IN_FILE', "
                        + "'Business key appears more than once in this file.' "
                        + "from customer_import_staging s "
                        + "join (select business_key from customer_import_staging "
                        + "where import_id = ? and business_key is not null and trim(business_key) <> '' "
                        + "group by business_key having count(*) > 1) d on d.business_key = s.business_key "
                        + "where s.import_id = ?",
                importId,
                importId
        );
        jdbcTemplate.update(
                "insert into import_issues (import_id, severity, row_number, field_name, code, message) "
                        + "select import_id, 'WARNING', row_number, 'amount', 'AMOUNT_LARGE', "
                        + "'Amount is unusually large and should be reviewed.' "
                        + "from customer_import_staging where import_id = ? and amount > 10000.00",
                importId
        );
        jdbcTemplate.update(
                "insert into import_issues (import_id, severity, row_number, field_name, code, message) "
                        + "select s.import_id, 'WARNING', s.row_number, 'businessKey', 'EXISTING_RECORD', "
                        + "'Existing database record will be updated.' "
                        + "from customer_import_staging s "
                        + "where s.import_id = ? and exists ("
                        + "select 1 from customer_records c where c.business_key = s.business_key)",
                importId
        );
    }

    public int countStagedRows(String importId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from customer_import_staging where import_id = ?",
                Integer.class,
                importId
        );
        return count == null ? 0 : count;
    }

    public int countBlockingIssues(String importId) {
        return countIssues(importId, "BLOCKING_ERROR");
    }

    public int countWarnings(String importId) {
        return countIssues(importId, "WARNING");
    }

    public int countRowsWithBlockingIssues(String importId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(distinct row_number) from import_issues where import_id = ? and severity = 'BLOCKING_ERROR'",
                Integer.class,
                importId
        );
        return count == null ? 0 : count;
    }

    public int countExistingStagedRows(String importId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from customer_import_staging s "
                        + "where s.import_id = ? and exists (select 1 from customer_records c where c.business_key = s.business_key)",
                Integer.class,
                importId
        );
        return count == null ? 0 : count;
    }

    public void mergeStagedRows(String importId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "update customer_records set "
                        + "name = (select s.name from customer_import_staging s where s.import_id = ? and s.business_key = customer_records.business_key), "
                        + "email = (select s.email from customer_import_staging s where s.import_id = ? and s.business_key = customer_records.business_key), "
                        + "amount = (select s.amount from customer_import_staging s where s.import_id = ? and s.business_key = customer_records.business_key), "
                        + "last_import_id = ?, updated_at = ?, version = version + 1 "
                        + "where exists (select 1 from customer_import_staging s where s.import_id = ? and s.business_key = customer_records.business_key)",
                importId,
                importId,
                importId,
                importId,
                now,
                importId
        );
        jdbcTemplate.update(
                "insert into customer_records "
                        + "(id, business_key, name, email, amount, last_import_id, updated_at, version) "
                        + "select nextval('customer_record_seq'), s.business_key, s.name, s.email, s.amount, ?, ?, 0 "
                        + "from customer_import_staging s "
                        + "where s.import_id = ? and not exists ("
                        + "select 1 from customer_records c where c.business_key = s.business_key)",
                importId,
                now,
                importId
        );
    }

    public void clearStaging(String importId) {
        jdbcTemplate.update("delete from customer_import_staging where import_id = ?", importId);
    }

    private void batchUpdate(final List<CustomerImportRow> rows, final String importId, final Instant now) {
        if (rows.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(UPDATE_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws SQLException {
                CustomerImportRow row = rows.get(index);
                ps.setString(1, row.getName());
                ps.setString(2, row.getEmail());
                ps.setBigDecimal(3, row.getAmount());
                ps.setString(4, importId);
                ps.setTimestamp(5, Timestamp.from(now));
                ps.setString(6, row.getBusinessKey());
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    private void batchInsert(final List<CustomerImportRow> rows, final String importId, final Instant now) {
        if (rows.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws SQLException {
                CustomerImportRow row = rows.get(index);
                ps.setString(1, row.getBusinessKey());
                ps.setString(2, row.getName());
                ps.setString(3, row.getEmail());
                ps.setBigDecimal(4, row.getAmount());
                ps.setString(5, importId);
                ps.setTimestamp(6, Timestamp.from(now));
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    private String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private void insertRequiredIssue(String importId, String columnName, String fieldName, String code, String message) {
        jdbcTemplate.update(
                "insert into import_issues (import_id, severity, row_number, field_name, code, message) "
                        + "select import_id, 'BLOCKING_ERROR', row_number, ?, ?, ? "
                        + "from customer_import_staging "
                        + "where import_id = ? and (" + columnName + " is null or trim(" + columnName + ") = '')",
                fieldName,
                code,
                message,
                importId
        );
    }

    private int countIssues(String importId, String severity) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from import_issues where import_id = ? and severity = ?",
                Integer.class,
                importId,
                severity
        );
        return count == null ? 0 : count;
    }
}
