package com.metrolist.shared.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Hat and glasses on a 24-unit grid; not part of the Material Symbols set, so it lives outside [MetrolistIcons]. */
val IncognitoIcon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "Filled.Incognito",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData =
                    PathParser()
                        .parsePathString(
                            "M5.5 11 L7.8 4 H16.2 L18.5 11 Z " +
                                "M2.5 11.5 H21.5 V13.2 H2.5 Z " +
                                "M4.5 17.5 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0 Z " +
                                "M13.5 17.5 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0 Z",
                        ).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
}
