package com.intellij.markdown.compose.preview

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.asComposeFontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.util.ui.JBUI
import org.intellij.plugins.markdown.ui.preview.PreviewStyleScheme
import org.jetbrains.jewel.bridge.retrievePlatformTextStyle
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.*
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Fenced.InfoPosition
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.Code.Indented
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List.Ordered
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List.Unordered
import java.awt.Font

@OptIn(ExperimentalTextApi::class)
@Suppress("FunctionName")
internal fun JcefLikeMarkdownStyling(scheme: PreviewStyleScheme, fontSize: TextUnit): MarkdownStyling {
  val fontSizeDp = fontSize.value.dp
  val defaultTextStyle = TextStyle(
    fontSize = fontSize,
    fontFamily = FontFamily.SansSerif,
    lineHeight = fontSize * 1.6,
    platformStyle = retrievePlatformTextStyle()
  ).copy(
    color = scheme.foregroundColor.toComposeColor()
  )

  val blockVerticalSpacing: Dp = 16.dp
  val baseTextStyle: TextStyle = defaultTextStyle

  val codeFenceFont = Font(FontPreferences.JETBRAINS_MONO, Font.PLAIN, (fontSize.value * 0.9f).toInt()).asComposeFontFamily()
  val codeFenceTextStyle = baseTextStyle.copy(fontFamily = codeFenceFont)

  val inlinesStyling = createInlinesStyling(baseTextStyle, scheme)
  val paragraph = createParagraphStyling(inlinesStyling)
  val heading = createHeadingStyling(scheme, baseTextStyle, fontSizeDp)
  val blockQuote = createBlockQuoteStyling(scheme)
  val code: Code = createCodeStyling(codeFenceTextStyle, scheme)
  val list: List = createListStyling(baseTextStyle)
  val image: Image = createImageStyling()
  val thematicBreak: ThematicBreak = createThematicBreakStyling(scheme)
  val htmlBlock: HtmlBlock = createHtmlBlockStyling(baseTextStyle, scheme)

  return MarkdownStyling(
    blockVerticalSpacing,
    paragraph,
    heading,
    blockQuote,
    code,
    list,
    image,
    thematicBreak,
    htmlBlock,
  )
}

private fun createInlinesStyling(
  baseTextStyle: TextStyle,
  scheme: PreviewStyleScheme,
  link: SpanStyle = baseTextStyle.copy(
    color = JBUI.CurrentTheme.Link.Foreground.ENABLED.toComposeColor(),
    textDecoration = TextDecoration.Underline,
  ).toSpanStyle()
): InlinesStyling = InlinesStyling(
  textStyle = baseTextStyle,
  inlineCode = baseTextStyle
    .copy(
      fontSize = baseTextStyle.fontSize,
      background = scheme.fenceBackgroundColor.toComposeColor(),
    )
    .toSpanStyle(),
  link = link,
  emphasis = baseTextStyle.copy(fontStyle = FontStyle.Italic).toSpanStyle(),
  strongEmphasis = baseTextStyle.copy(fontWeight = FontWeight.Bold).toSpanStyle(),
  inlineHtml = baseTextStyle.toSpanStyle(),
  linkDisabled = link.copy(color = JBUI.CurrentTheme.Link.Foreground.DISABLED.toComposeColor()),
  linkHovered = link.copy(color = JBUI.CurrentTheme.Link.Foreground.HOVERED.toComposeColor()),
  linkFocused = link.copy(
    color = JBUI.CurrentTheme.Link.Foreground.HOVERED.toComposeColor(),
  ),
  linkPressed = link.copy(
    color = JBUI.CurrentTheme.Link.Foreground.PRESSED.toComposeColor(),
  ),
  linkVisited = link.copy(color = JBUI.CurrentTheme.Link.Foreground.VISITED.toComposeColor())
)

private fun createParagraphStyling(inlinesStyling: InlinesStyling): Paragraph = Paragraph(inlinesStyling)

private fun createHeadingStyling(
  scheme: PreviewStyleScheme,
  defaultTextStyle: TextStyle,
  fontSizeDp: Dp,
): Heading = Heading(
  h1 = Heading.H1(
    inlinesStyling = headerInlinesStyling(scheme, defaultTextStyle, 2.2f, 1.4f),
    underlineWidth = 0.dp,
    underlineColor = Color.Unspecified,
    underlineGap = 0.dp,
    padding = PaddingValues(top = fontSizeDp * 2.2f * 1.6f),
  ),
  h2 = Heading.H2(
    inlinesStyling = headerInlinesStyling(scheme, defaultTextStyle, 1.8f, 1.2f),
    underlineWidth = 0.dp,
    underlineColor = Color.Unspecified,
    underlineGap = 0.dp,
    padding = PaddingValues(top = fontSizeDp * 1.8f * 1.6f)
  ),
  h3 = Heading.H3(
    inlinesStyling = headerInlinesStyling(scheme, defaultTextStyle, 1.3f, 1.0f),
    underlineWidth = 0.dp,
    underlineColor = Color.Unspecified,
    underlineGap = 0.dp,
    padding = PaddingValues(top = fontSizeDp * 1.3f * 1.6f)
  ),
  h4 = Heading.H4(
    inlinesStyling = headerInlinesStyling(scheme, defaultTextStyle, 1.0f, 1.4f),
    underlineWidth = 0.dp,
    underlineColor = Color.Unspecified,
    underlineGap = 0.dp,
    padding = PaddingValues(top = fontSizeDp),
  ),
  h5 = Heading.H5(
    inlinesStyling = headerInlinesStyling(scheme, defaultTextStyle, 1.0f, 1.4f),
    underlineWidth = 0.dp,
    underlineColor = Color.Unspecified,
    underlineGap = 0.dp,
    padding = PaddingValues(top = fontSizeDp),
  ),
  h6 = Heading.H6(
    inlinesStyling = headerInlinesStyling(scheme, defaultTextStyle, 1.0f, 1.4f, scheme.infoForegroundColor.toComposeColor()),
    underlineWidth = 0.dp,
    underlineColor = Color.Unspecified,
    underlineGap = 0.dp,
    padding = PaddingValues(top = fontSizeDp),
  ),
)

private fun headerInlinesStyling(scheme: PreviewStyleScheme, textStyle: TextStyle, fontSizeMultiplier: Float, lineHeightMultiplier: Float, color: Color? = null): InlinesStyling {
  val fontSize = textStyle.fontSize * fontSizeMultiplier
  return createInlinesStyling(textStyle.copy(
    fontWeight = FontWeight.Bold,
    color = color ?: textStyle.color,
    fontSize = fontSize,
    lineHeight = fontSize * lineHeightMultiplier,
  ), scheme)
}

private fun createBlockQuoteStyling(scheme: PreviewStyleScheme): BlockQuote = BlockQuote(
  padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
  lineWidth = 2.dp,
  lineColor = scheme.linkActiveForegroundColor.toComposeColor().copy(alpha = 0.4f),
  pathEffect = null,
  strokeCap = StrokeCap.Square,
  textColor = Color(0xFF656d76),
)

private fun createCodeStyling(
  defaultTextStyle: TextStyle,
  scheme: PreviewStyleScheme,
): Code = Code(
  indented = Indented(
    editorTextStyle = defaultTextStyle,
    padding = PaddingValues(16.dp),
    shape = RectangleShape,
    background = scheme.fenceBackgroundColor.toComposeColor(),
    borderWidth = 0.dp,
    borderColor = Color.Unspecified,
    fillWidth = true,
    scrollsHorizontally = true,
  ),
  fenced = Fenced(
    editorTextStyle = defaultTextStyle,
    padding = PaddingValues(16.dp),
    shape = RectangleShape,
    background = scheme.fenceBackgroundColor.toComposeColor(),
    borderWidth = 0.dp,
    borderColor = Color.Unspecified,
    fillWidth = true,
    scrollsHorizontally = true,
    infoTextStyle = TextStyle(color = scheme.infoForegroundColor.toComposeColor(), fontSize = 12.sp),
    infoPadding = PaddingValues(bottom = 16.dp),
    infoPosition = InfoPosition.Hide,
  )
)

private fun createListStyling(baseTextStyle: TextStyle): List = List(
  ordered = Ordered(
    numberStyle = baseTextStyle,
    numberContentGap = 8.dp,
    numberMinWidth = 16.dp,
    numberTextAlign = TextAlign.End,
    itemVerticalSpacing = 16.dp,
    itemVerticalSpacingTight = 4.dp,
    padding = PaddingValues(start = 16.dp),
  ),
  unordered = Unordered(
    bullet = '•',
    bulletStyle = baseTextStyle.copy(fontWeight = FontWeight.Black),
    bulletContentGap = 16.dp,
    itemVerticalSpacing = 16.dp,
    itemVerticalSpacingTight = 4.dp,
    padding = PaddingValues(start = 16.dp),
  )
)

private fun createImageStyling(): Image = Image(
  alignment = Alignment.Center,
  contentScale = ContentScale.Fit,
  padding = PaddingValues(),
  shape = RectangleShape,
  background = Color.Unspecified,
  borderWidth = 0.dp,
  borderColor = Color.Unspecified,
)

private fun createThematicBreakStyling(scheme: PreviewStyleScheme): ThematicBreak = ThematicBreak(
  padding = PaddingValues(),
  lineWidth = 2.dp,
  lineColor = scheme.separatorColor.toComposeColor(),
)

private fun createHtmlBlockStyling(
  baseTextStyle: TextStyle,
  scheme: PreviewStyleScheme,
): HtmlBlock = HtmlBlock(
  textStyle = baseTextStyle,
  padding = PaddingValues(8.dp),
  shape = RoundedCornerShape(4.dp),
  background = scheme.fenceBackgroundColor.toComposeColor(),
  borderWidth = 1.dp,
  borderColor = scheme.separatorColor.toComposeColor(),
  fillWidth = true,
)
