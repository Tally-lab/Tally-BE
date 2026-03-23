package com.devpulse.agent.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Media;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;

@Slf4j
@Service
public class PdfGenerationService {

    @Value("${report.frontend-url:http://localhost:5174}")
    private String frontendUrl;

    public byte[] generatePdf(String reportId) {
        log.info("Generating PDF for report: {}", reportId);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true)
            );
            BrowserContext context = browser.newContext();
            Page page = context.newPage();

            // FE 리포트 페이지로 이동
            String reportUrl = frontendUrl + "/report/" + reportId;
            log.info("Navigating to: {}", reportUrl);
            page.navigate(reportUrl);

            // 차트 렌더링 대기 (Recharts는 비동기 렌더링)
            page.waitForSelector("[data-report-ready='true']",
                    new Page.WaitForSelectorOptions().setTimeout(30000));

            // 추가 안정화 대기
            page.waitForTimeout(2000);

            // PDF 생성 (A4)
            page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));
            Page.PdfOptions pdfOptions = new Page.PdfOptions()
                    .setFormat("A4")
                    .setPrintBackground(true);
            pdfOptions.margin = new com.microsoft.playwright.options.Margin()
                    .setTop("20mm")
                    .setBottom("20mm")
                    .setLeft("15mm")
                    .setRight("15mm");
            byte[] pdf = page.pdf(pdfOptions);

            log.info("PDF generated: {} bytes", pdf.length);
            browser.close();
            return pdf;
        } catch (Exception e) {
            log.error("PDF generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }
}
