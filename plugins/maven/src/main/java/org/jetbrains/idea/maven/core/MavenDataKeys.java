package org.jetbrains.idea.maven.core;

import com.intellij.openapi.actionSystem.DataKey;

import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenDataKeys {
  public static final DataKey<List<String>> MAVEN_GOALS_KEY = DataKey.create("MAVEN_GOALS");
  public static final DataKey<List<String>> MAVEN_PROFILES_KEY = DataKey.create("MAVEN_PROFILES");
}
