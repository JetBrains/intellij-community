// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.actionSystem.DataKey;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenProfileKind;

import javax.swing.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;


public final class MavenDataKeys {
  public static final DataKey<List<String>> MAVEN_GOALS = DataKey.create("MAVEN_GOALS");
  public static final DataKey<RunnerAndConfigurationSettings> RUN_CONFIGURATION = DataKey.create("MAVEN_RUN_CONFIGURATION");
  public static final DataKey<Map<String, MavenProfileKind>> MAVEN_PROFILES = DataKey.create("MAVEN_PROFILES");
  public static final DataKey<Collection<MavenArtifact>> MAVEN_DEPENDENCIES = DataKey.create("MAVEN_DEPENDENCIES");
  public static final DataKey<JTree> MAVEN_PROJECTS_TREE = DataKey.create("MAVEN_PROJECTS_TREE");

  private MavenDataKeys() {
  }
}
