package com.intellij.mcpserver.toolsets.general

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PatchTargetPathsTest {
  @Test
  fun `extracts add, update, move and delete paths`() {
    val patch = listOf(
      "*** Begin Patch",
      "*** Add File: src/Added.cs",
      "+content",
      "*** Update File: src/Updated.cs",
      "@@",
      "-old",
      "+new",
      "*** Update File: src/Moved.cs",
      "*** Move to: src/dir/MovedTarget.cs",
      "@@",
      "-old",
      "+new",
      "*** Delete File: src/Deleted.cs",
      "*** End Patch",
    ).joinToString("\n")

    val paths = extractPatchTargetPaths(patch)

    assertThat(paths.createdOrUpdated).containsExactly("src/Added.cs", "src/Updated.cs", "src/dir/MovedTarget.cs")
    assertThat(paths.deleted).containsExactly("src/Deleted.cs")
  }

  @Test
  fun `extracts unified git diff target paths`() {
    val patch = listOf(
      "diff --git a/src/Updated.cs b/src/Updated.cs",
      "--- a/src/Updated.cs",
      "+++ b/src/Updated.cs",
      "@@ -1,1 +1,1 @@",
      "-old",
      "+new",
      "diff --git a/src/Deleted.cs b/src/Deleted.cs",
      "deleted file mode 100644",
      "--- a/src/Deleted.cs",
      "+++ /dev/null",
      "@@ -1,1 +0,0 @@",
      "-gone",
    ).joinToString("\n")

    val paths = extractPatchTargetPaths(patch)

    assertThat(paths.createdOrUpdated).containsExactly("src/Updated.cs")
    assertThat(paths.deleted).containsExactly("src/Deleted.cs")
  }
}
