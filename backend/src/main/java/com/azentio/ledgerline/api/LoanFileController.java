package com.azentio.ledgerline.api;

import com.azentio.ledgerline.domain.LoanFile;
import com.azentio.ledgerline.service.LoanFileService;
import com.azentio.ledgerline.service.Submission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/loan-files")
public class LoanFileController {

    private final LoanFileService service;

    public LoanFileController(LoanFileService service) {
        this.service = service;
    }

    /** 201 for a newly evaluated file, 200 when the same fileId and payload were already processed, 409 on a changed payload. */
    @PostMapping
    public ResponseEntity<LoanFileResult> submit(@Valid @RequestBody LoanFile file) {
        Submission submission = service.submit(file);
        return ResponseEntity.status(submission.created() ? HttpStatus.CREATED : HttpStatus.OK).body(submission.result());
    }
}
