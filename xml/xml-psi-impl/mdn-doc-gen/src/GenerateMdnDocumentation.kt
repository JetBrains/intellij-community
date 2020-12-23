// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.stream.JsonReader
import com.intellij.documentation.mdn.MdnApiStatus
import com.intellij.documentation.mdn.MdnHtmlAttributeDocumentation
import com.intellij.documentation.mdn.MdnHtmlElementDocumentation
import com.intellij.lang.html.HTMLLanguage
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.impl.source.html.HtmlFileImpl
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.castSafelyTo
import com.intellij.util.text.NameUtilCore
import junit.framework.TestCase
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite
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

const val YARI_BUILD_PATH = "/Users/piotr.tomiak/WebstormProjects/yari/client/build"
const val BUILT_LANG = "en-us"
const val WEB_DOCS = "docs/web"
const val MDN_DOCS_URL_PREFIX = "\$MDN_URL\$"
const val OUTPUT_DIR = "xml/xml-psi-impl/gen/com/intellij/documentation/mdn/"

/* It's so much easier to run a test, than to setup the whole IJ environment */
class GenerateMdnDocumentation : BasePlatformTestCase() {

  /* Run these tests to generate documentation */
  fun testGenHtml() {
    val attributes = extractInformation("html/global_attributes", this::extractAttributeDocumentation)
    outputJson("mdn-html.json", mapOf(
      "attrs" to attributes,
      "tags" to extractInformation("html/element", htmlSpecialMappings.keys) { this.extractElementDocumentation(it, attributes) },
      "tagAliases" to reversedAliasesMap(htmlSpecialMappings)
    ))
  }

  fun testGenMathML() {
    val attributes = extractInformation("mathml/attribute", this::extractAttributeDocumentation)
    outputJson("mdn-mathml.json", mapOf(
      "attrs" to attributes,
      "tags" to extractInformation("mathml/element") { this.extractElementDocumentation(it, attributes) }
    ))
  }

  fun testGenSvg() {
    val attributes = extractInformation("svg/attribute", this::extractAttributeDocumentation)
    outputJson("mdn-svg.json", mapOf(
      "attrs" to attributes,
      "tags" to extractInformation("svg/element") { this.extractElementDocumentation(it, attributes) }
    ))
  }

  private fun outputJson(outputFile: String, data: Map<String, Any>) {
    FileUtil.writeToFile(Path.of(PathManager.getCommunityHomePath(), OUTPUT_DIR, outputFile).toFile(),
                         GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                           .toJson(licenseAndAttribution() + data))
  }

  private fun <T> extractInformation(path: String, extractor: (File) -> T): Map<String, T> =
    extractInformation(path, emptySet(), extractor)

  private fun <T> extractInformation(path: String, specialDirs: Set<String>, extractor: (File) -> T): Map<String, T> =
    Path.of(YARI_BUILD_PATH, BUILT_LANG, WEB_DOCS, path).toFile().listFiles()!!
      .asSequence()
      .filter { it.isDirectory && (!it.name.contains('-') || specialDirs.contains(it.name.toLowerCase(Locale.US))) && File(it, "index.json").exists() }
      .map { docDir ->
        try {
          Pair(docDir.name, extractor(docDir))
        }
        catch (e: Exception) {
          System.err.println("Error for $docDir: ${e.message}")
          throw e
        }
      }
      .sortedBy { it.first }
      .toMap()


  private fun licenseAndAttribution(): Map<String, Any> =
    mapOf(
      "license" to mapOf(
        "name" to "CC-BY-SA 2.5",
        "url" to "https://creativecommons.org/licenses/by-sa/2.5/",
      ),
      "author" to mapOf(
        "name" to "Mozzila Contributors",
        "url" to "https://github.com/mdn/content"
      )
    )

  private fun extractElementDocumentation(dir: File,
                                          commonAttributes: Map<String, MdnHtmlAttributeDocumentation>): MdnHtmlElementDocumentation {
    val (compatData, indexDataProseValues) = getDataAndProseValues(dir)

    val elementDoc = indexDataProseValues.first()
      .getAsJsonPrimitive("content").asString
      .let { patchProse(it) }

    val attributesDoc = indexDataProseValues
      .filter { value ->
        value.get("id")
          .takeIf { it is JsonPrimitive }
          ?.asString?.toLowerCase(Locale.US)?.let {
            it == "attributes"
            || it == "attributes_for_form_submission"
            || it == "deprecated_attributes"
            || it == "obsolete_attributes"
            || it == "non-standard_attributes"
          } == true
      }
      .joinToString("\n") { it.getAsJsonPrimitive("content").asString }
      .let { patchProse(it) }

    val status = extractStatus(compatData)
    val doc = processElementDocumentation(elementDoc)

    val documentation = doc.first
    val properties = doc.second.takeIf { it.isNotEmpty() }
                     ?: indexDataProseValues.find { value ->
                       value.get("id")
                         .takeIf { it is JsonPrimitive }
                         ?.asString?.toLowerCase(Locale.US)?.let {
                           it == "properties"
                         } == true
                     }
                       ?.getAsJsonPrimitive("content")?.asString
                       ?.let { patchProse(it) }
                       ?.let { processElementDocumentation(it) }
                       ?.second
                       ?.takeIf { it.isNotEmpty() }

    return MdnHtmlElementDocumentation(getMdnDocsUrl(dir), status, documentation, properties,
                                       buildAttributes(dir, attributesDoc, compatData, commonAttributes))
  }

  private fun extractAttributeDocumentation(dir: File): MdnHtmlAttributeDocumentation {
    val (compatData, indexDataProseValues) = getDataAndProseValues(dir)
    return MdnHtmlAttributeDocumentation(getMdnDocsUrl(dir), extractStatus(compatData),
                                         indexDataProseValues.first()
                                           .getAsJsonPrimitive("content").asString
                                           .let { patchProse(it) }
                                           .let { createHtmlFile(it).text }, null)
  }

  private fun reversedAliasesMap(specialMappings: Map<String, Set<String>>): Map<String, String> =
    specialMappings.entries.asSequence().flatMap {mapping -> mapping.value.asSequence().map { Pair(it, mapping.key) } }.toMap()

  private fun getMdnDocsUrl(dir: File): String =
    MDN_DOCS_URL_PREFIX + dir.path.replace('\\', '/').let { it.substring(it.indexOf("/docs/") + 5) }

  private fun getDataAndProseValues(dir: File): Pair<JsonObject?, List<JsonObject>> =
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

  private fun patchProse(prose: String): String =
    prose
      .replace("/en-US/docs", MDN_DOCS_URL_PREFIX, true)
      .replace("&apos;", "'")
      .replace("&quot;", "\"")
      .also { fixedProse ->
        Regex("&(?!lt|gt|amp)[a-z]*;").find(fixedProse)?.let {
          throw Exception("Unknown entity found in prose: ${it.value}")
        }
      }

  private fun buildAttributes(tagDir: File,
                              attributesDoc: String?,
                              elementCompatData: JsonObject?,
                              commonAttributes: Map<String, MdnHtmlAttributeDocumentation>): Map<String, MdnHtmlAttributeDocumentation>? {
    val docAttrs = mutableMapOf<String, String>()
    if (!attributesDoc.isNullOrBlank()) {
      var lastTitle = ""
      createHtmlFile(attributesDoc)
        .document?.children?.asSequence()
        ?.filterIsInstance<XmlTag>()
        ?.filter { it.name == "dl" }
        ?.flatMap { it.subTags.asSequence() }
        ?.forEach { data ->
          @Suppress("RegExpDuplicateAlternationBranch")
          when (data.name) {
            "dt" -> lastTitle = data
              .let { it.findSubTag("code") ?: it }
              .let { it.findSubTag("a") ?: it }
              .let { it.findSubTag("strong") ?: it }
              .let { it.findSubTag("code") ?: it }
              .innerHtml()
              .also { if (it.contains('<')) throw Exception(it) }
              .replace(Regex("\uD83D\uDDD1|\uD83D\uDC4E|⚠️|\uD83E\uDDEA"), "")
              .toLowerCase().trim()
            "dd" -> docAttrs[lastTitle] = docAttrs.getOrDefault(lastTitle, "") + data.innerHtml()
          }
        }
    }
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
      .map { Pair(it, MdnHtmlAttributeDocumentation(urlPrefix + it, compatAttrs[it], docAttrs[it], null)) }
      .sortedBy { it.first }
      .toMap()
      .takeIf { it.isNotEmpty() }
  }

  private fun processElementDocumentation(elementDoc: String): Pair<String, Map<String, String>> {
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
            sections[title] = content
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
    return Pair(fixSpaces(htmlFile.text), sections)
  }

  private fun createHtmlFile(contents: String): HtmlFileImpl {
    val htmlFile = PsiFileFactory.getInstance(project)
      .createFileFromText("dummy.html", HTMLLanguage.INSTANCE, contents, false, true)
    val toRemove = mutableSetOf<XmlTag>()
    htmlFile.acceptChildren(object : XmlRecursiveElementVisitor() {
      override fun visitXmlTag(tag: XmlTag) {
        if ((tag.getAttributeValue("class")?.contains("hidden", true) == true)
            || tag.getAttributeValue("id")
              .let { it == "topExample" || it == "Exeemple" || it == "Example" || it == "Exemple" || it == "LiveSample" }
            || (tag.name == "span" && tag.getAttributeValue("class") == "notecard inline warning")
            || tag.name == "iframe") {
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
    })
    toRemove.forEach { it.delete() }
    val text = fixSpaces(htmlFile.text)
    if (text.contains("The source for this interact"))
      throw Exception("Unhidden stuff!")
    return PsiFileFactory.getInstance(project)
      .createFileFromText("dummy.html", HTMLLanguage.INSTANCE, text, false, true)
      as HtmlFileImpl
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
}

private fun XmlTag.innerHtml(): String = this.children.asSequence()
  .filter { it is XmlTag || it is XmlText }.map { it.text }.joinToString("\n").let { fixSpaces(it) }

private fun XmlElement.findSubTag(name: String): XmlTag? = this.children.find { (it as? XmlTag)?.name == name } as? XmlTag

private fun fixSpaces(doc: String): String = doc.replace(Regex("[ \t]*\n[ \t\n]*"), "\n").trim()

private fun String.toPascalCase(): String = getWords().map { it.toLowerCase() }.joinToString(separator = "", transform = String::capitalize)
private fun String.getWords() = NameUtilCore.nameToWords(this).filter { it.isNotEmpty() && Character.isLetterOrDigit(it[0]) }