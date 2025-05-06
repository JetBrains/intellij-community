// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.html

import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.html.HtmlFileImpl
import com.intellij.psi.util.siblings
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.asSafely
import com.intellij.xml.Html5SchemaProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.io.FileNotFoundException

@RunWith(JUnit4::class)
class Html5TagAndAttributeNamesProviderTest : BasePlatformTestCase() {

  @Test
  fun testHtml5TagAndAttributeNamesProvider() {
    val location = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') +
                   "/xml/xml-psi-impl/gen/com/intellij/xml/util/Html5TagAndAttributeNamesProvider.kt"
    val contents = FileUtil.loadFile(File(location), "UTF-8", false)
    val version = Regex("VERSION = ([0-9]+)").find(contents)?.groups?.get(1)?.value?.toIntOrNull() ?: 0
    val expectedContents = generateFile(version)
    if (expectedContents != contents) {
      throw FileComparisonFailedError(
        "Looks like HTML5 schema was updated, so Html5TagAndAttributeNamesProvider needs to be updated as well. Copy version change!!!",
        contents, generateFile(version + 1), location)
    }
  }

  private fun generateFile(version: Int): String {
    val virtualFile: VirtualFile?
    val location = Html5SchemaProvider.getHtml5SchemaLocation()
    virtualFile = VfsUtil.findFileByIoFile(File(location), true)
    val descriptor = (PsiManager.getInstance(project).findFile(
      virtualFile
      ?: throw FileNotFoundException(location)
    ) as XmlFile).document!!.metaData as RelaxedHtmlNSDescriptor

    val htmlTags = descriptor.getRootElementsDescriptors(null)

    val htmlFile = PsiFileFactory.getInstance(project).createFileFromText(
      "test.html", HTMLLanguage.INSTANCE, "<svg></svg><math></math>") as HtmlFileImpl

    val svg = htmlFile.document?.rootTag?.asSafely<XmlTag>()!!
    val math = svg.siblings(withSelf = false).firstNotNullOf { it as? XmlTag }
    val map = sequenceOf("HTML" to htmlTags,
                         "SVG" to svg.descriptor!!.getElementsDescriptors(svg),
                         "MathML" to math.descriptor!!.getElementsDescriptors(math)
    ).associate { (namespace, tags) ->
      Pair(namespace,
           tags.sortedBy { it.name }.associate { tag ->
             Pair(tag.name, tag.getAttributesDescriptors(null).map { it.name }.filter { !it.startsWith("aria-") }
               .distinct()
               .sorted())
           })
    }
    val htmlMap = map["HTML"]!!
    val dlAttrs = htmlMap["dl"]!!.toSet()
    val acronymAttrs = htmlMap["acronym"]!!.toSet()
    val htmlAttrs = htmlMap["div"]!!.filter { dlAttrs.contains(it) && acronymAttrs.contains(it) }.toSet()

    val baseHtmlAttrs = htmlMap["frame"]!!.filter { htmlAttrs.contains(it) }.toSet()

    val svgMap = map["SVG"]!!
    val svgBasicAttrs = setOf("base", "id", "space")

    val gAttrs = svgMap["g"]!!.toSet()
    val animateAttrs = svgMap["animate"]!!.toSet()
    val svgAttrs = svgMap["marker"]!!.filter { gAttrs.contains(it) && animateAttrs.contains(it) }.toSet()
    assert(svgAttrs.containsAll(svgBasicAttrs))

    val lineAttrs = svgMap["line"]!!.toSet()
    val defsAttrs = svgMap["defs"]!!.toSet()
    val circleAttrs = svgMap["circle"]!!.toSet()
    val textAttrs = svgMap["text"]!!.toSet()
    val svgGraphicAttrs = svgMap["filter"]!!.filter {
      lineAttrs.contains(it)
      && defsAttrs.contains(it)
      && circleAttrs.contains(it)
      && textAttrs.contains(it)
    }.toSet()
    assert(svgGraphicAttrs.containsAll(svgAttrs))

    val svgTextAttrs = svgMap["filter"]!!.filter { defsAttrs.contains(it) && textAttrs.contains(it) }.toSet()
    assert(svgTextAttrs.containsAll(svgGraphicAttrs))

    val mathMap = map["MathML"]!!
    val mathBasicAttrs = setOf("class", "href", "id", "xref")
    val mathAttrs = mathMap["factorial"]!!.toSet()
    assert(mathAttrs.containsAll(mathBasicAttrs))

    return """
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
        const val VERSION = $version

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
          ${baseHtmlAttrs.joinToString { '"' + it + '"' }}
        )

        private val htmlAttrs = baseHtmlAttrs + listOf(
          ${(htmlAttrs - baseHtmlAttrs).joinToString { '"' + it + '"' }}
        )

        private val svgBasicAttrs = listOf(
          ${svgBasicAttrs.joinToString { '"' + it + '"' }}
        )

        private val svgAttrs = svgBasicAttrs + listOf(
          ${(svgAttrs - svgBasicAttrs).joinToString { '"' + it + '"' }}
        )

        private val svgGraphicAttrs = svgAttrs + listOf(
          ${(svgGraphicAttrs - svgAttrs).joinToString { '"' + it + '"' }}
        )

        private val svgTextAttrs = svgGraphicAttrs + listOf(
          ${(svgTextAttrs - svgGraphicAttrs).joinToString { '"' + it + '"' }}
        )

        private val mathBasicAttrs = listOf(
          ${mathBasicAttrs.joinToString { '"' + it + '"' }}
        )

        private val mathAttrs = mathBasicAttrs + listOf(
          ${(mathAttrs - mathBasicAttrs).joinToString { '"' + it + '"' }}
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
            ${
      map.entries.joinToString(",\n            ") { (namespace, tags) ->
        "Namespace.${namespace} to tags(\n             " +
        tags.entries.filter { it.value.isNotEmpty() }.joinToString(",\n             ") { (tagName, attrs) ->
          val (tag, list) = if (attrs.containsAll(htmlAttrs))
            Pair("htmlAttrs", attrs - htmlAttrs)
          else if (attrs.containsAll(baseHtmlAttrs))
            Pair("baseHtmlAttrs", attrs - baseHtmlAttrs)
          else if (attrs.containsAll(svgTextAttrs))
            Pair("svgTextAttrs", attrs - svgTextAttrs)
          else if (attrs.containsAll(svgGraphicAttrs))
            Pair("svgGraphicAttrs", attrs - svgGraphicAttrs)
          else if (attrs.containsAll(svgAttrs))
            Pair("svgAttrs", attrs - svgAttrs)
          else if (attrs.containsAll(mathAttrs))
            Pair("mathAttrs", attrs - mathAttrs)
          else if (attrs.containsAll(svgBasicAttrs))
            Pair("svgBasicAttrs", attrs - svgBasicAttrs)
          else if (attrs.containsAll(mathBasicAttrs))
            Pair("mathBasicAttrs", attrs - mathBasicAttrs)
          else
            Pair("emptyList()", attrs)

          """ "${tagName}" to attrs($tag, ${list.joinToString { '"' + it + '"' }})"""
        } + "\n            )"
      }
    }
          )
        }
      }
    """.trimIndent()
  }

}