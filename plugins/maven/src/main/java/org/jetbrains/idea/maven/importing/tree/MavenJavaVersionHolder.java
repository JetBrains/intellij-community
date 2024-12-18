// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.Nullable;

public class MavenJavaVersionHolder {
  @Nullable public final LanguageLevel sourceLevel;
  @Nullable public final LanguageLevel targetLevel;
  @Nullable public final LanguageLevel testSourceLevel;
  @Nullable public final LanguageLevel testTargetLevel;
  public final boolean hasExecutionsForTests;

  public MavenJavaVersionHolder(@Nullable LanguageLevel sourceLevel,
                                @Nullable LanguageLevel targetLevel,
                                @Nullable LanguageLevel testSourceLevel,
                                @Nullable LanguageLevel testTargetLevel,
                                boolean hasExecutionsForTests) {
    this.sourceLevel = sourceLevel;
    this.targetLevel = targetLevel;
    this.testSourceLevel = testSourceLevel;
    this.testTargetLevel = testTargetLevel;
    this.hasExecutionsForTests = hasExecutionsForTests;
  }

  public boolean needSeparateTestModule() {
    return hasExecutionsForTests || (testSourceLevel != null && !testSourceLevel.equals(sourceLevel))
           || (testTargetLevel != null && !testTargetLevel.equals(targetLevel));
  }
}
