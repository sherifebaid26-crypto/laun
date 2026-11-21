package com.aura.launcher

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** ساعة وتاريخ أعلى الشاشة الرئيسية (بتتحدد من الثيم). scale للمعاينات الصغيرة. */
@Composable
fun ClockHeader(
    style: ClockStyle,
    color: Color,
    shadow: Color,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    if (style == ClockStyle.NONE) return
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 50L)
        }
    }

    val is24 = DateFormat.is24HourFormat(context)
    val timeText = now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm"))
    val dateText = now.format(
        DateTimeFormatter.ofPattern(tr("EEEE، d MMMM", "EEEE, d MMMM"), Locale.getDefault()),
    )

    val family: FontFamily
    val weight: FontWeight
    val sizeSp: Float
    when (style) {
        ClockStyle.BIG_SERIF -> { family = FontFamily.Serif; weight = FontWeight.Bold; sizeSp = 80f }
        ClockStyle.SMALL -> { family = FontFamily.SansSerif; weight = FontWeight.Light; sizeSp = 44f }
        else -> { family = FontFamily.SansSerif; weight = FontWeight.Light; sizeSp = 84f }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = (28f * scale).dp, vertical = (10f * scale).dp),
    ) {
        Text(
            text = timeText,
            style = TextStyle(
                color = color,
                fontSize = (sizeSp * scale).sp,
                fontFamily = family,
                fontWeight = weight,
                letterSpacing = (-1f * scale).sp,
                shadow = Shadow(color = shadow, blurRadius = 6f),
            ),
        )
        Text(
            text = dateText,
            style = TextStyle(
                color = color.copy(alpha = 0.85f),
                fontSize = (15f * scale).sp,
                shadow = Shadow(color = shadow, blurRadius = 4f),
            ),
        )
    }
}
