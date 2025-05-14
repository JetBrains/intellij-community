// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.documentation.mdn

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil.capitalize
import com.intellij.openapi.util.text.StringUtil.toLowerCase
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.html.dtd.HtmlSymbolDeclaration
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.*
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.asSafely
import com.intellij.webSymbols.WebSymbolApiStatus
import com.intellij.webSymbols.WebSymbolsBundle
import com.intellij.xml.frontback.impl.icons.XmlFrontbackImplIcons
import com.intellij.xml.util.HtmlUtil
import org.jetbrains.annotations.Nls
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.text.Regex.Companion.escapeReplacement

fun getJsMdnDocumentation(namespace: MdnApiNamespace, qualifiedName: String): MdnSymbolDocumentation? {
  assert(namespace == MdnApiNamespace.WebApi || namespace == MdnApiNamespace.GlobalObjects)
  val mdnQualifiedName = qualifiedName.let {
    when {
      it.startsWith("HTMLDocument") -> it.removePrefix("HTML")
      it.contains('.') -> it.replace("Constructor", "")
      it.endsWith("Constructor") -> "$it.$it"
      else -> it
    }
  }.lowercase(Locale.US).let { webApiIndex[it] ?: it }
  val jsNamespace = qualifiedName.substringBefore(".", "")
  if (jsNamespace.endsWith("EventMap")) {
    getDomEventDocumentation(qualifiedName.substring(jsNamespace.length + 1))?.let { return it }
  }
  val documentation = documentationCache[
    Pair(namespace, if (namespace == MdnApiNamespace.WebApi) getWebApiFragment(mdnQualifiedName) else null)
  ] as? MdnJsDocumentation ?: return null
  return documentation.symbols[mdnQualifiedName]
           ?.let { MdnSymbolDocumentationAdapter(mdnQualifiedName, documentation, it) }
         ?: qualifiedName.takeIf { it.startsWith("CSSStyleDeclaration.") }
           ?.let { getCssMdnDocumentation(it.substring("CSSStyleDeclaration.".length).toKebabCase(), MdnCssSymbolKind.Property) }
}

fun getDomEventDocumentation(name: String): MdnSymbolDocumentation? =
  innerGetEventDoc(name)?.let { MdnSymbolDocumentationAdapter(name, it.first, it.second) }

fun getCssMdnDocumentation(name: String, kind: MdnCssSymbolKind): MdnSymbolDocumentation? {
  val documentation = documentationCache[Pair(MdnApiNamespace.Css, null)] as? MdnCssDocumentation ?: return null
  return kind.getSymbolDoc(documentation, name) ?: getUnprefixedName(name)?.let { kind.getSymbolDoc(documentation, it) }
}

fun getHtmlMdnDocumentation(element: PsiElement, context: XmlTag?): MdnSymbolDocumentation? {
  var symbolName: String? = null
  return when {
    // Directly from the file
    element is XmlTag && !element.containingFile.name.endsWith(".xsd", true) -> {
      symbolName = element.localName
      getTagDocumentation(getHtmlApiNamespace(element.namespace, element, toLowerCase(symbolName)), toLowerCase(symbolName))
    }
    // TODO support special documentation for attribute values
    element is XmlAttribute || element is XmlAttributeValue -> {
      PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java, false)?.let { attr ->
        symbolName = attr.localName
        getAttributeDocumentation(getHtmlApiNamespace(attr.namespace, attr, toLowerCase(symbolName)),
                                  attr.parent.localName, toLowerCase(symbolName))
      }
    }
    else -> {
      var isTag = true
      when (element) {
        // XSD
        is XmlTag -> {
          element.metaData?.let { metaData ->
            isTag = element.localName == "element"
            symbolName = metaData.name
          }
        }
        // DTD
        is XmlElementDecl -> {
          symbolName = element.name
        }
        is XmlAttributeDecl -> {
          isTag = false
          symbolName = element.nameElement.text
        }
        is HtmlSymbolDeclaration -> {
          isTag = element.kind == HtmlSymbolDeclaration.Kind.ELEMENT
          symbolName = element.name
        }
      }
      symbolName?.let {
        val lcName = toLowerCase(it)
        val namespace = getHtmlApiNamespace(context?.namespace, context, lcName)
        if (isTag) {
          getTagDocumentation(namespace, lcName)
        }
        else {
          getAttributeDocumentation(namespace, context?.localName?.let(::toLowerCase), lcName)
        }
      }
    }
  }
    ?.let { (source, doc) ->
      MdnSymbolDocumentationAdapter(if (context?.isCaseSensitive == true) symbolName!! else toLowerCase(symbolName!!), source, doc)
    }
}

fun getHtmlMdnTagDocumentation(namespace: MdnApiNamespace, tagName: String): MdnSymbolDocumentation? {
  assert(namespace == MdnApiNamespace.Html || namespace == MdnApiNamespace.MathML || namespace == MdnApiNamespace.Svg)

  return getTagDocumentation(namespace, tagName)?.let { (source, doc) ->
    MdnSymbolDocumentationAdapter(tagName, source, doc)
  }
}

fun getHtmlMdnAttributeDocumentation(
  namespace: MdnApiNamespace,
  tagName: String?,
  attributeName: String,
): MdnSymbolDocumentation? {
  assert(namespace == MdnApiNamespace.Html || namespace == MdnApiNamespace.MathML || namespace == MdnApiNamespace.Svg)
  return getAttributeDocumentation(namespace, tagName, attributeName)?.let { (source, doc) ->
    MdnSymbolDocumentationAdapter(attributeName, source, doc)
  }
}

private fun getTagDocumentation(namespace: MdnApiNamespace, tagName: String): Pair<MdnHtmlDocumentation, MdnHtmlElementDocumentation>? {
  val documentation = documentationCache[Pair(namespace, null)] as? MdnHtmlDocumentation ?: return null
  return documentation.tags[tagName.let { documentation.tagAliases[it] ?: it }]?.let { Pair(documentation, it) }
}

private fun getAttributeDocumentation(
  namespace: MdnApiNamespace,
  tagName: String?,
  attributeName: String,
): Pair<MdnDocumentation, MdnRawSymbolDocumentation>? {
  val documentation = documentationCache[Pair(namespace, null)] as? MdnHtmlDocumentation ?: return null
  return tagName
           ?.let { name ->
             getTagDocumentation(namespace, name)
               ?.let { (source, tagDoc) ->
                 tagDoc.attrs?.get(attributeName)
                   ?.let { mergeWithGlobal(it, documentation.attrs[attributeName]) }
                   ?.let { Pair(source, it) }
               }
           }
         ?: documentation.attrs[attributeName]?.let { Pair(documentation, it) }
         ?: attributeName.takeIf { it.startsWith("on") }
           ?.let { innerGetEventDoc(it.substring(2)) }
}

private fun mergeWithGlobal(tag: MdnHtmlAttributeDocumentation, global: MdnHtmlAttributeDocumentation?): MdnHtmlAttributeDocumentation =
  global?.let {
    MdnHtmlAttributeDocumentation(
      tag.url,
      tag.status ?: global.status,
      tag.compatibility ?: global.compatibility,
      tag.baseline ?: global.baseline,
      tag.doc ?: global.doc
    )
  } ?: tag

private fun innerGetEventDoc(eventName: String): Pair<MdnDocumentation, MdnDomEventDocumentation>? =
  (documentationCache[Pair(MdnApiNamespace.DomEvents, null)] as MdnDomEventsDocumentation)
    .let { Pair(it, it.events[eventName] ?: return@let null) }

interface MdnSymbolDocumentation {
  val name: String
  val url: String?
  val apiStatus: WebSymbolApiStatus
  val description: String
  val sections: Map<@Nls String, @Nls String>
  val footnote: @Nls String?

  fun getDocumentation(withDefinition: Boolean): @NlsSafe String

  fun getDocumentation(
    withDefinition: Boolean,
    additionalSectionsContent: Consumer<java.lang.StringBuilder>?,
  ): @NlsSafe String

}

private const val defaultBcdContext = "default_context"

class MdnSymbolDocumentationAdapter(
  override val name: String,
  private val source: MdnDocumentation,
  private val doc: MdnRawSymbolDocumentation,
) : MdnSymbolDocumentation {
  override val url: String?
    get() = doc.url?.let { fixMdnUrls(it, source.lang) }

  override val apiStatus: WebSymbolApiStatus
    get() = doc.status?.let {
      when {
        it.contains(MdnApiStatus.Obsolete) -> WebSymbolApiStatus.Obsolete
        it.contains(MdnApiStatus.Deprecated) -> WebSymbolApiStatus.Deprecated
        it.contains(MdnApiStatus.Experimental) -> WebSymbolApiStatus.Experimental
        else -> null
      }
    } ?: WebSymbolApiStatus.Stable

  override val description: String
    get() = renderBaseline() + capitalize(
      doc.doc?.let {
        if (it.contains("```"))
          DocMarkdownToHtmlConverter.convert(DefaultProjectFactory.getInstance().defaultProject, it)
        else
          it
      } ?: ""
    ).fixUrls()

  override val sections: Map<String, String>
    get() {
      val result = doc.sections.toMutableMap()
      if (doc.compatibility != null) {
        doc.compatibility!!.entries.forEach { (id, map) ->
          if (id == defaultBcdContext && doc.baseline != null) return@forEach
          val actualId = if (id == defaultBcdContext) "browser_compatibility" else id
          val bundleKey = "mdn.documentation.section.compat.$actualId"
          val sectionName: String = if (actualId.startsWith("support_of_") && !MdnBundle.hasKey(bundleKey)) {
            MdnBundle.message("mdn.documentation.section.compat.support_of", actualId.substring("support_of_".length))
          }
          else {
            MdnBundle.message(bundleKey)
          }
          result[sectionName] = map.entries
            .joinToString(", ") { it.key.displayName + (if (it.value.isNotEmpty()) " " + it.value else "") }
            .ifBlank { MdnBundle.message("mdn.documentation.section.compat.supported_by.none") }
        }
      }
      doc.status?.asSequence()
        ?.filter { it != MdnApiStatus.StandardTrack }
        ?.map { Pair(WebSymbolsBundle.message("mdn.documentation.section.status." + it.name), "") }
        ?.toMap(result)
      return result.map { (key, value) -> Pair(key.fixUrls(), value.fixUrls()) }.toMap()
    }

  override val footnote: String
    get() = "By <a href='${doc.url}/contributors.txt'>Mozilla Contributors</a>, <a href='https://creativecommons.org/licenses/by-sa/2.5/'>CC BY-SA 2.5</a>"
      .fixUrls()

  override fun getDocumentation(withDefinition: Boolean): String =
    getDocumentation(withDefinition, null)

  override fun getDocumentation(
    withDefinition: Boolean,
    additionalSectionsContent: Consumer<java.lang.StringBuilder>?,
  ) =
    buildDoc(this, withDefinition, additionalSectionsContent)

  private fun String.fixUrls(): String =
    fixMdnUrls(replace(Regex("<a[ \n\t]+href=[ \t]*['\"]#([^'\"]*)['\"]"), "<a href=\"${escapeReplacement(doc.url ?: "")}#$1\""),
               source.lang)

  private fun renderBaseline(): String {
    val baseline = doc.baseline ?: return ""
    val supportedEngines = doc.compatibility?.get(defaultBcdContext)
    val notSupportedEngines = supportedEngines?.let { BASELINE_BROWSERS.filter { supportedEngines[it] == null }.toMutableSet() }
    val supportedEnginesString = supportedEngines?.entries
      ?.joinToString(", ") { it.key.displayName + (if (it.value.isNotEmpty()) " " + it.value else "") }
      ?.ifBlank { MdnBundle.message("mdn.documentation.section.compat.supported_by.none") }
    val result = StringBuilder()
    if (supportedEnginesString != null) {
      result.append("<details style='margin-bottom: ${JBUIScale.scale(4)}px'><summary>")
    }
    result.append("<table style='border-spacing: 0; border-width: 0'><tr><td width=2 valign=top style='padding: 0 ${JBUIScale.scale(4)}px 0 0'>" +
                  "<icon src='" + XmlFrontbackImplIcons::class.java.name + ".Baseline${baseline.level.name.lowercase().capitalize()}'></icon>\n" +
                  "<td style='padding: 0; width:100%'>")
    when (baseline.level) {
      BaselineLevel.NONE -> {
        if (notSupportedEngines != null && notSupportedEngines.size == BASELINE_BROWSERS.size)
          result.append(MdnBundle.message("mdn.documentation.baseline.not-supported"))
        else
          result.append(MdnBundle.message("mdn.documentation.baseline.limited-availability"))
      }
      BaselineLevel.LOW -> {
        result.append(MdnBundle.message("mdn.documentation.baseline.newly-available"))
      }
      BaselineLevel.HIGH -> {
        result.append(MdnBundle.message("mdn.documentation.baseline.widely-available"))
      }
    }
    result.append("\n</td></tr></table>")
    if (supportedEnginesString != null) {
      result.append("</summary>")
      result.append("<p class='grayed' style='margin: 0; padding: 0 0 ${JBUIScale.scale(4)}px 0; line-height: 100%'>")
      result.append(supportedEnginesString)
      if (notSupportedEngines != null && notSupportedEngines.isNotEmpty()) {
        notSupportedEngines.removeAll(notSupportedEngines.mapNotNull { it.mobileRuntime })
        result.append(" <i>(")
        result.append(MdnBundle.message("mdn.documentation.baseline.not-supported-by",
                                        notSupportedEngines.map { it.displayName }.sorted().joinToString(", ")))
        result.append(")</i>")
      } else {
        (baseline.highDate ?: baseline.lowDate)?.dropWhile { !it.isDigit() }?.let { date ->
          result.append(". <i>")
          result.append(MdnBundle.message("mdn.documentation.baseline.since", date.takeWhile { it.isDigit() })
                          .replace(" ", "&nbsp;"))
          result.append("</i>")
        }
      }
      result.append("</details>")
    }
    result.append("\n")
    return result.toString()
  }

}

typealias CompatibilityMap = Map<String, Map<MdnJavaScriptRuntime, String>>

interface MdnRawSymbolDocumentation {
  val url: String?
  val status: Set<MdnApiStatus>?
  val compatibility: CompatibilityMap?
  val baseline: BaselineData?
  val doc: String?
  val sections: Map<String, String> get() = emptyMap()
}

sealed interface MdnDocumentation {
  val license: MdnNameWithUrl
  val author: MdnNameWithUrl
  val lang: String
}

enum class BaselineLevel {
  NONE,
  LOW,
  HIGH
}

data class BaselineData(
  val level: BaselineLevel,
  val lowDate: String?,
  val highDate: String?,
)

data class MdnNameWithUrl(
  val name: String,
  val url: String,
)

data class MdnHtmlDocumentation(
  override val license: MdnNameWithUrl,
  override val author: MdnNameWithUrl,
  override val lang: String,
  val attrs: Map<String, MdnHtmlAttributeDocumentation>,
  val tags: Map<String, MdnHtmlElementDocumentation>,
  val tagAliases: Map<String, String> = emptyMap(),
) : MdnDocumentation

data class MdnJsDocumentation(
  override val license: MdnNameWithUrl,
  override val author: MdnNameWithUrl,
  override val lang: String,
  val symbols: Map<String, MdnJsSymbolDocumentation>,
) : MdnDocumentation

data class MdnDomEventsDocumentation(
  override val license: MdnNameWithUrl,
  override val author: MdnNameWithUrl,
  override val lang: String,
  val events: Map<String, MdnDomEventDocumentation>,
) : MdnDocumentation

data class MdnCssDocumentation(
  override val license: MdnNameWithUrl,
  override val author: MdnNameWithUrl,
  override val lang: String,
  val atRules: Map<String, MdnCssAtRuleSymbolDocumentation>,
  val dataTypes: Map<String, MdnCssPropertySymbolDocumentation>,
  val functions: Map<String, MdnCssPropertySymbolDocumentation>,
  val properties: Map<String, MdnCssPropertySymbolDocumentation>,
  val pseudoClasses: Map<String, MdnCssBasicSymbolDocumentation>,
  val pseudoElements: Map<String, MdnCssBasicSymbolDocumentation>,
) : MdnDocumentation

data class MdnHtmlElementDocumentation(
  override val url: String?,
  override val status: Set<MdnApiStatus>?,
  @JsonDeserialize(using = CompatibilityMapDeserializer::class)
  override val compatibility: CompatibilityMap?,
  override val baseline: BaselineData?,
  override val doc: String,
  val details: Map<String, String>?,
  val attrs: Map<String, MdnHtmlAttributeDocumentation>?,
) : MdnRawSymbolDocumentation

data class MdnHtmlAttributeDocumentation(
  override val url: String?,
  override val status: Set<MdnApiStatus>?,
  @JsonDeserialize(using = CompatibilityMapDeserializer::class)
  override val compatibility: CompatibilityMap?,
  override val baseline: BaselineData?,
  override val doc: String?,
  val details: Map<String, String>? = null,
) : MdnRawSymbolDocumentation {
  override val sections: Map<String, String>
    get() = details ?: emptyMap()
}

data class MdnDomEventDocumentation(
  override val url: String?,
  override val status: Set<MdnApiStatus>?,
  @JsonDeserialize(using = CompatibilityMapDeserializer::class)
  override val compatibility: CompatibilityMap?,
  override val baseline: BaselineData?,
  override val doc: String?,
) : MdnRawSymbolDocumentation

data class MdnJsSymbolDocumentation(
  override val url: String?,
  override val status: Set<MdnApiStatus>?,
  @JsonDeserialize(using = CompatibilityMapDeserializer::class)
  override val compatibility: CompatibilityMap?,
  override val baseline: BaselineData?,
  override val doc: String?,
  val parameters: Map<String, String>?,
  val returns: String?,
  val throws: Map<String, String>?,
) : MdnRawSymbolDocumentation {
  override val sections: Map<String, String>
    get() {
      val result = mutableMapOf<String, String>()
      parameters?.takeIf { it.isNotEmpty() }?.let {
        result.put(MdnBundle.message("mdn.documentation.section.parameters"), buildSubSection(it))
      }
      returns?.let { result.put(MdnBundle.message("mdn.documentation.section.returns"), it) }
      throws?.takeIf { it.isNotEmpty() }?.let {
        result.put(MdnBundle.message("mdn.documentation.section.throws"), buildSubSection(it))
      }
      return result
    }
}

data class MdnCssBasicSymbolDocumentation(
  override val url: String?,
  override val status: Set<MdnApiStatus>?,
  @JsonDeserialize(using = CompatibilityMapDeserializer::class)
  override val compatibility: CompatibilityMap?,
  override val baseline: BaselineData?,
  override val doc: String?,
  val formalSyntax: String?,
) : MdnRawSymbolDocumentation {
  override val sections: Map<String, String>
    get() {
      val result = mutableMapOf<String, String>()
      formalSyntax?.takeIf { it.isNotEmpty() }?.let {
        result.put(MdnBundle.message("mdn.documentation.section.syntax"), "<pre><code>$it</code></pre>")
      }
      return result
    }
}

data class MdnCssAtRuleSymbolDocumentation(
  override val url: String?,
  override val status: Set<MdnApiStatus>?,
  @JsonDeserialize(using = CompatibilityMapDeserializer::class)
  override val compatibility: CompatibilityMap?,
  override val baseline: BaselineData?,
  override val doc: String?,
  val properties: Map<String, MdnCssPropertySymbolDocumentation>?,
  val formalSyntax: String?,
) : MdnRawSymbolDocumentation {
  override val sections: Map<String, String>
    get() {
      val result = mutableMapOf<String, String>()
      formalSyntax?.takeIf { it.isNotEmpty() }?.let {
        result.put(MdnBundle.message("mdn.documentation.section.syntax"), "<pre><code>$it</code></pre>")
      }
      return result
    }
}

data class MdnCssPropertySymbolDocumentation(
  override val url: String?,
  override val status: Set<MdnApiStatus>?,
  @JsonDeserialize(using = CompatibilityMapDeserializer::class)
  override val compatibility: CompatibilityMap?,
  override val baseline: BaselineData?,
  override val doc: String?,
  val formalSyntax: String?,
  val values: Map<String, String>?,
) : MdnRawSymbolDocumentation {
  override val sections: Map<String, String>
    get() {
      val result = mutableMapOf<String, String>()
      formalSyntax?.takeIf { it.isNotEmpty() }?.let {
        result.put(MdnBundle.message("mdn.documentation.section.syntax"), "<pre><code>$it</code></pre>")
      }
      values?.takeIf { it.isNotEmpty() }?.let {
        result.put(MdnBundle.message("mdn.documentation.section.values"), buildSubSection(values))
      }
      return result
    }
}

enum class MdnApiNamespace {
  Html,
  Svg,
  MathML,
  WebApi,
  GlobalObjects,
  DomEvents,
  Css
}

enum class MdnApiStatus {
  Experimental,
  StandardTrack,
  Deprecated,
  Obsolete
}

enum class MdnJavaScriptRuntime(displayName: String? = null, mdnId: String? = null, val firstVersion: String = "1") {
  Chrome,
  ChromeAndroid(displayName = "Chrome Android", mdnId = "chrome_android", firstVersion = "18"),
  Edge(firstVersion = "12"),
  Firefox,
  FirefoxAndroid(displayName = "Firefox Android", mdnId = "firefox_android"),
  Opera,
  Safari,
  SafariIOS(displayName = "Safari iOS", mdnId = "safari_ios"),
  Nodejs(displayName = "Node.js", firstVersion = "0.10.0"),
  Deno(firstVersion = "1.0")
  ;

  val mobileRuntime: MdnJavaScriptRuntime?
    get() = when (this) {
      Chrome -> ChromeAndroid
      Firefox -> FirefoxAndroid
      Safari -> SafariIOS
      else -> null
    }
  val mdnId: String = mdnId ?: toLowerCase(name)
  val displayName: String = displayName ?: name

}

internal val BASELINE_BROWSERS: Set<MdnJavaScriptRuntime> = setOf(
  MdnJavaScriptRuntime.Chrome,
  MdnJavaScriptRuntime.ChromeAndroid,
  MdnJavaScriptRuntime.Firefox,
  MdnJavaScriptRuntime.FirefoxAndroid,
  MdnJavaScriptRuntime.Edge,
  MdnJavaScriptRuntime.Safari,
  MdnJavaScriptRuntime.SafariIOS,
)

enum class MdnCssSymbolKind {
  AtRule {
    override fun decorateName(name: String): String = "@$name"
    override fun getDocumentationMap(documentation: MdnCssDocumentation): Map<String, MdnRawSymbolDocumentation> = documentation.atRules
  },
  Property {
    override fun decorateName(name: String): String = name
    override fun getDocumentationMap(documentation: MdnCssDocumentation): Map<String, MdnRawSymbolDocumentation> = documentation.properties
    override fun getSymbolDoc(documentation: MdnCssDocumentation, name: String): MdnSymbolDocumentation? {
      if (name.startsWith("@")) {
        val atRule = name.takeWhile { it != '.' }.substring(1).lowercase(Locale.US)
        val propertyName = name.takeLastWhile { it != '.' }.lowercase(Locale.US)
        documentation.atRules[atRule]?.properties?.get(propertyName)?.let {
          return MdnSymbolDocumentationAdapter(name, documentation, it)
        }
        return super.getSymbolDoc(documentation, propertyName)
      }
      return super.getSymbolDoc(documentation, name)
    }
  },
  PseudoClass {
    override fun decorateName(name: String): String = ":$name"
    override fun getDocumentationMap(documentation: MdnCssDocumentation): Map<String, MdnRawSymbolDocumentation> = documentation.pseudoClasses
    override fun getSymbolDoc(documentation: MdnCssDocumentation, name: String): MdnSymbolDocumentation? =
      // In case of pseudo class query we should fallback to pseudo element
      super.getSymbolDoc(documentation, name) ?: PseudoElement.getSymbolDoc(documentation, name)
  },
  PseudoElement {
    override fun decorateName(name: String): String = "::$name"
    override fun getDocumentationMap(documentation: MdnCssDocumentation): Map<String, MdnRawSymbolDocumentation> = documentation.pseudoElements
  },
  Function {
    override fun decorateName(name: String): String = "$name()"
    override fun getDocumentationMap(documentation: MdnCssDocumentation): Map<String, MdnRawSymbolDocumentation> = documentation.functions
  },
  DataType {
    override fun decorateName(name: String): String = name
    override fun getDocumentationMap(documentation: MdnCssDocumentation): Map<String, MdnRawSymbolDocumentation> = documentation.dataTypes
  },
  ;

  protected abstract fun getDocumentationMap(documentation: MdnCssDocumentation): Map<String, MdnRawSymbolDocumentation>

  protected abstract fun decorateName(name: String): String

  open fun getSymbolDoc(documentation: MdnCssDocumentation, name: String): MdnSymbolDocumentation? =
    getDocumentationMap(documentation)[name.lowercase(Locale.US)]?.let {
      MdnSymbolDocumentationAdapter(decorateName(name), documentation, it)
    }
}

val webApiFragmentStarts = arrayOf('a', 'e', 'l', 'r', 'u')

private class CompatibilityMapDeserializer : JsonDeserializer<CompatibilityMap>() {

  override fun deserialize(p: JsonParser, ctxt: DeserializationContext): CompatibilityMap =
    p.readValueAsTree<TreeNode>()
      .asSafely<ObjectNode>()
      ?.let {
        if (it.firstOrNull() is ObjectNode) {
          it.fields().asSequence()
            .map { (key, value) -> Pair(key, (value as ObjectNode).toBcdMap()) }
            .toMap()
        }
        else {
          mapOf(Pair(defaultBcdContext, it.toBcdMap()))
        }
      }
    ?: emptyMap()

  private fun ObjectNode.toBcdMap(): Map<MdnJavaScriptRuntime, String> =
    this.fields().asSequence().map { (key, value) ->
      Pair(MdnJavaScriptRuntime.valueOf(key), (value as TextNode).asText())
    }.toMap()

}

private fun getWebApiFragment(name: String): Char =
  webApiFragmentStarts.findLast { it <= name[0].lowercaseChar() }!!

private const val MDN_DOCS_URL_PREFIX = "\$MDN_URL\$"

private val documentationCache: LoadingCache<Pair<MdnApiNamespace, Char?>, MdnDocumentation> = Caffeine.newBuilder()
  .expireAfterAccess(10, TimeUnit.MINUTES)
  .build { (namespace, segment) -> loadDocumentation(namespace, segment) }

private val webApiIndex: Map<String, String> by lazy {
  MdnHtmlDocumentation::class.java.getResource("WebApi-index.json")!!
    .let { jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(it) }
}

private fun fixMdnUrls(content: String, lang: String): String =
  content.replace("$MDN_DOCS_URL_PREFIX/", "https://developer.mozilla.org/$lang/docs/")
    .replace(MDN_DOCS_URL_PREFIX, "https://developer.mozilla.org/$lang/docs")

fun getHtmlApiNamespace(namespace: String?, element: PsiElement?, symbolName: String): MdnApiNamespace =
  when {
    symbolName.equals("svg", true) -> MdnApiNamespace.Svg
    symbolName.equals("math", true) -> MdnApiNamespace.MathML
    namespace == HtmlUtil.SVG_NAMESPACE -> MdnApiNamespace.Svg
    namespace == HtmlUtil.MATH_ML_NAMESPACE -> MdnApiNamespace.MathML
    else -> PsiTreeUtil.findFirstParent(element, false) { parent ->
      parent is XmlTag && parent.localName.lowercase(Locale.US).let { it == "svg" || it == "math" }
    }?.asSafely<XmlTag>()?.let {
      when (it.name.lowercase(Locale.US)) {
        "svg" -> MdnApiNamespace.Svg
        "math" -> MdnApiNamespace.MathML
        else -> null
      }
    } ?: MdnApiNamespace.Html
  }


private fun loadDocumentation(namespace: MdnApiNamespace, segment: Char?): MdnDocumentation =
  loadDocumentation(namespace, segment, when (namespace) {
    MdnApiNamespace.Css -> MdnCssDocumentation::class.java
    MdnApiNamespace.WebApi, MdnApiNamespace.GlobalObjects -> MdnJsDocumentation::class.java
    MdnApiNamespace.DomEvents -> MdnDomEventsDocumentation::class.java
    else -> MdnHtmlDocumentation::class.java
  })

private fun <T : MdnDocumentation> loadDocumentation(namespace: MdnApiNamespace, segment: Char?, clazz: Class<T>): T {
  val mainFile = MdnHtmlDocumentation::class.java.getResource("${namespace.name}${segment?.let { "-$it" } ?: ""}.json")
  val obsoleteFile = MdnHtmlDocumentation::class.java.getResource("${namespace.name}${segment?.let { "-$it" } ?: ""}-obsolete.json")
  return sequenceOf(mainFile, obsoleteFile)
    .filterNotNull()
    .map { jacksonObjectMapper().readValue(it, clazz) }
    .reduce(::mergeDocumentation)
}

private fun buildDoc(
  doc: MdnSymbolDocumentation,
  withDefinition: Boolean,
  additionalSectionsContent: Consumer<java.lang.StringBuilder>?,
): @NlsSafe String {
  val buf = StringBuilder()
  if (withDefinition)
    buf.append(DocumentationMarkup.DEFINITION_START)
      .append(doc.name)
      .append(DocumentationMarkup.DEFINITION_END)
      .append("\n")

  buf.append(DocumentationMarkup.CONTENT_START)
    .append(doc.description)
    .append(DocumentationMarkup.CONTENT_END)

  val sections = doc.sections

  if (sections.isNotEmpty() || additionalSectionsContent != null) {
    buf.append("\n")
      .append(DocumentationMarkup.SECTIONS_START)
    for (entry in sections) {
      buf.append("\n")
        .append(DocumentationMarkup.SECTION_HEADER_START)
        .append(entry.key)
      if (entry.value.isNotEmpty()) {
        if (!entry.key.endsWith(":"))
          buf.append(':')
        buf.append(DocumentationMarkup.SECTION_SEPARATOR)
          .append(entry.value)
      }
      buf.append(DocumentationMarkup.SECTION_END)
    }
    additionalSectionsContent?.accept(buf)
    buf.append(DocumentationMarkup.SECTIONS_END)
  }
  buf.append("\n")
    .append(DocumentationMarkup.CONTENT_START)
    .append(doc.footnote)
    .append(DocumentationMarkup.CONTENT_END)
    .append("\n")
  // Expand MDN URL prefix and fix relative "#" references to point to external MDN docs
  return buf.toString()
}

fun buildSubSection(items: Map<String, String>): String {
  val result = StringBuilder()
  items.forEach { (name, doc) ->
    result.append("<p><code>")
      .append(name)
      .append("</code> &ndash; ")
      .append(doc)
      .append("<br><span style='font-size:0.2em'>&nbsp;</span>\n")
  }
  return result.toString()
}

private fun getUnprefixedName(name: String): String? {
  if (name.length < 4 || name[0] != '-' || name[1] == '-') return null
  val index = name.indexOf('-', 2)
  return if (index < 0 || index == name.length - 1) null else name.substring(index + 1)
}

private val UPPER_CASE = Regex("(?=\\p{Upper})")

private fun String.toKebabCase() =
  this.split(UPPER_CASE).joinToString("-") { it.lowercase(Locale.US) }


@Suppress("UNCHECKED_CAST")
private fun <T : MdnDocumentation> mergeDocumentation(doc1: T, doc2: T): T =
  when (val src = doc1 as MdnDocumentation) {
    is MdnHtmlDocumentation -> src.copy(
      attrs = src.attrs + (doc2 as MdnHtmlDocumentation).attrs,
      tags = src.tags.mergeWith(doc2.tags) { t1, t2 -> t1.copy(attrs = merge(t1.attrs, t2.attrs)) }
    ) as T
    is MdnCssDocumentation -> src.copy(
      atRules = src.atRules.mergeWith((doc2 as MdnCssDocumentation).atRules) { r1, r2 ->
        r1.copy(properties = merge(r1.properties, r2.properties))
      },
      properties = src.properties + doc2.properties,
      pseudoClasses = src.pseudoClasses + doc2.pseudoClasses,
      pseudoElements = src.pseudoElements + doc2.pseudoElements,
      functions = src.functions + doc2.functions,
      dataTypes = src.dataTypes + doc2.dataTypes,
    ) as T
    is MdnDomEventsDocumentation -> src.copy(
      events = src.events + (doc2 as MdnDomEventsDocumentation).events,
    ) as T
    is MdnJsDocumentation -> src.copy(
      symbols = src.symbols + (doc2 as MdnJsDocumentation).symbols,
    ) as T
  }

private fun <K, V> merge(map1: Map<K, V>?, map2: Map<K, V>?): Map<K, V>? =
  if (map1 == null && map2 == null)
    null
  else if (map1 == null)
    map2
  else if (map2 == null)
    map1
  else
    (map1 + map2)


private fun <T : Any> Map<String, T>.mergeWith(other: Map<String, T>, mergeFunction: (T, T) -> T): Map<String, T> =
  toMutableMap().also { result ->
    other.entries.forEach { (key, value) ->
      result.merge(key, value, mergeFunction)
    }
  }
