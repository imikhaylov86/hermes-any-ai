package com.hermes.mobile.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val PrimaryIndigo = Color(0xFF6366F1)
val PrimaryIndigoLight = Color(0xFFA5B4FC)
val SecondarySky = Color(0xFF38BDF8)
val AccentEmerald = Color(0xFF10B981)
val AccentAmber = Color(0xFFF59E0B)
val AccentRose = Color(0xFFF43F5E)

val DarkBackground = Color(0xFF0F172A)
val DarkSurface = Color(0xFF1E293B)
val DarkSurfaceVariant = Color(0xFF334155)
val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFF94A3B8)

val UserBubbleColor = Color(0xFF4F46E5)
val AssistantBubbleColor = Color(0xFF1E293B)
val CodeBlockBackground = Color(0xFF090D16)

/**
 * Единые токены формы и скруглений приложения Hermes Any AI.
 * Все элементы UI используют стандартизированные радиусы:
 * - Чипы и бейджи: 10.dp
 * - Кнопки и поля ввода: 12.dp
 * - Карточки и диалоги: 16.dp
 * - Шторки (BottomSheet): 24.dp
 */
object AppDimensions {
    val RadiusBadge = 8.dp
    val RadiusChip = 10.dp
    val RadiusField = 12.dp
    val RadiusButton = 12.dp
    val RadiusCard = 16.dp
    val RadiusDialog = 16.dp
    val RadiusSheet = 24.dp
    
    val ShapeBadge = RoundedCornerShape(RadiusBadge)
    val ShapeChip = RoundedCornerShape(RadiusChip)
    val ShapeField = RoundedCornerShape(RadiusField)
    val ShapeButton = RoundedCornerShape(RadiusButton)
    val ShapeCard = RoundedCornerShape(RadiusCard)
    val ShapeDialog = RoundedCornerShape(RadiusDialog)
    val ShapeSheet = RoundedCornerShape(topStart = RadiusSheet, topEnd = RadiusSheet)
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(AppDimensions.RadiusBadge),
    small = RoundedCornerShape(AppDimensions.RadiusChip),
    medium = RoundedCornerShape(AppDimensions.RadiusField),
    large = RoundedCornerShape(AppDimensions.RadiusCard),
    extraLarge = RoundedCornerShape(AppDimensions.RadiusSheet)
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryIndigoLight,
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = PrimaryIndigo,
    onPrimaryContainer = Color.White,
    secondary = SecondarySky,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary,
    error = AccentRose
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryIndigo,
    onPrimary = Color.White,
    secondary = SecondarySky,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF64748B),
    error = AccentRose
)

@Composable
fun HermesMobileTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Отключаем динамические цвета для сохранения премиальной индиго/тёмной палитры
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        typography = Typography(),
        content = content
    )
}
