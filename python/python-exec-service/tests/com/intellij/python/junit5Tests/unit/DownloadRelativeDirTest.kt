// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.python.community.execService.impl.processLaunchers.computeDownloadRelativeDir
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Guards the relative-path computation used to download target-modified files (e.g. `pyproject.toml`,
 * `uv.lock`) back to the local machine. Regression test for PY-91340: the working-dir-equals-volume-root
 * case must yield an empty prefix, not an absolute target path, otherwise `download` silently fails.
 */
class DownloadRelativeDirTest {
  @Test
  fun `working dir equal to volume root yields empty prefix`() {
    // PY-91340: single-module uv-over-SSH case; must be "" so download gets a plain relative path.
    assertThat(computeDownloadRelativeDir("/home/user/proj", "/home/user/proj")).isEqualTo("")
  }

  @Test
  fun `working dir under volume root yields subpath with trailing slash`() {
    assertThat(computeDownloadRelativeDir("/home/user/proj/sub", "/home/user/proj")).isEqualTo("sub/")
    assertThat(computeDownloadRelativeDir("/home/user/proj/a/b", "/home/user/proj")).isEqualTo("a/b/")
  }

  /** PY-91930: the target path of a volume root has a trailing slash. */
  @Test
  fun `trailing slash yields the same prefix`() {
    assertThat(computeDownloadRelativeDir("/home/user/proj/", "/home/user/proj")).isEqualTo("")
    assertThat(computeDownloadRelativeDir("/home/user/proj", "/home/user/proj/")).isEqualTo("")
    assertThat(computeDownloadRelativeDir("/home/user/proj/sub/", "/home/user/proj")).isEqualTo("sub/")
  }

  @Test
  fun `working dir outside volume root yields null`() {
    assertThat(computeDownloadRelativeDir("/home/user/proj2", "/home/user/proj")).isNull()
    assertThat(computeDownloadRelativeDir("/home/user", "/home/user/proj")).isNull()
  }
}
