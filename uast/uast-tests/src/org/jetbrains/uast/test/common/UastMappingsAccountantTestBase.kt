// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.uast.test.common

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileNameMatcher
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMultiResolvable
import org.jetbrains.uast.UResolvable
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.Comparator
import kotlin.math.min


@Suppress("FunctionName")
interface UastMappingsAccountantTestBase {
  fun `test compute mappings by PSI element and print as trees oriented as child to parent`()
  fun `test compute mappings by PSI element and print as trees oriented as parent to child`()
  fun `test compute mappings by PSI element and print as lists oriented as child to parent`()
  fun `test compute mappings by PSI element and print as lists oriented as parent to child`()

  fun `test compute mappings by UAST element and print as trees oriented as child to parent`()
  fun `test compute mappings by UAST element and print as trees oriented as parent to child`()
  fun `test compute mappings by UAST element and print as lists oriented as child to parent`()
  fun `test compute mappings by UAST element and print as lists oriented as parent to child`()
  fun `test compute mappings by UAST element and print as a priory lists of PSI elements`()
}

@Suppress("FunctionName")
interface UastMappingsAccountantSingleTestBase {
  fun `test compute all mappings and print in all variations`()
}

typealias RenderedContext = Iterable<String>
typealias RenderedMappings = Map3<String, RenderedContext, String, Iterable<PairWithFirstIdentity<String?, Location>>>

data class AllRenderedUastMappings(val byPsiElement: RenderedMappings, val byUElement: RenderedMappings)

/**
 * Computes Psi to Uast, Uast from Psi mappings and prints them as .html file
 *
 * This class is supposed to be used as a delegate.
 */
class UastMappingsAccountantTest(
  private val sources: Iterable<Lazy<Pair<PsiFile, Path>?>>,
  private val storeResultsTo: Path,
  private val resultsNamePrefix: String,
  private val psiClassPrinter: (PsiClazz?) -> String = PsiClassToString.asClosestInterface,
  private val uastClassPrinter: (UastClazz?) -> String = UastClassToString.asUastInterface,
  private val cache: Cache? = AllInstancesCache,
  private val doInParallel: Boolean = false,
  private val logger: Logger? = null
) : UastMappingsAccountantTestBase, UastMappingsAccountantSingleTestBase {

  //region Caching mappings across different tests
  companion object {
    interface Cache {
      fun UastMappingsAccountantTest.computeOrGetMappings(): Pair<
        UastMappingsRepository<PsiClazz, PairWithFirstIdentity<UastClazz?, Location>>,
        UastMappingsRepository<UastClazz, Location>
        >

      fun UastMappingsAccountantTest.computeOrGetRenderedMappings(): AllRenderedUastMappings
    }

    object AllInstancesCache : Cache {
      private val oneOfUs = AtomicReference<UastMappingsAccountantTest?>(null)

      private val ourComputedMappings by lazy { oneOfUs.get()!!.computeMappings() }
      private val ourComputedAndRenderedMappings by lazy { oneOfUs.get()!!.renderMappings(ourComputedMappings) }

      override fun UastMappingsAccountantTest.computeOrGetMappings() =
        oneOfUs.compareAndSet(null, this).let { ourComputedMappings }

      override fun UastMappingsAccountantTest.computeOrGetRenderedMappings() =
        oneOfUs.compareAndSet(null, this).let { ourComputedAndRenderedMappings }
    }
  }
  //endregion

  //region Mappings computation and rendering
  fun computeMappings(): Pair<
    UastMappingsRepository<PsiClazz, PairWithFirstIdentity<UastClazz?, Location>>,
    UastMappingsRepository<UastClazz, Location>
    > =
    when (doInParallel) {
      false -> UastMappingsAccountant.computeMappingsByPsiElementsAndUElements(sources, logger)
      true -> {
        val sourcesList = sources.toList()
        val nCores = Runtime.getRuntime().availableProcessors()
        val batchSize = sourcesList.size / nCores
        val batches = List(nCores) { i ->
          sourcesList.subList(i * batchSize, min((i + 1) * batchSize, sourcesList.size))
        }

        val byPsi = mutableUastMappingsRepository<PsiClazz, PairWithFirstIdentity<UastClazz?, Location>>()
        val byUast = mutableUastMappingsRepository<UastClazz, Location>()

        batches
          .map {
            ApplicationManager.getApplication().executeOnPooledThread(Callable {
              UastMappingsAccountant.computeMappingsByPsiElementsAndUElements(it, logger)
            })
          }
          .map { it.get() }
          .forEach {
            byPsi.mergeMap3(it.first) { curr, (_, new) -> (curr ?: mutableSetOf()).apply { addAll(new) } }
            byUast.mergeMap3(it.second) { curr, (_, new) -> (curr ?: mutableSetOf()).apply { addAll(new) } }
          }

        Pair(byPsi, byUast)
      }
    }

  /**
   * Stringifies computed mappings by [psiClassPrinter] and [uastClassPrinter]
   * and performs folding of mappings groups with equal targets replacing them
   * with "ALWAYS" and "ANY_CONTEXT" abbreviations.
   */
  fun renderMappings(
    mappings: Pair<
      UastMappingsRepository<PsiClazz, PairWithFirstIdentity<UastClazz?, Location>>,
      UastMappingsRepository<UastClazz, Location>
      >
  ): AllRenderedUastMappings =
    mappings
      // filters vast amount of nulls in conversions
      .let { (byPsi, byUast) ->
        Pair(
          byPsi.mapValues { (_, contextMap) ->
            contextMap.filterInner { _, type, targets ->
              val targetsList = targets.toList()
              !(targetsList.size == 1 && targetsList.single().first == null && type != UElement::class.java)
            }
          },
          byUast
        )
      }
      // renders everything except locations
      .let { (byPsi, byUast) ->
        Pair<MutableMap3<String, List<String>, String, MutableSet<PairWithFirstIdentity<String?, Location>>>, MutableMap3<String, Iterable<String>, String, MutableSet<PairWithFirstIdentity<String?, Location>>>>(
          byPsi.transformMap3(
            psiClassPrinter,
            { psiContext -> psiContext.map { psiClassPrinter(it) } },
            UastClassToString.asIs,
            { it.mapTo(mutableSetOf()) { (uClazz, loc) -> PairWithFirstIdentity(uastClassPrinter(uClazz) as String?, loc) } },
            { prev: MutableSet<PairWithFirstIdentity<String?, Location>>?, new ->
              (prev ?: mutableSetOf()).apply { addAll(new) }
            }
          ),

          byUast.transformMap3(
            uastClassPrinter,
            { psiContext -> psiContext.map { psiClassPrinter(it) }.asIterable() },
            UastClassToString.asIs,
            { it.mapTo(mutableSetOf()) { loc -> PairWithFirstIdentity(null as String?, loc) } },
            { prev: MutableSet<PairWithFirstIdentity<String?, Location>>?, new -> (prev ?: mutableSetOf()).apply { addAll(new) } }
          )
        )
      }
      // folds regions with equal targets
      .let { (byPsiWithNotFullyRenderedTargets, byUast) ->
        val byPsiInAnyContext = byPsiWithNotFullyRenderedTargets.mapValues { it.value.values.toSet() }

        @Suppress("UNCHECKED_CAST")
        AllRenderedUastMappings(
          byPsiWithNotFullyRenderedTargets
            .mapValues { (psiClass, innerMap) ->
              innerMap.foldKeysByLevel(level = 0, keyBuilder = { it }, keyFolder = {
                when (byPsiInAnyContext.getValue(psiClass).size) {
                  1 -> listOf("ALWAYS")
                  else -> null
                }
              })
            }
            .mapValues { (psiClass, innerMap) ->
              innerMap.foldKeysByLevel(level = 1, keyBuilder = { it }, keyFolder = kf@{ context ->
                if (context.isEmpty())
                  return@kf null
                val allTargetsWithSameFirstContextElement = byPsiWithNotFullyRenderedTargets
                  .getValue(psiClass)
                  .entries.mapNotNullTo(mutableSetOf()) { (key, value) ->
                    key.firstOrNull()?.run { if (equals(context[0])) value else null }
                  }
                when (allTargetsWithSameFirstContextElement.size) {
                  1 -> listOf("ANY_CONTEXT")
                  else -> null
                }
              })
            } as RenderedMappings,

          byUast.mapValues { (uastClass, innerMap) ->
            innerMap.foldKeysByLevel(level = 1, keyBuilder = { it }, keyFolder = kf@{ context ->
              if (context.isEmpty())
                return@kf null
              when (byPsiInAnyContext
                .getValue(context[0]).singleOrNull()
                ?.mapTo(mutableSetOf()) { it.value }?.singleOrNull()
                ?.singleOrNull()
                ?.first?.equals(uastClass)
              ) {
                true -> listOf("ANY_CONTEXT")
                else -> null
              }
            })
          }
        )
      }

  private val computedMappings
    get() =
      cache?.let { with(it) { this@UastMappingsAccountantTest.computeOrGetMappings() } }
      ?: computeMappings()

  private val computedAndRenderedMappings
    get() =
      cache?.let { with(it) { this@UastMappingsAccountantTest.computeOrGetRenderedMappings() } }
      ?: renderMappings(computeMappings())
  //endregion

  //region Mappings pretty-printing
  private inline fun into(fileName: String, init: StringBuilder.() -> Unit) =
    File(storeResultsTo.toFile(), "$resultsNamePrefix-$fileName").writeText(buildString { init() })

  private fun span(cssClass: String, body: String) = """<span class="$cssClass">$body</span>"""

  private fun detailsBegin(open: Boolean = false, cssClass: String) = """<details class="$cssClass" ${if (open) "open" else ""}>"""
  private fun detailsEnd() = """</details>"""

  private fun summary(body: String) = """<summary>$body</summary>"""

  private fun RenderedMappings.print(
    fileName: String,
    margin: CharSequence = "    ",
    printTargetsOnNewLine: Boolean = false,
    contextPrinter: Appendable.(Map<Iterable<String>, Map<String, Iterable<PairWithFirstIdentity<String?, Location>>>>,
                                Appendable.(Iterable<String>, Map<String, Iterable<PairWithFirstIdentity<String?, Location>>>) -> Unit) -> Unit
  ) {
    into(fileName) sb@{

      append(
        """
          <!DOCTYPE html>
          <html>

          <head>
            <meta charset="utf-8">
            <title>mappings</title>
            <script>
              function toggleSpoilers() {
                [].forEach.call(document.getElementsByClassName('details_marker'), e => {
                  if (e.hasAttribute('open')) {
                    e.removeAttribute('open');
                  } else {
                    e.setAttribute('open', '');
                  }
                })
              }
            </script>
            <style>
              body {
                font-family: monospace;
                width: fit-content;
              }
              button {
                color: white;
                background: #4C8FFB;
                border: 1px #3079ED solid;
                box-shadow: inset 0 1px 0 #80B0FB;
                padding: 5px 10px;
                border-radius: 2px;
                font-weight: bold;
                font-size: 10pt;
                outline: none;
              }
              button:hover {
                border: 1px #2F5BB7 solid;
                box-shadow: 0 1px 1px #EAEAEA, inset 0 1px 0 #5A94F1;
                background: #3F83F1;
              }
              .toggle_spoilers {
                ;
              }
              .details_marker {
                display: block;
                width: 100%;
                border: 2px solid #f1f1f1;
              }
              details summary {
                outline-style: none;
                cursor: pointer;
              }
              .source {
                font-weight: bold;
              }
              .overview {
                color: black;
              }
              .location {
                color: silver;
              }
              .context {
                color: blueviolet;
              }
              .type {
                color: seagreen;
              }
              .targets {
                color: #000066;
              }
            </style>
          </head>

          <body>
            <button class="toggle_spoilers" onclick="toggleSpoilers()">Toggle Spoilers</button>
          <pre>
        """.trimIndent()
      )

      printMap(this@print, separator = "\n\n", keyComparator = Comparator.naturalOrder()) { source, contextMap ->
        append(detailsBegin(cssClass = "details_marker"))
        append(summary(span("source", "$source:")))

        val allPossibleTargets = contextMap.values
          .flatMap { typeToTargets -> typeToTargets.values.flatMap { targets -> targets.map { it.first ?: "null" } } }
          .distinct().sorted()
        val allPossibleTypes = contextMap.values.flatMap { it.keys }.distinct().sorted()
        var overviewWasPrinted = false

        withMargin(margin) m@{

          if (allPossibleTargets.size > 1) {
            overviewWasPrinted = true
            append(span("overview", "    All possible targets:    "))
            allPossibleTargets.joinTo(this) { span("targets", it) }
            append("\n")
          }

          if (contextMap.keys.size > 1) {
            overviewWasPrinted = true
            append(span("overview", "    All possible types  :    "))
            allPossibleTypes.joinTo(this) { span("type", it) }
            append("\n")
          }

          if (overviewWasPrinted) append("\n")

          this@m.contextPrinter(contextMap) { _, typeMap ->

            val typeMapWithTypesFlattened = typeMap.run {
              val distinctTargets = values.distinct()
              if (distinctTargets.size == 1) {
                val newKey = keys.sorted().joinToString(separator = " | ")
                mapOf(newKey to distinctTargets.single())
              }
              else this
            }

            withMargin(marginOf(currentLineLengthIgnoringTags - margin.length - 1),
                       withTrailingNewLine = printTargetsOnNewLine) m2@{
              this@m2.printMap(typeMapWithTypesFlattened, keyComparator = Comparator.naturalOrder()) { type, targets ->

                append(span("type", "$margin| ${"%-30s".format(type)} -> "))
                targets
                  .sortedBy { it.first }
                  .joinTo(this@sb, separator = "\n${marginOf(currentLineLengthIgnoringTags - 2)}, ") { (target, location) ->
                    val renderedTarget = target?.let { span("targets", "%-30s".format(it)) } ?: ""
                    renderedTarget + span("location", " in $location")
                  }
              }
            }
          }
        }
        append(detailsEnd())
      }

      append(
        """
          </pre>

          </body>
          </html>
        """.trimIndent()
      )

    }
  }

  private fun RenderedMappings.printAsAlignedLists(
    fileName: String,
    contextMargin: CharSequence = "    ",
    contextAlignmentByRight: Boolean = false,
    printTargetsOnNewLine: Boolean = false,
    contextOrdering: (RenderedContext) -> List<String>
  ) {
    print(fileName, contextMargin, printTargetsOnNewLine) { contextMap, typeMapPrinter ->
      printMapAsAlignedLists(
        contextMap, lexicographicalOrder<String>(),
        prefix = """<span class="context">[""",
        postfix = """]</span>""",
        alignByRight = contextAlignmentByRight,
        keyToList = contextOrdering, valuePrinter = typeMapPrinter
      )
    }
  }

  private fun RenderedMappings.printAsAlignedTrees(
    fileName: String,
    contextMargin: CharSequence = "    ",
    treeMargin: CharSequence = "            ",
    printTargetsOnNewLine: Boolean = false,
    contextOrdering: (RenderedContext) -> List<String>
  ) {
    print(fileName, contextMargin, printTargetsOnNewLine) { contextMap, typeMapPrinter ->
      printMapAsAlignedTrees(
        contextMap, treeMargin, keyToList = contextOrdering,
        keyPrinter = { key, padding -> append(span("context", "| %-${padding}s".format(key))) },
        valuePrinter = typeMapPrinter)
    }
  }

  private val childToParentOrder: (RenderedContext) -> List<String> = { context -> context.toList() }
  private val parentToChildOrder: (RenderedContext) -> List<String> = { context -> context.toList().asReversed() }

  private val StringBuilder.currentLineLengthIgnoringTags
    get(): Int {
      var r = 0
      var inTag = false
      for (i in lastIndexOf("\n") until length) {
        when (get(i)) {
          '<' -> inTag = true
          '>' -> inTag = false
          else -> if (!inTag) r++
        }
      }
      return r
    }

  private fun marginOf(length: Int): String {
    val tmp = CharArray(length)
    Arrays.fill(tmp, ' ')
    return String(tmp)
  }
  //endregion

  //region Tests
  override fun `test compute mappings by PSI element and print as trees oriented as child to parent`() {
    computedAndRenderedMappings.byPsiElement.printAsAlignedTrees(
      fileName = "by-psi-as-trees-child-to-parent.html",
      contextOrdering = childToParentOrder)
  }

  override fun `test compute mappings by PSI element and print as trees oriented as parent to child`() {
    computedAndRenderedMappings.byPsiElement.printAsAlignedTrees(
      fileName = "by-psi-as-trees-parent-to-child.html",
      contextOrdering = parentToChildOrder)
  }

  override fun `test compute mappings by PSI element and print as lists oriented as child to parent`() {
    computedAndRenderedMappings.byPsiElement.printAsAlignedLists(
      fileName = "by-psi-as-lists-child-to-parent.html",
      contextOrdering = childToParentOrder)
  }

  override fun `test compute mappings by PSI element and print as lists oriented as parent to child`() {
    computedAndRenderedMappings.byPsiElement.printAsAlignedLists(
      fileName = "by-psi-as-lists-parent-to-child.html",
      contextOrdering = parentToChildOrder,
      contextAlignmentByRight = true)
  }

  override fun `test compute mappings by UAST element and print as trees oriented as child to parent`() {
    computedAndRenderedMappings.byUElement.printAsAlignedTrees(
      fileName = "by-uast-as-trees-child-to-parent.html",
      contextOrdering = childToParentOrder)
  }

  override fun `test compute mappings by UAST element and print as trees oriented as parent to child`() {
    computedAndRenderedMappings.byUElement.printAsAlignedTrees(
      fileName = "by-uast-as-trees-parent-to-child.html",
      contextOrdering = parentToChildOrder)
  }

  override fun `test compute mappings by UAST element and print as lists oriented as child to parent`() {
    computedAndRenderedMappings.byUElement.printAsAlignedLists(
      fileName = "by-uast-as-lists-child-to-parent.html",
      contextOrdering = childToParentOrder)
  }

  override fun `test compute mappings by UAST element and print as lists oriented as parent to child`() {
    computedAndRenderedMappings.byUElement.printAsAlignedLists(
      fileName = "by-uast-as-lists-parent-to-child.html",
      contextOrdering = parentToChildOrder,
      contextAlignmentByRight = true)
  }

  override fun `test compute mappings by UAST element and print as a priory lists of PSI elements`() {
    val mappings = allUElementSubtypes.associateTo(mutableMapOf()) { Pair(UastClassToString.asIs(it), TreeSet<String>()) }
    for ((uElement, mappingCases) in computedMappings.second) {
      val psiSources = mappingCases.keys.map { it.first() }
      uElement.getImplementedUElementInterfaces().forEach { uInterface ->
        mappings.compute(UastClassToString.asIs(uInterface)) { _, old ->
          if (old == null) {
            logger?.warn("$uInterface is missed from ${allUElementSubtypes.javaClass.packageName}.allUElementSubtypes")
            TreeSet()
          }
          else old
        }!!
          .addAll(psiSources.asSequence().map { psiClassPrinter(it) })
      }
    }

    into("by-uast-as-a-priory-lists-of-PSI-elements.kt") {
      append(
        """
          |import org.jetbrains.uast.*
          |import org.jetbrains.uast.expressions.UInjectionHost
          |import org.jetbrains.uast.internal.ClassSet
          |import org.jetbrains.uast.psi.UElementWithLocation
          |
          |object UastAPrioryPsiLists {
          |  val possibleSourceTypes = mapOf<Class<out UElement>, ClassSet>(
          |""".trimMargin()
      )

      mappings.entries
        .sortedWith(compareBy { it.key })
        .joinTo(this, separator = ",\n") { (uTarget, psiSources) ->
          append("    ${uTarget}::class.java to ClassSet(\n")
          psiSources.joinTo(this, separator = ",\n") { "      $it::class.java" }
          append("\n    )")
          ""
        }

      append(
        """
          |
          |  )
          |}
          |""".trimMargin()
      )
    }
  }

  override fun `test compute all mappings and print in all variations`() {
    `test compute mappings by PSI element and print as lists oriented as child to parent`()
    `test compute mappings by PSI element and print as lists oriented as parent to child`()
    `test compute mappings by PSI element and print as trees oriented as child to parent`()
    `test compute mappings by PSI element and print as trees oriented as parent to child`()
    `test compute mappings by UAST element and print as a priory lists of PSI elements`()
    `test compute mappings by UAST element and print as lists oriented as child to parent`()
    `test compute mappings by UAST element and print as lists oriented as parent to child`()
    `test compute mappings by UAST element and print as trees oriented as child to parent`()
    `test compute mappings by UAST element and print as trees oriented as parent to child`()
  }
  //endregion

}


/* ------------------------------------------------------------------------------------------- */
//region Sources grabbers

fun sourcesFromDirRecursive(dir: File, fileMatcher: FileNameMatcher, fixture: JavaCodeInsightTestFixture) =
  dir
    .walkTopDown()
    .filter { fileMatcher.acceptsCharSequence(it.name) }
    .map { source ->
      lazy {
        val testFile = source.relativeTo(dir)
        Pair(fixture.configureByFile(testFile.path), source.toPath())
      }
    }

fun sourcesFromLargeProject(fileType: FileType, project: Project, sourcesToProcessLimit: Int = Int.MAX_VALUE / 2, logger: Logger?) =
  runReadAction {
    val files = FileTypeIndex.getFiles(fileType, ProjectScope.getProjectScope(project))
    val actualSourcesAmount = min(files.size, sourcesToProcessLimit)

    val totalProcessed = AtomicInteger(0)
    files.asSequence()
      .take(sourcesToProcessLimit)
      .map { source ->
        lazy(LazyThreadSafetyMode.NONE) {
          val total = totalProcessed.incrementAndGet()
          if (total % 500 == 0) {
            logger?.warn("Total processed: $total (${"%.2f".format(total * 100.0 / actualSourcesAmount)}%)")
          }

          try {
            val path = source.toNioPath()
            PsiManager.getInstance(project).findFile(source)
              ?.let { Pair(it, Paths.get(project.basePath!!).relativize(path)) }
          }
          catch (_: UnsupportedOperationException) {
            null
          }
        }
      }
  }

//endregion
/* ------------------------------------------------------------------------------------------- */


/* ------------------------------------------------------------------------------------------- */
//region Utils

private fun <T : Comparable<T>> lexicographicalOrder(): Comparator<Iterable<T>> =
  Comparator comparator@{ p1, p2 ->
    val p1Iter = p1.iterator()
    val p2Iter = p2.iterator()
    while (p1Iter.hasNext() && p2Iter.hasNext()) {
      val t = p1Iter.next().compareTo(p2Iter.next())
      if (t != 0) return@comparator t
    }
    when (p1Iter.hasNext() to p2Iter.hasNext()) {
      Pair(true, true) -> 0
      Pair(true, false) -> 1
      Pair(false, true) -> -1
      Pair(false, false) -> 0
      else -> throw AssertionError("Impossible")
    }
  }

object UastClassToString {
  val asIs: UastClazz?.() -> String = { this?.simpleName ?: "null" }

  val asUastInterface: UastClazz?.() -> String = {
    this?.getImplementedUastInterfaces()
      ?.firstOrNull()
      ?.simpleName
    ?: "null"
  }
}

object PsiClassToString {
  val asIs: PsiClazz?.() -> String = { this?.simpleName ?: "null" }

  @Suppress("unused")
  val kotlinAsIs: PsiClazz?.() -> String = {
    if (this == null)
      "null"
    else {
      val sName = simpleName
      if (LightElement::class.java.isAssignableFrom(this) || sName.isBlank()) {
        if (sName.startsWith("Uast"))
          sName
        else {
          val baseClassesOfInterest = getBaseClassesAndInterfaces()
            .filter {
              val n = it.simpleName
              n.startsWith("KtLightAnnotation") ||
              n.startsWith("KtLightParameterList") ||
              n.startsWith("Fake")
            }

          val baseInterfacesOfInterest = getBaseClassesAndInterfaces()
            .filter { it.isInterface && it.simpleName.startsWith("KtLight") }

          (baseClassesOfInterest.firstOrNull() ?: baseInterfacesOfInterest.firstOrNull())?.simpleName ?: "PsiElement"
        }
      }
      else sName
    }
  }

  val asClosestInterface: PsiClazz?.() -> String = {
    generateSequence(this as Class<*>?) {
      when {
        it.isInterface -> null
        it.interfaces.isNotEmpty() -> it.interfaces.first()
        else -> it.superclass
      }
    }
      .take(3)
      .lastOrNull()
      ?.simpleName
    ?: "null"
  }
}

private fun Class<*>.getBaseClassesAndInterfaces(): Sequence<Class<*>> =
  sequence {
    val todo = java.util.ArrayDeque<Class<*>>(listOf(this@getBaseClassesAndInterfaces))
    while (todo.isNotEmpty()) {
      val head = todo.pollFirst()
      todo.addAll(head.interfaces.asList())
      head.superclass?.let { todo.add(it) }
      yield(head)
    }
  }

fun UastClazz.getImplementedUastInterfaces(): Sequence<Class<*>> =
  getBaseClassesAndInterfaces()
    .filter { clazz ->
      clazz.simpleName.startsWith("U") &&
      listOf(UElement::class.java, UResolvable::class.java, UMultiResolvable::class.java).any {
        it.isAssignableFrom(clazz)
      }
    }

@Suppress("UNCHECKED_CAST")
fun UastClazz.getImplementedUElementInterfaces(): Sequence<Class<out UElement>> =
  getImplementedUastInterfaces().filter { UElement::class.java.isAssignableFrom(it) }
    as Sequence<Class<out UElement>>

//endregion
/* ------------------------------------------------------------------------------------------- */