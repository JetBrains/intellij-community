package com.intellij.searchEverywhereLucene.backend.providers.files

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory

@TestApplication
class PrefixFileSearchTest : FileSearchTestBase() {
  fun prefixesOf(fileName: String) = (1..fileName.length)
    .map { fileName.substring(0, it) }

  @TestFactory
  fun `test prefixes`(): List<DynamicNode> {
    return listOf(
      "Readme.md",
      "shell.nix",
      "temp.out.bc.exe",
      "File2Index.kt",
      "some-file-index.kt",
      "another_file.kt",
      "mix_of-both.md",
      "what-even++.txt",
      "my test.md",
      "[bracket].md",
      ".gitignore"
    )
      .flatMap { fileName ->
        val file = file(fileName)
        indexWith(listOf(file)) { index ->
          prefixesOf(fileName)
            .forEach { prefix ->
              index.assertSearch(prefix) {
                findsAllOf(file)
              }
            }
        }
      }
  }

  @TestFactory
  fun `IJPL-220105 path matching`(): List<DynamicNode> {
    val clarify_agent = file("deepresearch/clarify_agent.py")

    return indexWith(listOf(clarify_agent)) { index ->
      // Search using only PATH_SEGMENT tokens (individual path components)
      index.assertSearch("clde") {
        findsAllOf(clarify_agent)
      }
    }
  }
}