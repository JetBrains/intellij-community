// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.intellij.documentation.mdn.*
import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.javascript.library.JSCorePredefinedLibrariesProvider
import com.intellij.lang.javascript.psi.JSPsiElementBase
import com.intellij.lang.javascript.psi.stubs.JSSymbolIndex2
import com.intellij.lang.javascript.psi.types.JSNamedTypeFactory
import com.intellij.lang.javascript.psi.types.JSTypeContext
import com.intellij.lang.javascript.psi.types.JSTypeSourceFactory
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.source.html.HtmlFileImpl
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.castSafelyTo
import com.intellij.util.text.NameUtilCore
import java.io.File
import java.io.FileReader
import java.nio.file.Path
import java.util.*

val missingDoc = mapOf(
  //HTML
  "area" to setOf("media"),
  "audio" to setOf("buffered", "mozcurrentsampleoffset", "played", "volume"),
  "colgroup" to setOf("width"),
  "div" to setOf("align"),
  "ol" to setOf("compact"),
  "img" to setOf("onerror"),
  "script" to setOf("text"),
  "video" to setOf("played"),

  //MathML
  "mmultiscripts" to setOf("dir", "mathsize"),
  "mo" to setOf("moveablelimits"),
  "mstyle" to setOf("infixbreakstyle"),

  // SVG
  "fecomposite" to setOf("lighterforerror"),
  "hatch" to setOf("hatchcontentunits", "hatchunits", "pitch"),
  "hatchpath" to setOf("offset"),
  "switch" to setOf("allowreorder"),
)

val htmlSpecialMappings = mapOf(
  "heading_elements" to setOf("h1", "h2", "h3", "h4", "h5", "h6")
)

val webApiBlockList = setOf("index")

const val YARI_BUILD_PATH = "/Users/piotr.tomiak/WebstormProjects/yari/client/build"
const val BUILT_LANG = "en-us"
const val WEB_DOCS = "docs/web"
const val MDN_DOCS_URL_PREFIX = "\$MDN_URL\$"
const val OUTPUT_DIR = "xml/xml-psi-impl/gen/com/intellij/documentation/mdn/"

/* It's so much easier to run a test, than to setup the whole IJ environment */
class GenerateMdnDocumentation : BasePlatformTestCase() {

  /* Run these tests to generate documentation */
  fun testGenHtml() {
    val attributes = extractInformationSimple("html/global_attributes", this::extractAttributeDocumentation)
    outputJson(MdnApiNamespace.Html.name, mapOf(
      "attrs" to attributes,
      "tags" to extractInformationSimple("html/element", allowList = htmlSpecialMappings.keys) {
        this.extractElementDocumentation(it, attributes)
      },
      "tagAliases" to reversedAliasesMap(htmlSpecialMappings)
    ))
  }

  fun testGenMathML() {
    val attributes = extractInformationSimple("mathml/attribute", this::extractAttributeDocumentation)
    outputJson(MdnApiNamespace.MathML.name, mapOf(
      "attrs" to attributes,
      "tags" to extractInformationSimple("mathml/element") { this.extractElementDocumentation(it, attributes) }
    ))
  }

  fun testGenSvg() {
    val attributes = extractInformationSimple("svg/attribute", this::extractAttributeDocumentation)
    outputJson(MdnApiNamespace.Svg.name, mapOf(
      "attrs" to attributes,
      "tags" to extractInformationSimple("svg/element") { this.extractElementDocumentation(it, attributes) }
    ))
  }

  fun testGenJsWebApi() {
    val symbols = extractInformation("api", blockList = webApiBlockList) { extractJavascriptDocumentation(it, it.name) }
    val fragments = webApiFragmentStarts.map { Pair(it, sortedMapOf<String, MdnJsSymbolDocumentation>()) }.toMap(TreeMap())
    symbols.forEach { (name, doc) ->
      fragments.floorEntry(name[0]).value[name] = doc
    }
    fragments.forEach { (prefix, symbols) ->
      outputJson("${MdnApiNamespace.WebApi.name}-$prefix", mapOf(
        "symbols" to symbols
      ))
    }

    val index = createDtsMdnIndex(symbols)
    FileUtil.writeToFile(Path.of(PathManager.getCommunityHomePath(), OUTPUT_DIR, "${MdnApiNamespace.WebApi.name}-index.json").toFile(),
                         GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                           .toJson(index))
  }

  fun testGenJsGlobalObjects() {
    outputJson(MdnApiNamespace.GlobalObjects.name, mapOf(
      "symbols" to extractInformation("javascript/reference/global_objects") { extractJavascriptDocumentation(it, it.name) }
    ))
  }

  fun testGenCss() {
    outputJson(MdnApiNamespace.Css.name, buildCssDocs())
  }

  private fun outputJson(outputFile: String, data: Map<String, Any>) {
    FileUtil.writeToFile(Path.of(PathManager.getCommunityHomePath(), OUTPUT_DIR, "$outputFile.json").toFile(),
                         GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                           .toJson(additionalMetadata() + data))
  }

  private fun <T> extractInformationSimple(mdnPath: String, extractor: (File) -> T): Map<String, T> =
    extractInformationSimple(mdnPath, emptySet(), extractor)

  private fun <T> extractInformationSimple(mdnPath: String, allowList: Set<String>, extractor: (File) -> T): Map<String, T> =
    extractInformation(mdnPath, allowList) { docDir ->
      try {
        listOf(Pair(docDir.name, extractor(docDir)))
      }
      catch (e: Exception) {
        System.err.println("Error for $docDir: ${e.message}")
        throw e
      }
    }

  private fun getMdnDir(mdnPath: String): File =
    Path.of(YARI_BUILD_PATH, BUILT_LANG, WEB_DOCS, mdnPath).toFile()

  private fun <T> extractInformation(mdnPath: String,
                                     allowList: Set<String> = emptySet(),
                                     blockList: Set<String> = emptySet(),
                                     extractor: (File) -> List<Pair<String, T>>): Map<String, T> =
    extractInformationFull(getMdnDir(mdnPath), allowList, blockList, extractor)

  private fun <T> extractInformationFull(dir: File,
                                         allowList: Set<String> = emptySet(),
                                         blockList: Set<String> = emptySet(),
                                         extractor: (File) -> List<Pair<String, T>>): Map<String, T> =
    extractInformationFull(dir, { ((!it.contains('-') && !it.contains('_')) || allowList.contains(it)) && !blockList.contains(it) },
                           extractor)

  private fun <T> extractInformationFull(dir: File,
                                         nameFilter: (String) -> Boolean,
                                         extractor: (File) -> List<Pair<String, T>>): Map<String, T> =
    dir.listFiles()!!.asSequence()
      .filter { file ->
        file.isDirectory
        && file.name.toLowerCase(Locale.US).let(nameFilter)
        && File(file, "index.json").exists()
      }
      .flatMap(extractor)
      .distinct()
      .sortedBy { it.first }
      .toMap()


  private fun additionalMetadata(): Map<String, Any> =
    mapOf(
      "license" to mapOf(
        "name" to "CC-BY-SA 2.5",
        "url" to "https://creativecommons.org/licenses/by-sa/2.5/",
      ),
      "author" to mapOf(
        "name" to "Mozzila Contributors",
        "url" to "https://github.com/mdn/content"
      ),
      "lang" to BUILT_LANG,
    )

  private fun extractElementDocumentation(dir: File,
                                          commonAttributes: Map<String, MdnHtmlAttributeDocumentation>): MdnHtmlElementDocumentation {
    val (compatData, indexDataProseValues) = getCompatDataAndProseValues(dir)

    val elementDoc = indexDataProseValues.first().getProseContent()

    val attributesDoc = filterProseById(indexDataProseValues, "attributes", "attributes_for_form_submission", "deprecated_attributes",
                                        "obsolete_attributes", "non-standard_attributes")
      .joinToString("\n") { it.getProseContent().content }
      .let { RawProse(it) }

    val status = extractStatus(compatData)
    val doc = processElementDocumentation(elementDoc)

    val documentation = doc.first
    val properties = doc.second.takeIf { it.isNotEmpty() }
                     ?: filterProseById(indexDataProseValues, "properties")
                       .firstOrNull()
                       ?.getProseContent()
                       ?.let { processElementDocumentation(it) }
                       ?.second
                       ?.takeIf { it.isNotEmpty() }

    return MdnHtmlElementDocumentation(getMdnDocsUrl(dir), status, documentation, properties,
                                       buildAttributes(dir, attributesDoc, compatData, commonAttributes))
  }

  private fun extractAttributeDocumentation(dir: File): MdnHtmlAttributeDocumentation {
    val (compatData, indexDataProseValues) = getCompatDataAndProseValues(dir)
    return MdnHtmlAttributeDocumentation(getMdnDocsUrl(dir), extractStatus(compatData),
                                         extractDescription(indexDataProseValues), null)
  }

  private fun extractJavascriptDocumentation(dir: File, name: String): List<Pair<String, MdnJsSymbolDocumentation>> {
    try {
      val (compatData, indexDataProseValues) = getCompatDataAndProseValues(dir)
      return extractInformationFull(dir, blockList = webApiBlockList) { subDir ->
        extractJavascriptDocumentation(subDir, "$name.${subDir.name}")
      }.toList() + Pair(name, MdnJsSymbolDocumentation(getMdnDocsUrl(dir), extractStatus(compatData),
                                                       extractDescription(indexDataProseValues),
                                                       extractParameters(indexDataProseValues),
                                                       extractReturns(indexDataProseValues)?.patch(),
                                                       extractThrows(indexDataProseValues)))
    }
    catch (e: Exception) {
      System.err.println("Error for $dir: ${e.message}")
      throw e
    }
  }

  private fun buildCssDocs(): Map<String, Map<String, MdnRawSymbolDocumentation>> {
    val result = TreeMap<String, MutableMap<String, MdnRawSymbolDocumentation>>()
    val cssMdnDir = getMdnDir("CSS")
    extractInformationFull(cssMdnDir,
                           { it.startsWith("_colon_") || it.startsWith("_doublecolon_") || !it.contains('_') },
                           { docDir ->
                             try {
                               listOf(Pair(docDir.name, extractCssElementDocumentation(docDir)))
                             }
                             catch (e: Exception) {
                               System.err.println("Error for $docDir: ${e.message}")
                               throw e
                             }
                           })
      .forEach { (name, doc) ->
        when {
          name.startsWith("_colon_") -> Pair("pseudoClasses", name.substring("_colon_".length))
          name.startsWith("_doublecolon_") -> Pair("pseudoElements", name.substring("_doublecolon_".length))
          name.startsWith("@") -> Pair("atRules", name.substring(1))
          name.endsWith("()") -> Pair("functions", name.substring(0, name.length - 2))
          else -> {
            if (File(cssMdnDir, "$name/bcd.json").takeIf { it.exists() }
                ?.let { file ->
                  JsonParser.parseReader(JsonReader(FileReader(file)).also { it.isLenient = true })
                    .asJsonObject
                    .getAsJsonPrimitive("query")
                    ?.asString
                    ?.startsWith("css.types.")
                } == true)
              Pair("dataTypes", name)
            else
              Pair("properties", name)
          }
        }.let { (kind, simpleName) ->
          result.getOrPut(kind, ::TreeMap)[simpleName] = doc
        }
      }
    return result
  }

  private fun extractCssElementDocumentation(dir: File): MdnRawSymbolDocumentation {
    val (compatData, indexDataProseValues) = getCompatDataAndProseValues(dir)
    val url = getMdnDocsUrl(dir)
    val status = extractStatus(compatData)
    val description = extractDescription(indexDataProseValues)
    val dirName = dir.name
    return when {
      dirName.startsWith('_') || dirName.endsWith("()") ->
        MdnCssBasicSymbolDocumentation(url, status, description, null)
      dirName.startsWith('@') ->
        MdnCssAtRuleSymbolDocumentation(
          url, status, description, null,
          extractInformationFull(dir,
                                 { !it.contains('_') },
                                 { docDir ->
                                   try {
                                     listOf(Pair(docDir.name, extractCssElementDocumentation(docDir) as MdnCssPropertySymbolDocumentation))
                                   }
                                   catch (e: Exception) {
                                     System.err.println("Error for $docDir: ${e.message}")
                                     throw e
                                   }
                                 }).takeIf { it.isNotEmpty() }
        )
      else ->
        MdnCssPropertySymbolDocumentation(
          url, status, description, extractPropertyValues(indexDataProseValues))
    }
  }

  private fun extractDescription(indexDataProseValues: List<JsonObject>): String =
    indexDataProseValues.first().getProseContent()
      .let { createHtmlFile(it).patchedText() }

  private fun filterProseById(sections: List<JsonObject>, vararg ids: String): Sequence<JsonObject> =
    sections.asSequence().filter { value ->
      value.get("id")
        .takeIf { it is JsonPrimitive }
        ?.asString?.toLowerCase(Locale.US)?.let { id ->
          ids.any { id == it }
        } == true
    }

  private fun getProseContentById(sections: List<JsonObject>, id: String) =
    filterProseById(sections, id).firstOrNull()?.getProseContent()

  private fun reversedAliasesMap(specialMappings: Map<String, Set<String>>): Map<String, String> =
    specialMappings.entries.asSequence().flatMap { mapping -> mapping.value.asSequence().map { Pair(it, mapping.key) } }.toMap()

  private fun getMdnDocsUrl(dir: File): String =
    MDN_DOCS_URL_PREFIX + dir.path.replace('\\', '/').let { it.substring(it.indexOf("/docs/") + 5) }

  private fun getCompatDataAndProseValues(dir: File): Pair<JsonObject?, List<JsonObject>> =
    Pair(
      File(dir, "bcd.json").takeIf { it.exists() }?.let { file ->
        JsonParser.parseReader(JsonReader(FileReader(file)).also { it.isLenient = true })
          .asJsonObject
          .getAsJsonObject("data")
      },
      JsonParser.parseReader(JsonReader(FileReader(File(dir, "index.json"))).also { it.isLenient = true })
        .asJsonObject
        .getAsJsonObject("doc")
        .getAsJsonArray("body")!!
        .map { it.asJsonObject }
        .filter { it.getAsJsonPrimitive("type").asString == "prose" }
        .map { it.getAsJsonObject("value") }
        .toList()
    )

  private fun buildAttributes(tagDir: File,
                              attributesDoc: RawProse?,
                              elementCompatData: JsonObject?,
                              commonAttributes: Map<String, MdnHtmlAttributeDocumentation>): Map<String, MdnHtmlAttributeDocumentation>? {
    val docAttrs = processDataList(attributesDoc).flatMap { (name, doc) ->
      if (name.contains(','))
        name.splitToSequence(',').map { Pair(it.trim(), doc) }
      else
        sequenceOf(Pair(name, doc))
    }.toMap()
    val compatAttrs = elementCompatData?.entrySet()?.asSequence()
                        ?.filter { data -> !data.key.any { it == '_' || it == '-' } }
                        ?.map { Pair(it.key.toLowerCase(), extractStatus(it.value.asJsonObject)) }
                        ?.toMap() ?: emptyMap()

    val tagName = tagDir.name
    val missing = (((compatAttrs.keys - docAttrs.keys)) - commonAttributes.keys) - (missingDoc[tagName] ?: emptySet())
    if (missing.isNotEmpty()) {
      System.err.println("$tagName - missing attrs present in BCD: ${missing}")
    }

    val urlPrefix = getMdnDocsUrl(tagDir) + "#attr-"
    return docAttrs.keys.asSequence()
      .plus(compatAttrs.keys).distinct()
      .map { StringUtil.toLowerCase(it) }
      .map { Pair(it, MdnHtmlAttributeDocumentation(urlPrefix + it, compatAttrs[it], docAttrs[it], null)) }
      .sortedBy { it.first }
      .toMap()
      .takeIf { it.isNotEmpty() }
  }

  private fun processElementDocumentation(elementDoc: RawProse): Pair<String, Map<String, String>> {
    val sections = mutableMapOf<String, String>()

    fun processPropertiesTable(table: XmlTag) {
      table.acceptChildren(object : XmlRecursiveElementVisitor() {
        override fun visitXmlTag(tag: XmlTag) {
          if (tag.name == "tr") {
            var title = ""
            var content = ""
            tag.subTags.forEach {
              when (it.name) {
                "th" -> title += it.innerHtml()
                "td" -> content += it.innerHtml() + "\n"
              }
            }
            sections[title.patchProse()] = content.patchProse()
          }
          else super.visitXmlTag(tag)
        }
      })
    }

    val htmlFile = createHtmlFile(elementDoc)
    htmlFile.acceptChildren(object : XmlRecursiveElementVisitor() {
      override fun visitXmlTag(tag: XmlTag) {
        if (tag.name == "table" && tag.getAttributeValue("class") == "properties") {
          processPropertiesTable(tag)
          tag.delete()
        }
        else super.visitXmlTag(tag)
      }
    })
    return Pair(fixSpaces(htmlFile.patchedText()), sections)
  }

  private fun createHtmlFile(contents: RawProse): HtmlFileImpl {
    val htmlFile = PsiFileFactory.getInstance(project)
      .createFileFromText("dummy.html", HTMLLanguage.INSTANCE, contents.content, false, true)
    val toRemove = mutableSetOf<XmlElement>()
    val toSimplify = mutableSetOf<XmlTag>()
    htmlFile.acceptChildren(object : XmlRecursiveElementVisitor() {
      override fun visitXmlTag(tag: XmlTag) {
        if (tag.name == "pre"
            && tag.getAttributeValue("class")?.matches(Regex("brush: ?(js|html|css|xml)[a-z0-9-;\\[\\] ]*")) == true) {
          tag.findSubTag("code")?.let { toSimplify.add(it) }
        }
        else if ((tag.getAttributeValue("class")?.contains("hidden", true) == true)
                 || tag.getAttributeValue("id")
                   .let { it == "topExample" || it == "Exeemple" || it == "Example" || it == "Exemple" || it == "LiveSample" }
                 || (tag.name == "span" && tag.getAttributeValue("class") == "notecard inline warning")
                 || (tag.name == "p" && tag.innerHtml().trim().startsWith("The source for this interact"))
                 || tag.name == "iframe"
                 || tag.name == "img") {
          toRemove.add(tag)
        }
        else if (tag.name == "svg") {
          val cls = tag.getAttributeValue("class")
          if (cls != null) {
            when {
              cls.contains("obsolete") -> "🗑"
              cls.contains("deprecated") -> "👎"
              cls.contains("non-standard") -> "⚠️"
              cls.contains("experimental") -> "🧪"
              else -> {
                throw Exception("Unknown SVG class: $cls")
              }
            }.let {
              tag.parentTag!!.addBefore(XmlElementFactory.getInstance(project).createDisplayText(it), tag)
            }
          }
          else {
            throw Exception("SVG with no class")
          }
          tag.delete()
        }
        else super.visitXmlTag(tag)
      }

      override fun visitXmlAttribute(attribute: XmlAttribute) {
        if (attribute.name.let { it == "data-flaw-src" || it == "title" || it == "alt" }) {
          toRemove.add(attribute)
        }
        else if (attribute.value?.let { it.contains("&quot;") || it.contains("&apos;") } == true) {
          if (attribute.name.let { it == "id" || it == "style" }) {
            toRemove.add(attribute)
          }
          else {
            throw Exception("Entities: " + attribute.text)
          }
        }
      }
    })
    toRemove.forEach { it.delete() }
    removeEmptyTags(htmlFile)
    toSimplify.forEach(::simplifyTag)
    val text = fixSpaces(htmlFile.text)
    if (text.contains("The source for this interact"))
      throw Exception("Unhidden stuff!")
    if (text.contains("class=\"token punctuation\""))
      throw Exception("Code fragment")
    return PsiFileFactory.getInstance(project)
      .createFileFromText("dummy.html", HtmlFileType.INSTANCE, text, System.currentTimeMillis(), false, true)
      as HtmlFileImpl
  }

  private fun removeEmptyTags(htmlFile: PsiFile) {
    val toRemove = mutableSetOf<XmlTag>()
    htmlFile.acceptChildren(object : XmlRecursiveElementVisitor() {
      override fun visitXmlTag(tag: XmlTag) {
        if (isEmptyTag(tag)) {
          toRemove.add(tag)
        }
        else {
          super.visitXmlTag(tag)
        }
      }
    })
    toRemove.forEach { it.delete() }
  }

  private fun simplifyTag(tag: XmlTag) {
    val result = StringBuilder()
    tag.acceptChildren(object : XmlRecursiveElementVisitor() {
      override fun visitXmlText(text: XmlText) {
        result.append(text.text.replace(' ', ' '))
      }
    })
    tag.replace(
      XmlElementFactory.getInstance(tag.project).createTagFromText("""<${tag.name}>$result</${tag.name}>""", HTMLLanguage.INSTANCE))
  }

  private fun isEmptyTag(tag: XmlTag): Boolean =
    tag.name.let { it != "br" && it != "hr" }
    && !tag.children.map {
      (it is XmlAttribute && it.name != "class")
      || (it is XmlText && it.text.isNotBlank())
      || (it is XmlTag && !isEmptyTag(it))
    }.any { it }

  private fun extractParameters(sections: List<JsonObject>): Map<String, String>? =
    extractFromDataList(sections, "parameters")

  private fun extractReturns(sections: List<JsonObject>): RawProse? =
    filterProseById(sections, "return_value").firstOrNull()
      ?.getProseContent()

  private fun extractThrows(sections: List<JsonObject>): Map<String, String>? =
    extractFromDataList(sections, "exceptions")

  private fun extractPropertyValues(sections: List<JsonObject>): Map<String, String>? =
    extractFromDataList(sections, "values")

  private fun extractFromDataList(sections: List<JsonObject>, id: String): Map<String, String>? =
    processDataList(getProseContentById(sections, id))
      .takeIf { it.isNotEmpty() }
      ?.mapValues { (_, doc) -> doc.replace(Regex("<p>(.*)</p>"), "$1<br>").patchProse() }

  private fun processDataList(doc: RawProse?): Map<String, String> {
    val result = mutableMapOf<String, String>()
    if (doc != null && doc.content.isNotBlank()) {
      var lastTitle = ""
      createHtmlFile(doc)
        .document?.children?.asSequence()
        ?.filterIsInstance<XmlTag>()
        ?.filter { it.name == "dl" || it.name == "table" }
        ?.flatMap { it.subTags.asSequence() }
        ?.flatMap { if (it.name == "tbody") it.subTags.asSequence() else sequenceOf(it) }
        ?.forEach { data ->
          when (data.name) {
            "dt" -> lastTitle = extractIdentifier(data)
            "dd" -> if (lastTitle.isNotBlank()) result[lastTitle] = result.getOrDefault(lastTitle, "") + data.innerHtml()
            "tr" -> {
              val cells = data.findSubTags("td")
              if (cells.size == 2) {
                val title = extractIdentifier(cells[0])
                if (title.isNotBlank()) result[title] = result.getOrDefault(title, "") + cells[1].innerHtml()
              }
            }
          }
        }
    }
    return result.asSequence().map { Pair(it.key.patchProse(), it.value.patchProse()) }.toMap()
  }

  @Suppress("RegExpDuplicateAlternationBranch")
  private fun extractIdentifier(data: XmlTag): String {
    val result = StringBuilder()
    var ok = true
    var stop = false
    data.acceptChildren(object : XmlRecursiveElementVisitor() {
      override fun visitXmlTag(tag: XmlTag) {
        val tagName = tag.name
        if (tagName == "br") {
          result.append("{{br}}")
        }
        else if (tagName == "table") {
          ok = false
        }
        else if (tagName != "span" || tag.getAttributeValue("class")
            ?.let { cls ->
              cls.contains("badge")
              || cls.contains("inlineIndicator")
              || cls.contains("notecard")
            } != true) {
          super.visitXmlTag(tag)
        }
        else {
          stop = true
        }
      }

      override fun visitXmlText(text: XmlText) {
        if (!stop) {
          val infoIndex = text.text.indexOfAny(listOf("🗑", "👎", "⚠️", "🧪"))
          if (infoIndex >= 0) {
            stop = true
            result.append(text.text.substring(0, infoIndex))
          }
          else {
            result.append(text.text)
          }
        }
      }
    })
    if (result.contains(">"))
      throw Exception("Something went wrong!")
    return if (ok)
      result.toString().replace("{{br}}", "<br>").trim()
    else ""
  }

  private fun extractStatus(compatData: JsonObject?): Set<MdnApiStatus>? =
    compatData?.get("__compat")
      ?.castSafelyTo<JsonObject>()
      ?.getAsJsonObject("status")
      ?.entrySet()
      ?.asSequence()
      ?.filter { it.value.asBoolean }
      ?.map { MdnApiStatus.valueOf(it.key.toPascalCase()) }
      ?.toSet()

  private fun createDtsMdnIndex(symbols: Map<String, MdnJsSymbolDocumentation>): Map<String, String> {
    val context = JSCorePredefinedLibrariesProvider.getAllJSPredefinedLibraryFiles()
      .find { it.name == "lib.dom.d.ts" }
      ?.let { PsiManager.getInstance(project).findFile(it) }

    val realNameMap = StubIndex.getInstance().getAllKeys(JSSymbolIndex2.KEY, project)
      .map { Pair(it.toLowerCase(Locale.US), it) }
      .toMap()

    return symbols.keys.asSequence()
      .filter { it.contains('.') }
      .mapNotNull { symbolName ->
        val namespace = symbolName.takeWhile { it != '.' }
        val member = symbolName.takeLastWhile { it != '.' }
        val actualNamespace = realNameMap[namespace]
                              ?: return@mapNotNull null
        (JSNamedTypeFactory.createType(actualNamespace,
                                       JSTypeSourceFactory.createTypeSource(context, false), JSTypeContext.INSTANCE)
          .asRecordType()
          .properties
          .find { it.memberName.equals(member, true) })
          ?.memberSource
          ?.singleElement
          ?.castSafelyTo<JSPsiElementBase>()
          ?.qualifiedName
          ?.takeIf { !it.equals(symbolName, true) }
          ?.toLowerCase(Locale.US)
          ?.let { Pair(it, symbolName) }
      }
      .toMap(TreeMap())
  }
}

private fun XmlTag.innerHtml(): String = this.children.asSequence()
  .filter { it is XmlTag || it is XmlText }.map { it.text }.joinToString("\n").let { fixSpaces(it) }

private fun XmlElement.findSubTag(name: String): XmlTag? = this.children.find { (it as? XmlTag)?.name == name } as? XmlTag

private fun fixSpaces(doc: String): String = doc.replace(Regex("[ \t]*\n[ \t\n]*"), "\n").trim()

private fun String.toPascalCase(): String = getWords().map { it.toLowerCase() }.joinToString(separator = "", transform = String::capitalize)
private fun String.getWords() = NameUtilCore.nameToWords(this).filter { it.isNotEmpty() && Character.isLetterOrDigit(it[0]) }

private data class RawProse(val content: String) {
  fun patch(): String = content.patchProse()
}

private fun PsiFile.patchedText() =
  text.patchProse()

private fun JsonObject.getProseContent(): RawProse =
  this.getAsJsonPrimitive("content").asString
    .let { RawProse(it) }

private fun String.patchProse(): String =
  replace("/en-US/docs", MDN_DOCS_URL_PREFIX, true)
    .replace("&apos;", "'")
    .replace("&quot;", "\"")
    .also { fixedProse ->
      Regex("&(?!lt|gt|amp)[a-z]*;").find(fixedProse)?.let {
        throw Exception("Unknown entity found in prose: ${it.value}")
      }
    }