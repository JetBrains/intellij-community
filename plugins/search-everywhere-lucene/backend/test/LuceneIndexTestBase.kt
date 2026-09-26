// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhereLucene.backend

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.rules.ProjectModelExtension
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute
import org.apache.lucene.document.Document
import org.apache.lucene.document.TextField
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.util.Bits
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.UUID

@SystemProperty("idea.test.logs.echo.debug.to.stdout", "true")
abstract class LuceneIndexTestBase {
  @RegisterExtension
  protected val projectModel: ProjectModelExtension = ProjectModelExtension()

  protected val project: Project get() = projectModel.project

  abstract val log: Logger

  @BeforeEach
  fun setupLogging() {
    TestLoggerFactory.enableDebugLogging(projectModel.disposableRule.disposable,
                                         "#com.intellij.searchEverywhereLucene.backend",
                                         "#${this::class.java.name}")
    val rootLogger = java.util.logging.Logger.getLogger("")
    if (rootLogger.handlers.none { it is TestLoggerFactory.LogToStdoutJulHandler }) {
      val handler = TestLoggerFactory.LogToStdoutJulHandler()
      rootLogger.addHandler(handler)
      Disposer.register(projectModel.disposableRule.disposable) {
        rootLogger.removeHandler(handler)
      }
    }
  }


  inner class SearchFlowAssert(
    val index: LuceneIndex,
    val query: Query,
    val results: List<Pair<ScoreDoc, Document>>,
    val isEquivalent: (Document, Document) -> Boolean,
  ) {
    private fun log(message: String) {
      this@LuceneIndexTestBase.log.info("[DEBUG_LOG] $message")
    }

    fun explainResults(resultsToExplain: List<Pair<ScoreDoc, Document>> = results, limit: Int = 3): String {
      return index.withSearcher { searcher ->
        resultsToExplain.joinToString(
          separator = "\n\n",
          limit = limit,
          truncated = "... (total ${resultsToExplain.size})"
        ) { (score, doc) ->
          """
Document: ${doc.get("uri")}
Explanation:
${searcher.explain(query, score.doc).toString().trim().prependIndent(">   ")}
          """
        }.prependIndent("  ")
      }
    }

    private fun explainAllIndexedDocs(limit: Int = 5): String {
      return index.withSearcher { searcher ->
        val reader = searcher.indexReader
        val maxDoc = reader.maxDoc()
        val liveDocs: Bits? = reader.leaves().firstOrNull()?.reader()?.liveDocs
        (0 until minOf(maxDoc, limit))
          .filter { docId -> liveDocs == null || liveDocs.get(docId) }
          .joinToString(separator = "\n\n") { docId ->
            val doc = searcher.storedFields().document(docId)
            val explanation = searcher.explain(query, docId)
            "Doc $docId ${doc.get("uri")}\n" +
            "Score: ${explanation.value}\n" +
            explanation.toString().trim().prependIndent(">   ")
          }
          .let { it.ifEmpty { "(index is empty)" } }
          .prependIndent("  ")
      }
    }

    fun findsNothing() {
      log("finds Nothing")
      if (results.isNotEmpty()) {
        fail<Nothing>("Expected no results, but found ${results.size} documents:\n${explainResults(results)}")
      }
    }

    fun findsAllOf(vararg expectedDocs: Document) {
      val expectedDocsString: String =
        expectedDocs.joinToString("", limit = 3, truncated = "... (total ${expectedDocs.size})", transform = { it.get("uri").toString() })
      log("finds all of $expectedDocsString")
      for (expectedDoc in expectedDocs) {
        if (results.none { isEquivalent(it.second, expectedDoc) }) {
          fail<Nothing>("""
            Expected document not found in results.
            Query: $query
            Missing Document: ${expectedDoc.get("uri")}

            === All indexed docs (score explanation for query) ===
            ${explainAllIndexedDocs()}

            Total Results (${results.size}):
            ${explainResults(results)}
          """.trimIndent())
        }
      }
    }

    fun findsNoneOf(vararg expectedDocs: Document) {
      val expectedDocsString: String =
        expectedDocs.joinToString("", limit = 3, truncated = "... (total ${expectedDocs.size})", transform = { it.get("uri").toString() })
      log("finds none of $expectedDocsString")
      for (expectedDoc in expectedDocs) {
        val found = results.filter { isEquivalent(it.second, expectedDoc) }
        if (found.isNotEmpty()) {
          fail<Nothing>("""
            Found document that should NOT be in results.
            Query: $query
            
            ${explainResults(found)}
          """.trimIndent())
        }
      }
    }

    fun findsWithOrdering(expectedOrder: List<Document>, containsAll: Boolean = true) {
      val all = if (containsAll) " all" else ""
      val expectedDocsString: String =
        expectedOrder.joinToString("", limit = 3, truncated = "... (total ${expectedOrder.size})", transform = { it.get("uri").toString() })
      log("finds$all in order: $expectedDocsString")

      val foundDocs = expectedOrder.map { expected ->
        results.find { isEquivalent(it.second, expected) }
      }

      log("""
Found ${results.size} documents:
${explainResults(results)}
      """.trimIndent())

      if (containsAll) {
        expectedOrder.forEachIndexed { index, doc ->
          if (foundDocs[index] == null) {
            fail<Nothing>("""
              Document not found in results (required by containsAll=true):
              Missing Document: ${doc.get("uri")}
            """.trimIndent())
          }
        }
      }

      val presentFoundDocs = foundDocs.filterNotNull()
      val actualIndices = presentFoundDocs.map { found -> results.indexOf(found) }

      for (i in 0 until actualIndices.size - 1) {
        if (actualIndices[i] > actualIndices[i + 1]) {
          val doc1 = presentFoundDocs[i]
          val doc2 = presentFoundDocs[i + 1]
          val scoreDoc1 = doc1.first
          val scoreDoc2 = doc2.first

          val explanation1 = index.withSearcher { it.explain(query, scoreDoc1.doc) }
          val explanation2 = index.withSearcher { it.explain(query, scoreDoc2.doc) }

          val diff = scoreDoc2.score - scoreDoc1.score
          fail<Nothing>("""
            Wrong relative ordering:

            Expected:  ${doc1.second.get("uri")} > ${doc2.second.get("uri")} 
            Actual:    ${doc2.second.get("uri")} > ${doc1.second.get("uri")} 

            Doc 1: ${doc1.second.get("uri")}
            Score: ${scoreDoc1.score} ($diff LOWER than Doc 2)
            Explanation:
            ${explanation1.toString().prependIndent("  ")}

            Doc 2: ${doc2.second.get("uri")}
            Score: ${scoreDoc2.score} ($diff HIGHER than Doc 1)
            Explanation:
            ${explanation2.toString().prependIndent("  ")}
          """.trimIndent())
        }
      }
    }

  }

  private fun logIndexedDocuments(doc: Document): String = buildString {
    appendLine("Document:")
    for (field in doc.fields) {
      val value = field.stringValue() ?: continue
      appendLine("  - \"${field.name()}\": \"$value\"")
    }
    appendLine("  Tokenized to:")
    for (field in doc.fields) {
      if (!field.fieldType().tokenized()) continue
      val tField = field as TextField
      appendLine("    - \"${tField.name()}\": ${getTokensWithTypes(tField.tokenStreamValue()).joinToString()}")
    }
  }.trimEnd()


  private fun getTokensWithTypes(stream: TokenStream): List<String> {
    val termAttr = stream.addAttribute(CharTermAttribute::class.java)
    val result = mutableListOf<String>()
    stream.reset()
    while (stream.incrementToken()) {
      val term = termAttr.toString()
      result.add(term)
    }
    stream.end()
    stream.close()
    return result
  }

  open fun buildSimpleQuery(pattern: String): Query {
    throw UnsupportedOperationException("Override buildSimpleQuery or pass a buildQuery lambda to assertSearch")
  }

  open val isEquivalent: (Document, Document) -> Boolean = { d1, d2 -> d1.get("uri") == d2.get("uri") }

  private val dynamicNodes = mutableListOf<DynamicNode>()

  fun <T> LuceneIndex.assertSearch(
    input: T,
    buildQuery: (T) -> Query = { input -> buildSimpleQuery(input.toString()) },
    isEquivalent: (Document, Document) -> Boolean = this@LuceneIndexTestBase.isEquivalent,
    block: SearchFlowAssert.() -> Unit,
  ) {
    val query = buildQuery(input)
    dynamicNodes.add(dynamicTest("Searching for `$input`") {
      runBlocking {
        this@LuceneIndexTestBase.log.info("Searching for `$input` with query: $query ")
        val results = search(query).toList()
        SearchFlowAssert(this@assertSearch, query, results, isEquivalent).block()
      }
    })
  }


  fun indexWith(docs: List<Document>, block: suspend (LuceneIndex) -> Unit): List<DynamicNode> = runBlocking {
    val indexName = "test-index-${UUID.randomUUID()}"

    val luceneIndex = LuceneIndex(project, indexName, log)
    Disposer.register(projectModel.disposableRule.disposable, luceneIndex)

    log.info("Indexing ${docs.size} documents: \n ${
      docs.joinToString(limit = 2,
                        postfix = "\n",
                        prefix = "\n",
                        separator = "\n",
                        truncated = "... remaining Documents omitted",
                        transform = { logIndexedDocuments(it) })
    }")

    luceneIndex.processChanges { writer ->
      writer.deleteAll()
      for (document in docs) {
        writer.addDocument(document)
      }
    }

    dynamicNodes.clear()
    block(luceneIndex)
    dynamicNodes.toList()
  }
}
