// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ui

import com.intellij.execution.Platform
import com.jetbrains.python.ui.targetPathEditor.ManualPathEntryDialog
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ManualPathEntryDialogTest {
  @Parameterized.Parameter(0)
  @JvmField
  var path: String? = null

  @Parameterized.Parameter(1)
  @JvmField
  var platform: Platform? = null

  @Parameterized.Parameter(2)
  @JvmField
  var isAbsolute: Boolean = false

  @Test
  fun `test isAbsolutePath`() {
    Assert.assertEquals(ManualPathEntryDialog.isAbsolutePath(path!!, platform!!), isAbsolute)
  }

  companion object {
    @Parameterized.Parameters(name = "[{1}] Path ''{0}'' is absolute == {2}")
    @JvmStatic
    fun data() = arrayOf(
      // Unix absolute paths
      arrayOf("/", Platform.UNIX, true),
      arrayOf("/opt", Platform.UNIX, true),
      arrayOf("/opt/", Platform.UNIX, true),
      arrayOf("/opt/project", Platform.UNIX, true),
      arrayOf("/opt/project/", Platform.UNIX, true),
      arrayOf("//", Platform.UNIX, true),
      arrayOf("/opt//project", Platform.UNIX, true),
      arrayOf("/opt//project//", Platform.UNIX, true),

      // Unix relative paths
      arrayOf(".", Platform.UNIX, false),
      arrayOf("./", Platform.UNIX, false),
      arrayOf("opt/", Platform.UNIX, false),
      arrayOf("opt/project", Platform.UNIX, false),
      arrayOf("opt/project/", Platform.UNIX, false),
      arrayOf("./opt/", Platform.UNIX, false),
      arrayOf("./opt/project", Platform.UNIX, false),
      arrayOf("./opt/project/", Platform.UNIX, false),

      // Windows absolute paths
      arrayOf("C:\\", Platform.WINDOWS, true),
      arrayOf("C:/", Platform.WINDOWS, true),
      arrayOf("c:\\", Platform.WINDOWS, true),
      arrayOf("c:/", Platform.WINDOWS, true),
      arrayOf("C:/opt/", Platform.WINDOWS, true),
      arrayOf("C:/opt/project", Platform.WINDOWS, true),
      arrayOf("C:/opt/project/", Platform.WINDOWS, true),
      arrayOf("C:\\opt\\", Platform.WINDOWS, true),
      arrayOf("C:\\opt\\project", Platform.WINDOWS, true),
      arrayOf("C:\\opt\\project\\", Platform.WINDOWS, true),

      // Windows relative paths
      arrayOf("opt/", Platform.WINDOWS, false),
      arrayOf("opt/project", Platform.WINDOWS, false),
      arrayOf("opt/project/", Platform.WINDOWS, false),
      arrayOf("./opt/", Platform.WINDOWS, false),
      arrayOf("./opt/project", Platform.WINDOWS, false),
      arrayOf("./opt/project/", Platform.WINDOWS, false),
      arrayOf("opt\\", Platform.WINDOWS, false),
      arrayOf("opt\\project", Platform.WINDOWS, false),
      arrayOf("opt\\project\\", Platform.WINDOWS, false),
      arrayOf(".\\opt\\", Platform.WINDOWS, false),
      arrayOf(".\\opt\\project", Platform.WINDOWS, false),
      arrayOf(".\\opt\\project\\", Platform.WINDOWS, false),
    )
  }
}