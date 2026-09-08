package com.intellij.markdown.figmaAdvertiser

import com.intellij.ide.plugins.PluginManager
import org.jetbrains.annotations.ApiStatus

/**
 * Whether the banner is willing to look inside [filePath].
 *
 * The provider is asked about every file that is opened, and this is the second question it answers,
 * so it is a string check over the VFS path. VFS paths are `/`-separated on every OS.
 */
@ApiStatus.Internal
fun isMarkdownSuggestionFile(filePath: String): Boolean {
  val name = filePath.substringAfterLast('/')
  return name.substringAfterLast('.', "").lowercase() in MARKDOWN_EXTENSIONS
}

/**
 * Whether [text] contains a link to a Figma file.
 *
 * `FigmaUrlDetector` in the Figma Connect plugin holds the other copy of this rule, and the two
 * copies must both stay. This advertisement runs exactly when that plugin is absent, so a module
 * dependency on it would be dropped in the one case the advertisement exists for, and the banner
 * would never appear. Neither copy is the original; whoever changes the set of Figma URL shapes
 * changes both.
 *
 * **The two rules answer different questions, and this one is deliberately the broader.**
 * `FigmaUrlDetector` asks which Figma node to open, so it requires a file key after the path
 * segment and parses the node id out. This asks whether the author works with Figma, and a
 * truncated or keyless link — `https://www.figma.com/design/`, `.../file/-abc/Checkout` — still
 * answers yes. So the banner appears over a URL Figma Connect itself cannot open. That is the right
 * trade for a suggestion: the cost is one offer the user can dismiss, and narrowing the rule to
 * match the detector would drop real matches for a question this function is not asking.
 *
 * Do not align the two. Whoever adds a Figma URL shape adds it to both; whoever tightens either one
 * has to say which of the two questions changed.
 */
@ApiStatus.Internal
fun containsFigmaUrl(text: CharSequence): Boolean = FIGMA_URL_PATTERN.containsMatchIn(text)

/**
 * Whether a link to a Figma file in [text] meets the change over `[changeStart, changeEnd)`.
 *
 * A match that is there after a change and was not there before it holds at least one character the
 * change wrote, and a match a pure deletion produced spans the position the deletion left behind.
 * So a link the change created always meets the change, and a link that was already there and was
 * not touched does not. Answering the wider question — a link anywhere near the change — would say
 * yes to every keystroke within [FIGMA_URL_MAX_MATCH] characters of a link the author wrote
 * yesterday.
 *
 * [FIGMA_URL_PATTERN] matches at most [FIGMA_URL_MAX_MATCH] characters, so a match that meets the
 * change lies inside the changed range grown by that many characters at each end. Searching that
 * window keeps an edit in a long Markdown file as cheap as an edit in a short one.
 */
@ApiStatus.Internal
fun changeTouchesFigmaUrl(text: CharSequence, changeStart: Int, changeEnd: Int): Boolean {
  val from = (changeStart - FIGMA_URL_MAX_MATCH).coerceIn(0, text.length)
  val to = (changeEnd + FIGMA_URL_MAX_MATCH).coerceIn(from, text.length)
  // A deletion writes no character, so it marks the single position it left behind.
  val touchedEnd = maxOf(changeEnd, changeStart + 1)
  return FIGMA_URL_PATTERN.findAll(text.subSequence(from, to)).any { match ->
    from + match.range.first < touchedEnd && changeStart < from + match.range.last + 1
  }
}

/**
 * Whether Figma Connect is loaded.
 *
 * [FigmaConnectPluginSuggestionProvider] is given this exclusion by `buildSuggestionIfNeeded`, which
 * drops every plugin id already in `PluginManager.getLoadedPlugins()`. A caller that does not reach
 * that call asks here.
 */
@ApiStatus.Internal
fun isFigmaConnectLoaded(): Boolean =
  PluginManager.getLoadedPlugins().any { it.pluginId.idString == FIGMA_CONNECT_PLUGIN_ID }

/**
 * Lower case. A file system keeps the case a user typed, and `README.MD` names the same extension.
 *
 * Mirrors the `extensions` attribute of the `Markdown` file type
 * (`community/plugins/markdown/core/resources/META-INF/plugin.xml:194`). A user who maps another
 * extension to Markdown is not followed here, and pays one surface for it.
 */
private val MARKDOWN_EXTENSIONS: Set<String> = setOf("md", "markdown", "mdc")

/**
 * `figma.com/file/`, `figma.com/design/` and `figma.com/proto/`, with or without a `www.` host
 * prefix and over either scheme. A bare `figma.com` mention is not a link to a design and does not
 * match.
 */
private val FIGMA_URL_PATTERN: Regex =
  Regex("""https?://(?:www\.)?figma\.com/(?:file|design|proto)/""", RegexOption.IGNORE_CASE)

/**
 * A bound on the length of a string [FIGMA_URL_PATTERN] matches. The longest one is
 * `https://www.figma.com/design/`, at 29 characters; the bound is set well above it so that a URL
 * shape added to the pattern has room before the bound has to move with it.
 */
private const val FIGMA_URL_MAX_MATCH: Int = 64

/** The plugin this module advertises. Owned by `plugins/figma/resources/META-INF/plugin.xml:2`. */
@ApiStatus.Internal
const val FIGMA_CONNECT_PLUGIN_ID: String = "com.intellij.figma"

/** Its `<name>`, which the platform's Install action label is built from. */
@ApiStatus.Internal
const val FIGMA_CONNECT_PLUGIN_NAME: String = "Figma Connect"
