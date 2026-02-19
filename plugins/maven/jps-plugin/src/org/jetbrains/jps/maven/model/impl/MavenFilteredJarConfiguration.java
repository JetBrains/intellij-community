// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.maven.model.impl;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

@ApiStatus.Internal
public class MavenFilteredJarConfiguration {
  @Tag("classifier") public @NotNull String classifier;

  @Tag("includes")
  public Set<String> includes = new HashSet<>();

  @Tag("excludes")
  public Set<String> excludes = new HashSet<>();

  @Tag("moduleName")
  public @NotNull @NlsSafe String moduleName;

  @Tag("isTest")
  public boolean isTest;

  @Tag("originalOutput")
  public @NotNull String originalOutput;

  @Tag("jarOutput")
  public @NotNull String jarOutput;

  @Tag("name")
  public @NotNull String name;
}
