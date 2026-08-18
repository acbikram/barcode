package com.industrial.barcodescanner.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.industrial.barcodescanner.presentation.theme.AppDimens
import com.industrial.barcodescanner.presentation.theme.BlueAccent
import com.industrial.barcodescanner.presentation.theme.CyanAccent
import com.industrial.barcodescanner.presentation.theme.ErrorRed
import com.industrial.barcodescanner.presentation.theme.GreenAccent
import com.industrial.barcodescanner.presentation.theme.OrangeAccent
import com.industrial.barcodescanner.presentation.theme.SubtleGray

/**
 * Shared, accessible frosted surfaces adapted from Near Expiry Manager. Opaque
 * tinted surfaces preserve readable contrast in both light and dark themes.
 */
private val GlassCardShape = RoundedCornerShape(20.dp)
private val GlassControlShape = RoundedCornerShape(16.dp)

@Composable
private fun isLightAppearance(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f

@Composable
private fun glassSurfaceColor(selected: Boolean, accent: Color): Color {
    val base = if (isLightAppearance()) Color(0xFFE6F3F8) else Color(0xFF132632)
    return if (selected) accent.copy(alpha = if (isLightAppearance()) 0.22f else 0.34f).compositeOver(base) else base
}

@Composable
private fun glassOutline(selected: Boolean, accent: Color): Color = when {
    selected -> accent.copy(alpha = if (isLightAppearance()) 0.90f else 0.96f)
    isLightAppearance() -> accent.copy(alpha = 0.34f)
    else -> accent.copy(alpha = 0.46f)
}

@Composable
fun GlassSectionCard(
    modifier: Modifier = Modifier,
    accent: Color = CyanAccent,
    selected: Boolean = false,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val emphasized = selected || pressed
    Surface(
        modifier = modifier
            .clip(GlassCardShape)
            .border(
                if (emphasized) 1.5.dp else 1.dp,
                glassOutline(emphasized, accent),
                GlassCardShape
            ),
        color = glassSurfaceColor(emphasized, accent),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = if (emphasized) 5.dp else 2.dp,
        tonalElevation = 0.dp,
        shape = GlassCardShape
    ) { content() }
}

@Composable
fun GlassSelectableOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    accent: Color = CyanAccent,
    leadingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val emphasized = selected || pressed
    val elevation by animateDpAsState(
        targetValue = if (emphasized) 5.dp else 0.dp,
        animationSpec = tween(durationMillis = 160),
        label = "glassSelectableElevation"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.MinimumTouchTarget)
            .clip(GlassControlShape)
            .border(
                if (emphasized) 1.5.dp else 1.dp,
                glassOutline(emphasized, accent),
                GlassControlShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            ),
        color = glassSurfaceColor(emphasized, accent),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = elevation,
        tonalElevation = 0.dp,
        shape = GlassControlShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = if (selected) accent else SubtleGray, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface
                )
                if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = SubtleGray)
            }
            trailingContent?.invoke() ?: GlassSelectionIndicator(selected = selected, accent = accent)
        }
    }
}

@Composable
fun GlassSelectionIndicator(selected: Boolean, accent: Color = CyanAccent) {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = if (selected) accent.copy(alpha = 0.18f) else Color.Transparent,
        border = BorderStroke(if (selected) 2.dp else 1.5.dp, if (selected) accent else SubtleGray.copy(alpha = 0.75f))
    ) {
        if (selected) Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Check, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun GlassActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    tone: GlassActionTone = GlassActionTone.Primary,
    enabled: Boolean = true
) {
    val accent = when (tone) {
        GlassActionTone.Primary -> CyanAccent
        GlassActionTone.Success -> GreenAccent
        GlassActionTone.Warning -> OrangeAccent
        GlassActionTone.Destructive -> ErrorRed
        GlassActionTone.Neutral -> BlueAccent
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val emphasized = tone != GlassActionTone.Neutral || pressed
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.MinimumTouchTarget)
            .clip(GlassControlShape)
            .border(
                if (emphasized) 1.5.dp else 1.dp,
                glassOutline(emphasized, accent),
                GlassControlShape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        color = glassSurfaceColor(emphasized, accent),
        contentColor = accent,
        shadowElevation = if (emphasized && enabled) 4.dp else 1.dp,
        tonalElevation = 0.dp,
        shape = GlassControlShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (enabled) accent else SubtleGray)
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = if (enabled) accent else SubtleGray)
                if (supportingText != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(supportingText, style = MaterialTheme.typography.bodySmall, color = SubtleGray)
                }
            }
        }
    }
}

enum class GlassActionTone { Primary, Success, Warning, Destructive, Neutral }
