// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.intellij.application.options.CodeStyle
import com.intellij.bcd.json.*
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
import com.intellij.util.applyIf
import com.intellij.util.asSafely
import com.intellij.util.text.CharSequenceSubSequence
import com.intellij.util.text.NameUtilCore
import java.io.File
import java.io.FileReader
import java.lang.reflect.Type
import java.nio.file.Path
import java.util.*
import java.util.function.BiFunction

val htmlSpecialMappings = mapOf(
  "heading_elements" to setOf("h1", "h2", "h3", "h4", "h5", "h6")
)

/**
 * When adding a known property here, ensure that there is appropriate key in XmlPsiBundle.
 */
const val defaultBcdContext = "\$default_context"

val jsRuntimesMap = MdnJavaScriptRuntime.entries.associateBy { it.mdnId }

val BROWSER_COMPAT_DATA_PATH = PathManager.getCommunityHomePath() + "/xml/xml-psi-impl/mdn-doc-gen/work/browser-compat-data.json"
val MDN_CONTENT_ROOT = PathManager.getCommunityHomePath() + "/xml/xml-psi-impl/mdn-doc-gen/work/mdn-content/files"
val YARI_BUILD_PATH = PathManager.getCommunityHomePath() + "/xml/xml-psi-impl/mdn-doc-gen/work/yari/client/build"
const val BUILT_LANG = "en-us"
const val WEB_DOCS = "docs/web"
const val MDN_DOCS_URL_PREFIX = "\$MDN_URL\$"
const val OUTPUT_DIR = "xml/xml-psi-impl/resources-gen/com/intellij/documentation/mdn/"
const val BUNDLE_OUTPUT_DIR = "xml/xml-psi-impl/resources-gen/messages/"


private val LICENSE = MdnNameWithUrl(
  "CC-BY-SA 2.5",
  "https://creativecommons.org/licenses/by-sa/2.5/",
)

private val AUTHOR = MdnNameWithUrl(
  "Mozilla Contributors",
  "https://github.com/mdn/content",
)

const val SEE_REFERENCE = "\$SEE_REFERENCE\$"
val seePattern = Regex("<p>See <a href=\"/$BUILT_LANG/$WEB_DOCS/([a-z0-9_\\-/]+)(#[a-z]+)?\"><code>[a-z0-9_\\-]+</code></a>\\.</p>",
                       RegexOption.IGNORE_CASE)

val svgAttributeSectionsPattern = Regex(
  "(.*)<em>Value type</em>\\s*:(.*)\\s*<em>Default value(?:</em>)?\\s*:(.*)\\s+(?:<em>)?Animatable</em>\\s*:\\s*(.*)",
  RegexOption.DOT_MATCHES_ALL)

val bcd: Bcd = readBcd(BROWSER_COMPAT_DATA_PATH)

val jsWebApiNameFilter: (String) -> Boolean = { name ->
  !name.contains('-')
  && (!name.contains("_") || name.endsWith("_static"))
  && name != "index"
}

val bcdIdDescriptions = mutableMapOf<String, String>()

/**
 * Run the tests to generate documentation. Prepare MDN documentation repositories with `prepare-mdn.sh`.
 * Afterwards run MdnDocUpdateTestSuite to update any failing tests due to doc content changes.
 *
 * It's so much easier to run a test, than to setup the whole IJ environment.
 *
 */
class GenerateMdnDocumentation : BasePlatformTestCase() {

  /* Run these tests to generate documentation. Prepare MDN documentation repositories with `prepare-mdn.sh` */
  fun testGenHtml() {
    val attributes =
      extractInformationSimple("html.global_attributes", "html/reference/global_attributes", listOf('_', '-'),
                               this::extractAttributeDocumentation)
    outputJson(MdnApiNamespace.Html.name, MdnHtmlDocumentation(
      LICENSE, AUTHOR, BUILT_LANG,
      attributes,
      extractInformationSimple("html.elements", "html/reference/elements", listOf('_', '-'),
                               allowList = htmlSpecialMappings.keys) { dir, bcdPath, bcdInfo ->
        this.extractElementDocumentation(dir, "html/reference/attributes", "html/reference/global_attributes",
                                         bcdPath, bcdInfo, attributes)
      },
      reversedAliasesMap(htmlSpecialMappings)
    ))
  }

  fun testGenMathML() {
    val attributes =
      extractInformationSimple("mathml.global_attributes", "mathml/reference/global_attributes", emptyList(),
                               this::extractAttributeDocumentation)
    outputJson(MdnApiNamespace.MathML.name, MdnHtmlDocumentation(
      LICENSE, AUTHOR, BUILT_LANG,
      attributes,
      extractInformationSimple("mathml.elements", "mathml/reference/element", emptyList()) { dir, bcdPath, bcdInfo ->
        this.extractElementDocumentation(dir, "mathml/reference/attribute", "mathml/reference/global_attributes",
                                         bcdPath, bcdInfo, attributes)
      }
    ))
  }

  fun testGenSvg() {
    val attributes =
      extractInformationSimple("svg.global_attributes", "svg/reference/attribute", listOf('_')) { dir, bcdPath, bcd ->
        if (bcd == null && dir.name !in listOf("requiredfeatures", "systemlanguage"))
          null
        else
          extractAttributeDocumentation(dir, bcdPath, bcd)
      }
    outputJson(MdnApiNamespace.Svg.name, MdnHtmlDocumentation(
      LICENSE, AUTHOR, BUILT_LANG,
      attributes,
      extractInformationSimple("svg.elements", "svg/reference/element", listOf('_')) { dir, bcdPath, bcdInfo ->
        this.extractElementDocumentation(dir, "svg/reference/attribute", null, bcdPath, bcdInfo, attributes)
      }
    ))
  }

  fun testGenJsWebApi() {
    val symbols = extractInformation(
      "api", bcd.resolve("api"), jsWebApiNameFilter
    ) { dir, _, bcdInfo ->
      extractJavascriptDocumentation(dir, bcdInfo, "")
    }
      .filter { !it.key.startsWith("window.window.") }
      .resolveReferences()
    val fragments = webApiFragmentStarts.associateWithTo(TreeMap()) { sortedMapOf<String, MdnJsSymbolDocumentation>() }
    symbols.forEach { (name, doc) ->
      (fragments.floorEntry(name[0]) ?: throw IllegalStateException("Null entry for $name")).value[name] = doc
    }
    fragments.forEach { (prefix, symbols) ->
      outputJson("${MdnApiNamespace.WebApi.name}-$prefix", MdnJsDocumentation(
        LICENSE, AUTHOR, BUILT_LANG, symbols
      ))
    }

    val index = createDtsMdnIndex(symbols)
    FileUtil.writeToFile(Path.of(PathManager.getCommunityHomePath(), OUTPUT_DIR, "${MdnApiNamespace.WebApi.name}-index.json").toFile(),
                         GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
                           .toJson(index))
  }

  fun testGenJsGlobalObjects() {
    outputJson(MdnApiNamespace.GlobalObjects.name, MdnJsDocumentation(
      LICENSE, AUTHOR, BUILT_LANG,
      extractInformation("javascript/reference/global_objects", bcd.resolve("javascript.builtins"),
                         listOf('-', '_')) { it, _, bcdInfo ->
        extractJavascriptDocumentation(it, bcdInfo, "")
      }.resolveReferences()
    ))
  }

  fun testGenCss() {
    outputJson(MdnApiNamespace.Css.name, buildCssDocs())
  }

  fun testGenDomEvents() {
    outputJson(MdnApiNamespace.DomEvents.name, MdnDomEventsDocumentation(
      LICENSE, AUTHOR, BUILT_LANG, extractDomEvents(),
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
    try {
      CodeStyleSettingsManager.getInstance(project).dropTemporarySettings()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  private fun outputJson(outputFile: String, data: MdnDocumentation) {
    val targetFile = Path.of(PathManager.getCommunityHomePath(), OUTPUT_DIR, "$outputFile.json").toFile()
    if (data is MdnHtmlDocumentation || data is MdnCssDocumentation || data is MdnDomEventsDocumentation) {
      val targetObsoleteFile = Path.of(PathManager.getCommunityHomePath(), OUTPUT_DIR, "$outputFile-obsolete.json").toFile()

      val obsoleteData = jacksonObjectMapper().readValue(targetObsoleteFile, data::class.java)
      val currentData = jacksonObjectMapper().readValue(targetFile, data::class.java)

      when (data) {
        is MdnDomEventsDocumentation -> {
          currentData as MdnDomEventsDocumentation
          obsoleteData as MdnDomEventsDocumentation

          makeMissingSymbolsObsolete(data, currentData, obsoleteData, MdnDomEventsDocumentation::events) {
            it.copy(status = setOf(MdnApiStatus.Obsolete))
          }
        }
        is MdnHtmlDocumentation -> {
          currentData as MdnHtmlDocumentation
          obsoleteData as MdnHtmlDocumentation

          makeMissingSymbolsObsolete(
            data, currentData, obsoleteData, MdnHtmlDocumentation::tags,
            { it.copy(status = setOf(MdnApiStatus.Obsolete)) },
            MdnHtmlElementDocumentation::attrs,
            { MdnHtmlElementDocumentation(null, null, null, null, "", null, mutableMapOf()) },
            { it.copy(status = setOf(MdnApiStatus.Obsolete)) },
          )
          makeMissingSymbolsObsolete(data, currentData, obsoleteData, MdnHtmlDocumentation::attrs) {
            it.copy(status = setOf(MdnApiStatus.Obsolete))
          }
        }
        is MdnCssDocumentation -> {
          currentData as MdnCssDocumentation
          obsoleteData as MdnCssDocumentation

          makeMissingSymbolsObsolete(
            data, currentData, obsoleteData, MdnCssDocumentation::atRules,
            { it.copy(status = setOf(MdnApiStatus.Obsolete)) },
            MdnCssAtRuleSymbolDocumentation::properties,
            { MdnCssAtRuleSymbolDocumentation(null, null, null, null, "", mutableMapOf(), null) },
            { it.copy(status = setOf(MdnApiStatus.Obsolete)) }
          )
          makeMissingSymbolsObsolete(data, currentData, obsoleteData, MdnCssDocumentation::properties) {
            it.copy(status = setOf(MdnApiStatus.Obsolete))
          }
          makeMissingSymbolsObsolete(data, currentData, obsoleteData, MdnCssDocumentation::pseudoClasses) {
            it.copy(status = setOf(MdnApiStatus.Obsolete))
          }
          makeMissingSymbolsObsolete(data, currentData, obsoleteData, MdnCssDocumentation::pseudoElements) {
            it.copy(status = setOf(MdnApiStatus.Obsolete))
          }
          makeMissingSymbolsObsolete(data, currentData, obsoleteData, MdnCssDocumentation::functions) {
            it.copy(status = setOf(MdnApiStatus.Obsolete))
          }
          makeMissingSymbolsObsolete(data, currentData, obsoleteData, MdnCssDocumentation::dataTypes) {
            it.copy(status = setOf(MdnApiStatus.Obsolete))
          }
        }
        else -> throw RuntimeException("Unsupported documentation type: ${data::class.java.name}")
      }
      FileUtil.writeToFile(targetObsoleteFile, GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
        .registerTypeAdapter(Map::class.java, CompatibilityMapSerializer(true))
        .create().toJson(obsoleteData))
    }
    FileUtil.writeToFile(targetFile, GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
      .registerTypeAdapter(Map::class.java, CompatibilityMapSerializer(false))
      .create().toJson(data))
    updateBcdDescriptions()
  }

  private fun <T : Any> extractInformationSimple(
    bcdPath: String, mdnPath: String,
    disallowedChars: List<Char>,
    extractor: (File, String?, Identifier?) -> T?,
  ): Map<String, T> =
    extractInformationSimple(bcdPath, mdnPath, disallowedChars, emptySet()) { file, innerBcdPath, id ->
      extractor(file, innerBcdPath, id)
    }

  private fun <T : Any> extractInformationSimple(
    bcdPath: String, mdnPath: String, disallowedChars: List<Char>,
    allowList: Set<String>, extractor: (File, String?, Identifier?) -> T?,
  ): Map<String, T> =
    extractInformation(mdnPath, bcd.resolve(bcdPath), disallowedChars, bcdPath, allowList) { docDir, innerBcdPath, id ->
      try {
        extractor(docDir, innerBcdPath, id)?.let { listOf(Pair(docDir.name, it)) } ?: emptyList()
      }
      catch (e: Exception) {
        System.err.println("Error for $docDir: ${e.message}")
        throw e
      }
    }

  private fun getMdnDir(mdnPath: String): File =
    Path.of(YARI_BUILD_PATH, BUILT_LANG, WEB_DOCS, mdnPath
      .replace("*", "_star_")
      .replace("::", "_doublecolon_")
      .replace(":", "_colon_")
    ).toFile()

  private fun <T> extractInformation(
    mdnPath: String,
    bcd: Identifier?,
    disallowedChars: List<Char>,
    bcdPath: String? = null,
    allowList: Set<String> = emptySet(),
    blockList: Set<String> = emptySet(),
    mdnUrlBuilder: ((String, Identifier) -> String?)? = null,
    extractor: (File, String?, Identifier?) -> List<Pair<String, T>>,
  ): Map<String, T> =
    extractInformation(mdnPath, bcd,
                       { (disallowedChars.none { ch -> it.contains(ch) } || allowList.contains(it)) && !blockList.contains(it) },
                       bcdPath, mdnUrlBuilder, extractor)

  private fun <T> extractInformation(
    mdnPath: String,
    bcd: Identifier?,
    nameFilter: (String) -> Boolean,
    bcdPath: String? = null,
    mdnUrlBuilder: ((String, Identifier) -> String?)? = null,
    extractor: (File, String?, Identifier?) -> List<Pair<String, T>>,
  ): Map<String, T> {
    val namesWithBcd = mutableSetOf<String>()
    val resultWithBcd = (bcd?.additionalProperties?.asSequence() ?: emptySequence())
      .flatMap { (name, bcd) ->
        if (bcd.compat == null) {
          bcd.additionalProperties.entries.map { Pair(name + "/" + it.key, it.value) }
        }
        else listOf(Pair(name, bcd))
      }
      .flatMap { (name, bcdInfo) ->
        if (!nameFilter(name)) return@flatMap emptyList()
        try {
          val mdnUrl = bcdInfo.compat?.mdnUrl?.lowercase(Locale.US)?.removePrefix("https://developer.mozilla.org/docs/web/")
                       ?: mdnUrlBuilder?.invoke(name, bcdInfo)
                       ?: "$mdnPath/$name"
          val dir = getMdnDir(mdnUrl).let {
            if (it.name.endsWith("()") && !it.exists()) {
              val suffixed = File(it.parentFile, it.name.removeSuffix("()") + "_function")
              if (suffixed.exists()) return@let suffixed
            }
            it
          }
          if (!dir.exists()) {
            System.err.println("Dir not found: $dir")
            emptyList()
          }
          else {
            namesWithBcd.add(dir.name.takeLastWhile { it != '/' }.lowercase(Locale.US))
            extractor(dir, bcdPath?.let { "$bcdPath.${name.replace('/', '.')}" }, bcdInfo)
          }
        }
        catch (e: Exception) {
          throw RuntimeException("Failed to process $mdnPath/$name: ${e.message}", e)
        }
      }
      .toMap(TreeMap())

    val resultWithoutBcd = (getMdnDir(mdnPath).listFiles()?.asSequence() ?: emptySequence())
      .filter { file ->
        file.isDirectory
        && file.name.lowercase(Locale.US).let { nameFilter(it) && !namesWithBcd.contains(it) }
        && File(file, "index.json").exists()
      }
      .flatMap {
        try {
          extractor(it, null, null)
        }
        catch (e: Exception) {
          throw RuntimeException("Failed to process $it: ${e.message}", e)
        }
      }
      .distinct()
      .sortedBy { it.first }
      .toMap()
    return TreeMap(resultWithoutBcd + resultWithBcd)
  }

  private fun extractElementDocumentation(
    dir: File,
    attributesMdnPath: String,
    globalAttributesMdnPath: String?,
    bcdPath: String?,
    compatData: Identifier?,
    commonAttributes: Map<String, MdnHtmlAttributeDocumentation>,
  ): MdnHtmlElementDocumentation {
    val contents = DocContents(dir, compatData)

    val elementDoc = contents.prose.first().getProseContent().appendOtherSections(contents.prose)

    val attributesDoc = contents.prose.filterProseById("attributes", "attributes_for_form_submission", "deprecated_attributes",
                                                       "obsolete_attributes",
                                                       "non-standard_attributes")
      .joinToString("\n") { it.getProseContent().content }
      .let { RawProse(it) }

    val status = extractStatus(contents)
    val compatibility = extractCompatibilityInfo(contents)
    val doc = processElementDocumentation(elementDoc, contents.prose.getProseContentById("summary"))

    val documentation = doc.first
    val properties = doc.second.takeIf { it.isNotEmpty() }
                     ?: contents.prose.filterProseById("properties", "technical_summary")
                       .firstOrNull()
                       ?.getProseContent()
                       ?.let { processElementDocumentation(it, null) }
                       ?.second
                       ?.takeIf { it.isNotEmpty() }

    return MdnHtmlElementDocumentation(getMdnDocsUrl(dir), status, compatibility, contents.baseline, documentation, properties,
                                       buildAttributes(dir, getMdnDir(attributesMdnPath), globalAttributesMdnPath?.let { getMdnDir(it) },
                                                       attributesDoc, bcdPath, compatData, commonAttributes))
  }

  private fun extractAttributeDocumentation(dir: File, bcdPath: String?, compatData: Identifier?): MdnHtmlAttributeDocumentation {
    val contents = DocContents(dir, compatData, bcdPath)
    return MdnHtmlAttributeDocumentation(getMdnDocsUrl(dir), extractStatus(contents),
                                         extractCompatibilityInfo(contents),
                                         contents.baseline,
                                         extractDescription(contents.prose, true))
  }

  private fun extractDomEvents(): Map<String, MdnDomEventDocumentation> {
    val prefix = "/$BUILT_LANG/$WEB_DOCS/events/"
    return redirects.asSequence()
      .filter { (from, to) ->
        from.startsWith(prefix)
        && !from.contains("_")
        && !to.contains("#")
        && !to.endsWith("/window")
      }
      .mapNotNull {
        Pair(it.key.substring(prefix.length),
             extractDomEventDocumentation(Path.of(YARI_BUILD_PATH, it.value).toFile()) ?: return@mapNotNull null)
      }
      .toMap()
  }

  private fun extractDomEventDocumentation(dir: File): MdnDomEventDocumentation? {
    val contents = DocContents(dir, null)
    return MdnDomEventDocumentation(getMdnDocsUrl(dir), extractStatus(contents),
                                    extractCompatibilityInfo(contents),
                                    contents.baseline,
                                    extractEventDescription(contents.prose) ?: return null)
  }

  private fun extractJavascriptDocumentation(
    dir: File,
    compatData: Identifier?,
    namePrefix: String,
  ): List<Pair<String, MdnJsSymbolDocumentation>> {
    try {
      val contents = DocContents(dir, compatData)
      val thisNamePrefix = "$namePrefix${dir.name.lowercase(Locale.US)}".removeSuffix("_static")
      return extractInformation(dir.toMdnUrl(), compatData, jsWebApiNameFilter) { subDir, _, subBcdInfo ->
        extractJavascriptDocumentation(
          subDir, subBcdInfo, "$thisNamePrefix.")
      }.toList() +
             Pair(thisNamePrefix,
                  MdnJsSymbolDocumentation(getMdnDocsUrl(dir), compatData?.let { extractStatus(contents) },
                                           compatData?.let { extractCompatibilityInfo(contents) },
                                           contents.baseline,
                                           extractDescription(contents.prose),
                                           extractParameters(contents.prose),
                                           extractReturns(contents.prose)?.patch(),
                                           extractThrows(contents.prose)))

    }
    catch (e: Exception) {
      System.err.println("Error for $dir: ${e.message}")
      throw e
    }
  }

  private fun buildCssDocs(): MdnCssDocumentation {
    val result = TreeMap<String, MutableMap<String, MdnRawSymbolDocumentation>>()
    extractInformation(
      "css",
      bcd.resolve("css"),
      {
        (it.contains("_colon_") || it.contains("_doublecolon_") || it.endsWith("_function") || !it.contains('_'))
      },
    ) { docDir, _, bcdInfo ->
      try {
        if (!docDir.isDirectory) return@extractInformation emptyList()
        val info = CssElementInfo(docDir)
        if (info.hasDataType) {
          extractInformation(docDir.toMdnUrl(), bcdInfo, { true }) { innerDocDir, _, innerBcdInfo ->
            try {
              listOf(Pair(CssElementInfo(innerDocDir).name,
                          extractCssElementDocumentation(innerDocDir, innerBcdInfo)))
            }
            catch (e: Exception) {
              System.err.println("Error for $innerDocDir: ${e.message}")
              throw e
            }
          }.toList()
        }
        else {
          emptyList()
        } + Pair(info.name, extractCssElementDocumentation(docDir, bcdInfo))
      }
      catch (e: Exception) {
        System.err.println("Error for $docDir: ${e.message}")
        throw e
      }
    }
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
    postProcessProperties(result["properties"] as MutableMap)
    return MdnCssDocumentation(
      LICENSE, AUTHOR, BUILT_LANG,
      result["atRules"]!!.mapValues { it.value as MdnCssAtRuleSymbolDocumentation },
      result["dataTypes"]!!.mapValues { it.value as MdnCssPropertySymbolDocumentation },
      result["functions"]!!.mapValues { it.value as MdnCssPropertySymbolDocumentation },
      result["properties"]!!.mapValues { it.value as MdnCssPropertySymbolDocumentation },
      result["pseudoClasses"]!!.mapValues { it.value.asMdnCssBasicSymbolDocumentation() },
      result["pseudoElements"]!!.mapValues { it.value.asMdnCssBasicSymbolDocumentation() },
    )
  }

  private fun MdnRawSymbolDocumentation.asMdnCssBasicSymbolDocumentation() =
    this as? MdnCssBasicSymbolDocumentation
    ?: MdnCssBasicSymbolDocumentation(url, status, compatibility, baseline, doc, (this as? MdnCssPropertySymbolDocumentation)?.formalSyntax)

  private fun extractCssElementDocumentation(dir: File, compatData: Identifier?): MdnRawSymbolDocumentation {
    val contents = DocContents(dir, compatData)
    val url = getMdnDocsUrl(dir)
    val status = extractStatus(contents)
    val compatibility = extractCompatibilityInfo(contents)
    val description = extractDescription(contents.prose)
    val dirName = dir.name
    return when {
      dirName.startsWith('_') || dirName.endsWith("()") ->
        MdnCssBasicSymbolDocumentation(url, status, compatibility, contents.baseline, description, extractFormalSyntax(contents.prose))
      dirName.startsWith('@') ->
        MdnCssAtRuleSymbolDocumentation(
          url, status, compatibility, contents.baseline, description,
          extractInformation(dir.toMdnUrl(), compatData, { !it.contains('_') }) { docDir, _, innerBcdInfo ->
            try {
              if (!docDir.isDirectory) {
                emptyList()
              }
              else {
                listOf(Pair(docDir.name,
                            extractCssElementDocumentation(docDir, innerBcdInfo) as MdnCssPropertySymbolDocumentation))
              }
            }
            catch (e: Exception) {
              System.err.println("Error for $docDir: ${e.message}")
              throw e
            }
          }.takeIf { it.isNotEmpty() }, extractFormalSyntax(contents.prose)
        )
      else ->
        MdnCssPropertySymbolDocumentation(
          url, status, compatibility, contents.baseline, description, extractFormalSyntax(contents.prose), extractPropertyValues(contents.prose))
    }
  }

  private fun postProcessProperties(properties: MutableMap<String, MdnRawSymbolDocumentation>) {
    properties.computeIfPresent("place-items", BiFunction { _, doc ->
      val alignItems = properties["align-items"]!! as MdnCssPropertySymbolDocumentation
      val justifyItems = properties["justify-items"]!! as MdnCssPropertySymbolDocumentation
      (doc as MdnCssPropertySymbolDocumentation).copy(
        values = alignItems.values!! + justifyItems.values!!
      )
    })
  }

  private fun extractDescription(indexDataProseValues: List<JsonObject>, appendOtherSections: Boolean = false): String =
    indexDataProseValues.firstOrNull()?.getProseContent()?.applyIf(appendOtherSections) { appendOtherSections(indexDataProseValues) }
      ?.let { createHtmlFile(it).patchedText() }
    ?: ""

  private fun extractFormalSyntax(indexDataProseValues: List<JsonObject>): String? =
    indexDataProseValues.getProseContentById("formal_syntax")
      ?.let { createHtmlFile(it) }
      ?.let { PsiTreeUtil.findChildOfType(it, XmlTag::class.java) }
      ?.let { extractPreText(it) }
      ?.patchProse()
      ?.replace(" ", " ")
      ?.replace(Regex("\n +"), "\n")
      ?.let { formatFormalCssSyntax(it) }

  private fun formatFormalCssSyntax(str: String): String {
    if (str.startsWith("\n```css")) {
      return str.removePrefix("\n```css\n").removeSuffix("\n```\n")
        .replace(Regex(" +"), " ")
    }
    val result = StringBuilder()
    val firstEquals = str.indexOf('=')
    val firstLine = str.indexOf('\n')
    val start = if (firstEquals < 0 || firstLine in 0 until firstEquals) 0 else firstEquals + 1
    var i = start
    var indentation = 1
    while (i < str.length) {
      val seq = CharSequenceSubSequence(str, i, str.length)
      val prevContents = CharSequenceSubSequence(str, start, i)
      if (prevContents.endsWith("\n\n")) {
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
        result.append(str[i++])
      }
      else {
        val prevChar = if (i <= 0) ' ' else str[i - 1]
        when (val ch = str[i++]) {
          ' ' ->
            if (prevChar != ' ')
              result.append(" ")
          '\n' ->
            when (prevChar) {
              '(' -> result.append('\n')
              '|' -> result.append(" ")
            }
          else -> result.append(ch)
        }
      }
    }
    return result.toString()
      .replace(Regex("[ \n]+\n"), "\n")
      .trim()
  }

  private fun extractEventDescription(indexDataProseValues: List<JsonObject>): String? =
    indexDataProseValues.first().getProseContent()
      .let { createHtmlFile(it) }
      .children[0].children.filter { it is XmlTag && it.name == "p" }
      .takeIf { it.isNotEmpty() }
      ?.joinToString("\n") { it.patchedText() }

  private fun reversedAliasesMap(specialMappings: Map<String, Set<String>>): Map<String, String> =
    specialMappings.entries.asSequence().flatMap { mapping -> mapping.value.asSequence().map { Pair(it, mapping.key) } }.toMap()

  private fun getMdnDocsUrl(dir: File): String =
    MDN_DOCS_URL_PREFIX + dir.path.replace('\\', '/').let { it.substring(it.indexOf("/docs/") + 5) }


  private fun parseIndexJson(dir: File): JsonObject =
    JsonParser.parseReader(JsonReader(FileReader(File(dir, "index.json"))).also { it.isLenient = true })
      .asJsonObject


  private fun buildAttributes(
    tagDir: File,
    attributesDir: File,
    globalAttributesDir: File?,
    attributesDoc: RawProse?,
    bcdPath: String?,
    elementCompatData: Identifier?,
    commonAttributes: Map<String, MdnHtmlAttributeDocumentation>,
  ): Map<String, MdnHtmlAttributeDocumentation>? {
    assert(attributesDir.exists()) { "Attributes dir does not exist - $attributesDir" }
    assert(globalAttributesDir == null || globalAttributesDir.exists()) { "Global attributes dir does not exist - $globalAttributesDir" }
    val docAttrs = processDataList(attributesDoc).flatMap { (name, doc) ->
      if (name.contains(','))
        name.splitToSequence(',').map { Pair(it.trim(), doc) }
      else
        sequenceOf(Pair(name, doc))
    }.toMap()
    val compatAttrs = elementCompatData?.additionalProperties?.entries?.asSequence()
                        ?.filter { data -> !data.key.any { it == '_' || it == '-' } }
                        ?.map {
                          val contents = DocContents(null, it.value, "$bcdPath.${it.key}")
                          Pair(it.key.lowercase(Locale.US), Triple(
                            extractStatus(contents),
                            extractCompatibilityInfo(contents),
                            contents.baseline
                          ))
                        }
                        ?.toMap() ?: emptyMap()

    val tagName = tagDir.name
    val missing = ((compatAttrs.keys - docAttrs.keys)) - commonAttributes.keys

    val fromLocal = docAttrs.keys.asSequence()
      .plus(compatAttrs.keys).distinct()
      .associateWith { id ->
        val dir = File(attributesDir, id).takeIf { it.isDirectory }
        val bcd = elementCompatData?.additionalProperties?.entries?.find { it.key.equals(id, true) }?.value
        if (dir?.isDirectory == true) {
          extractAttributeDocumentation(dir, bcdPath?.let { "$it.$id" }, bcd)
        }
        else null
      }

    val fromGlobal = if (globalAttributesDir != null)
      missing.mapNotNull { id ->
        val dir = File(globalAttributesDir, id).takeIf { it.isDirectory }
        val bcd = elementCompatData?.additionalProperties?.entries?.find { it.key.equals(id, true) }?.value
        if (dir?.isDirectory == true) {
          Pair(id, extractAttributeDocumentation(dir, bcdPath?.let { "$it.$id" }, bcd))
        }
        else null
      }
    else
      emptyList()

    val reallyMissing = missing.minus(fromGlobal.map { it.first }.toSet())
    if (reallyMissing.isNotEmpty()) {
      System.err.println("$tagName - missing attrs present in BCD: ${reallyMissing}")
    }

    val urlPrefix = getMdnDocsUrl(tagDir) + "#"
    return docAttrs.keys.asSequence()
      .plus(compatAttrs.keys).distinct()
      .map { StringUtil.toLowerCase(it) }
      .map {
        val local = fromLocal[it]
        val (doc, sections) = docAttrs[it].extractSvgAttributesSections()
        Pair(it, MdnHtmlAttributeDocumentation(
          urlPrefix + it, compatAttrs[it]?.first, compatAttrs[it]?.second, compatAttrs[it]?.third,
          doc ?: local?.doc, sections ?: local?.sections?.takeIf { it.isNotEmpty() }))
      }
      .plus(fromGlobal)
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

  private val BRUSH_REGEX = Regex("brush: ?(js|html|css|xml|http)[a-z0-9-;\\[\\] ]*")

  private fun createHtmlFile(contents: RawProse): HtmlFileImpl {
    val htmlFile = PsiFileFactory.getInstance(project)
      .createFileFromText("dummy.html", HTMLLanguage.INSTANCE, contents.content, false, true)
    val toRemove = mutableSetOf<PsiElement>()
    val codeBlocks = mutableSetOf<XmlTag>()
    htmlFile.acceptChildren(object : XmlRecursiveElementVisitor() {

      override fun visitXmlComment(comment: XmlComment) {
        toRemove.add(comment)
      }

      override fun visitXmlTag(tag: XmlTag) {
        if (tag.name == "pre"
            && tag.getAttributeValue("class")?.matches(BRUSH_REGEX) == true) {
          tag.findSubTag("code")?.let { codeBlocks.add(tag) }
        }
        else if (tag.name == "span"
                 && tag.getAttributeValue("class")?.trim() == "language-name") {
          tag.children.filterTo(toRemove) { it is XmlText }
          super.visitXmlTag(tag)
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
          else if (
          // An SVG diagram
            !tag.text.contains("height=\"25\" fill=\"#F4F7F8\" stroke=\"#D4DDE4\" stroke-width=\"2px\"></rect>"
            )) {
            throw Exception("SVG with no class: " + tag.text)
          }
          toRemove.add(tag)
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
    codeBlocks.forEach(::processCodeBlock)
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

  private fun processCodeBlock(tag: XmlTag) {
    val language = BRUSH_REGEX
      .matchEntire(tag.getAttributeValue("class")!!)!!
      .groupValues[1]
    val code = tag.findSubTag("code")
      ?.let { extractText(it) }
      ?.let { StringUtil.unescapeXmlEntities(it) }
    val toReplace = if (tag.parent.let { it is XmlTag && it.getAttributeValue("class") == "code-example" })
      tag.parent
    else
      tag
    val parent = toReplace.parent
    parent.addBefore(XmlElementFactory.getInstance(tag.project).createTagFromText(
      "<div>\n```$language\n$code\n```\n</div>", HTMLLanguage.INSTANCE), toReplace)
    toReplace.delete()
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

  private fun extractPreText(tag: XmlTag): String {
    val result = StringBuilder()
    tag.acceptChildren(object : XmlRecursiveElementVisitor() {
      override fun visitXmlText(text: XmlText) {
        result.append(text.text.replace(' ', ' '))
      }

      override fun visitXmlTag(tag: XmlTag) {
        if (tag.name == "br")
          result.append("\n")
        super.visitXmlTag(tag)
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
    sections.filterProseById("return_value").firstOrNull()
      ?.getProseContent()

  private fun extractThrows(sections: List<JsonObject>): Map<String, String>? =
    extractFromDataList(sections, "exceptions")

  private fun extractPropertyValues(sections: List<JsonObject>): Map<String, String>? =
    extractFromDataList(sections, "values")


  private fun extractFromDataList(sections: List<JsonObject>, vararg ids: String): Map<String, String>? =
    ids.asSequence()
      .map { processDataList(sections.getProseContentById(it)) }
      .filter { it.isNotEmpty() }
      .firstOrNull()
      ?.mapValues { (_, doc) ->
        doc.replace(Regex("\n{2,}"), "<br>")
          .removeSuffix("<br>")
          .patchProse()
      }

  private fun processDataList(doc: RawProse?): Map<String, String> {
    val result = mutableMapOf<String, String>()
    if (doc != null && doc.content.isNotBlank()) {
      seePattern.matchEntire(doc.content)?.let {
        return mapOf(SEE_REFERENCE to it.groupValues[1].lowercase(Locale.US))
      }
      var lastTitle = ""
      createHtmlFile(doc)
        .document?.children?.asSequence()
        ?.filterIsInstance<XmlTag>()
        ?.flatMap { if (it.name == "figure") it.subTags.asSequence() else sequenceOf(it) }
        ?.filter { it.name == "dl" || it.name == "table" }
        ?.flatMap { it.subTags.asSequence() }
        ?.flatMap { if (it.name == "tbody") it.subTags.asSequence() else sequenceOf(it) }
        ?.forEach { data ->
          when (data.name) {
            "dt" -> lastTitle = extractIdentifier(data)
            "dd" -> if (lastTitle.isNotBlank()) result[lastTitle] = result.getOrDefault(lastTitle, "") + data.innerHtml()
            "tr" -> {
              val cells = data.findSubTags("td").filter {
                val subTags = it.subTags
                subTags.size != 1 || subTags[0].name != "img"
              }
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
        .let {
          if (!it.contains("(") && it.length > 20)
            it.replace(Regex("[,.]\\s+"), "<br>\n")
          else
            it
        }
    else ""
  }

  private fun extractStatus(contents: DocContents): Set<MdnApiStatus>? =
    contents.getBcdMap()
      .mapNotNull { it.value.compat?.status }
      .let { statuses ->
        setOfNotNull(
          MdnApiStatus.Experimental.takeIf { statuses.all { it.experimental } },
          MdnApiStatus.StandardTrack.takeIf { statuses.any { it.standardTrack } },
          MdnApiStatus.Deprecated.takeIf { statuses.all { it.deprecated } },
        )
      }
      .takeIf { it.isNotEmpty() }

  private fun extractCompatibilityInfo(contents: DocContents): CompatibilityMap? =
    contents.getBcdMap()
      .mapNotNull { (id, data) ->
        data.compat
          ?.support
          ?.additionalProperties
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
            @Suppress("UNCHECKED_CAST")
            val sortedMap = it.toMap(TreeMap()) as MutableMap<MdnJavaScriptRuntime, String>
            val bcdId = if (id != defaultBcdContext) getBcdId(id) else id
            if (bcdId != defaultBcdContext) {
              val description = data.compat.description
              if (description != null) {
                bcdIdDescriptions.putIfAbsent(bcdId, description)
              }
            }
            else {
              contents.baselineBrowsers?.let { baselineBrowsers -> sortedMap.putAll(baselineBrowsers) }
            }

            Pair(bcdId, sortedMap)
          }
      }
      .toMap(TreeMap())
      .takeIf { it.isNotEmpty() }
      ?.let { compatMap ->
        if (compatMap.size > 1) {
          val firstValue = compatMap.values.first()
          if (compatMap.values.all { it == firstValue })
            mapOf(defaultBcdContext to firstValue)
          else compatMap
        }
        else compatMap
      }

  private fun getBcdId(id: String): String =
    id.takeLastWhile { it != '.' }.let {
      if (it.endsWith("_context"))
        it
      else
        "support_of_$it"
    }

  private fun extractBrowserVersion(runtime: MdnJavaScriptRuntime, versionInfo: SupportStatement): String? {
    fun extractVersion(version: Any?): String? =
      when (version) {
        is Boolean -> {
          if (version) "" else null
        }
        is String -> {
          when {
            version.isEmpty() -> null
            version == runtime.firstVersion -> ""
            else -> version.removePrefix("≤")
          }
        }
        else -> null
      }


    @Suppress("UNCHECKED_CAST")
    val versions = when (val value = versionInfo.value) {
      is SimpleSupportStatement -> listOf(value)
      is List<*> -> value as List<SimpleSupportStatement>
      is String -> listOf(SimpleSupportStatement().also { it.versionAdded = VersionAdded().also { v -> v.value = value } })
      else -> emptyList()
    }
      .asSequence()
      .filter { it.prefix.isNullOrEmpty() && it.flags.isEmpty() && it.alternativeName.isNullOrEmpty() && it.partialImplementation == null }
      .map {
        Triple(extractVersion(it.versionAdded?.value), extractVersion(it.versionRemoved?.value), !it.notes.isNullOrEmpty())
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
      .ifEmpty { throw IllegalStateException("Uninitialized JS indexes.") }
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

  private fun File.toMdnUrl(): String =
    toString().removePrefix(getMdnDir("").toString())

  private fun updateBcdDescriptions() {
    val bundleFile = Path.of(PathManager.getCommunityHomePath(), BUNDLE_OUTPUT_DIR, "MdnBundle.properties").toFile()
    val contents = FileUtil.loadLines(bundleFile)

    val existingProperties = contents.asSequence()
      .filter { it.startsWith("mdn.documentation.section.compat.") }
      .map { it.substring("mdn.documentation.section.compat.".length).substringBefore("=") }
      .toSet()

    bcdIdDescriptions
      .filter { it.key !in existingProperties }
      .ifEmpty { return }
      .forEach { (id, description) ->
        contents.add("mdn.documentation.section.compat.$id=$description")
      }

    FileUtil.writeToFile(bundleFile, contents.joinToString("\n"));
  }

  private inner class CssElementInfo(docDir: File) {

    val name: String

    val hasDataType: Boolean get() = name.startsWith("<")

    init {
      val dirName = docDir.name
      if (dirName.startsWith("_colon_")
          || dirName.startsWith("_doublecolon_")
          || dirName.startsWith("@")
          || dirName.endsWith("()")
          || dirName.endsWith("_function")
      ) {
        name = if (dirName.endsWith("_function")) {
          dirName.removeSuffix("_function") + "()"
        }
        else dirName
      }
      else {
        val doc = parseIndexJson(docDir).getAsJsonObject("doc")
        val title = doc.getAsJsonPrimitive("title").asString
        val possiblyType = doc.getAsJsonArray("browserCompat")?.any { it.asString.startsWith("css.types.") } == true
        if (title.endsWith("()") || (title.startsWith("<") && title.endsWith(">"))) {
          name = title.lowercase(Locale.US)
        }
        else if (possiblyType) {
          name = "<$dirName>"
        }
        else {
          name = dirName
        }
      }
    }
  }

  private inner class DocContents(dir: File?, val compatData: Identifier?, bcdPath: String? = null) {

    val prose: List<JsonObject>
    val browserCompatData: List<String>
    val baseline: BaselineData?
    val baselineBrowsers: Map<MdnJavaScriptRuntime, String>?

    fun getBcdMap(): Map<String, Identifier> =
      browserCompatData
        .mapNotNull { id -> bcd.tryResolve(id)?.let { Pair(id, it) } }
        .let { data ->
          if (data.size == 1) {
            val entry = data[0].second
            val contexts = entry.additionalProperties.entries
              .filter { it.key.endsWith("_context") }
            if (contexts.isNotEmpty()) {
              contexts.map { Pair(it.key, it.value) }
            }
            else {
              listOf(defaultBcdContext to entry)
            }
          }
          else data
        }
        .ifEmpty { compatData?.let { listOf(Pair(defaultBcdContext, it)) } ?: emptyList() }
        .toMap()

    init {
      var baselineJson: JsonObject? = null
      if (dir != null) {
        val doc = parseIndexJson(dir)
          .getAsJsonObject("doc")
        prose = doc
          .getAsJsonArray("body")!!
          .map { it.asJsonObject }
          .filter { it.getAsJsonPrimitive("type").asString == "prose" }
          .map { it.getAsJsonObject("value") }
          .toList()
        browserCompatData = doc.getAsJsonArray("browserCompat")
                              ?.map { it.asString } ?: emptyList()
        if (doc.has("baseline"))
          baselineJson = doc.getAsJsonObject("baseline")
      }
      else {
        prose = emptyList()
        browserCompatData = emptyList()
      }
      if (bcdPath != null) {
        baselineJson = BaseLineService.computeBaseline(bcdPath)
      }
      baseline = if (baselineJson != null) {
        BaselineData(
          baselineJson.getAsJsonPrimitive("baseline").let {
            when {
              it.isBoolean -> BaselineLevel.NONE
              it.asString == "low" -> BaselineLevel.LOW
              it.asString == "high" -> BaselineLevel.HIGH
              else -> throw IllegalStateException(it.toString())
            }
          },
          lowDate = baselineJson.getAsJsonPrimitive("baseline_low_date")?.asString,
          highDate = baselineJson.getAsJsonPrimitive("baseline_high_date")?.asString,
        )
      }
      else null

      baselineBrowsers = baselineJson?.getAsJsonObject("support")?.entrySet()
        ?.mapNotNull { entry ->
          jsRuntimesMap[entry.key]?.let { runtime ->
            Pair(runtime, entry.value.asString.removePrefix("≤"))
          }
        }
        ?.toMap()
    }

  }
}

private fun <T : MdnDocumentation, K : MdnRawSymbolDocumentation> makeMissingSymbolsObsolete(
  newData: T,
  currentData: T,
  obsoleteData: T,
  mapGetter: (T) -> Map<String, K>,
  copyWithObsoleteStatus: (K) -> K,
) {
  makeMissingSymbolsObsolete<T, K, MdnRawSymbolDocumentation>(newData, currentData, obsoleteData, mapGetter, copyWithObsoleteStatus, null, null, null)
}

private fun <T : MdnDocumentation, K : MdnRawSymbolDocumentation, L : MdnRawSymbolDocumentation> makeMissingSymbolsObsolete(
  newData: T,
  currentData: T,
  obsoleteData: T,
  mapGetter: (T) -> Map<String, K>,
  copyWithObsoleteStatus: (K) -> K,
  subMapGetter: ((K) -> Map<String, L>?)?,
  symbolCreator: ((String) -> K)?,
  subCopyWithObsoleteStatus: ((L) -> L)?,
) {

  val newMap = mapGetter(newData)
  val currentMap = mapGetter(currentData)
  val obsoleteMap = mapGetter(obsoleteData) as MutableMap<String, K>

  currentMap.entries.forEach { (key, value) ->
    if (!newMap.contains(key)) {
      obsoleteMap.put(key, copyWithObsoleteStatus(value))
    }
    else if (subMapGetter != null) {
      val newSubMap = subMapGetter(newMap[key]!!)
      val currentSubMap = subMapGetter(value)
      val obsoleteSubMap by lazy {
        subMapGetter(obsoleteMap.computeIfAbsent(key, symbolCreator!!)) as MutableMap<String, L>
      }

      currentSubMap?.entries?.forEach {
        if (newSubMap == null || !newSubMap.contains(it.key)) {
          obsoleteSubMap.put(it.key, subCopyWithObsoleteStatus!!(it.value))
        }
      }
    }
  }
  obsoleteMap.entries.removeIf { newMap.containsKey(it.key) && it.value.doc != "" }
  if (subMapGetter != null) {
    obsoleteMap.entries.forEach { entry ->
      val newSubMap = subMapGetter(newMap[entry.key] ?: return@forEach) ?: return@forEach
      (subMapGetter(entry.value) as? MutableMap<String, L>)?.entries?.removeIf { newSubMap.contains(it.key) }
    }
  }
}

private fun String?.extractSvgAttributesSections(): Pair<String?, Map<String, String>?> {
  if (this == null) return Pair(null, null)
  val match = svgAttributeSectionsPattern.matchEntire(this)
              ?: return Pair(this, null)
  return Pair(
    match.groupValues[1].trim().removeSuffix("<br>").removeSuffix("<p>").trim(),
    mapOf(
      "Value type" to match.groupValues[2].trim().removeSuffix(".").removeSuffix(";").trim(),
      "Default" to match.groupValues[3].trim().removeSuffix(".").removeSuffix(";").trim(),
      "Animatable" to match.groupValues[4].trim().removeSuffix(".").removeSuffix(";").trim(),
    )
  )
}

private fun Map<String, MdnJsSymbolDocumentation>.resolveReferences(): Map<String, MdnJsSymbolDocumentation> =
  mapValues { (_, value) ->
    value.resolveReferences(this)
  }

private fun MdnJsSymbolDocumentation.resolveReferences(symbols: Map<String, MdnJsSymbolDocumentation>): MdnJsSymbolDocumentation {
  val newParameters = parameters?.get(SEE_REFERENCE)?.let { ref -> symbols[ref.takeLastWhile { it != '/' }] }?.parameters
  val newThrows = throws?.get(SEE_REFERENCE)?.let { ref -> symbols[ref.takeLastWhile { it != '/' }] }?.throws
  return if (newParameters != null || newThrows != null)
    copy(parameters = newParameters ?: parameters, throws = newThrows ?: throws)
  else
    this
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

private fun RawProse.appendOtherSections(indexDataProseValues: List<JsonObject>): RawProse {
  val try_it_section = indexDataProseValues.getProseContentById("try_it")?.content
  val description_section = indexDataProseValues.getProseContentById("description")?.content
  if (try_it_section == null && description_section == null) return this
  return RawProse(
    listOf(
      content,
      if (try_it_section != null) {
        val iframeEnd = try_it_section.indexOf("</iframe>")
                          .takeIf { it > 0 }?.let { it + "</iframe>".length }
                        ?: try_it_section.indexOf("</interactive-example>")
                          .takeIf { it > 0 }
                          ?.let { try_it_section.lastIndexOf("</code></pre></div>") + "</code></pre></div>".length }
                          ?.takeIf { it > 0 }
        if (iframeEnd == null) throw RuntimeException("Cannot find iframe or interactive-example end - $try_it_section")
        try_it_section.substring(iframeEnd).trim()
      }
      else "",
      description_section?.trim() ?: ""
    )
      .filter { it.isNotEmpty() }
      .joinToString("\n")
  )
}

private fun List<JsonObject>.filterProseById(vararg ids: String): Sequence<JsonObject> =
  asSequence().filter { value ->
    value.get("id")
      .takeIf { it is JsonPrimitive }
      ?.asString?.lowercase(Locale.US)?.let { id ->
        ids.any { id == it }
      } == true
  }

private fun List<JsonObject>.getProseContentById(id: String) =
  filterProseById(id).firstOrNull()?.getProseContent()

private fun String.patchProse(): String =
  replace("/en-US/docs", MDN_DOCS_URL_PREFIX, true)
    .replace("&apos;", "'")
    .replace("&quot;", "\"")
    .replace("&nbsp;", " ")
    .replace("&ldquo", "“")
    .replace("&rdquo", "”")
    .replace("&hellip", "…")
    .replace(Regex("<p>\\s+"), "<p>")
    .replace(Regex("(^<p>\\s*)|(\\s*</p>)|(\\s*<img[^>]*>)|(\\s*<figure\\s*class=\"table-container\">\\s*</figure>\\s*)"), "")
    .also { fixedProse ->
      Regex("&(?!lt|gt|amp|shy)[a-z]*;").find(fixedProse)?.let {
        throw Exception("Unknown entity found in prose: ${it.value}")
      }
    }

private class CompatibilityMapSerializer(private val sortEntries: Boolean) : JsonSerializer<Map<*, *>> {
  override fun serialize(src: Map<*, *>, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
    if (src.size == 1 && src.containsKey(defaultBcdContext)) {
      context.serialize(src[defaultBcdContext])
    }
    else if (src.size == 1 && src.containsKey("default_context")) {
      context.serialize(src["default_context"])
    }
    else {
      val result = JsonObject()
      src.entries
        .let { set ->
          if (sortEntries && set.firstOrNull()?.value !is String)
            set.sortedBy { it.key.toString() }
          else
            set
        }
        .forEach { (key, value) ->
          result.add(key.toString(), context.serialize(value))
        }
      result
    }

}