package com.example.excelimporter.service.excel;

import com.example.excelimporter.service.ExcelImportException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;

@Component
@Slf4j
public class StreamingXlsxRowReader implements StreamingExcelRowReader {

    private static final int FIRST_EXCEL_ROW_NUMBER = 1;
    private static final int HEADER_BUFFER_SIZE = 16;

    @Override
    public void read(InputStream inputStream, Collection<String> requiredHeaders, ExcelRowHandler rowHandler) {
        Path workbookPath = null;
        try {
            workbookPath = copyToTempWorkbook(inputStream);
            read(workbookPath, requiredHeaders, rowHandler);
        } catch (IOException ex) {
            throw new ExcelImportException("Could not read Excel file.", ex);
        } finally {
            deleteTempWorkbook(workbookPath);
        }
    }

    @Override
    public void read(Path workbookPath, Collection<String> requiredHeaders, ExcelRowHandler rowHandler) {
        readWorkbook(workbookPath, requiredHeaders, rowHandler);
    }

    private void readWorkbook(Path workbookPath,
                              Collection<String> requiredHeaders,
                              ExcelRowHandler rowHandler) {
        OPCPackage packageReader = null;
        try {
            packageReader = OPCPackage.open(workbookPath.toFile(), PackageAccess.READ);
            XSSFReader workbookReader = new XSSFReader(packageReader);
            StylesTable styles = workbookReader.getStylesTable();
            SharedStrings sharedStrings = new ReadOnlySharedStringsTable(packageReader);

            Iterator<InputStream> sheets = workbookReader.getSheetsData();
            if (!sheets.hasNext()) {
                return;
            }

            try (InputStream sheet = sheets.next()) {
                parseSheet(styles, sharedStrings, sheet, requiredHeaders, rowHandler);
            }
        } catch (IOException ex) {
            throw new ExcelImportException("Could not read Excel file.", ex);
        } catch (OpenXML4JException ex) {
            throw new ExcelImportException("Uploaded file must be a valid .xlsx workbook.", ex);
        } catch (ParserConfigurationException ex) {
            throw new ExcelImportException("Could not configure Excel XML parser.", ex);
        } catch (SAXException ex) {
            throw new ExcelImportException("Could not parse Excel worksheet.", ex);
        } finally {
            if (packageReader != null) {
                packageReader.revert();
            }
        }
    }

    private Path copyToTempWorkbook(InputStream inputStream) throws IOException {
        Path workbookPath = Files.createTempFile("excel-import-", ".xlsx");
        Files.copy(inputStream, workbookPath, StandardCopyOption.REPLACE_EXISTING);
        return workbookPath;
    }

    private void deleteTempWorkbook(Path workbookPath) {
        if (workbookPath == null) {
            return;
        }

        try {
            Files.deleteIfExists(workbookPath);
        } catch (IOException ex) {
            log.warn("Could not delete temporary workbook file: {}", workbookPath, ex);
        }
    }

    private void parseSheet(StylesTable styles,
                            SharedStrings sharedStrings,
                            InputStream sheet,
                            Collection<String> requiredHeaders,
                            ExcelRowHandler rowHandler)
            throws SAXException, IOException, ParserConfigurationException {
        XMLReader xmlReader = XMLHelper.newXMLReader();
        XSSFSheetXMLHandler.SheetContentsHandler sheetHandler =
                new StreamingSheetContentsHandler(requiredHeaders, rowHandler);
        ContentHandler contentHandler = new XSSFSheetXMLHandler(
                styles,
                null,
                sharedStrings,
                sheetHandler,
                new DataFormatter(Locale.ENGLISH),
                false
        );

        xmlReader.setContentHandler(contentHandler);
        xmlReader.parse(new InputSource(sheet));
    }

    private static final class StreamingSheetContentsHandler implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final Collection<String> requiredHeaders;
        private final ExcelRowHandler rowHandler;

        private HeaderMapping headerMapping;
        private String[] currentValues;
        private int currentExcelRowNumber;
        private int lastSeenColumnIndex;

        private StreamingSheetContentsHandler(Collection<String> requiredHeaders, ExcelRowHandler rowHandler) {
            this.requiredHeaders = requiredHeaders;
            this.rowHandler = rowHandler;
        }

        @Override
        public void startRow(int rowNum) {
            this.currentExcelRowNumber = rowNum + 1;
            this.lastSeenColumnIndex = -1;
            this.currentValues = new String[currentBufferSize()];
        }

        @Override
        public void endRow(int rowNum) {
            if (currentExcelRowNumber == FIRST_EXCEL_ROW_NUMBER) {
                this.headerMapping = HeaderMapping.fromHeader(currentValues, requiredHeaders);
                rowHandler.header(headerMapping);
                return;
            }

            if (headerMapping == null) {
                throw new ExcelImportException("Excel header row is required.");
            }

            rowHandler.row(currentExcelRowNumber, currentValues);
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int columnIndex = columnIndex(cellReference);
            this.lastSeenColumnIndex = Math.max(lastSeenColumnIndex, columnIndex);

            if (headerMapping != null && columnIndex > headerMapping.getMaxColumnIndex()) {
                return;
            }
            
            ensureCapacity(columnIndex + 1);
            currentValues[columnIndex] = formattedValue;
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // Not part of import data.
        }

        private int currentBufferSize() {
            if (headerMapping == null) {
                return HEADER_BUFFER_SIZE;
            }
            return headerMapping.getMaxColumnIndex() + 1;
        }

        private int columnIndex(String cellReference) {
            if (cellReference == null) {
                return lastSeenColumnIndex + 1;
            }
            return new CellReference(cellReference).getCol();
        }

        private void ensureCapacity(int requiredCapacity) {
            if (requiredCapacity <= currentValues.length) {
                return;
            }

            String[] expandedValues = new String[Math.max(requiredCapacity, currentValues.length * 2)];
            System.arraycopy(currentValues, 0, expandedValues, 0, currentValues.length);
            this.currentValues = expandedValues;
        }
    }
}
