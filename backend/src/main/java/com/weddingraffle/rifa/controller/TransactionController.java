package com.weddingraffle.rifa.controller;

import com.weddingraffle.rifa.dto.TransactionCreateRequest;
import com.weddingraffle.rifa.dto.TransactionCreateResponse;
import com.weddingraffle.rifa.dto.TransactionQuoteRequest;
import com.weddingraffle.rifa.dto.TransactionQuoteResponse;
import com.weddingraffle.rifa.dto.TransactionRecoveryRequest;
import com.weddingraffle.rifa.dto.TransactionStatusResponse;
import com.weddingraffle.rifa.service.LuckyNumberPdfService;
import com.weddingraffle.rifa.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private static final int PDF_FILENAME_REFERENCE_LENGTH = 8;

    private final TransactionService transactionService;
    private final LuckyNumberPdfService luckyNumberPdfService;

    public TransactionController(TransactionService transactionService, LuckyNumberPdfService luckyNumberPdfService) {
        this.transactionService = transactionService;
        this.luckyNumberPdfService = luckyNumberPdfService;
    }

    @Operation(summary = "Calculate transaction total amount")
    @PostMapping("/quote")
    public ResponseEntity<TransactionQuoteResponse> quote(@Valid @RequestBody TransactionQuoteRequest request) {
        return ResponseEntity.ok(transactionService.quote(request));
    }

    @Operation(summary = "Create transaction and Mercado Pago checkout")
    @PostMapping
    public ResponseEntity<TransactionCreateResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransactionCreateRequest request) {
        return ResponseEntity.ok(transactionService.create(idempotencyKey, request));
    }

    @Operation(summary = "Recover lucky numbers by phone and recovery code")
    @PostMapping("/recovery")
    public ResponseEntity<TransactionStatusResponse> recover(@Valid @RequestBody TransactionRecoveryRequest request) {
        return ResponseEntity.ok(transactionService.recover(request));
    }

    @Operation(summary = "Get transaction payment status")
    @GetMapping("/{externalReference}/status")
    public ResponseEntity<TransactionStatusResponse> getStatus(@PathVariable String externalReference) {
        return ResponseEntity.ok(transactionService.getStatus(externalReference));
    }

    @Operation(summary = "Download lucky numbers PDF")
    @GetMapping("/{externalReference}/lucky-numbers.pdf")
    public ResponseEntity<byte[]> downloadLuckyNumbersPdf(@PathVariable String externalReference) {
        byte[] pdf = luckyNumberPdfService.generate(externalReference);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("Numeros_da_sorte_" + shortReference(externalReference) + ".pdf")
                                .build()
                                .toString())
                .body(pdf);
    }

    private static String shortReference(String externalReference) {
        String sanitizedReference = externalReference.replaceAll("[^A-Za-z0-9]", "");
        if (sanitizedReference.length() <= PDF_FILENAME_REFERENCE_LENGTH) {
            return sanitizedReference;
        }
        return sanitizedReference.substring(0, PDF_FILENAME_REFERENCE_LENGTH);
    }
}
