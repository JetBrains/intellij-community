package com.intellij.searchEverywhereLucene.backend.providers.files

import com.intellij.searchEverywhereLucene.backend.providers.files.analysis.FileTokenType
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory

@TestApplication
class TokenTypeFileSearchTest : FileSearchTestBase() {
  @TestFactory
  fun `FILENAME finds results`(): List<DynamicNode> {
    val pet = file("Pet.java")
    val petController = file("PetController.java")
    val readme = file("foo/Readme.md")

    return indexWith(listOf(pet, petController, readme)) { index ->
      // Search using only FILENAME tokens
      index.assertSearch(
        input = "Pet.java",
        buildQuery = { buildQueryOnlyUsingTokenTypes(it, FileTokenType.FILENAME.type) }
      ) {
        findsAllOf(pet)
      }

      index.assertSearch(
        input = "PetController.java",
        buildQuery = { buildQueryOnlyUsingTokenTypes(it, FileTokenType.FILENAME.type) }
      ) {
        findsAllOf(petController)
      }

      index.assertSearch(
        input = "Readme.md",
        buildQuery = { buildQueryOnlyUsingTokenTypes(it, FileTokenType.FILENAME.type) }
      ) {
        findsAllOf(readme)
      }
    }
  }

  @TestFactory
  fun `FILENAME_PART finds results`(): List<DynamicNode> {
    val petController = file("PetController.java")
    val searchEverywhereUI = file("SearchEverywhereUI.java")

    return indexWith(listOf(petController, searchEverywhereUI)) { index ->
      // Search using only FILENAME_PART tokens (camelCase parts)
      index.assertSearch(
        input = "Controller",
        buildQuery = { buildQueryOnlyUsingTokenTypes(it, FileTokenType.FILENAME_PART.type) }
      ) {
        findsAllOf(petController)
      }


      index.assertSearch(
        input = "Pet",
        buildQuery = { buildQueryOnlyUsingTokenTypes(it, FileTokenType.FILENAME_PART.type) }
      ) {
        findsAllOf(petController)
      }

      index.assertSearch(
        input = "Everywhere",
        buildQuery = { buildQueryOnlyUsingTokenTypes(it, FileTokenType.FILENAME_PART.type) }
      ) {
        findsAllOf(searchEverywhereUI)
      }
    }
  }

  @TestFactory
  fun `FILENAME_ABBREVIATION finds results`(): List<DynamicNode> {
    val searchEverywhereUI = file("SearchEverywhereUI.java")
    val petController = file("PetController.java")

    return indexWith(listOf(searchEverywhereUI, petController)) { index ->
      // Search using only FILENAME_ABBREVIATION tokens (initials)
      index.assertSearch(
        input = "SearchEverywhereUI.java",
        buildQuery = { buildQueryOnlyUsingTokenTypes(it, FileTokenType.FILENAME_ABBREVIATION.type) }
      ) {
        findsAllOf(searchEverywhereUI)
      }

      index.assertSearch(
        input = "PetController",
        buildQuery = { buildQueryOnlyUsingTokenTypes(it, FileTokenType.FILENAME_ABBREVIATION.type) }
      ) {
        findsAllOf(petController)
      }
    }
  }

  @TestFactory
  fun `PATH finds results`(): List<DynamicNode> {
    val fooReadme = file("foo/bar/Readme.md")
    val bazReadme = file("baz/qux/Readme.md")

    return indexWith(listOf(fooReadme, bazReadme)) { index ->
      // Search using only PATH tokens (full path)
      index.assertSearch(
        input = "foo/bar/Readme.md",
        buildQuery = { buildQueryOnlyUsingTokenTypes(it, FileTokenType.PATH.type) }
      ) {
        findsAllOf(fooReadme)
      }

      index.assertSearch(
        input = "baz/qux/Readme.md",
        buildQuery = { buildQueryOnlyUsingTokenTypes(it, FileTokenType.PATH.type) }
      ) {
        findsAllOf(bazReadme)
      }
    }
  }

  @TestFactory
  fun `PATH_SEGMENT finds results`(): List<DynamicNode> {
    val fooReadme = file("foo/bar/Readme.md")
    val bazReadme = file("baz/bar/Readme.md")

    return indexWith(listOf(fooReadme, bazReadme)) { index ->
      // Search using only PATH_SEGMENT tokens (individual path components)
      index.assertSearch(
        input = "foo",
        buildQuery = { buildQueryOnlyUsingTokenTypes(it, FileTokenType.PATH_SEGMENT.type) }
      ) {
        findsAllOf(fooReadme)
        findsNoneOf(bazReadme)
      }

      index.assertSearch(
        input = "bar",
        buildQuery = { buildQueryOnlyUsingTokenTypes(it, FileTokenType.PATH_SEGMENT.type) }
      ) {
        findsAllOf(fooReadme, bazReadme)
      }

      index.assertSearch(
        input = "baz",
        buildQuery = { buildQueryOnlyUsingTokenTypes(it, FileTokenType.PATH_SEGMENT.type) }
      ) {
        findsAllOf(bazReadme)
        findsNoneOf(fooReadme)
      }
    }
  }

  @TestFactory
  fun `FILETYPE finds results`(): List<DynamicNode> {
    val javaFile = file("Pet.java")
    val kotlinFile = file("Controller.kt")
    val markdownFile = file("Readme.md")

    return indexWith(listOf(javaFile, kotlinFile, markdownFile)) { index ->
      // Search using only FILETYPE tokens
      index.assertSearch(
        input = "java",
        buildQuery = { buildQueryOnlyUsingTokenTypes(it, FileTokenType.FILETYPE.type) }
      ) {
        findsAllOf(javaFile)
        findsNoneOf(kotlinFile, markdownFile)
      }

      index.assertSearch(
        input = "kt",
        buildQuery = { buildQueryOnlyUsingTokenTypes(it, FileTokenType.FILETYPE.type) }
      ) {
        findsAllOf(kotlinFile)
        findsNoneOf(javaFile, markdownFile)
      }

      index.assertSearch(
        input = "md",
        buildQuery = { buildQueryOnlyUsingTokenTypes(it, FileTokenType.FILETYPE.type) }
      ) {
        findsAllOf(markdownFile)
        findsNoneOf(javaFile, kotlinFile)
      }
    }
  }


}