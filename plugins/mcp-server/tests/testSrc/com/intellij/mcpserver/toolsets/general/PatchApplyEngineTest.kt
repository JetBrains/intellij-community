package com.intellij.mcpserver.toolsets.general

import com.intellij.mcpserver.McpExpectedError
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PatchApplyEngineTest {
  @Test
  fun `parsePatch requires begin marker`() {
    assertThatThrownBy { PatchApplyEngine.parsePatch("oops") }
      .isInstanceOf(McpExpectedError::class.java)
      .hasMessageContaining("patch must include *** Begin Patch")
  }

  @Test
  fun `parsePatch accepts heredoc wrapped input`() {
    val patch = buildPatch(
      "<<EOF",
      "*** Begin Patch",
      "*** Add File: notes.txt",
      "+alpha",
      "*** End Patch",
      "EOF",
    )

    val operations = PatchApplyEngine.parsePatch(patch)

    assertThat(operations).hasSize(1)
    assertThat(operations.single()).isEqualTo(
      AddPatchOperation(path = "notes.txt", content = "alpha\n")
    )
  }

  @Test
  fun `parsePatch rejects escaped control characters in paths`() {
    val patch = buildPatch(
      "*** Begin Patch",
      "*** Delete File: bad\\npath.txt",
      "*** End Patch",
    )

    assertThatThrownBy { PatchApplyEngine.parsePatch(patch) }
      .isInstanceOf(McpExpectedError::class.java)
      .hasMessageContaining("contains control characters or escape sequences")
  }

  @Test
  fun `parsePatch supports update chunks without initial header`() {
    val patch = buildPatch(
      "*** Begin Patch",
      "*** Update File: sample.txt",
      " alpha",
      "-beta",
      "+gamma",
      "*** End Patch",
    )

    val operations = PatchApplyEngine.parsePatch(patch)

    assertThat(operations).hasSize(1)
    val update = operations.single() as UpdatePatchOperation
    assertThat(update.path).isEqualTo("sample.txt")
    assertThat(update.hunks).hasSize(1)
    assertThat(update.hunks.single().lines).containsExactly(
      PatchHunkLine(' ', "alpha"),
      PatchHunkLine('-', "beta"),
      PatchHunkLine('+', "gamma"),
    )
  }

  @Test
  fun `parsePatch strips unified-diff line numbers from hunk header`() {
    val patch = buildPatch(
      "*** Begin Patch",
      "*** Update File: sample.txt",
      "@@@ -48,6 +48,7 @@@",
      " alpha",
      "-beta",
      "+gamma",
      "*** End Patch",
    )

    val operations = PatchApplyEngine.parsePatch(patch)

    assertThat(operations).hasSize(1)
    val update = operations.single() as UpdatePatchOperation
    assertThat(update.hunks).hasSize(1)
    assertThat(update.hunks.single().header).isNull()
  }

  @Test
  fun `parsePatch preserves non-coordinate hunk header text`() {
    val patch = buildPatch(
      "*** Begin Patch",
      "*** Update File: sample.txt",
      "@@ def sample()",
      " alpha",
      "-beta",
      "+gamma",
      "*** End Patch",
    )

    val operations = PatchApplyEngine.parsePatch(patch)

    assertThat(operations).hasSize(1)
    val update = operations.single() as UpdatePatchOperation
    assertThat(update.hunks).hasSize(1)
    assertThat(update.hunks.single().header).isEqualTo("def sample()")
  }

  @Test
  fun `parsePatch requires end marker`() {
    assertParseFails(
      "patch must include *** End Patch",
      "*** Begin Patch",
      "*** Add File: notes.txt",
      "+alpha",
    )
  }

  @Test
  fun `parsePatch rejects patch with no operations`() {
    assertParseFails(
      "patch did not contain any operations",
      "*** Begin Patch",
      "*** End Patch",
    )
  }

  @Test
  fun `parsePatch rejects unexpected top level line`() {
    assertParseFails(
      "Unexpected patch line",
      "*** Begin Patch",
      "oops",
      "*** End Patch",
    )
  }

  @Test
  fun `parsePatch rejects add file without path`() {
    assertParseFails(
      "Add File requires a path",
      "*** Begin Patch",
      "*** Add File:   ",
      "*** End Patch",
    )
  }

  @Test
  fun `parsePatch rejects add file body line without plus prefix`() {
    assertParseFails(
      "Add File lines must start with +",
      "*** Begin Patch",
      "*** Add File: notes.txt",
      "alpha",
      "*** End Patch",
    )
  }

  @Test
  fun `parsePatch rejects add file mixed body with later invalid line`() {
    assertParseFails(
      "Add File lines must start with +",
      "*** Begin Patch",
      "*** Add File: notes.txt",
      "+alpha",
      "beta",
      "*** End Patch",
    )
  }

  @Test
  fun `parsePatch rejects delete file without path`() {
    assertParseFails(
      "Delete File requires a path",
      "*** Begin Patch",
      "*** Delete File:   ",
      "*** End Patch",
    )
  }

  @Test
  fun `parsePatch rejects update file without path`() {
    assertParseFails(
      "Update File requires a path",
      "*** Begin Patch",
      "*** Update File:   ",
      "*** End Patch",
    )
  }

  @Test
  fun `parsePatch rejects move target without path`() {
    assertParseFails(
      "Move to requires a path",
      "*** Begin Patch",
      "*** Update File: sample.txt",
      "*** Move to:   ",
      "*** End Patch",
    )
  }

  @Test
  fun `parsePatch rejects update without hunks`() {
    assertParseFails(
      "Update File requires at least one hunk",
      "*** Begin Patch",
      "*** Update File: sample.txt",
      "*** End Patch",
    )
  }

  @Test
  fun `parsePatch rejects first hunk line without header or diff prefix`() {
    assertParseFails(
      "Expected @@ hunk header",
      "*** Begin Patch",
      "*** Update File: sample.txt",
      "oops",
      "*** End Patch",
    )
  }

  @Test
  fun `parsePatch rejects malformed second hunk without header`() {
    assertParseFails(
      "Expected @@ hunk header",
      "*** Begin Patch",
      "*** Update File: sample.txt",
      " alpha",
      "-beta",
      "+gamma",
      "oops",
      "*** End Patch",
    )
  }

  @Test
  fun `parsePatch rejects invalid first line inside hunk`() {
    assertParseFails(
      "Hunk lines must start with space, +, or -",
      "*** Begin Patch",
      "*** Update File: sample.txt",
      "@@",
      "oops",
      "*** End Patch",
    )
  }

  @Test
  fun `parsePatch rejects empty hunk after header`() {
    assertParseFails(
      "Empty hunk in Update File",
      "*** Begin Patch",
      "*** Update File: sample.txt",
      "@@",
      "*** End Patch",
    )
  }

  @Test
  fun `parsePatch rejects escaped control characters in add path`() {
    assertParseFails(
      "Add File path contains control characters or escape sequences",
      "*** Begin Patch",
      "*** Add File: bad\\npath.txt",
      "+alpha",
      "*** End Patch",
    )
  }

  @Test
  fun `parsePatch rejects escaped control characters in update path`() {
    assertParseFails(
      "Update File path contains control characters or escape sequences",
      "*** Begin Patch",
      "*** Update File: bad\\npath.txt",
      "*** End Patch",
    )
  }

  @Test
  fun `parsePatch rejects escaped control characters in move path`() {
    assertParseFails(
      "Move to path contains control characters or escape sequences",
      "*** Begin Patch",
      "*** Update File: sample.txt",
      "*** Move to: bad\\npath.txt",
      "*** End Patch",
    )
  }

  @Test
  fun `applyHunks works with unified-diff style hunk headers`() {
    val patch = buildPatch(
      "*** Begin Patch",
      "*** Update File: sample.txt",
      "@@ -1,3 +1,4 @@",
      " alpha",
      "-beta",
      "+BETA",
      " gamma",
      "+delta",
      "*** End Patch",
    )

    val operations = PatchApplyEngine.parsePatch(patch)
    val update = operations.single() as UpdatePatchOperation
    val result = PatchApplyEngine.applyHunks("alpha\nbeta\ngamma\n", update.hunks)

    assertThat(result).isEqualTo("alpha\nBETA\ngamma\ndelta\n")
  }

  @Test
  fun `applyHunks matches lines with trailing whitespace differences`() {
    val result = PatchApplyEngine.applyHunks(
      "alpha   \nbeta\n",
      listOf(
        PatchHunk(
          header = null,
          lines = listOf(
            PatchHunkLine('-', "alpha"),
            PatchHunkLine('+', "alpha2"),
            PatchHunkLine(' ', "beta"),
          ),
          isEndOfFile = false,
        )
      )
    )

    assertThat(result).isEqualTo("alpha2\nbeta\n")
  }

  @Test
  fun `applyHunks matches lines with unicode punctuation differences`() {
    val result = PatchApplyEngine.applyHunks(
      "import asyncio  # local import – avoids top‑level dep\n",
      listOf(
        PatchHunk(
          header = null,
          lines = listOf(
            PatchHunkLine('-', "import asyncio  # local import - avoids top-level dep"),
            PatchHunkLine('+', "import asyncio  # HELLO"),
          ),
          isEndOfFile = false,
        )
      )
    )

    assertThat(result).isEqualTo("import asyncio  # HELLO\n")
  }

  @Test
  fun `applyHunks keeps normalized matching behavior for repeated lines`() {
    val result = PatchApplyEngine.applyHunks(
      "first\nimport asyncio  # local import – avoids top‑level dep\nimport asyncio  # local import – avoids top‑level dep\n",
      listOf(
        PatchHunk(
          header = null,
          lines = listOf(
            PatchHunkLine('-', "import asyncio  # local import - avoids top-level dep"),
            PatchHunkLine('+', "import asyncio  # FIRST"),
          ),
          isEndOfFile = false,
        ),
        PatchHunk(
          header = null,
          lines = listOf(
            PatchHunkLine('-', "import asyncio  # local import - avoids top-level dep"),
            PatchHunkLine('+', "import asyncio  # SECOND"),
          ),
          isEndOfFile = false,
        ),
      )
    )

    assertThat(result).isEqualTo("first\nimport asyncio  # FIRST\nimport asyncio  # SECOND\n")
  }

  @Test
  fun `applyHunks prefers exact matches before normalized fallback`() {
    val result = PatchApplyEngine.applyHunks(
      "first\nimport asyncio  # local import – avoids top‑level dep\nimport asyncio  # local import - avoids top-level dep\n",
      listOf(
        PatchHunk(
          header = null,
          lines = listOf(
            PatchHunkLine('-', "import asyncio  # local import - avoids top-level dep"),
            PatchHunkLine('+', "import asyncio  # EXACT"),
          ),
          isEndOfFile = false,
        )
      )
    )

    assertThat(result).isEqualTo("first\nimport asyncio  # local import – avoids top‑level dep\nimport asyncio  # EXACT\n")
  }

  @Test
  fun `applyHunks falls back to searching from start for non eof hunks`() {
    val result = PatchApplyEngine.applyHunks(
      "one\ntwo\nthree\none\n",
      listOf(
        PatchHunk(
          header = null,
          lines = listOf(
            PatchHunkLine('-', "one"),
            PatchHunkLine('+', "ONE"),
          ),
          isEndOfFile = true,
        ),
        PatchHunk(
          header = null,
          lines = listOf(
            PatchHunkLine('-', "two"),
            PatchHunkLine('+', "TWO"),
          ),
          isEndOfFile = false,
        ),
      )
    )

    assertThat(result).isEqualTo("one\nTWO\nthree\nONE\n")
  }

  @Test
  fun `applyHunks handles repeated large content deterministically`() {
    val replacements = 80
    val originalLines = 600
    val hunks = (0 until replacements).map { index ->
      PatchHunk(
        header = null,
        lines = listOf(
          PatchHunkLine('-', "alpha"),
          PatchHunkLine('+', "alpha-$index"),
        ),
        isEndOfFile = false,
      )
    }

    val original = buildString {
      repeat(originalLines) {
        append("alpha\n")
      }
    }

    val result = PatchApplyEngine.applyHunks(original, hunks)

    val expected = buildString {
      repeat(replacements) { index ->
        append("alpha-$index\n")
      }
      repeat(originalLines - replacements) {
        append("alpha\n")
      }
    }
    assertThat(result).isEqualTo(expected)
  }

  @Test
  fun `applyHunks honors end of file marker`() {
    val result = PatchApplyEngine.applyHunks(
      "alpha\nbeta\n",
      listOf(
        PatchHunk(
          header = null,
          lines = listOf(
            PatchHunkLine('-', "beta"),
            PatchHunkLine('+', "BETA"),
          ),
          isEndOfFile = true,
        )
      )
    )

    assertThat(result).isEqualTo("alpha\nBETA\n")
  }

  @Test
  fun `applyHunks rejects end of file marker when not at file end`() {
    assertThatThrownBy {
      PatchApplyEngine.applyHunks(
        "alpha\nbeta\ngamma\n",
        listOf(
          PatchHunk(
            header = null,
            lines = listOf(
              PatchHunkLine('-', "beta"),
              PatchHunkLine('+', "BETA"),
            ),
            isEndOfFile = true,
          )
        )
      )
    }
      .isInstanceOf(McpExpectedError::class.java)
      .hasMessageContaining("Hunk context not found")
  }

  private fun buildPatch(vararg lines: String): String {
    return lines.joinToString(separator = "\n", postfix = "\n")
  }

  private fun assertParseFails(messagePart: String, vararg lines: String) {
    val patch = buildPatch(*lines)
    assertThatThrownBy { PatchApplyEngine.parsePatch(patch) }
      .isInstanceOf(McpExpectedError::class.java)
      .hasMessageContaining(messagePart)
  }
}
