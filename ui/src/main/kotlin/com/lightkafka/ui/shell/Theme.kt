package com.lightkafka.ui.shell

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightkafka.ui.infra.AccentViolet
import com.lightkafka.ui.infra.AppBackgroundColor
import com.lightkafka.ui.infra.SurfaceCard
import com.lightkafka.ui.infra.SurfaceElevated
import com.lightkafka.ui.infra.TextPrimary
import com.lightkafka.ui.infra.TextSecondary

private val KafiDarkScheme =
    darkColorScheme(
        primary = AccentViolet,
        onPrimary = TextPrimary,
        secondary = AccentViolet,
        onSecondary = TextPrimary,
        background = AppBackgroundColor,
        onBackground = TextPrimary,
        surface = SurfaceCard,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceElevated,
        onSurfaceVariant = TextSecondary,
    )

private val KafiTypography =
    Typography(
        headlineMedium = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
    )

private val KafiShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(8.dp),
    )

@Suppress("ktlint:standard:function-naming")
@Composable
fun KafiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KafiDarkScheme,
        typography = KafiTypography,
        shapes = KafiShapes,
        content = content,
    )
}
