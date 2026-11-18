package com.metrolist.shared.ui.components

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.incognito_marked_badge
import com.metrolist.shared.generated.resources.skip_mark_song
import com.metrolist.shared.ui.icons.IncognitoIcon
import com.metrolist.shared.ui.icons.MetrolistIcons
import org.jetbrains.compose.resources.stringResource

/** Small icon for a song marked "always play incognito"; the caller decides whether it applies (see SongMarkBadges). */
@Composable
fun IncognitoMarkIcon(
    modifier: Modifier = Modifier,
    size: Dp = 13.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(
        imageVector = IncognitoIcon,
        contentDescription = stringResource(Res.string.incognito_marked_badge),
        tint = tint,
        modifier = modifier.offset(y = 1.dp).size(size),
    )
}

/** Small icon for a song that automatic queues leave out. */
@Composable
fun SkipMarkIcon(
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(
        imageVector = MetrolistIcons.Outlined.Block,
        contentDescription = stringResource(Res.string.skip_mark_song),
        tint = tint,
        modifier = modifier.offset(y = 1.dp).size(size),
    )
}
