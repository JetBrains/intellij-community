// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.Nullable;

public class MavenJavaVersionHolder {
  @Nullable final LanguageLevel sourceLevel;
  @Nullable final LanguageLevel targetLevel;
  @Nullable final LanguageLevel testSourceLevel;
  @Nullable final LanguageLevel testTargetLevel;

  public MavenJavaVersionHolder(@Nullable LanguageLevel sourceLevel,
                                @Nullable LanguageLevel targetLevel,
                                @Nullable LanguageLevel testSourceLevel,
                                @Nullable LanguageLevel testTargetLevel) {
    this.sourceLevel = sourceLevel;
    this.targetLevel = targetLevel;
    this.testSourceLevel = testSourceLevel;
    this.testTargetLevel = testTargetLevel;
  }

  public MavenJavaVersionHolder(@Nullable LanguageLevel sourceLevel,
                                @Nullable LanguageLevel targetLevel) {
    this.sourceLevel = sourceLevel;
    this.targetLevel = targetLevel;
    this.testSourceLevel = null;
    this.testTargetLevel = null;
  }

  public boolean needSeparateTestModule() {
    return testSourceLevel != null && !testSourceLevel.equals(sourceLevel)
      || testTargetLevel != null && !testTargetLevel.equals(targetLevel);
  }
}
