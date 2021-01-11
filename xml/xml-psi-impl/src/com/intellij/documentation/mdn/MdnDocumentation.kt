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
import java.util.*
import java.util.concurrent.TimeUnit

fun getJsMdnDocumentation(qualifiedName: String, namespace: MdnApiNamespace): MdnSymbolDocumentation? {
  assert(namespace == MdnApiNamespace.WebApi && namespace == MdnApiNamespace.GlobalObjects)
  val documentation = documentationCache[
    Pair(namespace, if (namespace == MdnApiNamespace.WebApi) getWebApiFragment(qualifiedName) else null)
  ] as? MdnJsDocumentation ?: return null
  return documentation.symbols[qualifiedName]
    ?.let { MdnSymbolDocumentationAdapter(qualifiedName, it) }
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
    ?.let { MdnSymbolDocumentationAdapter(symbolName!!.toLowerCase(Locale.US), it) }
}

private fun getTagDocumentation(namespace: MdnApiNamespace, tagName: String): MdnHtmlElementDocumentation? {
  val documentation = documentationCache[Pair(namespace, null)] as? MdnHtmlDocumentation ?: return null
  return documentation.tags[tagName.let { documentation.tagAliases[it] ?: it }]
}

private fun getAttributeDocumentation(namespace: MdnApiNamespace, tagName: String?, attributeName: String): MdnHtmlAttributeDocumentation? {
  val documentation = documentationCache[Pair(namespace, null)] as? MdnHtmlDocumentation ?: return null
  return tagName?.let { getTagDocumentation(namespace, it)?.attrs?.get(attributeName) }
         ?: documentation.attrs[attributeName]
}

interface MdnSymbolDocumentation {
  val url: String
  val isDeprecated: Boolean
  fun getDocumentation(withDefinition: Boolean, quickDoc: Boolean): String
}

class MdnSymbolDocumentationAdapter(private val name: String, private val doc: MdnRawSymbolDocumentation) : MdnSymbolDocumentation {
  override val url: String
    get() = fixMdnUrls(doc.url)

  override val isDeprecated: Boolean
    get() = doc.status?.contains(MdnApiStatus.Deprecated) == true

  override fun getDocumentation(withDefinition: Boolean, quickDoc: Boolean) =
    buildDoc(doc, name, withDefinition, quickDoc)

}

interface MdnRawSymbolDocumentation {
  val url: String
  val status: Set<MdnApiStatus>?
  val doc: String?
  val sections: Map<String, String>?
}

interface MdnDocumentation

data class MdnHtmlDocumentation(val attrs: Map<String, MdnHtmlAttributeDocumentation>,
                                val tags: Map<String, MdnHtmlElementDocumentation>,
                                val tagAliases: Map<String, String> = emptyMap()) : MdnDocumentation

data class MdnJsDocumentation(val symbols: Map<String, MdnJsSymbolDocumentation>) : MdnDocumentation

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
                                    override val sections: Map<String, String>?) : MdnRawSymbolDocumentation

enum class MdnApiNamespace {
  Html,
  Svg,
  MathML,
  WebApi,
  GlobalObjects;
}

enum class MdnApiStatus {
  Experimental,
  StandardTrack,
  Deprecated
}

val webApiFragmentStarts = arrayOf('a', 'e', 'l', 'r', 'u')

private fun getWebApiFragment(name: String): Char =
  webApiFragmentStarts.findLast { it <= name[0].toLowerCase() }!!

private const val MDN_DOCS_URL_PREFIX = "\$MDN_URL\$"

private val documentationCache: LoadingCache<Pair<MdnApiNamespace, Char?>, MdnDocumentation> = Caffeine.newBuilder()
  .expireAfterAccess(10, TimeUnit.MINUTES)
  .build { (namespace, segment) -> loadDocumentation(namespace, segment) }

private fun fixMdnUrls(content: String): String =
  content.replace("$MDN_DOCS_URL_PREFIX/", "https://developer.mozilla.org/docs/")
    .replace(MDN_DOCS_URL_PREFIX, "https://developer.mozilla.org/docs")

private fun getApiNamespace(namespace: String?, element: PsiElement?, symbolName: String): MdnApiNamespace =
  when {
    symbolName.equals("svg", true) -> MdnApiNamespace.Svg
    symbolName.equals("math", true) -> MdnApiNamespace.MathML
    namespace == "http://www.w3.org/2000/svg" -> MdnApiNamespace.Svg
    namespace == "http://www.w3.org/1998/Math/MathML" -> MdnApiNamespace.MathML
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
  if (namespace == MdnApiNamespace.WebApi || namespace == MdnApiNamespace.GlobalObjects)
    loadJavaScriptDocumentation(namespace, segment)
  else
    loadHtmlDocumentation(namespace)

private fun loadJavaScriptDocumentation(namespace: MdnApiNamespace, segment: Char?): MdnJsDocumentation =
  MdnHtmlDocumentation::class.java.getResource("${namespace.name}${segment?.let { "-$it" } ?: ""}.json")!!
    .let { jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(it) }

private fun loadHtmlDocumentation(namespace: MdnApiNamespace): MdnHtmlDocumentation =
  MdnHtmlDocumentation::class.java.getResource("${namespace.name}.json")!!
    .let { jacksonObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(it) }

private fun buildDoc(doc: MdnRawSymbolDocumentation, name: String, withDefinition: Boolean, quickDoc: Boolean): String {
  val buf = StringBuilder()
  if (withDefinition)
    buf.append(DocumentationMarkup.DEFINITION_START)
      .append(name)
      .append(DocumentationMarkup.DEFINITION_END)

  buf.append(DocumentationMarkup.CONTENT_START)
    .append(StringUtil.capitalize(doc.doc ?: ""))
    .append(DocumentationMarkup.CONTENT_END)

  if (!doc.sections.isNullOrEmpty() && !quickDoc) {
    buf.append(DocumentationMarkup.SECTIONS_START)
    for (entry in doc.sections ?: emptyMap()) {
      buf.append(DocumentationMarkup.SECTION_HEADER_START).append(entry.key)
        .append(DocumentationMarkup.SECTION_SEPARATOR).append(entry.value)
        .append(DocumentationMarkup.SECTION_END)
    }
    buf.append(DocumentationMarkup.SECTIONS_END)
  }
  buf.append(DocumentationMarkup.CONTENT_START)
    .append("By <a href='")
    .append(doc.url)
    .append("/contributors.txt'>Mozilla Contributors</a>, <a href='http://creativecommons.org/licenses/by-sa/2.5/'>CC BY-SA 2.5</a>")
    .append(DocumentationMarkup.CONTENT_END)
  return fixMdnUrls(buf.toString())
}

