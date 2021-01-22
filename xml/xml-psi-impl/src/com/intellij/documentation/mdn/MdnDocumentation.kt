// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.documentation.mdn

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.util.text.StringUtil
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
  val documentation = documentationCache[
    Pair(namespace, if (namespace == MdnApiNamespace.WebApi) getWebApiFragment(mdnQualifiedName) else null)
  ] as? MdnJsDocumentation ?: return null
  return documentation.symbols[mdnQualifiedName]
           ?.let { MdnSymbolDocumentationAdapter(mdnQualifiedName, documentation, it) }
         ?: qualifiedName.takeIf { it.startsWith("CSSStyleDeclaration.") }
           ?.let { getCssMdnDocumentation(it.substring("CSSStyleDeclaration.".length).toKebabCase(), MdnCssSymbolKind.Property) }
}

fun getCssMdnDocumentation(name: String, kind: MdnCssSymbolKind): MdnSymbolDocumentation? {
  val documentation = documentationCache[Pair(MdnApiNamespace.Css, null)] as? MdnCssDocumentation ?: return null
  return kind.getSymbolDoc(documentation, name) ?: getUnprefixedName(name)?.let { kind.getSymbolDoc(documentation, it) }
}

fun getHtmlMdnDocumentation(element: PsiElement, context: XmlTag?): MdnSymbolDocumentation? {
  var symbolName: String? = null
  return when {
    // Directly from the file
    element is XmlTag && !element.containingFile.name.endsWith(".xsd", true) -> {
      symbolName = element.localName.let { if (element.isCaseSensitive) it else toLowerCase(it) }
      getTagDocumentation(getApiNamespace(element.namespace, element, symbolName), symbolName)
    }
    // TODO support special documentation for attribute values
    element is XmlAttribute || element is XmlAttributeValue -> {
      PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java, false)?.let { attr ->
        symbolName = attr.localName.let { if (attr.parent.isCaseSensitive) it else toLowerCase(it) }
        getAttributeDocumentation(getApiNamespace(attr.namespace, attr, symbolName!!), attr.parent.localName, symbolName!!)
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
          symbolName = toLowerCase(element.name)
        }
        is XmlAttributeDecl -> {
          isTag = false
          symbolName = element.nameElement.text.let { toLowerCase(it) }
        }
        is HtmlSymbolDeclaration -> {
          isTag = element.kind == HtmlSymbolDeclaration.Kind.ELEMENT
          symbolName = toLowerCase(element.name)
        }
      }
      symbolName?.let {
        val namespace = getApiNamespace(context?.namespace, context, it)
        if (isTag) {
          getTagDocumentation(namespace, it)
        }
        else {
          getAttributeDocumentation(namespace, context?.localName?.let(::toLowerCase), it)
        }
      }
    }
  }
    ?.takeIf { symbolName != null }
    ?.let { (source, doc) -> MdnSymbolDocumentationAdapter(symbolName!!.toLowerCase(Locale.US), source, doc) }
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
                                      attributeName: String): Pair<MdnHtmlDocumentation, MdnHtmlAttributeDocumentation>? {
  val documentation = documentationCache[Pair(namespace, null)] as? MdnHtmlDocumentation ?: return null
  return tagName
           ?.let { name ->
             getTagDocumentation(namespace, name)?.let { (source, tagDoc) ->
               tagDoc.attrs?.get(attributeName)?.let { Pair(source, it) }
             }
           }
         ?: documentation.attrs[attributeName]?.let { Pair(documentation, it) }
}

interface MdnSymbolDocumentation {
  val url: String
  val isDeprecated: Boolean

  fun getDocumentation(withDefinition: Boolean,
                       quickDoc: Boolean): String

  fun getDocumentation(withDefinition: Boolean,
                       quickDoc: Boolean,
                       additionalSectionsContent: Consumer<java.lang.StringBuilder>?): String
}

class MdnSymbolDocumentationAdapter(private val name: String,
                                    private val source: MdnDocumentation,
                                    private val doc: MdnRawSymbolDocumentation) : MdnSymbolDocumentation {
  override val url: String
    get() = fixMdnUrls(doc.url, source.lang)

  override val isDeprecated: Boolean
    get() = doc.status?.contains(MdnApiStatus.Deprecated) == true

  override fun getDocumentation(withDefinition: Boolean, quickDoc: Boolean): String =
    getDocumentation(withDefinition, quickDoc, null)

  override fun getDocumentation(withDefinition: Boolean, quickDoc: Boolean, additionalSectionsContent: Consumer<java.lang.StringBuilder>?) =
    buildDoc(doc, name, source.lang, withDefinition, quickDoc, additionalSectionsContent)

}

interface MdnRawSymbolDocumentation {
  val url: String
  val status: Set<MdnApiStatus>?
  val doc: String?
  val sections: Map<String, String>?
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

data class MdnCssDocumentation(override val lang: String,
                               val atRules: Map<String, MdnCssAtRuleSymbolDocumentation>,
                               val properties: Map<String, MdnCssPropertySymbolDocumentation>,
                               val pseudoClasses: Map<String, MdnCssBasicSymbolDocumentation>,
                               val pseudoElements: Map<String, MdnCssBasicSymbolDocumentation>,
                               val functions: Map<String, MdnCssBasicSymbolDocumentation>,
                               val dataTypes: Map<String, MdnCssBasicSymbolDocumentation>) : MdnDocumentation

data class MdnHtmlElementDocumentation(override val url: String,
                                       override val status: Set<MdnApiStatus>?,
                                       override val doc: String,
                                       override val sections: Map<String, String>?,
                                       val attrs: Map<String, MdnHtmlAttributeDocumentation>?) : MdnRawSymbolDocumentation

data class MdnHtmlAttributeDocumentation(override val url: String,
                                         override val status: Set<MdnApiStatus>?,
                                         override val doc: String?,
                                         override val sections: Map<String, String>?) : MdnRawSymbolDocumentation

data class MdnJsSymbolDocumentation(override val url: String,
                                    override val status: Set<MdnApiStatus>?,
                                    override val doc: String?,
                                    val parameters: Map<String, String>?,
                                    val returns: String?,
                                    val throws: Map<String, String>?) : MdnRawSymbolDocumentation {
  override val sections: Map<String, String>?
    get() {
      val result = mutableMapOf<String, String>()
      parameters?.takeIf { it.isNotEmpty() }?.let {
        result.put("Params", buildSubSection(it))
      }
      returns?.let { result.put("Returns", it) }
      throws?.takeIf { it.isNotEmpty() }?.let {
        result.put("Throws", buildSubSection(it))
      }
      return result.takeIf { it.isNotEmpty() }
    }
}

data class MdnCssBasicSymbolDocumentation(override val url: String,
                                          override val status: Set<MdnApiStatus>?,
                                          override val doc: String?,
                                          override val sections: Map<String, String>?) : MdnRawSymbolDocumentation

data class MdnCssAtRuleSymbolDocumentation(override val url: String,
                                           override val status: Set<MdnApiStatus>?,
                                           override val doc: String?,
                                           override val sections: Map<String, String>?,
                                           val properties: Map<String, MdnCssPropertySymbolDocumentation>?) : MdnRawSymbolDocumentation

data class MdnCssPropertySymbolDocumentation(override val url: String,
                                             override val status: Set<MdnApiStatus>?,
                                             override val doc: String?,
                                             val values: Map<String, String>?) : MdnRawSymbolDocumentation {
  override val sections: Map<String, String>?
    get() {
      val result = mutableMapOf<String, String>()
      values?.takeIf { it.isNotEmpty() }?.let {
        result.put("Values", buildSubSection(values))
      }
      return result.takeIf { it.isNotEmpty() }
    }
}

enum class MdnApiNamespace {
  Html,
  Svg,
  MathML,
  WebApi,
  GlobalObjects,
  Css
}

enum class MdnApiStatus {
  Experimental,
  StandardTrack,
  Deprecated
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
    else -> MdnHtmlDocumentation::class.java
  })

private fun <T : MdnDocumentation> loadDocumentation(namespace: MdnApiNamespace, segment: Char?, clazz: Class<T>): T =
  MdnHtmlDocumentation::class.java.getResource("${namespace.name}${segment?.let { "-$it" } ?: ""}.json")!!
    .let { jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(it, clazz) }

private fun buildDoc(doc: MdnRawSymbolDocumentation,
                     name: String,
                     lang: String,
                     withDefinition: Boolean,
                     quickDoc: Boolean,
                     additionalSectionsContent: Consumer<java.lang.StringBuilder>?): String {
  val buf = StringBuilder()
  if (withDefinition)
    buf.append(DocumentationMarkup.DEFINITION_START)
      .append(name)
      .append(DocumentationMarkup.DEFINITION_END)

  buf.append(DocumentationMarkup.CONTENT_START)
    .append(StringUtil.capitalize(doc.doc ?: ""))
    .append(DocumentationMarkup.CONTENT_END)

  val sections = doc.sections
  if (!sections.isNullOrEmpty() && !quickDoc) {
    buf.append(DocumentationMarkup.SECTIONS_START)
    for (entry in sections) {
      buf.append(DocumentationMarkup.SECTION_HEADER_START).append(entry.key)
        .append(DocumentationMarkup.SECTION_SEPARATOR).append(entry.value)
        .append(DocumentationMarkup.SECTION_END)
    }
    additionalSectionsContent?.accept(buf)
    buf.append(DocumentationMarkup.SECTIONS_END)
  }
  else if (additionalSectionsContent != null) {
    buf.append(DocumentationMarkup.SECTIONS_START)
    additionalSectionsContent.accept(buf)
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

private fun buildSubSection(items: Map<String, String>): String {
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

