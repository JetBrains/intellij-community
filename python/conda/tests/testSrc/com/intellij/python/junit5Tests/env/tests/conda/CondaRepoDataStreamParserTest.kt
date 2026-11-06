package com.intellij.python.junit5Tests.env.tests.conda

import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.Result
import com.jetbrains.python.packaging.conda.CondaRepoDataStreamParser
import kotlinx.coroutines.currentCoroutineContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CondaRepoDataStreamParserTest {
  @Test
  fun `parses expected json`(): Unit = timeoutRunBlocking {
    val json = """
      {
        "unexpected_field": {
          "foo": {
            "bar": 67
          }
        },
        "packages": {
          "entry1": {
            "trash": 67,
            "name": "foo",
            "version": "1.0.1"
          },
          "entry2": {
            "trash": 67,
            "name": "foo",
            "version": "1.0.2"
          },
          "entry3": {
            "trash": 67,
            "name": "foo",
            "version": "1.0.3"
          },
          "bad_entry1": {
            "name": "foo"
          },
          "bad_entry2": {
            "version": "1.3"
          },
          "bad_entry3": {
            "name": "bad"
          },
          "entry4": {
            "name": "baz",
            "version": "1.0"
          }
        },
        "packages.conda": {
          "entry1": {
            "trash": 67,
            "name": "foo",
            "version": "1.0.4"
          },
          "entry2": {
            "name": "bar",
            "version": "1.0.1"
          },
          "entry3": {
            "name": "bar",
            "version": "1.2.0"
          }
        },
        "irrelevant_field": {
          "foo": "bar"
        }
      }
    """.trimIndent()

    assertThat(CondaRepoDataStreamParser.parseVersions(json.byteInputStream(), currentCoroutineContext()))
      .isEqualTo(
        Result.Success(
          mapOf(
            "foo" to setOf("1.0.1", "1.0.2", "1.0.3", "1.0.4"),
            "baz" to setOf("1.0"),
            "bar" to setOf("1.0.1", "1.2.0")
          )
        )
      )
  }

  @Test
  fun `ignores empty input`(): Unit = timeoutRunBlocking {
    assertThat(CondaRepoDataStreamParser.parseVersions("{}".byteInputStream(), currentCoroutineContext()))
      .isEqualTo(Result.Success(emptyMap<String, Set<String>>()))
  }

  @Test
  fun `ignores empty package fields`(): Unit = timeoutRunBlocking {
    val json = """
      {
        "packages": {},
        "packages.conda": {}
      }
    """.trimIndent()

    assertThat(CondaRepoDataStreamParser.parseVersions(json.byteInputStream(), currentCoroutineContext()))
      .isEqualTo(Result.Success(emptyMap<String, Set<String>>()))
  }

  @Test
  fun `malformed json results in parse error`(): Unit = timeoutRunBlocking {
    val malformedJson = "{"
    val result = CondaRepoDataStreamParser.parseVersions(malformedJson.byteInputStream(), currentCoroutineContext())

    assertThat(result).isInstanceOf(Result.Failure::class.java)
    assertThat(result.errorOrNull?.message).isNotEmpty
  }
}