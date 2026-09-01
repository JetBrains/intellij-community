package com.intellij.markdown.figmaAdvertiser

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

/** The plugin this module advertises. Owned by `plugins/figma/resources/META-INF/plugin.xml:2`. */
@ApiStatus.Internal
const val FIGMA_CONNECT_PLUGIN_ID: String = "com.intellij.figma"

/** Its `<name>`, which the platform's Install action label is built from. */
@ApiStatus.Internal
const val FIGMA_CONNECT_PLUGIN_NAME: String = "Figma Connect"
