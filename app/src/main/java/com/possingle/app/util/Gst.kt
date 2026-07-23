package com.possingle.app.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.round

object Gst {
    /** Rounds to 2 decimal places using standard half-up rounding, as GST invoices require. */
    fun round2(value: Double): Double = round(value * 100.0) / 100.0

    /** GST amount for a line, given the pre-tax price, quantity and GST%. */
    fun gstAmount(unitPrice: Double, qty: Double, gstPercent: Double): Double =
        round2(unitPrice * qty * gstPercent / 100.0)

    /** Pre-tax line total (unitPrice * qty), rounded. */
    fun lineSubtotal(unitPrice: Double, qty: Double): Double =
        round2(unitPrice * qty)

    /** Pre-tax + GST for a single line. */
    fun lineTotal(unitPrice: Double, qty: Double, gstPercent: Double): Double =
        round2(lineSubtotal(unitPrice, qty) + gstAmount(unitPrice, qty, gstPercent))

    /**
     * Rounds a bill's grand total to the nearest whole rupee (or always up /
     * always down, per the store's preference). Returns the rounded total —
     * the difference from the original is the "Round off" line on the bill.
     */
    fun applyRoundOff(grandTotal: Double, mode: RoundOffMode): Double = when (mode) {
        RoundOffMode.NONE -> grandTotal
        RoundOffMode.NEAREST -> Math.round(grandTotal).toDouble()
        RoundOffMode.UP -> kotlin.math.ceil(grandTotal)
        RoundOffMode.DOWN -> kotlin.math.floor(grandTotal)
    }
}

enum class RoundOffMode { NONE, NEAREST, UP, DOWN }

private val inr = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

fun Double.asCurrency(): String = inr.format(this)
