// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree

import com.intellij.pom.java.LanguageLevel

class MavenJavaVersionHolder(
  @JvmField val sourceLevel: LanguageLevel?,
  @JvmField val targetLevel: LanguageLevel?,
  @JvmField val testSourceLevel: LanguageLevel?,
  @JvmField val testTargetLevel: LanguageLevel?,
  @JvmField val hasExecutionsForTests: Boolean,
  @JvmField val hasTestCompilerArgs: Boolean,
) {
  fun needSeparateTestModule(): Boolean {
    return hasTestCompilerArgs || hasExecutionsForTests || (testSourceLevel != null && testSourceLevel != sourceLevel)
           || (testTargetLevel != null && testTargetLevel != targetLevel)
  }
}
