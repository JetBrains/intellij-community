// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree.workspace;

import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.tree.MavenJavaVersionHolder;
import org.jetbrains.idea.maven.importing.tree.MavenModuleType;

public class ModuleData {
  @NotNull private final String moduleName;
  @NotNull private final String modulePath;
  @NotNull private final MavenModuleType type;
  @NotNull private final MavenJavaVersionHolder javaVersionHolder;

  public ModuleData(@NotNull String moduleName,
                    @NotNull String modulePath,
                    @NotNull MavenModuleType type,
                    @NotNull MavenJavaVersionHolder javaVersionHolder) {
    this.moduleName = moduleName;
    this.modulePath = modulePath;
    this.type = type;
    this.javaVersionHolder = javaVersionHolder;
  }

  @NotNull
  public String getModuleName() {
    return moduleName;
  }

  @NotNull
  public String getModulePath() {
    return modulePath;
  }

  @Nullable
  public LanguageLevel getSourceLanguageLevel() {
    return type == MavenModuleType.TEST ? javaVersionHolder.testSourceLevel : javaVersionHolder.sourceLevel;
  }

  @Nullable
  public LanguageLevel getTargetLanguageLevel() {
    return type == MavenModuleType.TEST ? javaVersionHolder.testTargetLevel : javaVersionHolder.targetLevel;
  }

  @NotNull
  public MavenJavaVersionHolder getJavaVersionHolder() {
    return javaVersionHolder;
  }

  @NotNull
  public MavenModuleType getType() {
    return type;
  }

  @Override
  public String toString() {
    return moduleName;
  }
}
