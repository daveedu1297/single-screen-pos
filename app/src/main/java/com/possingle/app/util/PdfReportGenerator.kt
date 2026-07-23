package com.possingle.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.possingle.app.data.BillEntity
import com.possingle.app.data.CategorySalesRow
import com.possingle.app.data.ItemSalesRow
import com.possingle.app.data.PaymentSummaryRow
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Four separate, focused PDF reports (rather than one giant combined file):
 * Sales Summary, Item-wise, Category-wise, and an "All Reports" file that
 * bundles the three together for whoever wants everything at once.
 * Built with Android's own PdfDocument — no external library, so it can't
 * introduce a new crash surface.
 */
object PdfReportGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f

    private class PageWriter(private val document: PdfDocument) {
        var pageNumber = 1
        var page: PdfDocument.Page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas
        var y = MARGIN

        fun newPageIfNeeded(linesNeeded: Int = 1) {
            if (y + linesNeeded * 16f > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                pageNumber += 1
                page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN
            }
        }

        fun finish() = document.finishPage(page)
    }

    private val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
    private val headingPaint = Paint().apply { textSize = 13f; isFakeBoldText = true }
    private val bodyPaint = Paint().apply { textSize = 11f }
    private val smallPaint = Paint().apply { textSize = 9f; color = 0xFF666666.toInt() }

    private fun dateHeader(w: PageWriter, storeName: String, reportTitle: String) {
        val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        w.canvas.drawText(storeName, MARGIN, w.y, titlePaint); w.y += 22f
        w.canvas.drawText("$reportTitle — generated $dateStr", MARGIN, w.y, smallPaint); w.y += 26f
    }

    private fun saveAndReturn(context: Context, document: PdfDocument, fileName: String): File {
        val reportsDir = File(context.getExternalFilesDir(null), "reports").apply { mkdirs() }
        val file = File(reportsDir, fileName)
        FileOutputStream(file).use { out -> document.writeTo(out) }
        document.close()
        return file
    }

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

    /** Bill count, total sales, GST, expenses, purchases, and payment-mode breakdown for today. */
    fun generateSalesSummary(
        context: Context,
        storeName: String,
        bills: List<BillEntity>,
        paymentSummary: List<PaymentSummaryRow>,
        expensesTotal: Double,
        purchasesTotal: Double
    ): File {
        val document = PdfDocument()
        val w = PageWriter(document)
        dateHeader(w, storeName, "Sales Summary")

        val totalSales = bills.sumOf { it.grandTotal }
        val totalGst = bills.sumOf { it.gstTotal }

        w.canvas.drawText("Bills today: ${bills.size}", MARGIN, w.y, bodyPaint); w.y += 18f
        w.canvas.drawText("Total Sales: ${totalSales.asCurrency()}", MARGIN, w.y, bodyPaint); w.y += 18f
        w.canvas.drawText("Total GST: ${totalGst.asCurrency()}", MARGIN, w.y, bodyPaint); w.y += 18f
        w.canvas.drawText("Total Expenses: ${expensesTotal.asCurrency()}", MARGIN, w.y, bodyPaint); w.y += 18f
        w.canvas.drawText("Total Purchases: ${purchasesTotal.asCurrency()}", MARGIN, w.y, bodyPaint); w.y += 18f
        val netCash = totalSales - expensesTotal - purchasesTotal
        w.canvas.drawText("Net (Sales - Expenses - Purchases): ${netCash.asCurrency()}", MARGIN, w.y, bodyPaint); w.y += 24f

        w.canvas.drawText("Payment Mode Breakdown", MARGIN, w.y, headingPaint); w.y += 18f
        if (paymentSummary.isEmpty()) {
            w.canvas.drawText("No sales recorded.", MARGIN, w.y, bodyPaint); w.y += 16f
        } else {
            paymentSummary.forEach { row ->
                w.newPageIfNeeded()
                w.canvas.drawText("${row.paymentMode} (${row.count} bills)", MARGIN, w.y, bodyPaint)
                w.canvas.drawText(row.total.asCurrency(), PAGE_WIDTH - MARGIN - 80f, w.y, bodyPaint)
                w.y += 16f
            }
        }

        w.finish()
        return saveAndReturn(context, document, "sales_summary_${stamp()}.pdf")
    }

    fun generateItemWiseReport(context: Context, storeName: String, itemizedSales: List<ItemSalesRow>): File {
        val document = PdfDocument()
        val w = PageWriter(document)
        dateHeader(w, storeName, "Item-wise Sales Report")

        if (itemizedSales.isEmpty()) {
            w.canvas.drawText("No items sold today.", MARGIN, w.y, bodyPaint)
        } else {
            w.canvas.drawText("Item", MARGIN, w.y, smallPaint)
            w.canvas.drawText("Qty", PAGE_WIDTH - MARGIN - 160f, w.y, smallPaint)
            w.canvas.drawText("Revenue", PAGE_WIDTH - MARGIN - 80f, w.y, smallPaint)
            w.y += 14f
            itemizedSales.forEach { row ->
                w.newPageIfNeeded()
                w.canvas.drawText(row.itemName, MARGIN, w.y, bodyPaint)
                w.canvas.drawText(row.totalQty.toString(), PAGE_WIDTH - MARGIN - 160f, w.y, bodyPaint)
                w.canvas.drawText(row.totalRevenue.asCurrency(), PAGE_WIDTH - MARGIN - 80f, w.y, bodyPaint)
                w.y += 16f
            }
        }
        w.finish()
        return saveAndReturn(context, document, "item_wise_report_${stamp()}.pdf")
    }

    fun generateCategoryWiseReport(context: Context, storeName: String, categorySales: List<CategorySalesRow>): File {
        val document = PdfDocument()
        val w = PageWriter(document)
        dateHeader(w, storeName, "Category-wise Sales Report")

        if (categorySales.isEmpty()) {
            w.canvas.drawText("No category sales today.", MARGIN, w.y, bodyPaint)
        } else {
            w.canvas.drawText("Category", MARGIN, w.y, smallPaint)
            w.canvas.drawText("Qty", PAGE_WIDTH - MARGIN - 160f, w.y, smallPaint)
            w.canvas.drawText("Revenue", PAGE_WIDTH - MARGIN - 80f, w.y, smallPaint)
            w.y += 14f
            categorySales.forEach { row ->
                w.newPageIfNeeded()
                w.canvas.drawText(row.categoryName, MARGIN, w.y, bodyPaint)
                w.canvas.drawText(row.totalQty.toString(), PAGE_WIDTH - MARGIN - 160f, w.y, bodyPaint)
                w.canvas.drawText(row.totalRevenue.asCurrency(), PAGE_WIDTH - MARGIN - 80f, w.y, bodyPaint)
                w.y += 16f
            }
        }
        w.finish()
        return saveAndReturn(context, document, "category_wise_report_${stamp()}.pdf")
    }

    /** Everything in one file: sales summary + item-wise + category-wise. */
    fun generateAllReports(
        context: Context,
        storeName: String,
        bills: List<BillEntity>,
        paymentSummary: List<PaymentSummaryRow>,
        itemizedSales: List<ItemSalesRow>,
        categorySales: List<CategorySalesRow>,
        expensesTotal: Double,
        purchasesTotal: Double
    ): File {
        val document = PdfDocument()
        val w = PageWriter(document)
        dateHeader(w, storeName, "All Reports")

        val totalSales = bills.sumOf { it.grandTotal }
        val totalGst = bills.sumOf { it.gstTotal }

        w.canvas.drawText("Sales Summary", MARGIN, w.y, headingPaint); w.y += 18f
        w.canvas.drawText("Bills: ${bills.size}   Sales: ${totalSales.asCurrency()}   GST: ${totalGst.asCurrency()}", MARGIN, w.y, bodyPaint); w.y += 16f
        w.canvas.drawText("Expenses: ${expensesTotal.asCurrency()}   Purchases: ${purchasesTotal.asCurrency()}", MARGIN, w.y, bodyPaint); w.y += 22f

        w.canvas.drawText("Payment Mode Breakdown", MARGIN, w.y, headingPaint); w.y += 18f
        paymentSummary.forEach { row ->
            w.newPageIfNeeded()
            w.canvas.drawText("${row.paymentMode} (${row.count})", MARGIN, w.y, bodyPaint)
            w.canvas.drawText(row.total.asCurrency(), PAGE_WIDTH - MARGIN - 80f, w.y, bodyPaint)
            w.y += 16f
        }
        w.y += 12f

        w.newPageIfNeeded(2)
        w.canvas.drawText("Item-wise Sales", MARGIN, w.y, headingPaint); w.y += 18f
        itemizedSales.forEach { row ->
            w.newPageIfNeeded()
            w.canvas.drawText(row.itemName, MARGIN, w.y, bodyPaint)
            w.canvas.drawText("${row.totalQty} · ${row.totalRevenue.asCurrency()}", PAGE_WIDTH - MARGIN - 140f, w.y, bodyPaint)
            w.y += 16f
        }
        w.y += 12f

        w.newPageIfNeeded(2)
        w.canvas.drawText("Category-wise Sales", MARGIN, w.y, headingPaint); w.y += 18f
        categorySales.forEach { row ->
            w.newPageIfNeeded()
            w.canvas.drawText(row.categoryName, MARGIN, w.y, bodyPaint)
            w.canvas.drawText("${row.totalQty} · ${row.totalRevenue.asCurrency()}", PAGE_WIDTH - MARGIN - 140f, w.y, bodyPaint)
            w.y += 16f
        }

        w.finish()
        return saveAndReturn(context, document, "all_reports_${stamp()}.pdf")
    }

    /** Opens the system share sheet so the PDF can be saved, emailed, printed, etc. */
    fun sharePdf(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share report"))
    }
}
