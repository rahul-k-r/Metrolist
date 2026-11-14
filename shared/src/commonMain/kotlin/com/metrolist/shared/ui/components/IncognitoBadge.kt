package com.metrolist.shared.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.metrolist.shared.settings.Settings
import com.metrolist.shared.ui.icons.IncognitoIcon
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.incognito_badge_description
import org.jetbrains.compose.resources.stringResource

/** Marks the profile button while incognito is active; draws nothing otherwise. */
@Composable
fun IncognitoBadge(modifier: Modifier = Modifier) {
    if (!Settings.shared.isIncognito) return
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier.size(16.dp).background(colors.inverseSurface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = IncognitoIcon,
            contentDescription = stringResource(Res.string.incognito_badge_description),
            tint = colors.inverseOnSurface,
            modifier = Modifier.size(12.dp),
        )
    }
}
