package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.actionSystem.DataKey;

import java.util.List;


public class MavenDataKeys {
  public static final DataKey<List<String>> MAVEN_GOALS = DataKey.create("MAVEN_GOALS");
  public static final DataKey<List<String>> MAVEN_PROFILES = DataKey.create("MAVEN_PROFILES");
}
