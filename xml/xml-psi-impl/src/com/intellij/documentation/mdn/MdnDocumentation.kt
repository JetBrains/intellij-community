// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.documentation.mdn

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.util.text.StringUtil.capitalize
import com.intellij.openapi.util.text.StringUtil.toLowerCase
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.html.dtd.HtmlSymbolDeclaration
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.*
import com.intellij.util.castSafelyTo
import com.intellij.xml.util.HtmlUtil
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
  }.toLowerCase(Locale.US).let { webApiIndex[it] ?: it }
  val jsNamespace = qualifiedName.takeWhile { it != '.' }
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
      getTagDocumentation(getApiNamespace(element.namespace, element, toLowerCase(symbolName)), toLowerCase(symbolName))
    }
    // TODO support special documentation for attribute values
    element is XmlAttribute || element is XmlAttributeValue -> {
      PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java, false)?.let { attr ->
        symbolName = attr.localName
        getAttributeDocumentation(getApiNamespace(attr.namespace, attr, toLowerCase(symbolName)),
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
        val namespace = getApiNamespace(context?.namespace, context, lcName)
        if (isTag) {
          getTagDocumentation(namespace, lcName)
        }
        else {
          getAttributeDocumentation(namespace, context?.localName?.let(::toLowerCase), lcName)
        }
      }
    }
  }
    ?.takeIf { symbolName != null }
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

fun getHtmlMdnAttributeDocumentation(namespace: MdnApiNamespace,
                                     tagName: String?,
                                     attributeName: String): MdnSymbolDocumentation? {
  assert(namespace == MdnApiNamespace.Html || namespace == MdnApiNamespace.MathML || namespace == MdnApiNamespace.Svg)
  return getAttributeDocumentation(namespace, tagName, attributeName)?.let { (source, doc) ->
    MdnSymbolDocumentationAdapter(attributeName, source, doc)
  }
}

private fun getTagDocumentation(namespace: MdnApiNamespace, tagName: String): Pair<MdnHtmlDocumentation, MdnHtmlElementDocumentation>? {
  val documentation = documentationCache[Pair(namespace, null)] as? MdnHtmlDocumentation ?: return null
  return documentation.tags[tagName.let { documentation.tagAliases[it] ?: it }]?.let { Pair(documentation, it) }
}

private fun getAttributeDocumentation(namespace: MdnApiNamespace,
                                      tagName: String?,
                                      attributeName: String): Pair<MdnDocumentation, MdnRawSymbolDocumentation>? {
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
      tag.doc ?: global.doc
    )
  } ?: tag

private fun innerGetEventDoc(eventName: String): Pair<MdnDocumentation, MdnDomEventDocumentation>? =
  (documentationCache[Pair(MdnApiNamespace.DomEvents, null)] as MdnDomEventsDocumentation)
    .let { Pair(it, it.events[eventName] ?: return@let null) }

interface MdnSymbolDocumentation {
  val url: String
  val isDeprecated: Boolean

  fun getDocumentation(withDefinition: Boolean): String

  fun getDocumentation(withDefinition: Boolean,
                       additionalSectionsContent: Consumer<java.lang.StringBuilder>?): String
}

class MdnSymbolDocumentationAdapter(private val name: String,
                                    private val source: MdnDocumentation,
                                    private val doc: MdnRawSymbolDocumentation) : MdnSymbolDocumentation {
  override val url: String
    get() = fixMdnUrls(doc.url, source.lang)

  override val isDeprecated: Boolean
    get() = doc.status?.contains(MdnApiStatus.Deprecated) == true

  override fun getDocumentation(withDefinition: Boolean): String =
    getDocumentation(withDefinition, null)

  override fun getDocumentation(withDefinition: Boolean,
                                additionalSectionsContent: Consumer<java.lang.StringBuilder>?) =
    buildDoc(doc, name, source.lang, withDefinition, additionalSectionsContent)

}

interface MdnRawSymbolDocumentation {
  val url: String
  val status: Set<MdnApiStatus>?
  val compatibility: Map<MdnJavaScriptRuntime, String>?
  val doc: String?
  val sections: Map<String, String> get() = emptyMap()
}

interface MdnDocumentation {
  val lang: String
}

data class MdnHtmlDocumentation(override val lang: String,
                                val attrs: Map<String, MdnHtmlAttributeDocumentation>,
                                val tags: Map<String, MdnHtmlElementDocumentation>,
                                val tagAliases: Map<String, String> = emptyMap()) : MdnDocumentation

data class MdnJsDocumentation(override val lang: String,
                              val symbols: Map<String, MdnJsSymbolDocumentation>) : MdnDocumentation

data class MdnDomEventsDocumentation(override val lang: String,
                                     val events: Map<String, MdnDomEventDocumentation>) : MdnDocumentation

data class MdnCssDocumentation(override val lang: String,
                               val atRules: Map<String, MdnCssAtRuleSymbolDocumentation>,
                               val properties: Map<String, MdnCssPropertySymbolDocumentation>,
                               val pseudoClasses: Map<String, MdnCssBasicSymbolDocumentation>,
                               val pseudoElements: Map<String, MdnCssBasicSymbolDocumentation>,
                               val functions: Map<String, MdnCssBasicSymbolDocumentation>,
                               val dataTypes: Map<String, MdnCssBasicSymbolDocumentation>) : MdnDocumentation

data class MdnHtmlElementDocumentation(override val url: String,
                                       override val status: Set<MdnApiStatus>?,
                                       override val compatibility: Map<MdnJavaScriptRuntime, String>?,
                                       override val doc: String,
                                       val details: Map<String, String>?,
                                       val attrs: Map<String, MdnHtmlAttributeDocumentation>?) : MdnRawSymbolDocumentation

data class MdnHtmlAttributeDocumentation(override val url: String,
                                         override val status: Set<MdnApiStatus>?,
                                         override val compatibility: Map<MdnJavaScriptRuntime, String>?,
                                         override val doc: String?) : MdnRawSymbolDocumentation

data class MdnDomEventDocumentation(override val url: String,
                                    override val status: Set<MdnApiStatus>?,
                                    override val compatibility: Map<MdnJavaScriptRuntime, String>?,
                                    override val doc: String?) : MdnRawSymbolDocumentation

data class MdnJsSymbolDocumentation(override val url: String,
                                    override val status: Set<MdnApiStatus>?,
                                    override val compatibility: Map<MdnJavaScriptRuntime, String>?,
                                    override val doc: String?,
                                    val parameters: Map<String, String>?,
                                    val returns: String?,
                                    val throws: Map<String, String>?) : MdnRawSymbolDocumentation {
  override val sections: Map<String, String>
    get() {
      val result = mutableMapOf<String, String>()
      parameters?.takeIf { it.isNotEmpty() }?.let {
        result.put("Params", buildSubSection(it))
      }
      returns?.let { result.put("Returns", it) }
      throws?.takeIf { it.isNotEmpty() }?.let {
        result.put("Throws", buildSubSection(it))
      }
      return result
    }
}

data class MdnCssBasicSymbolDocumentation(override val url: String,
                                          override val status: Set<MdnApiStatus>?,
                                          override val compatibility: Map<MdnJavaScriptRuntime, String>?,
                                          override val doc: String?) : MdnRawSymbolDocumentation

data class MdnCssAtRuleSymbolDocumentation(override val url: String,
                                           override val status: Set<MdnApiStatus>?,
                                           override val compatibility: Map<MdnJavaScriptRuntime, String>?,
                                           override val doc: String?,
                                           val properties: Map<String, MdnCssPropertySymbolDocumentation>?) : MdnRawSymbolDocumentation

data class MdnCssPropertySymbolDocumentation(override val url: String,
                                             override val status: Set<MdnApiStatus>?,
                                             override val compatibility: Map<MdnJavaScriptRuntime, String>?,
                                             override val doc: String?,
                                             val formalSyntax: String?,
                                             val values: Map<String, String>?) : MdnRawSymbolDocumentation {
  override val sections: Map<String, String>
    get() {
      val result = mutableMapOf<String, String>()
      formalSyntax?.takeIf { it.isNotEmpty() }?.let {
        result.put("Syntax", "<pre><code>$it</code></pre>")
      }
      values?.takeIf { it.isNotEmpty() }?.let {
        result.put("Values", buildSubSection(values))
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
  Deprecated
}

enum class MdnJavaScriptRuntime(displayName: String? = null, mdnId: String? = null, val firstVersion: String = "1") {
  Chrome,
  ChromeAndroid(displayName = "Chrome Android", mdnId = "chrome_android", firstVersion = "18"),
  Edge(firstVersion = "12"),
  Firefox,
  IE,
  Opera,
  Safari,
  SafariIOS(displayName = "Safari iOS", mdnId = "safari_ios"),
  Nodejs(displayName = "Node.js", firstVersion = "0.10.0");

  val mdnId: String = mdnId ?: toLowerCase(name)
  val displayName: String = displayName ?: name

}

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
        val atRule = name.takeWhile { it != '.' }.substring(1).toLowerCase(Locale.US)
        val propertyName = name.takeLastWhile { it != '.' }.toLowerCase(Locale.US)
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
  }, ;

  protected abstract fun getDocumentationMap(documentation: MdnCssDocumentation): Map<String, MdnRawSymbolDocumentation>

  protected abstract fun decorateName(name: String): String

  open fun getSymbolDoc(documentation: MdnCssDocumentation, name: String): MdnSymbolDocumentation? =
    getDocumentationMap(documentation)[name.toLowerCase(Locale.US)]?.let {
      MdnSymbolDocumentationAdapter(decorateName(name), documentation, it)
    }
}

val webApiFragmentStarts = arrayOf('a', 'e', 'l', 'r', 'u')

private fun getWebApiFragment(name: String): Char =
  webApiFragmentStarts.findLast { it <= name[0].toLowerCase() }!!

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

private fun getApiNamespace(namespace: String?, element: PsiElement?, symbolName: String): MdnApiNamespace =
  when {
    symbolName.equals("svg", true) -> MdnApiNamespace.Svg
    symbolName.equals("math", true) -> MdnApiNamespace.MathML
    namespace == HtmlUtil.SVG_NAMESPACE -> MdnApiNamespace.Svg
    namespace == HtmlUtil.MATH_ML_NAMESPACE -> MdnApiNamespace.MathML
    else -> PsiTreeUtil.findFirstParent(element, false) { parent ->
      parent is XmlTag && parent.localName.toLowerCase(Locale.US).let { it == "svg" || it == "math" }
    }?.castSafelyTo<XmlTag>()?.let {
      when (it.name.toLowerCase()) {
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

private fun <T : MdnDocumentation> loadDocumentation(namespace: MdnApiNamespace, segment: Char?, clazz: Class<T>): T =
  MdnHtmlDocumentation::class.java.getResource("${namespace.name}${segment?.let { "-$it" } ?: ""}.json")!!
    .let { jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(it, clazz) }

private fun buildDoc(doc: MdnRawSymbolDocumentation,
                     name: String,
                     lang: String,
                     withDefinition: Boolean,
                     additionalSectionsContent: Consumer<java.lang.StringBuilder>?): String {
  val buf = StringBuilder()
  if (withDefinition)
    buf.append(DocumentationMarkup.DEFINITION_START)
      .append(name)
      .append(DocumentationMarkup.DEFINITION_END)

  buf.append(DocumentationMarkup.CONTENT_START)
    .append(capitalize(doc.doc ?: ""))
    .append(DocumentationMarkup.CONTENT_END)

  val sections = doc.sections?.toMutableMap() ?: mutableMapOf()
  if (doc.compatibility != null) {
    sections["Supported by:"] = doc.compatibility!!.entries
      .joinToString(", ") { it.key.displayName + (if (it.value.isNotEmpty()) " " + it.value else "") }
      .ifBlank { "none" }
  }
  doc.status?.asSequence()
    ?.filter { it != MdnApiStatus.StandardTrack }
    ?.map { Pair(it.name, "") }
    ?.toMap(sections)
  if (!sections.isNullOrEmpty() || additionalSectionsContent != null) {
    buf.append(DocumentationMarkup.SECTIONS_START)
    for (entry in sections) {
      buf.append(DocumentationMarkup.SECTION_HEADER_START)
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
  buf.append(DocumentationMarkup.CONTENT_START)
    .append("By <a href='")
    .append(doc.url)
    .append("/contributors.txt'>Mozilla Contributors</a>, <a href='http://creativecommons.org/licenses/by-sa/2.5/'>CC BY-SA 2.5</a>")
    .append(DocumentationMarkup.CONTENT_END)
  // Expand MDN URL prefix and fix relative "#" references to point to external MDN docs
  return fixMdnUrls(
    buf.toString().replace(Regex("<a[ \n\t]+href=[ \t]*['\"]#([^'\"]*)['\"]"), "<a href=\"${escapeReplacement(doc.url)}#$1\""),
    lang)
}

fun buildSubSection(items: Map<String, String>): String {
  val result = StringBuilder()
  items.forEach { (name, doc) ->
    result.append("<p><code>")
      .append(name)
      .append("</code> &ndash; ")
      .append(doc)
      .append("<br><span style='font-size:0.2em'>&nbsp;</span>")
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
  this.split(UPPER_CASE).joinToString("-") { it.toLowerCase(Locale.US) }

