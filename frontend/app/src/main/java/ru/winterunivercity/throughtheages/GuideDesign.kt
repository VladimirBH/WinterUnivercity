package ru.winterunivercity.throughtheages

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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

internal enum class Glyph(val drawable: Int) {
    LEFT(R.drawable.icon_left),
    RIGHT(R.drawable.icon_right),
    CAMERA(R.drawable.icon_camera),
    SCAN(R.drawable.icon_scan),
    DOCUMENT(R.drawable.icon_document),
    CHURCH(R.drawable.icon_church),
    BRAND(R.drawable.icon_brand_header),
    COMPASS(R.drawable.icon_compass),
    PIN(R.drawable.icon_pin),
    CHECK(R.drawable.icon_check),
    LAYERS(R.drawable.icon_layers),
}
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
    ) { GuideGlyph(Glyph.LEFT, if (dark) OnNight else Ink, Modifier.size(20.dp)) }
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
    Image(
        painter = painterResource(glyph.drawable),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint),
    )
}
