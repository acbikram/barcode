package com.industrial.barcodescanner.presentation.screens.scan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.industrial.barcodescanner.R
import com.industrial.barcodescanner.presentation.screens.scan.components.SingleWheelBox
import com.industrial.barcodescanner.presentation.theme.CyanAccent
import com.industrial.barcodescanner.presentation.theme.SubtleGray
import com.industrial.barcodescanner.presentation.theme.SurfaceDark
import kotlinx.coroutines.delay

/**
 * A single wheel-picker dialog that auto-confirms the currently highlighted
 * value after [autoAdvanceSeconds] of no interaction.
 *
 * Used for the Tag Type, Unit Type, and Copies pickers in the scan flow.
 * Scrolling the wheel resets the countdown; tapping "Done" confirms
 * immediately.
 */
@Composable
fun AutoAdvanceWheelDialog(
    title: String,
    items: List<String>,
    initialSelectedIndex: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    productName: String? = null,
    barcode: String? = null,
    autoAdvanceSeconds: Int = 3
) {
    var selectedIndex by remember { mutableIntStateOf(initialSelectedIndex.coerceIn(0, items.lastIndex)) }
    var secondsLeft by remember { mutableIntStateOf(autoAdvanceSeconds) }
    var confirmed by remember { mutableStateOf(false) }
    var isInteracting by remember { mutableStateOf(false) }

    // Countdown ticks once per second. While the wheel is actively being
    // dragged/flung, the countdown is held at autoAdvanceSeconds — it only
    // starts running down once the user stops touching the wheel, so a
    // selection made just before the limit can't be overridden mid-scroll.
    LaunchedEffect(Unit) {
        while (!confirmed) {
            delay(1000)
            if (confirmed) break
            if (isInteracting) {
                secondsLeft = autoAdvanceSeconds
            } else {
                secondsLeft -= 1
                if (secondsLeft <= 0) {
                    confirmed = true
                    onConfirm(selectedIndex)
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = SurfaceDark,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                if (!productName.isNullOrBlank()) {
                    Text(
                        text = productName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
                if (!barcode.isNullOrBlank()) {
                    Text(
                        text = barcode,
                        style = MaterialTheme.typography.bodySmall.copy(color = SubtleGray),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                SingleWheelBox(
                    items = items,
                    selectedIndex = selectedIndex,
                    onSelectionChange = { idx -> selectedIndex = idx },
                    onInteractingChange = { interacting ->
                        isInteracting = interacting
                        secondsLeft = autoAdvanceSeconds
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.auto_select_in_format, secondsLeft),
                    style = MaterialTheme.typography.labelSmall.copy(color = SubtleGray)
                )

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF30363D))
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = SubtleGray)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (!confirmed) {
                                confirmed = true
                                onConfirm(selectedIndex)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.done), color = CyanAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Available Tag Type options, in display order. */
val TAG_TYPES = listOf("A4", "4PCS", "4PCS_DATE", "4PCS_SAME", "VEG")

/** Available Unit Type options, in display order. */
val UNIT_TYPES = listOf("PCS", "PKT", "CTN", "KGS")

/** Copies range shown in the Copies picker. */
val COPIES_OPTIONS = (1..10).map { it.toString() }

@Composable
fun TagTypePickerDialog(
    initialTagType: String,
    productName: String?,
    barcode: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialIndex = TAG_TYPES.indexOf(initialTagType).let { if (it >= 0) it else 0 }
    AutoAdvanceWheelDialog(
        title = stringResource(R.string.select_tag_type),
        items = TAG_TYPES,
        initialSelectedIndex = initialIndex,
        onConfirm = { idx -> onConfirm(TAG_TYPES[idx]) },
        onDismiss = onDismiss,
        productName = productName,
        barcode = barcode,
        autoAdvanceSeconds = 5
    )
}

@Composable
fun UnitTypePickerDialog(
    initialUnitType: String,
    productName: String?,
    barcode: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialIndex = UNIT_TYPES.indexOf(initialUnitType).let { if (it >= 0) it else 0 }
    AutoAdvanceWheelDialog(
        title = stringResource(R.string.select_unit_type),
        items = UNIT_TYPES,
        initialSelectedIndex = initialIndex,
        onConfirm = { idx -> onConfirm(UNIT_TYPES[idx]) },
        onDismiss = onDismiss,
        productName = productName,
        barcode = barcode
    )
}

@Composable
fun CopiesPickerDialog(
    initialCopies: Int,
    productName: String?,
    barcode: String?,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initialIndex = (initialCopies - 1).coerceIn(0, COPIES_OPTIONS.lastIndex)
    AutoAdvanceWheelDialog(
        title = stringResource(R.string.select_copies),
        items = COPIES_OPTIONS,
        initialSelectedIndex = initialIndex,
        onConfirm = { idx -> onConfirm(idx + 1) },
        onDismiss = onDismiss,
        productName = productName,
        barcode = barcode
    )
}

/**
 * Shown when a scan matches an existing row (same barcode + tag type + unit
 * type) — offers to merge by adding the new copies onto the existing count.
 */
@Composable
fun DuplicateCopiesDialog(
    existingCopies: Int,
    newCopies: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    productName: String? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.same_item_found)) },
        text = {
            Column {
                if (!productName.isNullOrBlank()) {
                    Text(
                        text = productName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                val previousLabel = stringResource(R.string.previous_copies)
                val newLabel = stringResource(R.string.new_copies)
                val finalLabel = stringResource(R.string.final_copies)
                val confirmSaveLabel = stringResource(R.string.confirm_save_question)
                Text(
                    buildAnnotatedString {
                        append(previousLabel)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("$existingCopies\n")
                        }
                        append(newLabel)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("$newCopies\n")
                        }
                        append(finalLabel)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("${existingCopies + newCopies}\n")
                        }
                        append(confirmSaveLabel)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
