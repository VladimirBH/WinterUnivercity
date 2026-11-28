package ru.winterunivercity.throughtheages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val Ink = Color(0xFF20242A)
internal val Muted = Color(0xFF545C66)
internal val Paper = Color.White
internal val Card = Color.White
internal val Border = Color(0xFFDFE3E8)
internal val Yellow = Color(0xFFFFCC00)
internal val PaleYellow = Color(0xFFFFF1BF)
internal val ActionDark = Color(0xFF222A35)
internal val Night = Color(0xFF202832)
internal val NightBorder = Color(0xFF46524A)
internal val NightText = Color(0xFFC3CEC6)
internal val OnNight = Color(0xFFFAFBF7)
internal val Scrim = Color(0xAD101814)

@OptIn(ExperimentalTextApi::class)
private fun guideFont(resource: Int, vararg weights: FontWeight): FontFamily = FontFamily(
    weights.map { weight ->
        Font(resource, weight = weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))
    },
)

internal val GuideBodyFont = guideFont(R.font.inter_variable, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)
internal val GuideHeadingFont = guideFont(R.font.onest_variable, FontWeight.Bold)
internal val GuideArchiveFont = guideFont(R.font.manrope_variable, FontWeight.Bold)

internal enum class Glyph { LEFT, RIGHT, CAMERA, SCAN, DOCUMENT, CHURCH, PIN, CHECK, LAYERS }
internal enum class ActionStyle { FILLED, OUTLINE, ACCENT, DARK_OUTLINE, PLAIN }

@Composable
internal fun GuidePage(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight).padding(start = 20.dp, end = 20.dp, top = 25.dp, bottom = 16.dp),
            content = content,
        )
    }
}

@Composable
internal fun GuideHeader(label: String, onBack: () -> Unit, centered: Boolean = false, dark: Boolean = false) {
    Box(Modifier.fillMaxWidth().height(44.dp)) {
        if (centered) {
            Text(label, Modifier.align(Alignment.Center), color = if (dark) OnNight else Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Text(
                label, Modifier.align(Alignment.CenterStart).padding(start = 56.dp),
                color = if (dark) OnNight else Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
        }
        BackArrow(onBack, dark)
    }
}

@Composable
internal fun BackArrow(onBack: () -> Unit, dark: Boolean = false) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        Modifier.size(44.dp).clip(shape)
            .background(if (dark) Night.copy(alpha = .88f) else Card)
            .border(1.dp, if (dark) NightBorder else Border, shape)
            .semantics { contentDescription = "Назад" }
            .clickable(role = Role.Button, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) { GuideGlyph(Glyph.LEFT, if (dark) OnNight else Ink, Modifier.size(18.dp)) }
}

@Composable
internal fun GuideAction(
    label: String,
    onClick: () -> Unit,
    style: ActionStyle = ActionStyle.FILLED,
    icon: Glyph? = null,
    arrow: Boolean = false,
    radius: Int = 9,
    height: Int = 52,
) {
    val background = when (style) {
        ActionStyle.FILLED -> ActionDark
        ActionStyle.ACCENT -> Yellow
        else -> Color.Transparent
    }
    val outline = when (style) {
        ActionStyle.OUTLINE -> Border
        ActionStyle.DARK_OUTLINE -> NightBorder
        else -> Color.Transparent
    }
    val foreground = if (style == ActionStyle.FILLED || style == ActionStyle.DARK_OUTLINE) OnNight else Ink
    val shape = RoundedCornerShape(radius.dp)
    Box(
        Modifier.fillMaxWidth().heightIn(min = height.dp).clip(shape).background(background)
            .border(1.dp, outline, shape).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon == null && !arrow) {
            Text(label, color = foreground, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    GuideGlyph(icon, foreground, Modifier.size(20.dp))
                    Spacer(Modifier.width(9.dp))
                }
                Text(label, Modifier.weight(1f), color = foreground, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                if (arrow) GuideGlyph(Glyph.RIGHT, foreground, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun GuideImage(
    drawable: Int,
    modifier: Modifier = Modifier,
    description: String? = null,
    scale: ContentScale = ContentScale.Crop,
) {
    Image(painterResource(drawable), description, modifier, contentScale = scale)
}

@Composable
internal fun CameraHeaderShade() {
    Box(
        Modifier.fillMaxWidth().height(118.dp)
            .background(Brush.verticalGradient(listOf(Color(0xEB141C18), Color.Transparent))),
    )
}

@Composable
internal fun GuideGlyph(glyph: Glyph, tint: Color, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            val outline = Path().apply {
                when (glyph) {
                    Glyph.LEFT -> { moveTo(14f, 5f); lineTo(7f, 12f); lineTo(14f, 19f); moveTo(7f, 12f); lineTo(21f, 12f) }
                    Glyph.RIGHT -> { moveTo(10f, 5f); lineTo(17f, 12f); lineTo(10f, 19f); moveTo(3f, 12f); lineTo(17f, 12f) }
                    Glyph.CAMERA -> {
                        moveTo(3f, 7f); lineTo(8f, 7f); lineTo(10f, 4f); lineTo(14f, 4f)
                        lineTo(16f, 7f); lineTo(21f, 7f); lineTo(21f, 19f); lineTo(3f, 19f); close()
                    }
                    Glyph.SCAN -> {
                        moveTo(8f, 3f); lineTo(3f, 3f); lineTo(3f, 8f)
                        moveTo(16f, 3f); lineTo(21f, 3f); lineTo(21f, 8f)
                        moveTo(21f, 16f); lineTo(21f, 21f); lineTo(16f, 21f)
                        moveTo(8f, 21f); lineTo(3f, 21f); lineTo(3f, 16f)
                    }
                    Glyph.DOCUMENT -> {
                        moveTo(5f, 2f); lineTo(19f, 2f); lineTo(19f, 22f); lineTo(5f, 22f); close()
                        moveTo(8f, 18f); lineTo(8f, 12f)
                        cubicTo(10.7f, 6.7f, 13.3f, 6.7f, 16f, 12f); lineTo(16f, 18f); close()
                    }
                    Glyph.CHURCH -> {
                        moveTo(4f, 21f); lineTo(4f, 12f); cubicTo(4f, 8.7f, 6.7f, 6f, 12f, 4f)
                        cubicTo(17.3f, 6f, 20f, 8.7f, 20f, 12f); lineTo(20f, 21f)
                        moveTo(8f, 21f); lineTo(8f, 12f); cubicTo(10.7f, 8f, 13.3f, 8f, 16f, 12f); lineTo(16f, 21f)
                        moveTo(2f, 21f); lineTo(22f, 21f); moveTo(12f, 4f); lineTo(12f, 1f)
                    }
                    Glyph.PIN -> {
                        moveTo(12f, 22f); cubicTo(10f, 20f, 4f, 14f, 4f, 9f)
                        cubicTo(4f, 0f, 20f, 0f, 20f, 9f); cubicTo(20f, 14f, 14f, 20f, 12f, 22f)
                    }
                    Glyph.CHECK -> { moveTo(4f, 12f); lineTo(9f, 17f); lineTo(20f, 6f) }
                    Glyph.LAYERS -> {
                        moveTo(3f, 8f); lineTo(12f, 3f); lineTo(21f, 8f); lineTo(12f, 13f); close()
                        moveTo(3f, 13f); lineTo(12f, 18f); lineTo(21f, 13f)
                        moveTo(3f, 17f); lineTo(12f, 22f); lineTo(21f, 17f)
                    }
                }
            }
            val stroke = Stroke(1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(outline, tint, style = stroke)
            when (glyph) {
                Glyph.CAMERA -> drawCircle(tint, 3.2f, Offset(12f, 12.7f), style = stroke)
                Glyph.SCAN -> drawCircle(tint, 3.2f, Offset(12f, 12f), style = stroke)
                Glyph.PIN -> drawCircle(tint, 2.8f, Offset(12f, 9f), style = stroke)
                else -> Unit
            }
        }
    }
}
