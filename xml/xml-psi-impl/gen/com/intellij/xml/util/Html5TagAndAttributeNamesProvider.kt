// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util

import com.intellij.psi.tree.IStubFileElementType
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.Interner
import java.util.*

/**
 * This utility object provides names for all known HTML, SVG and MathML elements and attributes. It is created
 * statically and can be used in parsers or lexers. Any stubbed file element types, which are created by parsers
 * using information provided by this class should include [Html5TagAndAttributeNamesProvider.VERSION] in
 * [IStubFileElementType.getStubVersion] version calculations.
 */
object Html5TagAndAttributeNamesProvider {

  /**
   * Version of the information, should be used to calculate stub version,
   * if parser or lexer depends on the information from this object.
   */
  const val VERSION = 3

  /**
   * Retrieves the set of all known HTML, SVG or MathML attributes of tags with a particular name.
   *
   * @param namespace tag's namespace - HTML, SVG or MathML
   * @param tagName
   * @param caseSensitive specifies whether the returned attribute names set should be case sensitive or not
   * @return a set containing [String] objects, or [null] if tag was not found. The set's contains check respects
   *         `caseSensitive` parameter.
   */
  @JvmStatic
  fun getTagAttributes(namespace: Namespace, tagName: CharSequence, caseSensitive: Boolean): Set<CharSequence>? =
    getMap(namespace, caseSensitive).let { it[tagName] }

  /**
   * Retrieves the set of all known HTML, SVG and MathML attributes of tags with a particular name.
   *
   * @param tagName
   * @param caseSensitive specifies whether the returned attribute names set should be case sensitive or not
   * @return a set containing [String] objects, or [null] if tag was not found. The set's contains check respects
   *         `caseSensitive` parameter.
   */
  @JvmStatic
  fun getTagAttributes(tagName: CharSequence, caseSensitive: Boolean): Set<CharSequence>? =
    getMap(caseSensitive).let { it[tagName] }

  /**
   * Retrieves the set of all known HTML, SVG or MathML tags
   *
   * @param namespace tag's namespace - HTML, SVG or MathML
   * @param caseSensitive specifies whether the returned tag names set should be case sensitive or not
   * @return a set containing [String] objects. The set's contains check respects [caseSensitive] parameter.
   */
  @JvmStatic
  fun getTags(namespace: Namespace, caseSensitive: Boolean): Set<CharSequence> =
    getMap(namespace, caseSensitive).keys

  /**
   * Retrieves the set of all known HTML, SVG and MathML tags
   *
   * @param caseSensitive specifies whether the returned tag names set should be case sensitive or not
   * @return a set containing [String] objects. The set's contains check respects [caseSensitive] parameter.
   */
  @JvmStatic
  fun getTags(caseSensitive: Boolean): Set<CharSequence> =
    getMap(caseSensitive).keys

  enum class Namespace {
    HTML,
    SVG,
    MathML
  }

  private fun getMap(namespace: Namespace, caseSensitive: Boolean): Map<CharSequence, Set<CharSequence>> =
    (if (caseSensitive) namespacedTagToAttributeMapCaseSensitive else namespacedTagToAttributeMapCaseInsensitive)[namespace]!!

  private fun getMap(caseSensitive: Boolean): Map<CharSequence, Set<CharSequence>> =
    if (caseSensitive) tagToAttributeMapCaseSensitive else tagToAttributeMapCaseInsensitive

  private val baseHtmlAttrs = listOf(
    "accesskey", "autocapitalize", "autofocus", "base", "class", "contenteditable", "dir", "draggable", "enterkeyhint", "hidden", "id", "inert", "inputmode", "is", "lang", "nonce", "onabort", "onauxclick", "onbeforeinput", "onbeforematch", "onblur", "oncancel", "oncanplay", "oncanplaythrough", "onchange", "onclick", "onclose", "oncontextlost", "oncontextmenu", "oncontextrestored", "oncopy", "oncuechange", "oncut", "ondblclick", "ondrag", "ondragend", "ondragenter", "ondragleave", "ondragover", "ondragstart", "ondrop", "ondurationchange", "onemptied", "onended", "onerror", "onfocus", "onfocusin", "onfocusout", "onformdata", "ongotpointercapture", "oninput", "oninvalid", "onkeydown", "onkeypress", "onkeyup", "onload", "onloadeddata", "onloadedmetadata", "onloadstart", "onlostpointercapture", "onmousedown", "onmouseenter", "onmouseleave", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "onpaste", "onpause", "onplay", "onplaying", "onpointercancel", "onpointerdown", "onpointerenter", "onpointerleave", "onpointermove", "onpointerout", "onpointerover", "onpointerrawupdate", "onpointerup", "onprogress", "onratechange", "onreset", "onresize", "onscroll", "onscrollend", "onsecuritypolicyviolation", "onseeked", "onseeking", "onselect", "onslotchange", "onstalled", "onsubmit", "onsuspend", "ontimeupdate", "ontoggle", "ontransitioncancel", "ontransitionend", "ontransitionrun", "ontransitionstart", "onvolumechange", "onwaiting", "onwheel", "slot", "space", "spellcheck", "style", "tabindex", "title", "translate"
  )

  private val htmlAttrs = baseHtmlAttrs + listOf(
    "about", "content", "datatype", "inlist", "itemid", "itemprop", "itemref", "itemscope", "itemtype", "prefix", "property", "rel", "resource", "rev", "typeof", "vocab"
  )

  private val svgBasicAttrs = listOf(
    "base", "id", "space"
  )

  private val svgAttrs = svgBasicAttrs + listOf(
    "externalResourcesRequired", "fill", "focusable", "lang", "tabindex"
  )

  private val svgGraphicAttrs = svgAttrs + listOf(
    "class", "clip-path", "clip-rule", "color", "color-interpolation", "color-rendering", "cursor", "display", "fill-opacity", "fill-rule", "filter", "image-rendering", "mask", "opacity", "pointer-events", "shape-rendering", "stroke", "stroke-dasharray", "stroke-dashoffset", "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "stroke-opacity", "stroke-width", "style", "text-rendering", "visibility"
  )

  private val svgTextAttrs = svgGraphicAttrs + listOf(
    "alignment-baseline", "baseline-shift", "direction", "dominant-baseline", "font-family", "font-size", "font-size-adjust", "font-stretch", "font-style", "font-variant", "font-weight", "glyph-orientation-horizontal", "glyph-orientation-vertical", "kerning", "letter-spacing", "text-anchor", "text-decoration", "unicode-bidi", "word-spacing", "writing-mode"
  )

  private val mathBasicAttrs = listOf(
    "class", "href", "id", "xref"
  )

  private val mathAttrs = mathBasicAttrs + listOf(
    "definitionURL", "encoding", "other", "style"
  )

  private val namespacedTagToAttributeMapCaseSensitive: Map<Namespace, Map<CharSequence, Set<CharSequence>>> =
    createMap(true)

  private val namespacedTagToAttributeMapCaseInsensitive: Map<Namespace, Map<CharSequence, Set<CharSequence>>> =
    createMap(false)

  private val tagToAttributeMapCaseSensitive: Map<CharSequence, Set<CharSequence>> =
    createMergedMap(true)

  private val tagToAttributeMapCaseInsensitive: Map<CharSequence, Set<CharSequence>> =
    createMergedMap(false)

  private fun createMergedMap(caseSensitive: Boolean): Map<CharSequence, Set<CharSequence>> =
    namespacedTagToAttributeMapCaseSensitive
      .flatMap { (_, tags) ->
        tags.entries.map { Pair(it.key.toString(), it.value) }
      }
      .groupingBy { it.first }
      .aggregateTo(CollectionFactory.createCharSequenceMap<MutableSet<CharSequence>>(caseSensitive)) { _, result, (_, attrs), _ ->
        (result ?: CollectionFactory.createCharSequenceSet(caseSensitive)).also { it.addAll(attrs) }
      }
      .mapValues { Collections.unmodifiableSet(it.value) as Set<CharSequence> }
      .let { Collections.unmodifiableMap(it) }

  private fun createMap(caseSensitive: Boolean): Map<Namespace, Map<CharSequence, Set<CharSequence>>> {
    val interner = Interner.createStringInterner()

    fun attrs(base: List<String>, vararg items: String): Set<CharSequence> =
      Collections.unmodifiableSet(
        CollectionFactory.createCharSequenceSet(caseSensitive).apply { addAll((base + items).map { interner.intern(it) }) }
      )

    fun tags(vararg items: Pair<String, Set<CharSequence>>): Map<CharSequence, Set<CharSequence>> =
      items.toMap(CollectionFactory.createCharSequenceMap<Set<CharSequence>>(caseSensitive))
        .let { Collections.unmodifiableMap(it) }

    return mapOf(
      Namespace.HTML to tags(
        "a" to attrs(htmlAttrs, "charset", "coords", "download", "href", "hreflang", "methods", "name", "referrerpolicy", "role", "shape", "target", "type", "urn"),
        "abbr" to attrs(htmlAttrs, "role"),
        "acronym" to attrs(htmlAttrs, ),
        "address" to attrs(htmlAttrs, "role"),
        "applet" to attrs(htmlAttrs, "alt", "archive", "code", "codebase", "height", "name", "width"),
        "area" to attrs(htmlAttrs, "alt", "coords", "download", "href", "hreflang", "nohref", "role", "shape", "target", "type"),
        "article" to attrs(htmlAttrs, "role"),
        "aside" to attrs(htmlAttrs, "role"),
        "audio" to attrs(htmlAttrs, "autoplay", "controls", "crossorigin", "loop", "muted", "preload", "role", "src"),
        "b" to attrs(htmlAttrs, "role"),
        "base" to attrs(baseHtmlAttrs, "about", "content", "datatype", "href", "inlist", "itemid", "itemprop", "itemref", "itemscope", "itemtype", "prefix", "property", "resource", "rev", "target", "typeof", "vocab"),
        "basefont" to attrs(htmlAttrs, "color", "face", "size"),
        "bdi" to attrs(htmlAttrs, "role"),
        "bdo" to attrs(htmlAttrs, "role"),
        "big" to attrs(htmlAttrs, ),
        "blockquote" to attrs(htmlAttrs, "cite", "role"),
        "body" to attrs(htmlAttrs, "alink", "background", "bgcolor", "bottommargin", "leftmargin", "link", "marginheight", "marginwidth", "onafterprint", "onbeforeprint", "onbeforeunload", "onhashchange", "onlanguagechange", "onmessage", "onmessageerror", "onoffline", "ononline", "onpagehide", "onpageshow", "onpopstate", "onrejectionhandled", "onstorage", "onunhandledrejection", "onunload", "rightmargin", "role", "text", "topmargin", "vlink"),
        "br" to attrs(htmlAttrs, "clear", "role"),
        "button" to attrs(htmlAttrs, "datafld", "dataformatas", "datasrc", "disabled", "form", "formaction", "formenctype", "formmethod", "formnovalidate", "formtarget", "name", "role", "type", "value"),
        "canvas" to attrs(htmlAttrs, "height", "role", "width"),
        "caption" to attrs(htmlAttrs, "align"),
        "center" to attrs(htmlAttrs, ),
        "cite" to attrs(htmlAttrs, "role"),
        "code" to attrs(htmlAttrs, "role"),
        "col" to attrs(htmlAttrs, "align", "char", "charoff", "span", "valign", "width"),
        "colgroup" to attrs(htmlAttrs, "align", "char", "charoff", "span", "valign", "width"),
        "data" to attrs(htmlAttrs, "role", "value"),
        "datalist" to attrs(htmlAttrs, "role"),
        "dd" to attrs(htmlAttrs, "role"),
        "del" to attrs(htmlAttrs, "cite", "datetime", "role"),
        "details" to attrs(htmlAttrs, "open", "role"),
        "dfn" to attrs(htmlAttrs, "role"),
        "dialog" to attrs(htmlAttrs, "open", "role"),
        "dir" to attrs(htmlAttrs, "compact"),
        "div" to attrs(htmlAttrs, "align", "datafld", "dataformatas", "datasrc", "role"),
        "dl" to attrs(htmlAttrs, "compact", "role"),
        "dt" to attrs(htmlAttrs, "role"),
        "em" to attrs(htmlAttrs, "role"),
        "embed" to attrs(htmlAttrs, "align", "height", "hspace", "name", "src", "type", "vspace", "width"),
        "fieldset" to attrs(htmlAttrs, "disabled", "form", "name", "role"),
        "figcaption" to attrs(htmlAttrs, "role"),
        "figure" to attrs(htmlAttrs, "role"),
        "font" to attrs(htmlAttrs, "color", "face", "role", "size"),
        "footer" to attrs(htmlAttrs, "role"),
        "form" to attrs(htmlAttrs, "accept-charset", "action", "autocomplete", "enctype", "method", "name", "novalidate", "role", "target"),
        "frame" to attrs(baseHtmlAttrs, "frameborder", "longdesc", "marginheight", "marginwidth", "name", "noresize", "scrolling", "src"),
        "frameset" to attrs(htmlAttrs, "columns", "onunload", "rows"),
        "h1" to attrs(htmlAttrs, "align", "role"),
        "h2" to attrs(htmlAttrs, "align", "role"),
        "h3" to attrs(htmlAttrs, "align", "role"),
        "h4" to attrs(htmlAttrs, "align", "role"),
        "h5" to attrs(htmlAttrs, "align", "role"),
        "h6" to attrs(htmlAttrs, "align", "role"),
        "head" to attrs(htmlAttrs, "profile"),
        "header" to attrs(htmlAttrs, "role"),
        "hgroup" to attrs(htmlAttrs, "role"),
        "hr" to attrs(htmlAttrs, "align", "color", "noshade", "role", "size", "width"),
        "html" to attrs(htmlAttrs, "manifest", "version"),
        "i" to attrs(htmlAttrs, "role"),
        "iframe" to attrs(htmlAttrs, "align", "allow", "allowfullscreen", "allowtransparency", "frameborder", "height", "hspace", "loading", "longdesc", "marginheight", "marginwidth", "name", "referrerpolicy", "role", "sandbox", "scrolling", "src", "srcdoc", "vspace", "width"),
        "img" to attrs(htmlAttrs, "align", "alt", "border", "crossorigin", "decoding", "fetchpriority", "generator-unable-to-provide-required-alt", "height", "hspace", "ismap", "loading", "longdesc", "name", "referrerpolicy", "role", "sizes", "src", "srcset", "usemap", "vspace", "width"),
        "input" to attrs(htmlAttrs, "accept", "align", "alt", "autocomplete", "capture", "checked", "datafld", "dataformatas", "datasrc", "dirname", "disabled", "form", "formaction", "formenctype", "formmethod", "formnovalidate", "formtarget", "height", "hspace", "list", "max", "maxlength", "min", "minlength", "multiple", "name", "pattern", "placeholder", "readonly", "required", "role", "size", "src", "step", "type", "usemap", "value", "vspace", "width"),
        "ins" to attrs(htmlAttrs, "cite", "datetime", "role"),
        "kbd" to attrs(htmlAttrs, "role"),
        "label" to attrs(htmlAttrs, "for"),
        "legend" to attrs(htmlAttrs, "align"),
        "li" to attrs(htmlAttrs, "role", "type", "value"),
        "link" to attrs(htmlAttrs, "as", "blocking", "charset", "color", "crossorigin", "disabled", "href", "hreflang", "imagesizes", "imagesrcset", "integrity", "media", "methods", "referrerpolicy", "role", "scope", "sizes", "target", "type", "updateviacache", "urn", "workertype"),
        "main" to attrs(htmlAttrs, "role"),
        "map" to attrs(htmlAttrs, "name"),
        "mark" to attrs(htmlAttrs, "role"),
        "math" to attrs(mathBasicAttrs, "accent", "accentunder", "align", "alignmentscope", "altimg", "altimg-height", "altimg-valign", "altimg-width", "alttext", "bevelled", "cdgroup", "charalign", "charspacing", "close", "columnalign", "columnlines", "columnspacing", "columnspan", "columnwidth", "crossout", "decimalpoint", "denomalign", "depth", "dir", "display", "displaystyle", "edge", "equalcolumns", "equalrows", "fence", "form", "frame", "framespacing", "groupalign", "height", "indentalign", "indentalignfirst", "indentalignlast", "indentshift", "indentshiftfirst", "indentshiftlast", "indenttarget", "infixlinebreakstyle", "largeop", "leftoverhang", "length", "linebreak", "linebreakmultchar", "linebreakstyle", "lineleading", "linethickness", "location", "longdivstyle", "lquote", "lspace", "macros", "mathbackground", "mathcolor", "mathsize", "mathvariant", "maxsize", "maxwidth", "minlabelspacing", "minsize", "mode", "movablelimits", "mslinethickness", "notation", "numalign", "open", "other", "overflow", "position", "rightoverhang", "role", "rowalign", "rowlines", "rowspacing", "rowspan", "rquote", "rspace", "scriptlevel", "scriptminsize", "scriptsizemultiplier", "selection", "separator", "separators", "shift", "side", "stackalign", "stretchy", "style", "subscriptshift", "superscriptshift", "symmetric", "valign", "width"),
        "menu" to attrs(htmlAttrs, "compact", "role"),
        "meta" to attrs(baseHtmlAttrs, "about", "charset", "content", "datatype", "http-equiv", "inlist", "itemid", "itemprop", "itemref", "itemscope", "itemtype", "media", "name", "prefix", "property", "resource", "role", "scheme", "typeof", "vocab"),
        "meter" to attrs(htmlAttrs, "high", "low", "max", "min", "optimum", "value"),
        "nav" to attrs(htmlAttrs, "role"),
        "noframes" to attrs(htmlAttrs, ),
        "noscript" to attrs(htmlAttrs, ),
        "object" to attrs(htmlAttrs, "align", "archive", "border", "classid", "code", "codebase", "codetype", "data", "datafld", "dataformatas", "datasrc", "declare", "form", "height", "hspace", "name", "role", "standby", "type", "usemap", "vspace", "width"),
        "ol" to attrs(htmlAttrs, "compact", "reversed", "role", "start", "type"),
        "optgroup" to attrs(htmlAttrs, "disabled", "label", "role"),
        "option" to attrs(htmlAttrs, "disabled", "label", "name", "role", "selected", "value"),
        "output" to attrs(htmlAttrs, "for", "form", "name", "role"),
        "p" to attrs(htmlAttrs, "align", "role"),
        "param" to attrs(htmlAttrs, "name", "type", "value", "valuetype"),
        "picture" to attrs(htmlAttrs, ),
        "pre" to attrs(htmlAttrs, "role", "width"),
        "progress" to attrs(htmlAttrs, "max", "role", "value"),
        "q" to attrs(htmlAttrs, "cite", "role"),
        "rb" to attrs(htmlAttrs, "role"),
        "rp" to attrs(htmlAttrs, "role"),
        "rt" to attrs(htmlAttrs, "role"),
        "rtc" to attrs(htmlAttrs, "role"),
        "ruby" to attrs(htmlAttrs, "role"),
        "s" to attrs(htmlAttrs, "role"),
        "samp" to attrs(htmlAttrs, "role"),
        "script" to attrs(htmlAttrs, "async", "blocking", "charset", "crossorigin", "defer", "event", "for", "integrity", "language", "nomodule", "referrerpolicy", "src", "type"),
        "search" to attrs(htmlAttrs, "role"),
        "section" to attrs(htmlAttrs, "role"),
        "select" to attrs(htmlAttrs, "autocomplete", "datafld", "dataformatas", "datasrc", "disabled", "form", "multiple", "name", "required", "role", "size"),
        "slot" to attrs(htmlAttrs, "name", "role"),
        "small" to attrs(htmlAttrs, "role"),
        "source" to attrs(htmlAttrs, "height", "media", "sizes", "src", "srcset", "type", "width"),
        "span" to attrs(htmlAttrs, "datafld", "dataformatas", "datasrc", "role"),
        "strike" to attrs(htmlAttrs, ),
        "strong" to attrs(htmlAttrs, "role"),
        "style" to attrs(htmlAttrs, "blocking", "media", "type"),
        "sub" to attrs(htmlAttrs, "role"),
        "summary" to attrs(htmlAttrs, "role"),
        "sup" to attrs(htmlAttrs, "role"),
        "svg" to attrs(svgTextAttrs, "baseProfile", "clip", "color-interpolation-filters", "color-profile", "contentScriptType", "contentStyleType", "enable-background", "flood-color", "flood-opacity", "height", "lighting-color", "marker-end", "marker-mid", "marker-start", "onabort", "onactivate", "onclick", "onerror", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "onresize", "onscroll", "onunload", "onzoom", "overflow", "preserveAspectRatio", "requiredExtensions", "requiredFeatures", "role", "stop-color", "stop-opacity", "systemLanguage", "version", "viewBox", "width", "x", "y", "zoomAndPan"),
        "table" to attrs(htmlAttrs, "align", "bgcolor", "border", "cellpadding", "cellspacing", "datafld", "dataformatas", "datapagesize", "datasrc", "frame", "role", "rules", "summary", "valign", "width"),
        "tbody" to attrs(htmlAttrs, "align", "char", "charoff", "role", "valign"),
        "td" to attrs(htmlAttrs, "abbr", "align", "axis", "bgcolor", "char", "charoff", "colspan", "headers", "height", "nowrap", "role", "rowspan", "scope", "valign", "width"),
        "template" to attrs(htmlAttrs, "span", "src"),
        "textarea" to attrs(htmlAttrs, "autocomplete", "cols", "datafld", "dataformatas", "datasrc", "dirname", "disabled", "form", "maxlength", "minlength", "name", "placeholder", "readonly", "required", "role", "rows", "wrap"),
        "tfoot" to attrs(htmlAttrs, "align", "char", "charoff", "role", "valign"),
        "th" to attrs(htmlAttrs, "abbr", "align", "axis", "bgcolor", "char", "charoff", "colspan", "headers", "height", "nowrap", "role", "rowspan", "scope", "valign", "width"),
        "thead" to attrs(htmlAttrs, "align", "char", "charoff", "role", "valign"),
        "time" to attrs(htmlAttrs, "datetime", "role"),
        "title" to attrs(htmlAttrs, ),
        "tr" to attrs(htmlAttrs, "align", "bgcolor", "char", "charoff", "role", "valign"),
        "track" to attrs(htmlAttrs, "default", "kind", "label", "src", "srclang"),
        "tt" to attrs(htmlAttrs, ),
        "u" to attrs(htmlAttrs, "role"),
        "ul" to attrs(htmlAttrs, "compact", "role", "type"),
        "var" to attrs(htmlAttrs, "role"),
        "video" to attrs(htmlAttrs, "autoplay", "controls", "crossorigin", "height", "loop", "muted", "playsinline", "poster", "preload", "role", "src", "width"),
        "wbr" to attrs(htmlAttrs, "role")
      ),
      Namespace.SVG to tags(
        "a" to attrs(svgTextAttrs, "actuate", "arcrole", "clip", "color-interpolation-filters", "color-profile", "enable-background", "flood-color", "flood-opacity", "href", "lighting-color", "marker-end", "marker-mid", "marker-start", "onactivate", "onclick", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "overflow", "rel", "requiredExtensions", "requiredFeatures", "role", "show", "stop-color", "stop-opacity", "systemLanguage", "target", "title", "transform", "type"),
        "altGlyphDef" to attrs(svgBasicAttrs, "focusable", "lang", "tabindex"),
        "animate" to attrs(svgAttrs, "accumulate", "actuate", "additive", "arcrole", "attributeName", "attributeType", "begin", "by", "calcMode", "dur", "end", "from", "href", "keySplines", "keyTimes", "max", "min", "onbegin", "onend", "onload", "onrepeat", "repeatCount", "repeatDur", "requiredExtensions", "requiredFeatures", "restart", "role", "show", "systemLanguage", "title", "to", "type", "values"),
        "animateColor" to attrs(svgAttrs, "accumulate", "actuate", "additive", "arcrole", "attributeName", "attributeType", "begin", "by", "calcMode", "dur", "end", "from", "href", "keySplines", "keyTimes", "max", "min", "onbegin", "onend", "onload", "onrepeat", "repeatCount", "repeatDur", "requiredExtensions", "requiredFeatures", "restart", "role", "show", "systemLanguage", "title", "to", "type", "values"),
        "animateMotion" to attrs(svgAttrs, "accumulate", "actuate", "additive", "arcrole", "begin", "by", "calcMode", "dur", "end", "from", "href", "keyPoints", "keySplines", "keyTimes", "max", "min", "onbegin", "onend", "onload", "onrepeat", "origin", "path", "repeatCount", "repeatDur", "requiredExtensions", "requiredFeatures", "restart", "role", "rotate", "show", "systemLanguage", "title", "to", "type", "values"),
        "animateTransform" to attrs(svgAttrs, "accumulate", "actuate", "additive", "arcrole", "attributeName", "attributeType", "begin", "by", "calcMode", "dur", "end", "from", "href", "keySplines", "keyTimes", "max", "min", "onbegin", "onend", "onload", "onrepeat", "repeatCount", "repeatDur", "requiredExtensions", "requiredFeatures", "restart", "role", "show", "systemLanguage", "title", "to", "type", "values"),
        "circle" to attrs(svgGraphicAttrs, "cx", "cy", "onactivate", "onclick", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "r", "requiredExtensions", "requiredFeatures", "role", "systemLanguage", "transform", "vector-effect"),
        "clipPath" to attrs(svgTextAttrs, "clipPathUnits", "requiredExtensions", "requiredFeatures", "systemLanguage", "transform"),
        "color-profile" to attrs(svgBasicAttrs, "actuate", "arcrole", "focusable", "href", "lang", "local", "name", "rendering-intent", "role", "show", "tabindex", "title", "type"),
        "cursor" to attrs(svgBasicAttrs, "actuate", "arcrole", "externalResourcesRequired", "focusable", "href", "lang", "requiredExtensions", "requiredFeatures", "role", "show", "systemLanguage", "tabindex", "title", "type", "x", "y"),
        "defs" to attrs(svgTextAttrs, "clip", "color-interpolation-filters", "color-profile", "enable-background", "flood-color", "flood-opacity", "lighting-color", "marker-end", "marker-mid", "marker-start", "onactivate", "onclick", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "overflow", "requiredExtensions", "requiredFeatures", "stop-color", "stop-opacity", "systemLanguage", "transform"),
        "desc" to attrs(svgBasicAttrs, "class", "focusable", "lang", "style", "tabindex"),
        "ellipse" to attrs(svgGraphicAttrs, "cx", "cy", "onactivate", "onclick", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "requiredExtensions", "requiredFeatures", "role", "rx", "ry", "systemLanguage", "transform", "vector-effect"),
        "filter" to attrs(svgTextAttrs, "actuate", "arcrole", "clip", "color-interpolation-filters", "color-profile", "enable-background", "filterRes", "filterUnits", "flood-color", "flood-opacity", "height", "href", "lighting-color", "marker-end", "marker-mid", "marker-start", "overflow", "primitiveUnits", "role", "show", "stop-color", "stop-opacity", "title", "type", "width", "x", "y"),
        "font" to attrs(svgTextAttrs, "clip", "color-interpolation-filters", "color-profile", "enable-background", "flood-color", "flood-opacity", "horiz-adv-x", "horiz-origin-x", "horiz-origin-y", "lighting-color", "marker-end", "marker-mid", "marker-start", "overflow", "stop-color", "stop-opacity", "vert-adv-y", "vert-origin-x", "vert-origin-y"),
        "font-face" to attrs(svgBasicAttrs, "accent-height", "alphabetic", "ascent", "bbox", "cap-height", "descent", "focusable", "font-family", "font-size", "font-stretch", "font-style", "font-variant", "font-weight", "hanging", "ideographic", "lang", "mathematical", "overline-position", "overline-thickness", "panose-1", "slope", "stemh", "stemv", "strikethrough-position", "strikethrough-thickness", "tabindex", "underline-position", "underline-thickness", "unicode-range", "units-per-em", "v-alphabetic", "v-hanging", "v-ideographic", "v-mathematical", "widths", "x-height"),
        "foreignObject" to attrs(svgTextAttrs, "clip", "color-interpolation-filters", "color-profile", "enable-background", "flood-color", "flood-opacity", "height", "lighting-color", "marker-end", "marker-mid", "marker-start", "onactivate", "onclick", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "overflow", "requiredExtensions", "requiredFeatures", "role", "stop-color", "stop-opacity", "systemLanguage", "transform", "vector-effect", "width", "x", "y"),
        "g" to attrs(svgTextAttrs, "clip", "color-interpolation-filters", "color-profile", "enable-background", "flood-color", "flood-opacity", "lighting-color", "marker-end", "marker-mid", "marker-start", "onactivate", "onclick", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "overflow", "requiredExtensions", "requiredFeatures", "role", "stop-color", "stop-opacity", "systemLanguage", "transform"),
        "image" to attrs(svgBasicAttrs, "actuate", "arcrole", "class", "clip", "clip-path", "clip-rule", "color", "color-interpolation", "color-profile", "color-rendering", "cursor", "display", "externalResourcesRequired", "fill-opacity", "filter", "focusable", "height", "href", "image-rendering", "lang", "mask", "onactivate", "onclick", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "opacity", "overflow", "pointer-events", "preserveAspectRatio", "requiredExtensions", "requiredFeatures", "role", "shape-rendering", "show", "stroke-opacity", "style", "systemLanguage", "tabindex", "text-rendering", "title", "transform", "type", "vector-effect", "visibility", "width", "x", "y"),
        "line" to attrs(svgGraphicAttrs, "marker-end", "marker-mid", "marker-start", "onactivate", "onclick", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "requiredExtensions", "requiredFeatures", "role", "systemLanguage", "transform", "vector-effect", "x1", "x2", "y1", "y2"),
        "linearGradient" to attrs(svgBasicAttrs, "actuate", "arcrole", "class", "color", "color-interpolation", "color-rendering", "externalResourcesRequired", "focusable", "gradientTransform", "gradientUnits", "href", "lang", "role", "show", "spreadMethod", "stop-color", "stop-opacity", "style", "tabindex", "title", "type", "x1", "x2", "y1", "y2"),
        "marker" to attrs(svgTextAttrs, "clip", "color-interpolation-filters", "color-profile", "enable-background", "flood-color", "flood-opacity", "lighting-color", "marker-end", "marker-mid", "marker-start", "markerHeight", "markerUnits", "markerWidth", "orient", "overflow", "preserveAspectRatio", "refX", "refY", "stop-color", "stop-opacity", "viewBox"),
        "mask" to attrs(svgTextAttrs, "clip", "color-interpolation-filters", "color-profile", "enable-background", "flood-color", "flood-opacity", "height", "lighting-color", "marker-end", "marker-mid", "marker-start", "maskContentUnits", "maskUnits", "overflow", "requiredExtensions", "requiredFeatures", "stop-color", "stop-opacity", "systemLanguage", "width", "x", "y"),
        "metadata" to attrs(svgBasicAttrs, "focusable", "lang", "tabindex"),
        "path" to attrs(svgGraphicAttrs, "d", "marker-end", "marker-mid", "marker-start", "onactivate", "onclick", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "pathLength", "requiredExtensions", "requiredFeatures", "role", "systemLanguage", "transform", "vector-effect"),
        "pattern" to attrs(svgTextAttrs, "actuate", "arcrole", "clip", "color-interpolation-filters", "color-profile", "enable-background", "flood-color", "flood-opacity", "height", "href", "lighting-color", "marker-end", "marker-mid", "marker-start", "overflow", "patternContentUnits", "patternTransform", "patternUnits", "preserveAspectRatio", "requiredExtensions", "requiredFeatures", "role", "show", "stop-color", "stop-opacity", "systemLanguage", "title", "type", "viewBox", "width", "x", "y"),
        "polygon" to attrs(svgGraphicAttrs, "marker-end", "marker-mid", "marker-start", "onactivate", "onclick", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "points", "requiredExtensions", "requiredFeatures", "role", "systemLanguage", "transform", "vector-effect"),
        "polyline" to attrs(svgGraphicAttrs, "marker-end", "marker-mid", "marker-start", "onactivate", "onclick", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "points", "requiredExtensions", "requiredFeatures", "role", "systemLanguage", "transform", "vector-effect"),
        "radialGradient" to attrs(svgBasicAttrs, "actuate", "arcrole", "class", "color", "color-interpolation", "color-rendering", "cx", "cy", "externalResourcesRequired", "focusable", "fx", "fy", "gradientTransform", "gradientUnits", "href", "lang", "r", "role", "show", "spreadMethod", "stop-color", "stop-opacity", "style", "tabindex", "title", "type"),
        "rect" to attrs(svgGraphicAttrs, "height", "onactivate", "onclick", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "requiredExtensions", "requiredFeatures", "role", "rx", "ry", "systemLanguage", "transform", "vector-effect", "width", "x", "y"),
        "script" to attrs(svgBasicAttrs, "actuate", "arcrole", "externalResourcesRequired", "focusable", "href", "lang", "role", "show", "tabindex", "title", "type"),
        "set" to attrs(svgAttrs, "actuate", "arcrole", "attributeName", "attributeType", "begin", "dur", "end", "href", "max", "min", "onbegin", "onend", "onload", "onrepeat", "repeatCount", "repeatDur", "requiredExtensions", "requiredFeatures", "restart", "role", "show", "systemLanguage", "title", "to", "type"),
        "style" to attrs(svgBasicAttrs, "lang", "media", "title", "type"),
        "svg" to attrs(svgTextAttrs, "baseProfile", "clip", "color-interpolation-filters", "color-profile", "contentScriptType", "contentStyleType", "enable-background", "flood-color", "flood-opacity", "height", "lighting-color", "marker-end", "marker-mid", "marker-start", "onabort", "onactivate", "onclick", "onerror", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "onresize", "onscroll", "onunload", "onzoom", "overflow", "preserveAspectRatio", "requiredExtensions", "requiredFeatures", "role", "stop-color", "stop-opacity", "systemLanguage", "version", "viewBox", "width", "x", "y", "zoomAndPan"),
        "switch" to attrs(svgTextAttrs, "clip", "color-interpolation-filters", "color-profile", "enable-background", "flood-color", "flood-opacity", "lighting-color", "marker-end", "marker-mid", "marker-start", "onactivate", "onclick", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "overflow", "requiredExtensions", "requiredFeatures", "stop-color", "stop-opacity", "systemLanguage", "transform"),
        "symbol" to attrs(svgTextAttrs, "clip", "color-interpolation-filters", "color-profile", "enable-background", "flood-color", "flood-opacity", "height", "lighting-color", "marker-end", "marker-mid", "marker-start", "onactivate", "onclick", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "overflow", "preserveAspectRatio", "role", "stop-color", "stop-opacity", "viewBox", "width"),
        "text" to attrs(svgTextAttrs, "dx", "dy", "lengthAdjust", "onactivate", "onclick", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "requiredExtensions", "requiredFeatures", "role", "rotate", "systemLanguage", "textLength", "transform", "vector-effect", "x", "y"),
        "title" to attrs(svgBasicAttrs, "class", "focusable", "lang", "style", "tabindex"),
        "use" to attrs(svgTextAttrs, "actuate", "arcrole", "clip", "color-interpolation-filters", "color-profile", "enable-background", "flood-color", "flood-opacity", "height", "href", "lighting-color", "marker-end", "marker-mid", "marker-start", "onactivate", "onclick", "onfocusin", "onfocusout", "onload", "onmousedown", "onmousemove", "onmouseout", "onmouseover", "onmouseup", "overflow", "requiredExtensions", "requiredFeatures", "role", "show", "stop-color", "stop-opacity", "systemLanguage", "title", "transform", "type", "vector-effect", "width", "x", "y"),
        "view" to attrs(svgBasicAttrs, "externalResourcesRequired", "focusable", "lang", "preserveAspectRatio", "tabindex", "viewBox", "viewTarget", "zoomAndPan")
      ),
      Namespace.MathML to tags(
        "abs" to attrs(mathAttrs, ),
        "and" to attrs(mathAttrs, ),
        "apply" to attrs(mathBasicAttrs, "other", "style"),
        "approx" to attrs(mathAttrs, ),
        "arccos" to attrs(mathAttrs, ),
        "arccosh" to attrs(mathAttrs, ),
        "arccot" to attrs(mathAttrs, ),
        "arccoth" to attrs(mathAttrs, ),
        "arccsc" to attrs(mathAttrs, ),
        "arccsch" to attrs(mathAttrs, ),
        "arcsec" to attrs(mathAttrs, ),
        "arcsech" to attrs(mathAttrs, ),
        "arcsin" to attrs(mathAttrs, ),
        "arcsinh" to attrs(mathAttrs, ),
        "arctan" to attrs(mathAttrs, ),
        "arctanh" to attrs(mathAttrs, ),
        "arg" to attrs(mathAttrs, ),
        "bind" to attrs(mathBasicAttrs, "other", "style"),
        "card" to attrs(mathAttrs, ),
        "cartesianproduct" to attrs(mathAttrs, ),
        "cbytes" to attrs(mathAttrs, ),
        "ceiling" to attrs(mathAttrs, ),
        "cerror" to attrs(mathBasicAttrs, "other", "style"),
        "ci" to attrs(mathAttrs, "type"),
        "cn" to attrs(mathAttrs, "base", "type"),
        "codomain" to attrs(mathAttrs, ),
        "complexes" to attrs(mathAttrs, ),
        "compose" to attrs(mathAttrs, ),
        "conjugate" to attrs(mathAttrs, ),
        "cos" to attrs(mathAttrs, ),
        "cosh" to attrs(mathAttrs, ),
        "cot" to attrs(mathAttrs, ),
        "coth" to attrs(mathAttrs, ),
        "cs" to attrs(mathAttrs, ),
        "csc" to attrs(mathAttrs, ),
        "csch" to attrs(mathAttrs, ),
        "csymbol" to attrs(mathAttrs, "cd", "type"),
        "curl" to attrs(mathAttrs, ),
        "declare" to attrs(emptyList(), "definitionURL", "encoding", "nargs", "occurrence", "scope", "type"),
        "determinant" to attrs(mathAttrs, ),
        "diff" to attrs(mathAttrs, ),
        "divergence" to attrs(mathAttrs, ),
        "divide" to attrs(mathAttrs, ),
        "domain" to attrs(mathAttrs, ),
        "emptyset" to attrs(mathAttrs, ),
        "eq" to attrs(mathAttrs, ),
        "equivalent" to attrs(mathAttrs, ),
        "eulergamma" to attrs(mathAttrs, ),
        "exists" to attrs(mathAttrs, ),
        "exp" to attrs(mathAttrs, ),
        "exponentiale" to attrs(mathAttrs, ),
        "factorial" to attrs(mathAttrs, ),
        "factorof" to attrs(mathAttrs, ),
        "false" to attrs(mathAttrs, ),
        "floor" to attrs(mathAttrs, ),
        "forall" to attrs(mathAttrs, ),
        "gcd" to attrs(mathAttrs, ),
        "geq" to attrs(mathAttrs, ),
        "grad" to attrs(mathAttrs, ),
        "gt" to attrs(mathAttrs, ),
        "ident" to attrs(mathAttrs, ),
        "image" to attrs(mathAttrs, ),
        "imaginary" to attrs(mathAttrs, ),
        "imaginaryi" to attrs(mathAttrs, ),
        "implies" to attrs(mathAttrs, ),
        "in" to attrs(mathAttrs, ),
        "infinity" to attrs(mathAttrs, ),
        "int" to attrs(mathAttrs, ),
        "integers" to attrs(mathAttrs, ),
        "intersect" to attrs(mathAttrs, ),
        "interval" to attrs(mathAttrs, "closure"),
        "inverse" to attrs(mathAttrs, ),
        "lambda" to attrs(mathAttrs, ),
        "laplacian" to attrs(mathAttrs, ),
        "lcm" to attrs(mathAttrs, ),
        "leq" to attrs(mathAttrs, ),
        "limit" to attrs(mathAttrs, ),
        "list" to attrs(mathAttrs, "order"),
        "ln" to attrs(mathAttrs, ),
        "log" to attrs(mathAttrs, ),
        "lt" to attrs(mathAttrs, ),
        "maction" to attrs(mathBasicAttrs, "actiontype", "mathbackground", "mathcolor", "other", "selection", "style"),
        "maligngroup" to attrs(mathBasicAttrs, "groupalign", "mathbackground", "mathcolor", "other", "style"),
        "malignmark" to attrs(mathBasicAttrs, "edge", "mathbackground", "mathcolor", "other", "style"),
        "matrix" to attrs(mathAttrs, ),
        "matrixrow" to attrs(mathAttrs, ),
        "max" to attrs(mathAttrs, ),
        "mean" to attrs(mathAttrs, ),
        "median" to attrs(mathAttrs, ),
        "menclose" to attrs(mathBasicAttrs, "mathbackground", "mathcolor", "notation", "other", "style"),
        "merror" to attrs(mathBasicAttrs, "mathbackground", "mathcolor", "other", "style"),
        "mfenced" to attrs(mathBasicAttrs, "close", "mathbackground", "mathcolor", "open", "other", "separators", "style"),
        "mfrac" to attrs(mathBasicAttrs, "bevelled", "denomalign", "linethickness", "mathbackground", "mathcolor", "numalign", "other", "style"),
        "mi" to attrs(mathBasicAttrs, "background", "color", "dir", "fontfamily", "fontsize", "fontstyle", "fontweight", "mathbackground", "mathcolor", "mathsize", "mathvariant", "other", "style"),
        "min" to attrs(mathAttrs, ),
        "minus" to attrs(mathAttrs, ),
        "mlongdiv" to attrs(mathBasicAttrs, "longdivstyle", "mathbackground", "mathcolor", "other", "position", "shift", "style"),
        "mmultiscripts" to attrs(mathBasicAttrs, "mathbackground", "mathcolor", "other", "style", "subscriptshift", "superscriptshift"),
        "mn" to attrs(mathBasicAttrs, "background", "color", "dir", "fontfamily", "fontsize", "fontstyle", "fontweight", "mathbackground", "mathcolor", "mathsize", "mathvariant", "other", "style"),
        "mo" to attrs(mathBasicAttrs, "accent", "background", "color", "dir", "fence", "fontfamily", "fontsize", "fontstyle", "fontweight", "form", "indentalign", "indentalignfirst", "indentalignlast", "indentshift", "indentshiftfirst", "indentshiftlast", "indenttarget", "largeop", "linebreak", "linebreakmultchar", "linebreakstyle", "lineleading", "lspace", "mathbackground", "mathcolor", "mathsize", "mathvariant", "maxsize", "minsize", "movablelimits", "other", "rspace", "separator", "stretchy", "style", "symmetric"),
        "mode" to attrs(mathAttrs, ),
        "moment" to attrs(mathAttrs, ),
        "mover" to attrs(mathBasicAttrs, "accent", "align", "mathbackground", "mathcolor", "other", "style"),
        "mpadded" to attrs(mathBasicAttrs, "depth", "height", "lspace", "mathbackground", "mathcolor", "other", "style", "voffset", "width"),
        "mphantom" to attrs(mathBasicAttrs, "mathbackground", "mathcolor", "other", "style"),
        "mroot" to attrs(mathBasicAttrs, "mathbackground", "mathcolor", "other", "style"),
        "mrow" to attrs(mathBasicAttrs, "dir", "mathbackground", "mathcolor", "other", "style"),
        "ms" to attrs(mathBasicAttrs, "background", "color", "dir", "fontfamily", "fontsize", "fontstyle", "fontweight", "lquote", "mathbackground", "mathcolor", "mathsize", "mathvariant", "other", "rquote", "style"),
        "mspace" to attrs(mathBasicAttrs, "background", "color", "depth", "dir", "fontfamily", "fontsize", "fontstyle", "fontweight", "height", "indentalign", "indentalignfirst", "indentalignlast", "indentshift", "indentshiftfirst", "indentshiftlast", "indenttarget", "linebreak", "mathbackground", "mathcolor", "mathsize", "mathvariant", "other", "style", "width"),
        "msqrt" to attrs(mathBasicAttrs, "mathbackground", "mathcolor", "other", "style"),
        "mstack" to attrs(mathBasicAttrs, "align", "charalign", "charspacing", "mathbackground", "mathcolor", "other", "stackalign", "style"),
        "mstyle" to attrs(mathBasicAttrs, "accent", "accentunder", "align", "alignmentscope", "background", "bevelled", "charalign", "charspacing", "close", "color", "columnalign", "columnlines", "columnspacing", "columnspan", "columnwidth", "crossout", "decimalpoint", "denomalign", "depth", "dir", "displaystyle", "edge", "equalcolumns", "equalrows", "fence", "fontfamily", "fontsize", "fontstyle", "fontweight", "form", "frame", "framespacing", "groupalign", "height", "indentalign", "indentalignfirst", "indentalignlast", "indentshift", "indentshiftfirst", "indentshiftlast", "indenttarget", "infixlinebreakstyle", "largeop", "leftoverhang", "length", "linebreak", "linebreakmultchar", "linebreakstyle", "lineleading", "linethickness", "location", "longdivstyle", "lquote", "lspace", "mathbackground", "mathcolor", "mathsize", "mathvariant", "maxsize", "mediummathspace", "minlabelspacing", "minsize", "movablelimits", "mslinethickness", "notation", "numalign", "open", "other", "position", "rightoverhang", "rowalign", "rowlines", "rowspacing", "rowspan", "rquote", "rspace", "scriptlevel", "scriptminsize", "scriptsizemultiplier", "selection", "separator", "separators", "shift", "side", "stackalign", "stretchy", "style", "subscriptshift", "superscriptshift", "symmetric", "thickmathspace", "thinmathspace", "valign", "verythickmathspace", "verythinmathspace", "veryverythickmathspace", "veryverythinmathspace", "width"),
        "msub" to attrs(mathBasicAttrs, "mathbackground", "mathcolor", "other", "style", "subscriptshift"),
        "msubsup" to attrs(mathBasicAttrs, "mathbackground", "mathcolor", "other", "style", "subscriptshift", "superscriptshift"),
        "msup" to attrs(mathBasicAttrs, "mathbackground", "mathcolor", "other", "style", "superscriptshift"),
        "mtable" to attrs(mathBasicAttrs, "align", "alignmentscope", "columnalign", "columnlines", "columnspacing", "columnwidth", "displaystyle", "equalcolumns", "equalrows", "frame", "framespacing", "groupalign", "mathbackground", "mathcolor", "minlabelspacing", "other", "rowalign", "rowlines", "rowspacing", "side", "style", "width"),
        "mtext" to attrs(mathBasicAttrs, "background", "color", "dir", "fontfamily", "fontsize", "fontstyle", "fontweight", "mathbackground", "mathcolor", "mathsize", "mathvariant", "other", "style"),
        "munder" to attrs(mathBasicAttrs, "accentunder", "align", "mathbackground", "mathcolor", "other", "style"),
        "munderover" to attrs(mathBasicAttrs, "accent", "accentunder", "align", "mathbackground", "mathcolor", "other", "style"),
        "naturalnumbers" to attrs(mathAttrs, ),
        "neq" to attrs(mathAttrs, ),
        "not" to attrs(mathAttrs, ),
        "notanumber" to attrs(mathAttrs, ),
        "notin" to attrs(mathAttrs, ),
        "notprsubset" to attrs(mathAttrs, ),
        "notsubset" to attrs(mathAttrs, ),
        "or" to attrs(mathAttrs, ),
        "outerproduct" to attrs(mathAttrs, ),
        "partialdiff" to attrs(mathAttrs, ),
        "pi" to attrs(mathAttrs, ),
        "piecewise" to attrs(mathAttrs, ),
        "plus" to attrs(mathAttrs, ),
        "power" to attrs(mathAttrs, ),
        "primes" to attrs(mathAttrs, ),
        "product" to attrs(mathAttrs, ),
        "prsubset" to attrs(mathAttrs, ),
        "quotient" to attrs(mathAttrs, ),
        "rationals" to attrs(mathAttrs, ),
        "real" to attrs(mathAttrs, ),
        "reals" to attrs(mathAttrs, ),
        "rem" to attrs(mathAttrs, ),
        "root" to attrs(mathAttrs, ),
        "scalarproduct" to attrs(mathAttrs, ),
        "sdev" to attrs(mathAttrs, ),
        "sec" to attrs(mathAttrs, ),
        "sech" to attrs(mathAttrs, ),
        "selector" to attrs(mathAttrs, ),
        "semantics" to attrs(mathAttrs, "cd", "name"),
        "set" to attrs(mathAttrs, "type"),
        "setdiff" to attrs(mathAttrs, ),
        "share" to attrs(mathBasicAttrs, "other", "src", "style"),
        "sin" to attrs(mathAttrs, ),
        "sinh" to attrs(mathAttrs, ),
        "subset" to attrs(mathAttrs, ),
        "sum" to attrs(mathAttrs, ),
        "tan" to attrs(mathAttrs, ),
        "tanh" to attrs(mathAttrs, ),
        "tendsto" to attrs(mathAttrs, "type"),
        "times" to attrs(mathAttrs, ),
        "transpose" to attrs(mathAttrs, ),
        "true" to attrs(mathAttrs, ),
        "union" to attrs(mathAttrs, ),
        "variance" to attrs(mathAttrs, ),
        "vector" to attrs(mathAttrs, ),
        "vectorproduct" to attrs(mathAttrs, ),
        "xor" to attrs(mathAttrs, )
      )
    )
  }
}