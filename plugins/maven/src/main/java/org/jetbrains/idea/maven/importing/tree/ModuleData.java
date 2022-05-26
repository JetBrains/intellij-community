// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.importing.tree;

import com.intellij.openapi.module.Module;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModuleData {
  @NotNull private final Module module;
  @NotNull private final MavenModuleType type;
  @NotNull private final MavenJavaVersionHolder javaVersionHolder;
  @Nullable private final Module mainModule;
  private final boolean newModule;

  public ModuleData(@NotNull Module module,
                    @NotNull MavenModuleType type,
                    @NotNull MavenJavaVersionHolder javaVersionHolder,
                    boolean newModule) {
    this(module, type, javaVersionHolder, null, newModule);
  }

  public ModuleData(@NotNull Module module,
                    @NotNull MavenModuleType type,
                    @NotNull MavenJavaVersionHolder javaVersionHolder,
                    @Nullable Module mainModule,
                    boolean newModule) {
    this.module = module;
    this.type = type;
    this.javaVersionHolder = javaVersionHolder;
    this.mainModule = mainModule;
    this.newModule = newModule;
  }

  public @NotNull String getModuleName() {
    return module.getName();
  }

  public @NotNull Module getModule() {
    return module;
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
  public MavenModuleType getType() {
    return type;
  }

  @Nullable
  public Module getMainModule() {
    return mainModule;
  }

  public boolean isNewModule() {
    return newModule;
  }

  @Override
  public String toString() {
    return module.getName();
  }
}
