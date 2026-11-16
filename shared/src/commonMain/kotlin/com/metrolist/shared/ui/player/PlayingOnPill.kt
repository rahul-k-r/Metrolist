package com.metrolist.shared.ui.player

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.playing_on_device
import com.metrolist.shared.ui.icons.MetrolistIcons
import org.jetbrains.compose.resources.stringResource

/** Single-line "Playing on <device>" caption for the mini player. */
@Composable
fun PlayingOnLine(
    name: String,
    color: androidx.compose.ui.graphics.Color,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(Res.string.playing_on_device, name),
        color = color,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier.basicMarquee(),
    )
}

/**
 * Pill naming the remote device audio is routed to. The name keeps scrolling when it does not fit the available width.
 * With a fixed [height] the pill matches the controls beside it; [iconOnly] collapses it to a circle of that height.
 */
@Composable
fun PlayingOnPill(
    name: String,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 140.dp,
    height: Dp = Dp.Unspecified,
    iconOnly: Boolean = false,
) {
    val description = stringResource(Res.string.playing_on_device, name)
    val sized = if (height.isSpecified) Modifier.height(height) else Modifier
    if (iconOnly && height.isSpecified) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = modifier.size(height).clearAndSetSemantics { contentDescription = description },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(MetrolistIcons.Outlined.Devices, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        return
    }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.widthIn(max = maxWidth).then(sized).clearAndSetSemantics { contentDescription = description },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                (if (height.isSpecified) Modifier.fillMaxHeight() else Modifier)
                    .padding(horizontal = 12.dp, vertical = if (height.isSpecified) 0.dp else 6.dp),
        ) {
            Icon(MetrolistIcons.Outlined.Devices, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f, fill = false).basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500, velocity = 30.dp),
            )
        }
    }
}
