package com.weddingraffle.rifa.controller;

import com.weddingraffle.rifa.dto.AdminGiftMessageResponse;
import com.weddingraffle.rifa.dto.AdminTransactionResponse;
import com.weddingraffle.rifa.dto.AdminTransactionSummaryResponse;
import com.weddingraffle.rifa.dto.CapacityReviewDecisionRequest;
import com.weddingraffle.rifa.dto.CashTransactionCreateRequest;
import com.weddingraffle.rifa.dto.CashTransactionCreateResponse;
import com.weddingraffle.rifa.service.AdminTransactionService;
import com.weddingraffle.rifa.service.LuckyNumberPdfService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
public class AdminTransactionController {

    private static final int PDF_FILENAME_REFERENCE_LENGTH = 8;

    private final AdminTransactionService adminTransactionService;
    private final LuckyNumberPdfService luckyNumberPdfService;

    public AdminTransactionController(
            AdminTransactionService adminTransactionService, LuckyNumberPdfService luckyNumberPdfService) {
        this.adminTransactionService = adminTransactionService;
        this.luckyNumberPdfService = luckyNumberPdfService;
    }

    @Operation(summary = "List gift messages for admin")
    @GetMapping("/messages")
    public ResponseEntity<Page<AdminGiftMessageResponse>> listGiftMessages(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(adminTransactionService.listGiftMessages(pageable));
    }

    @Operation(summary = "Get transaction summary for admin")
    @GetMapping("/summary")
    public ResponseEntity<AdminTransactionSummaryResponse> getSummary() {
        return ResponseEntity.ok(adminTransactionService.getSummary());
    }

    @Operation(summary = "List transactions for admin")
    @GetMapping
    public ResponseEntity<Page<AdminTransactionResponse>> list(
            @RequestParam(required = false) String query,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        return ResponseEntity.ok(adminTransactionService.list(query, pageable, isMaster(authentication)));
    }

    @Operation(summary = "Create approved cash transaction for admin")
    @PostMapping("/cash")
    public ResponseEntity<CashTransactionCreateResponse> createCashTransaction(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CashTransactionCreateRequest request,
            Authentication authentication) {
        CashTransactionCreateResponse response = adminTransactionService.createCashTransaction(idempotencyKey, request);
        return ResponseEntity.ok(isMaster(authentication) ? response : withoutFinancialValues(response));
    }

    @Operation(summary = "Download all approved lucky numbers PDF for the transaction participant")
    @GetMapping("/{externalReference}/participant-lucky-numbers.pdf")
    public ResponseEntity<byte[]> downloadParticipantLuckyNumbersPdf(@PathVariable String externalReference) {
        byte[] pdf = luckyNumberPdfService.generateForParticipant(externalReference);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("Numeros_do_participante_" + shortReference(externalReference) + ".pdf")
                                .build()
                                .toString())
                .body(pdf);
    }

    @Operation(summary = "Delete cash transaction for admin")
    @DeleteMapping("/{externalReference}")
    public ResponseEntity<Void> deleteCashTransaction(@PathVariable String externalReference) {
        adminTransactionService.deleteCashTransaction(externalReference);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @Operation(summary = "Resolve an approved payment without lucky-number capacity")
    @PutMapping("/{externalReference}/capacity-review")
    public ResponseEntity<Void> resolveCapacityReview(
            @PathVariable String externalReference, @Valid @RequestBody CapacityReviewDecisionRequest request) {
        adminTransactionService.resolveCapacityReview(externalReference, request.decision());
        return ResponseEntity.noContent().build();
    }

    private static String shortReference(String externalReference) {
        String sanitizedReference = externalReference.replaceAll("[^A-Za-z0-9]", "");
        if (sanitizedReference.length() <= PDF_FILENAME_REFERENCE_LENGTH) {
            return sanitizedReference;
        }
        return sanitizedReference.substring(0, PDF_FILENAME_REFERENCE_LENGTH);
    }

    private static boolean isMaster(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch("ROLE_MASTER"::equals);
    }

    private static CashTransactionCreateResponse withoutFinancialValues(CashTransactionCreateResponse response) {
        return new CashTransactionCreateResponse(
                response.externalReference(),
                response.recoveryCode(),
                response.name(),
                response.phone(),
                response.email(),
                response.paymentMethod(),
                response.status(),
                response.quantity(),
                null,
                response.participantFlagName(),
                response.participantFlagEmoji(),
                response.luckyNumbers(),
                response.previousLuckyNumbers(),
                response.totalLuckyNumbers());
    }
}
