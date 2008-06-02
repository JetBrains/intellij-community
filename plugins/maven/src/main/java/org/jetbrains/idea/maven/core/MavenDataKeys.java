package org.jetbrains.idea.maven.core;

import com.intellij.openapi.actionSystem.DataKey;
import org.jetbrains.idea.maven.project.MavenProjectModel;

import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenDataKeys {
  public static final DataKey<List<String>> MAVEN_GOALS_KEY = DataKey.create("MAVEN_GOALS");
  public static final DataKey<List<String>> MAVEN_PROFILES_KEY = DataKey.create("MAVEN_PROFILES");
  public static final DataKey<List<MavenProjectModel>> MAVEN_PROJECT_NODES = DataKey.create("MAVEN_PROJECT_NODES");
}
