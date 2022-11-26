// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.intellij.application.options.CodeStyle
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
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings
import com.intellij.psi.impl.source.html.HtmlFileImpl
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.*
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.asSafely
import com.intellij.util.text.CharSequenceSubSequence
import com.intellij.util.text.NameUtilCore
import java.io.File
import java.io.FileReader
import java.lang.reflect.Type
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

/**
 * When adding a known property here, ensure that there is appropriate key in XmlPsiBundle.
 */
val defaultBcdContext = "\$default_context"
val knownBcdIds = setOf(
  defaultBcdContext,
  "flex_context",
  "grid_context",
  "multicol_context",
  "paged_context",
  "supported_for_width_and_other_sizing_properties",
  "supported_in_grid_layout",
  // CSS values support - by default not translated
  "support_of_multiple_keyword_values", // translated
  "support_of_contents",
  "support_of_flow-root",
  "support_of_table",
  "support_of_grid",
  "support_of_flex",
  "support_of_ruby",
  "support_of_ruby_values", //translated
  "support_of_table_values", //translated
  "support_of_inline-table",
  "support_of_inline-grid",
  "support_of_inline-flex",
  "support_of_inline-block",
  "support_of_list-item",
)

val bcdIdIgnore = setOf(
  // This is a multi-variant entry in CSS - ignore it
  "-webkit-scrollbar-button",
  "-webkit-scrollbar-thumb",
  "-webkit-scrollbar-track",
  "-webkit-scrollbar-track-piece",
  "-webkit-scrollbar-corner",
  "-webkit-resizer"
)

/** JS API **/
val bcdNameVariantId = setOf(
  "compressedteximage3d",
  "compressedteximage2d",
  "texparameterf",
  "texparameteri",
)

val bcdIdMappings = mapOf(
  Pair("browser_compatibility", defaultBcdContext),
  Pair("browser_support", defaultBcdContext),
  Pair("compatibility", defaultBcdContext),
  Pair("specifications", defaultBcdContext),
  Pair("<link_crossorigin>", defaultBcdContext),
  Pair("-webkit-scrollbar", defaultBcdContext),
  Pair("webglrenderingcontext.texparameterf", "texparameterf"),
  Pair("webglrenderingcontext.texparameteri", "texparameteri"),
)

val webApiBlockList = setOf("index")

val jsRuntimesMap = MdnJavaScriptRuntime.values().associateBy { it.mdnId }

val MDN_CONTENT_ROOT = PathManager.getCommunityHomePath() + "/xml/xml-psi-impl/mdn-doc-gen/work/mdn-content/files"
val YARI_BUILD_PATH = PathManager.getCommunityHomePath() + "/xml/xml-psi-impl/mdn-doc-gen/work/yari/client/build"
const val BUILT_LANG = "en-us"
const val WEB_DOCS = "docs/web"
const val MDN_DOCS_URL_PREFIX = "\$MDN_URL\$"
const val OUTPUT_DIR = "xml/xml-psi-impl/gen/com/intellij/documentation/mdn/"

/* It's so much easier to run a test, than to setup the whole IJ environment */
class GenerateMdnDocumentation : BasePlatformTestCase() {

  /* Run these tests to generate documentation. Prepare MDN documentation repositories with `prepare-mdn.sh` */
  fun testGenHtml() {
    val attributes = extractInformationSimple("html/global_attributes", listOf('_', '-'), this::extractAttributeDocumentation)
    outputJson(MdnApiNamespace.Html.name, mapOf(
      "attrs" to attributes,
      "tags" to extractInformationSimple("html/element", listOf('_', '-'), allowList = htmlSpecialMappings.keys) {
        this.extractElementDocumentation(it, attributes)
      },
      "tagAliases" to reversedAliasesMap(htmlSpecialMappings)
    ))
  }

  fun testGenMathML() {
    val attributes = extractInformationSimple("mathml/attribute", emptyList(), this::extractAttributeDocumentation)
    outputJson(MdnApiNamespace.MathML.name, mapOf(
      "attrs" to attributes,
      "tags" to extractInformationSimple("mathml/element", emptyList()) { this.extractElementDocumentation(it, attributes) }
    ))
  }

  fun testGenSvg() {
    val attributes = extractInformationSimple("svg/attribute", listOf('_'), this::extractAttributeDocumentation)
    outputJson(MdnApiNamespace.Svg.name, mapOf(
      "attrs" to attributes,
      "tags" to extractInformationSimple("svg/element", listOf('_')) { this.extractElementDocumentation(it, attributes) }
    ))
  }

  fun testGenJsWebApi() {
    val symbols = extractInformation("api", listOf('_', '-'), blockList = webApiBlockList) { extractJavascriptDocumentation(it, "") }
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
      "symbols" to extractInformation("javascript/reference/global_objects", listOf('-', '_')) {
        extractJavascriptDocumentation(it, "")
      }
    ))
  }

  fun testGenCss() {
    outputJson(MdnApiNamespace.Css.name, buildCssDocs())
  }

  fun testGenDomEvents() {
    outputJson(MdnApiNamespace.DomEvents.name, mapOf(
      "events" to extractDomEvents(),
    ))
  }

  override fun setUp() {
    super.setUp()
    val manager = CodeStyleSettingsManager.getInstance(project)
    @Suppress("DEPRECATION") val currSettings = manager.currentSettings
    val clone = CodeStyle.createTestSettings(currSettings)
    manager.setTemporarySettings(clone)
    val htmlSettings = clone.getCustomSettings(HtmlCodeStyleSettings::class.java)
    htmlSettings.HTML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    htmlSettings.HTML_TEXT_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    clone.getCommonSettings(HTMLLanguage.INSTANCE).softMargins.clear()
  }

  override fun tearDown() {
    CodeStyleSettingsManager.getInstance(project).dropTemporarySettings()
    super.tearDown()
  }

  private fun outputJson(outputFile: String, data: Map<String, Any>) {
    FileUtil.writeToFile(Path.of(PathManager.getCommunityHomePath(), OUTPUT_DIR, "$outputFile.json").toFile(),
                         GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
                           .registerTypeAdapter(Map::class.java, CompatibilityMapSerializer())
                           .create()
                           .toJson(additionalMetadata() + data))
  }

  private fun <T> extractInformationSimple(mdnPath: String, disallowedChars: List<Char>, extractor: (File) -> T): Map<String, T> =
    extractInformationSimple(mdnPath, disallowedChars, emptySet(), extractor)

  private fun <T> extractInformationSimple(mdnPath: String, disallowedChars: List<Char>,
                                           allowList: Set<String>, extractor: (File) -> T): Map<String, T> =
    extractInformation(mdnPath, disallowedChars, allowList) { docDir ->
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
                                     disallowedChars: List<Char>,
                                     allowList: Set<String> = emptySet(),
                                     blockList: Set<String> = emptySet(),
                                     extractor: (File) -> List<Pair<String, T>>): Map<String, T> =
    extractInformationFull(getMdnDir(mdnPath), disallowedChars, allowList, blockList, extractor)

  private fun <T> extractInformationFull(dir: File,
                                         disallowedChars: List<Char>,
                                         allowList: Set<String> = emptySet(),
                                         blockList: Set<String> = emptySet(),
                                         extractor: (File) -> List<Pair<String, T>>): Map<String, T> =
    extractInformationFull(dir, { (disallowedChars.none { ch -> it.contains(ch) } || allowList.contains(it)) && !blockList.contains(it) },
                           extractor)

  private fun <T> extractInformationFull(dir: File,
                                         nameFilter: (String) -> Boolean,
                                         extractor: (File) -> List<Pair<String, T>>): Map<String, T> =
    dir.listFiles()!!.asSequence()
      .filter { file ->
        file.isDirectory
        && file.name.lowercase(Locale.US).let(nameFilter)
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
    val compatibility = extractCompatibilityInfo(compatData)
    val doc = processElementDocumentation(elementDoc, getProseContentById(indexDataProseValues, "summary"))

    val documentation = doc.first
    val properties = doc.second.takeIf { it.isNotEmpty() }
                     ?: filterProseById(indexDataProseValues, "properties")
                       .firstOrNull()
                       ?.getProseContent()
                       ?.let { processElementDocumentation(it, null) }
                       ?.second
                       ?.takeIf { it.isNotEmpty() }

    return MdnHtmlElementDocumentation(getMdnDocsUrl(dir), status, compatibility, documentation, properties,
                                       buildAttributes(dir, attributesDoc, compatData, commonAttributes))
  }

  private fun extractAttributeDocumentation(dir: File): MdnHtmlAttributeDocumentation {
    val (compatData, indexDataProseValues) = getCompatDataAndProseValues(dir)
    return MdnHtmlAttributeDocumentation(getMdnDocsUrl(dir), extractStatus(compatData),
                                         extractCompatibilityInfo(compatData),
                                         extractDescription(indexDataProseValues))
  }

  private fun extractDomEvents(): Map<String, MdnDomEventDocumentation> {
    val prefix = "/$BUILT_LANG/$WEB_DOCS/events/"
    return redirects.asSequence().filter { it.key.startsWith(prefix) && !it.key.contains("_") && !it.value.contains("#") }
      .mapNotNull {
        Pair(it.key.substring(prefix.length),
             extractDomEventDocumentation(Path.of(YARI_BUILD_PATH, it.value).toFile()) ?: return@mapNotNull null)
      }
      .toMap()
  }

  private fun extractDomEventDocumentation(dir: File): MdnDomEventDocumentation? {
    val (compatData, indexDataProseValues) = getCompatDataAndProseValues(dir)
    return MdnDomEventDocumentation(getMdnDocsUrl(dir), extractStatus(compatData),
                                    extractCompatibilityInfo(compatData),
                                    extractEventDescription(indexDataProseValues) ?: return null)
  }

  private fun extractJavascriptDocumentation(dir: File, namePrefix: String): List<Pair<String, MdnJsSymbolDocumentation>> {
    try {
      val (compatDataList, indexDataProseValues) = getCompatDataAndProseValues(dir, withIdVariants = true)
      return extractInformationFull(dir, listOf('_', '-'), blockList = webApiBlockList)
             { subDir ->
               extractJavascriptDocumentation(
                 subDir, "$namePrefix${dir.name}.")
             }.toList() +
             compatDataList
               .ifEmpty { listOf(Pair(defaultBcdContext, null)) }
               .map { (compatName, compatData) ->
                 Pair(namePrefix + (if (compatName == defaultBcdContext) dir.name else compatName),
                      MdnJsSymbolDocumentation(getMdnDocsUrl(dir), compatData?.let { extractStatus(it) },
                                               compatData?.let { extractCompatibilityInfo(it) },
                                               extractDescription(indexDataProseValues),
                                               extractParameters(indexDataProseValues),
                                               extractReturns(indexDataProseValues)?.patch(),
                                               extractThrows(indexDataProseValues)))
               }

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
                               val hasDataType = hasDataTypeDecl(docDir)
                               if (hasDataType) {
                                 extractInformationFull(docDir, { true }) { innerDocDir ->
                                   try {
                                     listOf(Pair(innerDocDir.name.let { if (hasDataTypeDecl(innerDocDir)) "<$it>" else it },
                                                 extractCssElementDocumentation(innerDocDir)))
                                   }
                                   catch (e: Exception) {
                                     System.err.println("Error for $innerDocDir: ${e.message}")
                                     throw e
                                   }
                                 }.toList()
                               }
                               else {
                                 emptyList()
                               } + Pair(docDir.name.let { if (hasDataType) "<$it>" else it }, extractCssElementDocumentation(docDir))
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
          name.startsWith("<") -> Pair("dataTypes", name.substring(1, name.length - 1))
          name.endsWith("()") -> Pair("functions", name.substring(0, name.length - 2))
          else -> Pair("properties", name)
        }.let { (kind, simpleName) ->
          result.getOrPut(kind, ::TreeMap)[simpleName] = doc
        }
      }
    return result
  }

  private fun hasDataTypeDecl(docDir: File) =
    !docDir.name.let {
      it.startsWith("_colon_")
      || it.startsWith("_doublecolon_")
      || it.startsWith("@")
      || it.endsWith("()")
    }
    && (parseIndexJson(docDir).getAsJsonObject("doc")
          .getAsJsonPrimitive("title")
          .asString.let { it.startsWith("<") && it.endsWith(">") }
        || (File(docDir, "bcd.json")
      .takeIf { it.exists() }
      ?.let { file ->
        JsonParser.parseReader(JsonReader(FileReader(file)).also { it.isLenient = true })
          .asJsonObject
          .getAsJsonPrimitive("query")
          ?.asString
          ?.startsWith("css.types.")
      } == true))

  private fun extractCssElementDocumentation(dir: File): MdnRawSymbolDocumentation {
    val (compatData, indexDataProseValues) = getCompatDataAndProseValues(dir, true)
    val url = getMdnDocsUrl(dir)
    val status = extractStatus(compatData)
    val compatibility = extractCompatibilityInfo(compatData)
    val description = extractDescription(indexDataProseValues)
    val dirName = dir.name
    return when {
      dirName.startsWith('_') || dirName.endsWith("()") ->
        MdnCssBasicSymbolDocumentation(url, status, compatibility, description)
      dirName.startsWith('@') ->
        MdnCssAtRuleSymbolDocumentation(
          url, status, compatibility, description,
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
          url, status, compatibility, description, extractFormalSyntax(indexDataProseValues), extractPropertyValues(indexDataProseValues))
    }
  }

  private fun extractDescription(indexDataProseValues: List<JsonObject>): String =
    indexDataProseValues.firstOrNull()?.getProseContent()
      ?.let { createHtmlFile(it).patchedText() }
    ?: ""

  private fun extractFormalSyntax(indexDataProseValues: List<JsonObject>): String? =
    getProseContentById(indexDataProseValues, "formal_syntax")
      ?.let { createHtmlFile(it) }
      ?.let { PsiTreeUtil.findChildOfType(it, XmlTag::class.java) }
      ?.let { extractText(it) }
      ?.patchProse()
      ?.replace(" ", " ")
      ?.let { formatFormalCssSyntax(it) }

  private fun formatFormalCssSyntax(str: String): String {
    val result = StringBuilder()
    result.append(str[0])
    var i = 1
    var indentation = 0
    while (i < str.length) {
      val seq = CharSequenceSubSequence(str, i, str.length)
      if (str[i - 1].let { it != ' ' }) {
        if (seq.startsWith("where ")) {
          if (indentation == 2) break
          i += "where ".length
          result.append("\n")
          repeat(++indentation) { result.append("  ") }
          continue
        }
        else if (seq.startsWith("&lt;")) {
          result.append("\n")
          repeat(indentation) { result.append("  ") }
          result.append("&lt;")
          i += "&lt;".length
          continue
        }
      }
      result.append(str[i++])
    }
    return result.toString()
  }

  private fun extractEventDescription(indexDataProseValues: List<JsonObject>): String? =
    indexDataProseValues.first().getProseContent()
      .let { createHtmlFile(it) }
      .children[0].children.filter { it is XmlTag && it.name == "p" }
      .takeIf { it.isNotEmpty() }
      ?.joinToString("\n") { it.patchedText() }

  private fun filterProseById(sections: List<JsonObject>, vararg ids: String): Sequence<JsonObject> =
    sections.asSequence().filter { value ->
      value.get("id")
        .takeIf { it is JsonPrimitive }
        ?.asString?.lowercase(Locale.US)?.let { id ->
          ids.any { id == it }
        } == true
    }

  private fun getProseContentById(sections: List<JsonObject>, id: String) =
    filterProseById(sections, id).firstOrNull()?.getProseContent()

  private fun reversedAliasesMap(specialMappings: Map<String, Set<String>>): Map<String, String> =
    specialMappings.entries.asSequence().flatMap { mapping -> mapping.value.asSequence().map { Pair(it, mapping.key) } }.toMap()

  private fun getMdnDocsUrl(dir: File): String =
    MDN_DOCS_URL_PREFIX + dir.path.replace('\\', '/').let { it.substring(it.indexOf("/docs/") + 5) }


  private fun parseIndexJson(dir: File): JsonObject =
    JsonParser.parseReader(JsonReader(FileReader(File(dir, "index.json"))).also { it.isLenient = true })
      .asJsonObject

  private fun getCompatDataAndProseValues(dir: File,
                                          withSubSections: Boolean = false,
                                          withIdVariants: Boolean = false): Pair<List<Pair<String, JsonObject>>, List<JsonObject>> =
    Pair(
      generateSequence(1) { it + 1 }
        .map { File(dir, if (it == 1) "bcd.json" else "bcd-$it.json") }
        .takeWhile { it.exists() }
        .flatMap { file ->
          val json = JsonParser.parseReader(JsonReader(FileReader(file)).also { it.isLenient = true })
            .asJsonObject
          val defaultId = json.get("id").let {
            if (it is JsonNull)
              defaultBcdContext
            else
              it.asString
          }
          val data = json.getAsJsonObject("data")
          if (!withSubSections || data.has("__compat")) {
            listOf(Pair(defaultId.lowercase(Locale.US), data))
          }
          else {
            data.entrySet().map { (key, value) ->
              Pair(key.lowercase(Locale.US), value.asJsonObject)
            }
          }
        }
        .filter { (key, _) -> !bcdIdIgnore.contains(key) }
        .map { (key, value) -> Pair(bcdIdMappings[key] ?: key, value) }
        .onEach {
          if (!knownBcdIds.contains(it.first) && (!withIdVariants || !bcdNameVariantId.contains(it.first)))
            throw RuntimeException("Unknown BCD id: ${it.first}")
        }
        .toList(),
      parseIndexJson(dir)
        .getAsJsonObject("doc")
        .getAsJsonArray("body")!!
        .map { it.asJsonObject }
        .filter { it.getAsJsonPrimitive("type").asString == "prose" }
        .map { it.getAsJsonObject("value") }
        .toList()
    )

  private fun buildAttributes(tagDir: File,
                              attributesDoc: RawProse?,
                              elementCompatDataList: List<Pair<String, JsonObject>>,
                              commonAttributes: Map<String, MdnHtmlAttributeDocumentation>): Map<String, MdnHtmlAttributeDocumentation>? {
    assert(elementCompatDataList.size < 2)
    val elementCompatData = elementCompatDataList.getOrNull(0)?.second
    val docAttrs = processDataList(attributesDoc).flatMap { (name, doc) ->
      if (name.contains(','))
        name.splitToSequence(',').map { Pair(it.trim(), doc) }
      else
        sequenceOf(Pair(name, doc))
    }.toMap()
    val compatAttrs = elementCompatData?.entrySet()?.asSequence()
                        ?.filter { data -> !data.key.any { it == '_' || it == '-' } }
                        ?.map {
                          Pair(it.key.lowercase(Locale.US),
                               Pair(extractStatus(it.value.asJsonObject), extractCompatibilityInfo(it.value.asJsonObject)))
                        }
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
      .map { Pair(it, MdnHtmlAttributeDocumentation(urlPrefix + it, compatAttrs[it]?.first, compatAttrs[it]?.second, docAttrs[it])) }
      .sortedBy { it.first }
      .toMap()
      .takeIf { it.isNotEmpty() }
  }

  private fun processElementDocumentation(elementDoc: RawProse, summaryDoc: RawProse?): Pair<String, Map<String, String>> {
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
    if (summaryDoc != null && htmlFile.firstChild.children.none { (it as? XmlTag)?.name == "p" }) {
      return Pair(fixSpaces(htmlFile.patchedText() + createHtmlFile(summaryDoc).patchedText()), sections)
    }
    return Pair(fixSpaces(htmlFile.patchedText()), sections)
  }

  private fun createHtmlFile(contents: RawProse): HtmlFileImpl {
    val htmlFile = PsiFileFactory.getInstance(project)
      .createFileFromText("dummy.html", HTMLLanguage.INSTANCE, contents.content, false, true)
    val toRemove = mutableSetOf<PsiElement>()
    val toSimplify = mutableSetOf<XmlTag>()
    htmlFile.acceptChildren(object : XmlRecursiveElementVisitor() {

      override fun visitXmlComment(comment: XmlComment) {
        toRemove.add(comment)
      }

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
              cls.contains("non-standard") || cls.contains("icon-nonstandard") -> "⚠️"
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
    tag.replace(
      XmlElementFactory.getInstance(tag.project).createTagFromText("""<${tag.name}>${extractText(tag)}</${tag.name}>""",
                                                                   HTMLLanguage.INSTANCE))
  }

  private fun extractText(tag: XmlTag): String {
    val result = StringBuilder()
    tag.acceptChildren(object : XmlRecursiveElementVisitor() {
      override fun visitXmlText(text: XmlText) {
        result.append(text.text.replace(' ', ' '))
      }
    })
    return result.toString()
  }

  private fun isEmptyTag(tag: XmlTag): Boolean =
    tag.name.let { it != "br" && it != "hr" }
    && !tag.children.map {
      (it is XmlAttribute && it.name != "class")
      || (it is XmlText && it.text.isNotBlank())
      || (it is XmlTag && !isEmptyTag(it))
    }.any { it }

  private fun extractParameters(sections: List<JsonObject>): Map<String, String>? =
    extractFromDataList(sections, "parameters", "syntax")

  private fun extractReturns(sections: List<JsonObject>): RawProse? =
    filterProseById(sections, "return_value").firstOrNull()
      ?.getProseContent()

  private fun extractThrows(sections: List<JsonObject>): Map<String, String>? =
    extractFromDataList(sections, "exceptions")

  private fun extractPropertyValues(sections: List<JsonObject>): Map<String, String>? =
    extractFromDataList(sections, "values")

  private fun extractFromDataList(sections: List<JsonObject>, vararg ids: String): Map<String, String>? =
    ids.asSequence()
      .map { processDataList(getProseContentById(sections, it)) }
      .filter { it.isNotEmpty() }
      .firstOrNull()
      ?.mapValues { (_, doc) ->
        doc.replace("\n", " ")
          .replace(Regex("<p>\\s*(.*?)\\s*</p>"), "$1<br>")
          .removeSuffix("<br>")
          .patchProse()
      }

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


  private fun extractStatus(compatData: JsonObject): Set<MdnApiStatus>? =
    extractStatus(listOf(Pair(defaultBcdContext, compatData)))

  private fun extractStatus(compatData: List<Pair<String, JsonObject>>): Set<MdnApiStatus>? =
    compatData
      .mapNotNull { it.second.get("__compat") as? JsonObject }
      .flatMap { it.getAsJsonObject("status").entrySet() }
      .filter { it.value.asBoolean }
      .map { MdnApiStatus.valueOf(it.key.toPascalCase()) }
      .toSet()
      .takeIf { it.isNotEmpty() }

  private fun extractCompatibilityInfo(compatData: JsonObject): CompatibilityMap? =
    extractCompatibilityInfo(listOf(Pair(defaultBcdContext, compatData)))

  @Suppress("UNCHECKED_CAST")
  private fun extractCompatibilityInfo(compatData: List<Pair<String, JsonObject>>): CompatibilityMap? =
    compatData
      .asSequence()
      .mapNotNull { (id, data) ->
        data.get("__compat")
          ?.asSafely<JsonObject>()
          ?.getAsJsonObject("support")
          ?.entrySet()
          ?.asSequence()
          ?.mapNotNull { entry ->
            jsRuntimesMap[entry.key]?.let { runtime ->
              Pair(runtime, entry.value?.let { extractBrowserVersion(runtime, it) })
            }
          }
          ?.toMap()
          ?.takeIf { map -> map.values.any { it == null || it.isNotEmpty() } }
          ?.filterValues { it != null }
          ?.let {
            Pair(if (bcdNameVariantId.contains(id)) defaultBcdContext else id,
                 it as Map<MdnJavaScriptRuntime, String>)
          }
      }
      .toMap()
      .takeIf { it.isNotEmpty() }


  private fun extractBrowserVersion(runtime: MdnJavaScriptRuntime, versionInfo: JsonElement): String? {
    fun extractVersion(element: JsonElement?): String? {
      if (element == null || element.isJsonNull)
        return null
      else if (element is JsonPrimitive) {
        if (element.isBoolean) {
          return if (element.asBoolean) "" else null
        }
        else if (element.isString) {
          val value = element.asString
          return when {
            value.isEmpty() -> null
            value == runtime.firstVersion -> ""
            else -> value.removePrefix("≤")
          }
        }
      }
      throw IllegalStateException(element.toString())
    }

    val versions = (if (versionInfo is JsonArray) versionInfo.asSequence() else sequenceOf(versionInfo))
      .onEach { if (it !is JsonObject) throw IllegalStateException(it.toString()) }
      .filterIsInstance<JsonObject>()
      .filter { !it.has("prefix") && !it.has("flags") && !it.has("alternative_name") && !it.has("partial_implementation") }
      .map {
        Triple(extractVersion(it.get("version_added")), extractVersion(it.get("version_removed")), it.has("notes"))
      }
      .filter { it.first != null && it.second == null }
      .sortedBy { it.first!! }
      .toList()
    if (versions.size > 1) {
      if (versions.all { it.third }) {
        return versions.joinToString(",") { it.first!! + "*" }
      }
      val withoutNotes = versions.filter { !it.third }
      if (withoutNotes.size == 1) {
        return withoutNotes[0].first!!
      }
      throw IllegalStateException(versionInfo.toString())
    }
    else if (versions.size == 1) {
      return versions[0].first
    }
    return null
  }

  private fun createDtsMdnIndex(symbols: Map<String, MdnJsSymbolDocumentation>): Map<String, String> {
    val context = JSCorePredefinedLibrariesProvider.getAllJSPredefinedLibraryFiles()
      .find { it.name == "lib.dom.d.ts" }
      ?.let { PsiManager.getInstance(project).findFile(it) }

    val realNameMap = StubIndex.getInstance().getAllKeys(JSSymbolIndex2.KEY, project)
      .associateBy { it.lowercase(Locale.US) }

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
          ?.asSafely<JSPsiElementBase>()
          ?.qualifiedName
          ?.takeIf { !it.equals(symbolName, true) }
          ?.lowercase(Locale.US)
          ?.let { Pair(it, symbolName) }
      }
      .toMap(TreeMap())
  }

  private val redirects = FileUtil.loadFile(Path.of(MDN_CONTENT_ROOT, BUILT_LANG, "_redirects.txt").toFile())
    .splitToSequence('\n')
    .mapNotNull { line ->
      line.splitToSequence('\t')
        .map { StringUtil.toLowerCase(it.trim()) }
        .toList()
        .takeIf { it.size == 2 && !it[0].startsWith("#") }
        ?.let { Pair(it[0], it[1]) }
    }
    .toMap()
}

private fun XmlTag.innerHtml(): String = this.children.asSequence()
  .filter { it is XmlTag || it is XmlText }.map { it.text }.joinToString("\n").let { fixSpaces(it) }

private fun XmlElement.findSubTag(name: String): XmlTag? = this.children.find { (it as? XmlTag)?.name == name } as? XmlTag

private fun fixSpaces(doc: String): String = doc.replace(Regex("[ \t]*\n[ \t\n]*"), "\n").trim()

private fun String.toPascalCase(): String = getWords().map { it.lowercase(Locale.US) }.joinToString(separator = "",
                                                                                                    transform = StringUtil::capitalize)

private fun String.getWords() = NameUtilCore.nameToWords(this).filter { it.isNotEmpty() && Character.isLetterOrDigit(it[0]) }

private data class RawProse(val content: String) {
  fun patch(): String = content.patchProse()
}

private fun PsiElement.patchedText() =
  text.patchProse()

private fun JsonObject.getProseContent(): RawProse =
  this.getAsJsonPrimitive("content").asString
    .let { RawProse(it) }

private fun String.patchProse(): String =
  replace("/en-US/docs", MDN_DOCS_URL_PREFIX, true)
    .replace("&apos;", "'")
    .replace("&quot;", "\"")
    .replace("&nbsp;", " ")
    .replace(Regex("<p>\\s+"), "<p>")
    .replace(Regex("\\s+</p>"), "</p>")
    .also { fixedProse ->
      Regex("&(?!lt|gt|amp)[a-z]*;").find(fixedProse)?.let {
        throw Exception("Unknown entity found in prose: ${it.value}")
      }
    }

private class CompatibilityMapSerializer : JsonSerializer<Map<*, *>> {
  override fun serialize(src: Map<*, *>, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
    if (src.size == 1 && src.containsKey(defaultBcdContext)) {
      context.serialize(src[defaultBcdContext])
    }
    else {
      val result = JsonObject()
      src.entries.forEach { (key, value) ->
        result.add(key.toString(), context.serialize(value))
      }
      result
    }

}