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
}
