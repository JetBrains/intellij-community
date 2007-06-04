package org.jetbrains.idea.maven.core.util;

import org.apache.maven.embedder.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
public class MavenEnv {
  @NonNls public static final String POM_FILE = "pom.xml";

  @NonNls private static final String USER_HOME_DIR = "user.home";
  @NonNls private static final String DOT_M2_DIR = ".m2";
  @NonNls private static final String SETTINGS_FILE = "settings.xml";
  @NonNls private static final String REPOSITORY_DIR = "repository";
  @NonNls private static final String LOCAL_REPOSITORY_TAG = "localRepository";
  @NonNls private static final String[] standardPhases = {"clean", "compile", "test", "package", "install", "site"};
  @NonNls private static final String[] standardGoals = {"clean", "validate", "generate-sources", "process-sources", "generate-resources",
    "process-resources", "compile", "process-classes", "generate-test-sources", "process-test-sources", "generate-test-resources",
    "process-test-resources", "test-compile", "test", "package", "pre-integration-test", "integration-test", "post-integration-test",
    "verify", "install", "site", "deploy"};

  private static String getChild(final String dir, final String child) {
    return new File(dir, child).getPath();
  }

  private static String getHomeDirectory() {
    return getChild(System.getProperty(USER_HOME_DIR), DOT_M2_DIR);
  }

  public static String getDefaultSettingsFile() {
    return getChild(getHomeDirectory(), SETTINGS_FILE);
  }

  public static String getDefaultLocalRepository() {
    return getChild(getHomeDirectory(), REPOSITORY_DIR);
  }

  public static String getRepositoryFromSettings(File file) {
    JDOMReader reader = new JDOMReader();
    try {
      reader.init(new FileInputStream(file));
    }
    catch (IOException ignore) {
    }
    return reader.getChildText(reader.getRootElement(), LOCAL_REPOSITORY_TAG);
  }

  public static List<String> getStandardPhasesList() {
    return Arrays.asList(standardPhases);
  }

  public static List<String> getStandardGoalsList() {
    return Arrays.asList(standardGoals);
  }

  @NotNull
  public static MavenEmbedder createEmbedder(@NotNull final String userSettings, ClassLoader classLoader) throws MavenEmbedderException {

    Configuration configuration = new DefaultConfiguration();

    configuration
      .setUserSettingsFile(new File(userSettings))
      .setGlobalSettingsFile(new File(getDefaultSettingsFile()))
      .setClassLoader(classLoader);

    ConfigurationValidationResult validationResult = MavenEmbedder.validateConfiguration(configuration);

    if (!validationResult.isValid()) {
      throw new MavenEmbedderException(toString(validationResult));
    }

    return new MavenEmbedder(configuration);
  }

  @NonNls
  private static String toString(final ConfigurationValidationResult validationResult) {
    return (validationResult.isGlobalSettingsFilePresent() ? "" : "Global settings file missing ") +
           (validationResult.isGlobalSettingsFileParses() ? "" : "Global settings file malformed ") +
           (validationResult.isUserSettingsFilePresent() ? "" : "User settings file missing ") +
           (validationResult.isUserSettingsFileParses() ? "" : "User settings file malformed");
  }
}
