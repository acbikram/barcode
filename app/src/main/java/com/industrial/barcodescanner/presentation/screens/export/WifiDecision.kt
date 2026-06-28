package com.industrial.barcodescanner.presentation.screens.export

/** Type-safe replacement for the raw "kind" String in WifiDecisionRequest. */
sealed class WifiDecisionKind {
    /** Some items failed — [readyCount] are ready. Print ready / Cancel. */
    data class PrintOrCancel(val readyCount: Int) : WifiDecisionKind()

    /** Ready items were printed — offer to retry [failed] items. */
    data class RetryLeft(val printedCount: Int) : WifiDecisionKind()

    /** Nothing was printable at all — Retry / Cancel. */
    object RetryOrCancel : WifiDecisionKind()

    /** Some physical sheets failed (printer error) after [printedCount] printed. */
    data class ReprintSheets(val printedCount: Int, val failedSheets: Int) : WifiDecisionKind()
}

data class WifiDecisionRequest(
    val kind: WifiDecisionKind,
    val failed: List<WifiFailedItem> = emptyList()
)
