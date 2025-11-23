package com.tally.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.tally.domain.ContributionStats;
import com.tally.domain.ContributionStats.RoleStats;
import com.tally.domain.PullRequest;
import com.tally.domain.Issue;
import lombok.extern.slf4j.Slf4j;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

/**
 * PDF 리포트 생성 서비스
 */
@Slf4j
public class PDFReportService {

    // 색상 정의
    private static final Color PRIMARY_COLOR = new Color(79, 70, 229);    // Indigo
    private static final Color SUCCESS_COLOR = new Color(34, 197, 94);    // Green
    private static final Color WARNING_COLOR = new Color(245, 158, 11);   // Amber
    private static final Color DARK_COLOR = new Color(31, 41, 55);        // Gray-800
    private static final Color LIGHT_COLOR = new Color(249, 250, 251);    // Gray-50
    private static final Color BORDER_COLOR = new Color(229, 231, 235);   // Gray-200

    // 폰트 정의
    private Font titleFont;
    private Font headerFont;
    private Font subHeaderFont;
    private Font normalFont;
    private Font smallFont;
    private Font boldFont;

    public PDFReportService() {
        initializeFonts();
    }

    private void initializeFonts() {
        // 기본 폰트 설정 (한글 지원을 위해 나중에 필요시 확장)
        titleFont = new Font(Font.HELVETICA, 28, Font.BOLD, DARK_COLOR);
        headerFont = new Font(Font.HELVETICA, 18, Font.BOLD, DARK_COLOR);
        subHeaderFont = new Font(Font.HELVETICA, 14, Font.BOLD, DARK_COLOR);
        normalFont = new Font(Font.HELVETICA, 11, Font.NORMAL, DARK_COLOR);
        smallFont = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(107, 114, 128));
        boldFont = new Font(Font.HELVETICA, 11, Font.BOLD, DARK_COLOR);
    }

    /**
     * PDF 리포트 생성 (Base64 인코딩된 문자열 반환)
     */
    public String generateReport(ContributionStats stats) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 50, 50);
            PdfWriter writer = PdfWriter.getInstance(document, baos);

            document.open();

            // 헤더 섹션
            addHeader(document, stats);

            // 통계 카드 섹션
            addStatsSection(document, stats);

            // 역할 분포 섹션
            if (stats.getRoleDistribution() != null && !stats.getRoleDistribution().isEmpty()) {
                addRoleDistributionSection(document, stats);
            }

            // PR 섹션
            if (stats.getPullRequests() != null && !stats.getPullRequests().isEmpty()) {
                addPullRequestSection(document, stats);
            }

            // Issue 섹션
            if (stats.getIssues() != null && !stats.getIssues().isEmpty()) {
                addIssueSection(document, stats);
            }

            // 푸터
            addFooter(document);

            document.close();

            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.error("PDF 생성 실패: {}", e.getMessage(), e);
            throw new RuntimeException("PDF 생성 실패", e);
        }
    }

    private void addHeader(Document document, ContributionStats stats) throws DocumentException {
        // 상단 장식 라인
        PdfPTable topLine = new PdfPTable(1);
        topLine.setWidthPercentage(100);
        PdfPCell lineCell = new PdfPCell();
        lineCell.setBackgroundColor(PRIMARY_COLOR);
        lineCell.setFixedHeight(5);
        lineCell.setBorder(Rectangle.NO_BORDER);
        topLine.addCell(lineCell);
        topLine.setSpacingAfter(30);
        document.add(topLine);

        // 타이틀
        Paragraph title = new Paragraph("CONTRIBUTION CERTIFICATE", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(15);
        document.add(title);

        // 서브 타이틀
        Font subtitleFont = new Font(Font.HELVETICA, 12, Font.NORMAL, new Color(107, 114, 128));
        Paragraph subtitle = new Paragraph("GitHub Repository Contribution Analysis", subtitleFont);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        subtitle.setSpacingAfter(25);
        document.add(subtitle);

        // 구분선
        addDivider(document);

        // 사용자 이름 (크게 표시)
        String username = stats.getUsername() != null ? stats.getUsername() : stats.getUserId();
        Font userFont = new Font(Font.HELVETICA, 24, Font.BOLD, PRIMARY_COLOR);
        Paragraph userPara = new Paragraph(username, userFont);
        userPara.setAlignment(Element.ALIGN_CENTER);
        userPara.setSpacingBefore(20);
        userPara.setSpacingAfter(10);
        document.add(userPara);

        // 레포지토리 이름
        Paragraph repoName = new Paragraph(stats.getRepositoryFullName(), headerFont);
        repoName.setAlignment(Element.ALIGN_CENTER);
        repoName.setSpacingAfter(15);
        document.add(repoName);

        // 활동 기간
        String periodStr = "Activity Period: ";
        if (stats.getFirstCommitDate() != null && stats.getLastCommitDate() != null) {
            periodStr += stats.getFirstCommitDate() + " ~ " + stats.getLastCommitDate();
        } else {
            periodStr += "N/A";
        }
        Paragraph period = new Paragraph(periodStr, normalFont);
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(5);
        document.add(period);

        // 생성 날짜
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        Paragraph date = new Paragraph("Generated: " + dateStr, smallFont);
        date.setAlignment(Element.ALIGN_CENTER);
        date.setSpacingAfter(30);
        document.add(date);
    }

    private void addStatsSection(Document document, ContributionStats stats) throws DocumentException {
        Paragraph sectionTitle = new Paragraph("Overview", headerFont);
        sectionTitle.setSpacingBefore(20);
        sectionTitle.setSpacingAfter(15);
        document.add(sectionTitle);

        // 3열 테이블로 통계 카드 표시
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setSpacingAfter(20);

        // 총 커밋
        addStatCard(table, "Total Commits", String.valueOf(stats.getTotalCommits()), "in project", PRIMARY_COLOR);

        // 내 커밋
        addStatCard(table, "My Commits", String.valueOf(stats.getUserCommits()),
            String.format("%.1f%% contribution", stats.getCommitPercentage()), SUCCESS_COLOR);

        // 코드 변경량
        String codeChanges = String.format("+%d / -%d",
            stats.getAdditions(),
            stats.getDeletions());
        addStatCard(table, "Code Changes", codeChanges, "lines modified", WARNING_COLOR);

        document.add(table);
    }

    private void addStatCard(PdfPTable table, String label, String value, String subValue, Color accentColor) {
        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(BORDER_COLOR);
        cell.setBorderWidth(1);
        cell.setPadding(15);
        cell.setBackgroundColor(LIGHT_COLOR);

        // 라벨
        Paragraph labelPara = new Paragraph(label, smallFont);
        labelPara.setSpacingAfter(5);
        cell.addElement(labelPara);

        // 값
        Font valueFont = new Font(Font.HELVETICA, 24, Font.BOLD, accentColor);
        Paragraph valuePara = new Paragraph(value, valueFont);
        valuePara.setSpacingAfter(3);
        cell.addElement(valuePara);

        // 서브값
        Paragraph subPara = new Paragraph(subValue, smallFont);
        cell.addElement(subPara);

        table.addCell(cell);
    }

    private void addRoleDistributionSection(Document document, ContributionStats stats) throws DocumentException {
        Paragraph sectionTitle = new Paragraph("Role Distribution", headerFont);
        sectionTitle.setSpacingBefore(20);
        sectionTitle.setSpacingAfter(15);
        document.add(sectionTitle);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 3});
        table.setSpacingAfter(20);

        // 역할별 색상 매핑
        Map<String, Color> roleColors = Map.of(
            "backend", new Color(59, 130, 246),
            "frontend", new Color(16, 185, 129),
            "infrastructure", new Color(245, 158, 11),
            "test", new Color(139, 92, 246),
            "documentation", new Color(236, 72, 153),
            "configuration", new Color(107, 114, 128),
            "other", new Color(156, 163, 175)
        );

        stats.getRoleDistribution().entrySet().stream()
            .filter(entry -> entry.getValue().getCommitCount() > 0)
            .sorted((a, b) -> Double.compare(b.getValue().getPercentage(), a.getValue().getPercentage()))
            .forEach(entry -> {
                try {
                    String role = entry.getKey();
                    RoleStats roleStats = entry.getValue();
                    Color color = roleColors.getOrDefault(role.toLowerCase(), DARK_COLOR);

                    // 역할명
                    PdfPCell nameCell = new PdfPCell();
                    nameCell.setBorder(Rectangle.NO_BORDER);
                    nameCell.setPadding(8);
                    Font roleFont = new Font(Font.HELVETICA, 11, Font.BOLD, color);
                    nameCell.addElement(new Paragraph(capitalize(role), roleFont));
                    table.addCell(nameCell);

                    // 프로그레스 바 + 퍼센트
                    PdfPCell barCell = new PdfPCell();
                    barCell.setBorder(Rectangle.NO_BORDER);
                    barCell.setPadding(8);

                    // 내부 테이블로 프로그레스 바 시뮬레이션
                    PdfPTable barTable = new PdfPTable(2);
                    barTable.setWidthPercentage(100);
                    barTable.setWidths(new float[]{4, 1});

                    // 바 셀
                    PdfPCell innerBarCell = new PdfPCell();
                    innerBarCell.setBorder(Rectangle.NO_BORDER);
                    innerBarCell.setBackgroundColor(new Color(229, 231, 235));
                    innerBarCell.setPadding(0);
                    innerBarCell.setFixedHeight(20);

                    // 채워진 부분
                    PdfPTable filledBar = new PdfPTable(1);
                    filledBar.setWidthPercentage((float) roleStats.getPercentage());
                    PdfPCell filledCell = new PdfPCell();
                    filledCell.setBorder(Rectangle.NO_BORDER);
                    filledCell.setBackgroundColor(color);
                    filledCell.setFixedHeight(20);
                    filledBar.addCell(filledCell);
                    innerBarCell.addElement(filledBar);
                    barTable.addCell(innerBarCell);

                    // 퍼센트 텍스트
                    PdfPCell percentCell = new PdfPCell();
                    percentCell.setBorder(Rectangle.NO_BORDER);
                    percentCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    percentCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    String percentText = String.format("%.1f%% (%d)", roleStats.getPercentage(), roleStats.getCommitCount());
                    percentCell.addElement(new Paragraph(percentText, smallFont));
                    barTable.addCell(percentCell);

                    barCell.addElement(barTable);
                    table.addCell(barCell);
                } catch (Exception e) {
                    log.error("역할 분포 추가 실패: {}", e.getMessage());
                }
            });

        document.add(table);
    }

    private void addPullRequestSection(Document document, ContributionStats stats) throws DocumentException {
        Paragraph sectionTitle = new Paragraph("Pull Requests (" + stats.getPullRequests().size() + ")", headerFont);
        sectionTitle.setSpacingBefore(20);
        sectionTitle.setSpacingAfter(15);
        document.add(sectionTitle);

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 5, 2});
        table.setSpacingAfter(15);

        // 헤더
        addTableHeader(table, "#");
        addTableHeader(table, "Title");
        addTableHeader(table, "Status");

        // 데이터 (최대 10개)
        int count = 0;
        for (PullRequest pr : stats.getPullRequests()) {
            if (count++ >= 10) break;

            addTableCell(table, String.valueOf(pr.getNumber()));
            addTableCell(table, pr.getTitle());
            addStatusCell(table, pr.getState());
        }

        document.add(table);

        if (stats.getPullRequests().size() > 10) {
            Paragraph more = new Paragraph("... and " + (stats.getPullRequests().size() - 10) + " more", smallFont);
            more.setAlignment(Element.ALIGN_CENTER);
            more.setSpacingAfter(10);
            document.add(more);
        }
    }

    private void addIssueSection(Document document, ContributionStats stats) throws DocumentException {
        Paragraph sectionTitle = new Paragraph("Issues (" + stats.getIssues().size() + ")", headerFont);
        sectionTitle.setSpacingBefore(20);
        sectionTitle.setSpacingAfter(15);
        document.add(sectionTitle);

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 5, 2});
        table.setSpacingAfter(15);

        // 헤더
        addTableHeader(table, "#");
        addTableHeader(table, "Title");
        addTableHeader(table, "Status");

        // 데이터 (최대 10개)
        int count = 0;
        for (Issue issue : stats.getIssues()) {
            if (count++ >= 10) break;

            addTableCell(table, String.valueOf(issue.getNumber()));
            addTableCell(table, issue.getTitle());
            addStatusCell(table, issue.getState());
        }

        document.add(table);

        if (stats.getIssues().size() > 10) {
            Paragraph more = new Paragraph("... and " + (stats.getIssues().size() - 10) + " more", smallFont);
            more.setAlignment(Element.ALIGN_CENTER);
            more.setSpacingAfter(10);
            document.add(more);
        }
    }

    private void addTableHeader(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, boldFont));
        cell.setBackgroundColor(LIGHT_COLOR);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(10);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, normalFont));
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(8);
        table.addCell(cell);
    }

    private void addStatusCell(PdfPTable table, String status) {
        Color bgColor;
        Color textColor = Color.WHITE;

        if ("merged".equalsIgnoreCase(status)) {
            bgColor = new Color(139, 92, 246);  // Purple
        } else if ("open".equalsIgnoreCase(status)) {
            bgColor = SUCCESS_COLOR;
        } else if ("closed".equalsIgnoreCase(status)) {
            bgColor = new Color(239, 68, 68);   // Red
        } else {
            bgColor = new Color(107, 114, 128);
            textColor = Color.WHITE;
        }

        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(BORDER_COLOR);
        cell.setBackgroundColor(bgColor);
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Font statusFont = new Font(Font.HELVETICA, 10, Font.BOLD, textColor);
        cell.addElement(new Paragraph(status.toUpperCase(), statusFont));
        table.addCell(cell);
    }

    private void addDivider(Document document) throws DocumentException {
        PdfPTable divider = new PdfPTable(1);
        divider.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BORDER_COLOR);
        cell.setBorderWidth(1);
        cell.setFixedHeight(1);
        divider.addCell(cell);
        divider.setSpacingAfter(10);
        document.add(divider);
    }

    private void addFooter(Document document) throws DocumentException {
        // 하단 장식 라인
        PdfPTable bottomLine = new PdfPTable(1);
        bottomLine.setWidthPercentage(100);
        PdfPCell lineCell = new PdfPCell();
        lineCell.setBackgroundColor(PRIMARY_COLOR);
        lineCell.setFixedHeight(3);
        lineCell.setBorder(Rectangle.NO_BORDER);
        bottomLine.addCell(lineCell);
        bottomLine.setSpacingBefore(30);
        bottomLine.setSpacingAfter(15);
        document.add(bottomLine);

        // 참고용 고지문 (박스)
        PdfPTable disclaimerBox = new PdfPTable(1);
        disclaimerBox.setWidthPercentage(90);
        PdfPCell disclaimerCell = new PdfPCell();
        disclaimerCell.setBackgroundColor(new Color(254, 249, 195)); // Yellow-100
        disclaimerCell.setBorderColor(new Color(253, 224, 71)); // Yellow-300
        disclaimerCell.setPadding(12);

        Font disclaimerFont = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(133, 77, 14)); // Yellow-800
        Paragraph disclaimer = new Paragraph(
            "* This certificate is for reference purposes only. " +
            "Data is collected from public GitHub API and may not reflect all contributions.",
            disclaimerFont
        );
        disclaimer.setAlignment(Element.ALIGN_CENTER);
        disclaimerCell.addElement(disclaimer);
        disclaimerBox.addCell(disclaimerCell);
        disclaimerBox.setSpacingAfter(15);
        document.add(disclaimerBox);

        // Tally 로고/이름
        Font footerBoldFont = new Font(Font.HELVETICA, 10, Font.BOLD, DARK_COLOR);
        Paragraph footerTitle = new Paragraph("Tally", footerBoldFont);
        footerTitle.setAlignment(Element.ALIGN_CENTER);
        footerTitle.setSpacingAfter(3);
        document.add(footerTitle);

        Paragraph footerDesc = new Paragraph("GitHub Contribution Analytics", smallFont);
        footerDesc.setAlignment(Element.ALIGN_CENTER);
        document.add(footerDesc);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
