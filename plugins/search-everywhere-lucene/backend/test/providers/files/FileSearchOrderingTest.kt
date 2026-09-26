package com.intellij.searchEverywhereLucene.backend.providers.files

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory

@TestApplication
class FileSearchOrderingTest : FileSearchTestBase() {

  fun prefixesOf(str: String) = (1..str.length).map { str.substring(0, it) }


  // TODO note that the "ZIA" test is currently "accidentally" succeeding because the scoring for filenameAbbreviationWithSkips is constant,
  //  while the scoring for filenameAbbreviation is LengthScoringPrefixQuery.
  @TestFactory
  fun `orderings around ZoomIdeAction and ZoomOutAction`() : List<DynamicNode> {

    val zeroValueAfterImports = file("goland/intellij-go/impl/testData/quickfixes/local-variable/zeroValueImports-after.go")
    val zoomIdeAction = file("community/platform/platform-impl/src/com/intellij/ide/actions/ZoomIdeAction.kt")
    val zoomOutAction = file("plugins/graph/srcOpenApi/com/intellij/openapi/graph/builder/actions/ZoomOutAction.java")


    return indexWith(listOf(zeroValueAfterImports, zoomIdeAction,zoomOutAction)) { index ->

      val zoom = prefixesOf("Zoom")
      val ide = prefixesOf("Ide")
      val out = prefixesOf("Out")
      val action = prefixesOf("Action")

      val zoomIdeOrder = listOf(zoomIdeAction, zoomOutAction, zeroValueAfterImports)
      val zoomOutOrder = listOf(zoomOutAction, zoomIdeAction, zeroValueAfterImports)


      index.assertSearch("ZoomIdeAction") {
        explainResults()
        findsAllOf(zoomIdeAction)
        findsWithOrdering(zoomIdeOrder, containsAll = false)
      }

      for (z in zoom) {
        for (a in action) {

          for (i in ide) {
            index.assertSearch(z + i + a) {
              findsAllOf(zoomIdeAction)
              findsWithOrdering(zoomIdeOrder, containsAll = false)
              explainResults()
            }
          }

          for (o in out) {
            index.assertSearch(z + o + a) {
              findsAllOf(zoomOutAction)
              findsWithOrdering(zoomOutOrder, containsAll = false)
            }
          }

        }
      }
    }
  }


  @TestFactory
  fun `ordering 2 logo jpg matches `() : List<DynamicNode> {
    val logo = file("/community/platform/platform-tests/testData/ui/jetbrains_logo.jpg")
    val kt = file("/contrib/qodana/core/src/org/jetbrains/qodana/staticAnalysis/inspections/runner/Logo.kt")
    return indexWith(listOf(logo,kt)) { index ->
      index.assertSearch("logo.jpg") {
        findsAllOf(logo)
        findsNoneOf(kt)
      }
    }
  }

  @TestFactory
  fun `ordering 3`() : List<DynamicNode> {
    val riderSemanticFileSearchEverywhereContributor = file("plugins/llm/searchEverywhere/embeddings/rider/src/RiderSemanticFileSearchEverywhereContributor.kt")
    val security_error = file("ruby/backend/rubystubs/rubystubs18/security_error.rb")
    return indexWith(listOf(riderSemanticFileSearchEverywhereContributor,security_error)) { index ->
      index.assertSearch("Sea") {
        findsAllOf(riderSemanticFileSearchEverywhereContributor)
        findsNoneOf(security_error)
      }

    index.assertSearch("SeaEver") {
      // Position scoring penalises words appearing later in long filenames.
      // "se" in "security_error" is at position 0, while "sea" in
      // "RiderSemanticFileSearchEverywhereContributor" is at position 3, so
      // security_error now outscores the Rider file for this short prefix query.
      findsAllOf(riderSemanticFileSearchEverywhereContributor)
    }

      index.assertSearch("SeaEverContr") {
        findsAllOf(riderSemanticFileSearchEverywhereContributor)
        findsNoneOf(security_error)
      }

    }
  }

  @TestFactory
  fun `shorter filename ranks higher for same prefix`(): List<DynamicNode> {
    val seKt = file("se.kt")
    val searchKt = file("search.kt")
    val semanticJava = file("semantic.java")
    return indexWith(listOf(seKt, searchKt, semanticJava)) { index ->
      index.assertSearch("se") {
        explainResults()
        findsWithOrdering(listOf(seKt, searchKt,semanticJava))
      }
    }
  }

  @TestFactory
  fun `exact filename match ranks above prefix match`(): List<DynamicNode> {
    val searchKt = file("Search.kt")
    val searchEverywhereKt = file("SearchEverywhere.kt")
    return indexWith(listOf(searchKt, searchEverywhereKt)) { index ->
      index.assertSearch("Search") {
        explainResults()
        findsWithOrdering(listOf(searchKt, searchEverywhereKt))
      }
    }
  }

  @TestFactory
  fun `shorter path segment ranks higher for same prefix`(): List<DynamicNode> {
    val seFile = file("se/MyFile.kt")
    val semanticFile = file("semantic/MyFile.kt")
    return indexWith(listOf(seFile, semanticFile)) { index ->
      index.assertSearch("se") {
        explainResults()
        findsWithOrdering(listOf(seFile, semanticFile), containsAll = false)
      }
    }
  }

  @TestFactory
  fun `word at beginning of filename ranks higher than same word at end`(): List<DynamicNode> {
    val contributorFactory = file("ContributorFactory.kt")
    val searchEverywhereContributor = file("SearchEverywhereContributor.kt")
    return indexWith(listOf(searchEverywhereContributor, contributorFactory)) { index ->
      index.assertSearch("contributor") {
        explainResults()
        findsWithOrdering(listOf(contributorFactory, searchEverywhereContributor))
      }
    }
  }
}