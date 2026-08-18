package com.industrial.barcodescanner.presentation.screens.scan.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.industrial.barcodescanner.presentation.theme.CyanAccent
import com.industrial.barcodescanner.presentation.theme.SubtleGray
import com.industrial.barcodescanner.presentation.theme.SurfaceDark
import kotlin.math.abs

val WHEEL_ITEM_HEIGHT: Dp = 48.dp
const val WHEEL_VISIBLE_ITEMS = 5          // must be odd
const val WHEEL_PADDING_ITEMS = WHEEL_VISIBLE_ITEMS / 2

/**
 * iOS-style wheel picker using LazyColumn + snap fling.
 *
 * [onInteractingChange] reports whether the wheel is currently being
 * dragged/flung — used by the picker dialogs to pause their auto-advance
 * countdown while the user is actively scrolling.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onInteractingChange: (Boolean) -> Unit = {}
) {
    if (items.isEmpty()) return

    val listState  = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val snapFling  = rememberSnapFlingBehavior(lazyListState = listState)
    val centeredIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    var wasScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            wasScrolling = true
            onInteractingChange(true)
        } else if (wasScrolling) {
            wasScrolling = false
            val settled = listState.firstVisibleItemIndex
            onSelectionChange(settled)
            onInteractingChange(false)
        }
    }

    LaunchedEffect(selectedIndex) {
        if (!listState.isScrollInProgress && centeredIndex != selectedIndex) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyColumn(
        state          = listState,
        flingBehavior  = snapFling,
        contentPadding = PaddingValues(vertical = WHEEL_ITEM_HEIGHT * WHEEL_PADDING_ITEMS),
        modifier       = modifier.height(WHEEL_ITEM_HEIGHT * WHEEL_VISIBLE_ITEMS)
    ) {
        items(items.size) { idx ->
            val distance = abs(centeredIndex - idx).coerceAtMost(WHEEL_PADDING_ITEMS + 1)

            val alpha = when (distance) {
                0    -> 1.00f
                1    -> 0.55f
                2    -> 0.25f
                else -> 0.08f
            }
            val scale = when (distance) {
                0    -> 1.00f
                1    -> 0.86f
                2    -> 0.74f
                else -> 0.62f
            }

            Box(
                modifier         = Modifier
                    .height(WHEEL_ITEM_HEIGHT)
                    .fillMaxWidth()
                    .alpha(alpha)
                    .scale(scale),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text(
                    text       = items[idx],
                    fontSize   = 18.sp,
                    fontWeight = if (distance == 0) FontWeight.Bold else FontWeight.Normal,
                    color      = if (distance == 0) Color.White else SubtleGray,
                    textAlign  = TextAlign.Center,
                    maxLines   = 1
                )
            }
        }
    }
}

/**
 * A single centred wheel with the "highlight band" + top/bottom fade
 * gradients used by all of the scan-flow picker dialogs.
 */
@Composable
fun SingleWheelBox(
    items: List<String>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    onInteractingChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(WHEEL_ITEM_HEIGHT * WHEEL_VISIBLE_ITEMS)
    ) {
        // Centre highlight band
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT)
                .background(
                    color = CyanAccent.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(10.dp)
                )
        )

        WheelPicker(
            items = items,
            selectedIndex = selectedIndex,
            onSelectionChange = onSelectionChange,
            onInteractingChange = onInteractingChange,
            modifier = Modifier.fillMaxWidth()
        )

        // Top + bottom fade gradients (iOS feel)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT * WHEEL_PADDING_ITEMS)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(SurfaceDark, Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT * WHEEL_PADDING_ITEMS)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, SurfaceDark)
                    )
                )
        )
    }
}
