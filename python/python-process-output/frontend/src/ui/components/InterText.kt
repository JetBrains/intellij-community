package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.jewel.ui.component.Text

@Composable
internal fun InterText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    InterText(
        text = AnnotatedString(text),
        modifier = modifier,
        color = color,
        overflow = overflow,
        fontWeight = fontWeight,
        textAlign = textAlign,
    )
}

@OptIn(ExperimentalTextApi::class)
@Composable
internal fun InterText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        overflow = overflow,
        fontFamily = FontFamily("Inter"),
        fontWeight = fontWeight,
        textAlign = textAlign,
    )
}
