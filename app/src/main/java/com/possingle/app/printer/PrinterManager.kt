package com.possingle.app.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.Socket
import java.util.UUID

enum class PrinterConnectionType { BLUETOOTH, USB, LAN }
enum class PaperWidth(val charsPerLine: Int) { MM_58(32), MM_80(48) }

sealed class PrinterResult {
    object Success : PrinterResult()
    data class Failure(val message: String) : PrinterResult()
}

/**
 * Builds raw ESC/POS command bytes for a receipt. Works the same regardless of
 * transport (Bluetooth SPP, USB, or raw socket to a network printer on port 9100).
 */
object EscPos {
    private const val ESC = 0x1B
    private const val GS = 0x1D

    fun init(): ByteArray = byteArrayOf(ESC.toByte(), '@'.code.toByte())
    fun alignCenter(): ByteArray = byteArrayOf(ESC.toByte(), 'a'.code.toByte(), 1)
    fun alignLeft(): ByteArray = byteArrayOf(ESC.toByte(), 'a'.code.toByte(), 0)
    fun boldOn(): ByteArray = byteArrayOf(ESC.toByte(), 'E'.code.toByte(), 1)
    fun boldOff(): ByteArray = byteArrayOf(ESC.toByte(), 'E'.code.toByte(), 0)
    fun cutPaper(): ByteArray = byteArrayOf(GS.toByte(), 'V'.code.toByte(), 1)
    fun feedLines(n: Int): ByteArray = byteArrayOf(ESC.toByte(), 'd'.code.toByte(), n.toByte())
    fun text(s: String): ByteArray = (s + "\n").toByteArray(Charsets.UTF_8)

    fun buildReceipt(
        storeName: String,
        storeAddress: String,
        gstin: String?,
        billNumber: String? = null,
        lines: List<Triple<String, Double, Double>>, // name, qty, lineTotal
        subtotal: Double,
        gstTotal: Double,
        parcelCharge: Double = 0.0,
        roundOffAdjustment: Double = 0.0,
        grandTotal: Double,
        footerMessage: String = "Thank you, visit again!",
        paperWidth: PaperWidth
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        fun write(bytes: ByteArray) = out.write(bytes)

        write(init())
        write(alignCenter())
        write(boldOn())
        write(text(storeName))
        write(boldOff())
        write(text(storeAddress))
        if (gstin != null) write(text("GSTIN: $gstin"))
        if (billNumber != null) write(text("Bill: $billNumber"))
        write(alignLeft())
        write(text("-".repeat(paperWidth.charsPerLine)))
        lines.forEach { (name, qty, total) ->
            val left = "$name x$qty"
            val right = String.format("%.2f", total)
            val padding = (paperWidth.charsPerLine - left.length - right.length).coerceAtLeast(1)
            write(text(left + " ".repeat(padding) + right))
        }
        write(text("-".repeat(paperWidth.charsPerLine)))
        write(text("Subtotal:".padEnd(paperWidth.charsPerLine - 8) + String.format("%.2f", subtotal)))
        val halfGst = gstTotal / 2.0
        write(text("CGST:".padEnd(paperWidth.charsPerLine - 8) + String.format("%.2f", halfGst)))
        write(text("SGST:".padEnd(paperWidth.charsPerLine - 8) + String.format("%.2f", halfGst)))
        if (parcelCharge > 0.0) {
            write(text("Parcel Charge:".padEnd(paperWidth.charsPerLine - 8) + String.format("%.2f", parcelCharge)))
        }
        if (roundOffAdjustment != 0.0) {
            val sign = if (roundOffAdjustment > 0) "+" else ""
            write(text("Round off:".padEnd(paperWidth.charsPerLine - 8) + sign + String.format("%.2f", roundOffAdjustment)))
        }
        write(boldOn())
        write(text("TOTAL:".padEnd(paperWidth.charsPerLine - 8) + String.format("%.2f", grandTotal)))
        write(boldOff())
        write(alignCenter())
        write(text(footerMessage))
        write(feedLines(3))
        write(cutPaper())
        return out.toByteArray()
    }
}

class PrinterManager(private val context: Context) {

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var btSocket: BluetoothSocket? = null

    // --- Bluetooth (fully implemented) ---
    @SuppressLint("MissingPermission")
    fun pairedBluetoothPrinters(): List<BluetoothDevice> {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return adapter.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun connectBluetooth(device: BluetoothDevice): PrinterResult = withContext(Dispatchers.IO) {
        try {
            btSocket?.close()
            val socket = device.createRfcommSocketToServiceRecord(sppUuid)
            socket.connect()
            btSocket = socket
            PrinterResult.Success
        } catch (e: Exception) {
            PrinterResult.Failure(e.message ?: "Bluetooth connection failed")
        }
    }

    suspend fun printViaBluetooth(bytes: ByteArray): PrinterResult = withContext(Dispatchers.IO) {
        val socket = btSocket ?: return@withContext PrinterResult.Failure("No Bluetooth printer connected")
        try {
            socket.outputStream.write(bytes)
            socket.outputStream.flush()
            PrinterResult.Success
        } catch (e: Exception) {
            PrinterResult.Failure(e.message ?: "Bluetooth print failed")
        }
    }

    // --- LAN (fully implemented; most thermal printers listen on raw TCP port 9100) ---
    suspend fun printViaLan(ipAddress: String, port: Int = 9100, bytes: ByteArray): PrinterResult =
        withContext(Dispatchers.IO) {
            try {
                Socket(ipAddress, port).use { socket ->
                    val out: OutputStream = socket.getOutputStream()
                    out.write(bytes)
                    out.flush()
                }
                PrinterResult.Success
            } catch (e: Exception) {
                PrinterResult.Failure(e.message ?: "LAN print failed")
            }
        }

    // --- USB (needs a device-specific vendor/product ID; wire up with UsbManager on real hardware) ---
    /**
     * USB thermal printers vary by vendor. This stub shows the shape of the call;
     * fill in device detection and bulk transfer once you have the target printer's
     * USB vendor ID / product ID and endpoint addresses (get these from UsbManager
     * .getDeviceList() when the printer is plugged into the tablet).
     */
    suspend fun printViaUsb(bytes: ByteArray): PrinterResult = withContext(Dispatchers.IO) {
        PrinterResult.Failure(
            "USB printing needs your printer's vendor/product ID wired into UsbManager " +
                "— connect the printer to the tablet and check Settings > Printer for detected devices."
        )
    }

    suspend fun testPrint(
        connectionType: PrinterConnectionType,
        paperWidth: PaperWidth,
        lanIp: String? = null
    ): PrinterResult {
        val bytes = EscPos.buildReceipt(
            storeName = "Test Print",
            storeAddress = "Single-Screen POS",
            gstin = null,
            lines = listOf(Triple("Sample Item", 1.0, 10.0)),
            subtotal = 10.0,
            gstTotal = 0.0,
            grandTotal = 10.0,
            paperWidth = paperWidth
        )
        return when (connectionType) {
            PrinterConnectionType.BLUETOOTH -> printViaBluetooth(bytes)
            PrinterConnectionType.LAN -> printViaLan(lanIp ?: return PrinterResult.Failure("No IP set"), bytes = bytes)
            PrinterConnectionType.USB -> printViaUsb(bytes)
        }
    }
}
