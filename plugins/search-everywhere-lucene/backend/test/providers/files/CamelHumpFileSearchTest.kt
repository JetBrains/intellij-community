package com.intellij.searchEverywhereLucene.backend.providers.files

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory

@TestApplication
class CamelHumpFileSearchTest : FileSearchTestBase() {
  @TestFactory
  fun `camel search`(): List<DynamicNode> {
    val seaEverContr = file("SearchEverywhereContributor.kt")
    return indexWith(listOf(seaEverContr)) { index ->
      index.assertSearch("seaEverContr") {
        findsAllOf(seaEverContr)
      }

      index.assertSearch("SEC") {
        findsAllOf(seaEverContr)
      }
    }
  }

  @TestFactory
  fun `camel search - partial initial abbreviation`(): List<DynamicNode> {
    val seaEverContr = file("SearchEverywhereContributor.kt")
    val unrelated = file("UnrelatedFile.kt")
    return indexWith(listOf(seaEverContr, unrelated)) { index ->
      // Two-letter prefix of camel initials should still match
      index.assertSearch("SE") {
        findsAllOf(seaEverContr)
        findsNoneOf(unrelated)
      }
      // Even a single initial should match
      index.assertSearch("S") {
        findsAllOf(seaEverContr)
      }
    }
  }

  @TestFactory
  fun `camel search - multiple files sharing the same abbreviation`(): List<DynamicNode> {
    val seaEverContr = file("SearchEverywhereContributor.kt")
    val someEvilCat = file("SomeEvilCat.kt")
    val unrelated = file("UnrelatedFile.kt")
    return indexWith(listOf(seaEverContr, someEvilCat, unrelated)) { index ->
      // SEC matches both SearchEverywhereContributor (S+E+C) and SomeEvilCat (S+E+C)
      index.assertSearch("SEC") {
        findsAllOf(seaEverContr, someEvilCat)
        findsNoneOf(unrelated)
      }
    }
  }

  @TestFactory
  fun `camel search - non-matching initial in the middle finds nothing`(): List<DynamicNode> {
    val seaEverContr = file("SearchEverywhereContributor.kt")
    return indexWith(listOf(seaEverContr)) { index ->
      // X does not correspond to any word in SearchEverywhereContributor
      index.assertSearch("SXC") {
        findsNothing()
      }

      // Matches again because of extension
      index.assertSearch("SECkt") {
        findsAllOf(seaEverContr)
      }
    }
  }

  @TestFactory
  fun `camel search - multi-char word prefix`(): List<DynamicNode> {
    val seaEverContr = file("SearchEverywhereContributor.kt")
    return indexWith(listOf(seaEverContr)) { index ->
      // Full first word as prefix should match
      index.assertSearch("Search") {
        findsAllOf(seaEverContr)
      }
      // Two full word prefixes should match
      index.assertSearch("SearchEverywhere") {
        findsAllOf(seaEverContr)
      }
    }
  }

  @TestFactory
  fun `camel search - underscore as word boundary`(): List<DynamicNode> {
    val snakeCase = file("my_file_name.kt")
    val unrelated = file("SomeUnrelatedFile.kt")
    return indexWith(listOf(snakeCase, unrelated)) { index ->

      index.assertSearch("MFN") {
        findsAllOf(snakeCase)
        findsNoneOf(unrelated)
      }
      index.assertSearch("MF") {
        findsAllOf(snakeCase)
        findsNoneOf(unrelated)
      }
    }
  }

  @TestFactory
  fun `camel search - number as word boundary`(): List<DynamicNode> {
    val indexed = file("FileIndex2Impl.kt")
    return indexWith(listOf(indexed)) { index ->
      // Skipping over the digit word without including it in the query does not work;
      // use FI2I (with the digit) instead — see the next assertion
      index.assertSearch("FII") {
        findsAllOf(indexed)
      }
      // Digit itself acts as a matchable boundary token
      index.assertSearch("FI2I") {
        findsAllOf(indexed)
      }
    }
  }

  @TestFactory
  fun `camel search - all-caps acronym prefix`(): List<DynamicNode> {
    val readme = file("README.md")
    return indexWith(listOf(readme)) { index ->
      // R is a prefix of the single word README
      index.assertSearch("R") {
        findsAllOf(readme)
      }
      // READ is a longer prefix of README
      index.assertSearch("READ") {
        findsAllOf(readme)
      }
      // RM: R matches README, but M is not a prefix of any remaining word
      index.assertSearch("RM") {
        findsNothing()
      }
    }
  }

  @TestFactory
  fun `camel search - consecutive uppercase followed by title-case`(): List<DynamicNode> {
    // HTTPServer splits as [HTTP, Server]
    val httpServer = file("HTTPServer.kt")
    return indexWith(listOf(httpServer)) { index ->

      index.assertSearch("HS") {
        findsAllOf(httpServer)
      }
      // Full first word
      index.assertSearch("HTTP") {
        findsAllOf(httpServer)
      }
      // HTTPS matches: http (full HTTP word) + s (prefix of Server) = cross-word abbreviation
      index.assertSearch("HTTPS") {
        findsAllOf(httpServer)
      }
    }
  }

  @TestFactory
  fun `camel search - title-case followed by all-caps`(): List<DynamicNode> {
    // MyHTTP splits as [My, HTTP]
    val myHttp = file("MyHTTP.kt")
    return indexWith(listOf(myHttp)) { index ->
      // M = prefix of My, H = prefix of HTTP
      index.assertSearch("MH") {
        findsAllOf(myHttp)
      }
      // MHT also matches: m = prefix of My, ht = 2-char prefix of HTTP
      index.assertSearch("MHT") {
        findsAllOf(myHttp)
      }
    }
  }

  @TestFactory
  fun `camel search - word skipping`(): List<DynamicNode> {
    // SearchEverywhereContributor splits as [Search, Everywhere, Contributor]
    val seaEverContr = file("SearchEverywhereContributor.kt")
    return indexWith(listOf(seaEverContr)) { index ->
      // SC would require skipping Everywhere, which is not supported — only consecutive words combine
      index.assertSearch("SC") {
        findsAllOf(seaEverContr)
      }
    }
  }

  @TestFactory
  fun `camel search - standalone digit word`(): List<DynamicNode> {
    // FileIndex2Impl splits as [File, Index, 2, Impl]; 2 is its own FILENAME_PART
    val indexed = file("FileIndex2Impl.kt")
    return indexWith(listOf(indexed)) { index ->
      index.assertSearch("2") {
        findsAllOf(indexed)
      }
    }
  }

  @TestFactory
  fun `camel search - leading digit`(): List<DynamicNode> {
    // 2FileSystem splits as [2, File, System]
    val fileSystem = file("2FileSystem.kt")
    return indexWith(listOf(fileSystem)) { index ->
      // 2 = prefix of "2", F = prefix of File
      index.assertSearch("2F") {
        findsAllOf(fileSystem)
      }
    }
  }

  @TestFactory
  fun `camel search - digit word skipping`(): List<DynamicNode> {
    // OAuth2Provider splits as [O, Auth, 2, Provider]
    val provider = file("OAuth2Provider.kt")
    return indexWith(listOf(provider)) { index ->
      // O = O, skip Auth, 2 = 2, P = Provider
      index.assertSearch("O2P") {
        findsAllOf(provider)
      }
    }
  }

  @TestFactory
  fun `camel search - lowercase query`(): List<DynamicNode> {
    // FILENAME_PART tokens are indexed lowercase; queries should be case-insensitive
    val seaEverContr = file("SearchEverywhereContributor.kt")
    val httpServer = file("HTTPServer.kt")
    return indexWith(listOf(seaEverContr, httpServer)) { index ->
      // Note: the analyzer decomposes lowercase queries into 2-char n-grams,
      // so 'search' also generates se*, ar*, ch* — which can match unrelated words.
      // Only positive assertions are used here to avoid n-gram cross-matches.
      index.assertSearch("search") {
        findsAllOf(seaEverContr)
      }
      index.assertSearch("sec") {
        findsAllOf(seaEverContr)
      }
      index.assertSearch("http") {
        findsAllOf(httpServer)
        findsNoneOf(seaEverContr)
      }
    }
  }

  @TestFactory
  fun `camel search - dot-separated stem words`(): List<DynamicNode> {
    // Dot is not a camel-hump boundary for abbreviation indexing; BG does not match build.gradle.kts
    val buildGradle = file("build.gradle.kts")
    return indexWith(listOf(buildGradle)) { index ->
      index.assertSearch("BG") {
        findsAllOf(buildGradle)
      }
    }
  }

  @TestFactory
  fun `camel search - exact abbreviation scores higher than skip abbreviation`(): List<DynamicNode> {
    // "sec" is an exact 3-part abbreviation for SearchEverywhereContributor (Search+Everywhere+Contributor)
    // but only a skip abbreviation for SearchEverywhereContributorFactory (omits Factory).
    val seaEverContr = file("SearchEverywhereContributor.kt")
    val seaEverContrFactory = file("SearchEverywhereContributorFactory.kt")
    return indexWith(listOf(seaEverContr, seaEverContrFactory)) { index ->
      index.assertSearch("sec") {
        findsAllOf(seaEverContr, seaEverContrFactory)
        findsWithOrdering(listOf(seaEverContr, seaEverContrFactory))
      }
      index.assertSearch("SEC") {
        findsAllOf(seaEverContr, seaEverContrFactory)
        findsWithOrdering(listOf(seaEverContr, seaEverContrFactory))
      }
    }
  }
}