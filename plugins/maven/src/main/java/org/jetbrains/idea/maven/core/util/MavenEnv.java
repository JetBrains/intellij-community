package org.jetbrains.idea.maven.core.util;

import com.intellij.openapi.util.text.StringUtil;
import org.apache.maven.embedder.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @NonNls private static final String PROP_USER_HOME = "user.home";
  @NonNls private static final String ENV_M2_HOME = "M2_HOME";

  @NonNls private static final String M2_DIR = "m2";
  @NonNls private static final String BIN_DIR = "bin";
  @NonNls private static final String DOT_M2_DIR = ".m2";
  @NonNls private static final String CONF_DIR = "conf";
  @NonNls private static final String SETTINGS_FILE = "settings.xml";
  @NonNls private static final String M2_CONF_FILE = "m2.conf";

  @NonNls private static final String REPOSITORY_DIR = "repository";

  @NonNls private static final String LOCAL_REPOSITORY_TAG = "localRepository";

  @NonNls private static final String[] standardPhases = {"clean", "compile", "test", "package", "install", "site"};
  @NonNls private static final String[] standardGoals = {"clean", "validate", "generate-sources", "process-sources", "generate-resources",
    "process-resources", "compile", "process-classes", "generate-test-sources", "process-test-sources", "generate-test-resources",
    "process-test-resources", "test-compile", "test", "package", "pre-integration-test", "integration-test", "post-integration-test",
    "verify", "install", "site", "deploy"};

  @Nullable
  public static File resolveMavenHomeDirectory(@Nullable final String override) {
    if (!StringUtil.isEmptyOrSpaces(override)) {
      return new File(override);
    }

    final String m2home = System.getenv(ENV_M2_HOME);
    if (!StringUtil.isEmptyOrSpaces(m2home)) {
      final File homeFromEnv = new File(m2home);
      if (isValidMavenHome(homeFromEnv)) {
        return homeFromEnv;
      }
    }

    final String userHome = System.getProperty(PROP_USER_HOME);
    if (!StringUtil.isEmptyOrSpaces(userHome)) {
      final File underUserHome = new File(userHome, M2_DIR);
      if (isValidMavenHome(underUserHome)) {
        return underUserHome;
      }
    }

    return null;
  }

  public static boolean isValidMavenHome(File home) {
    return getMavenConfFile(home).exists();
  }

  public static File getMavenConfFile(File mavenHome) {
    return new File(new File(mavenHome, BIN_DIR), M2_CONF_FILE);
  }

  @Nullable
  public static File resolveGlobalSettingsFile(final String override) {
    final File directory = resolveMavenHomeDirectory(override);
    if (directory != null) {
      return new File(new File(directory, CONF_DIR), SETTINGS_FILE);
    }
    return null;
  }

  @Nullable
  public static File resolveUserSettingsFile(final String override) {
    if (!StringUtil.isEmptyOrSpaces(override)) {
      return new File(override);
    }
    final String userHome = System.getProperty(PROP_USER_HOME);
    if (!StringUtil.isEmptyOrSpaces(userHome)) {
      return new File(new File(userHome, DOT_M2_DIR), SETTINGS_FILE);
    }
    return null;
  }

  @Nullable
  public static File resolveLocalRepository(final String mavenHome, final String userSettings, final String override) {
    if (!StringUtil.isEmpty(override)) {
      return new File(override);
    }

    final File userSettingsFile = resolveUserSettingsFile(userSettings);
    if (userSettingsFile != null) {
      final String fromUserSettings = MavenEnv.getRepositoryFromSettings(userSettingsFile);
      if (!StringUtil.isEmpty(fromUserSettings)) {
        return new File(fromUserSettings);
      }
    }

    final File globalSettingsFile = resolveGlobalSettingsFile(mavenHome);
    if (globalSettingsFile != null) {
      final String fromGlobalSettings = MavenEnv.getRepositoryFromSettings(globalSettingsFile);
      if (!StringUtil.isEmpty(fromGlobalSettings)) {
        return new File(fromGlobalSettings);
      }
    }

    final String userHome = System.getProperty(PROP_USER_HOME);
    if (!StringUtil.isEmptyOrSpaces(userHome)) {
      return new File(new File(userHome, DOT_M2_DIR), REPOSITORY_DIR);
    }

    return null;
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
  public static MavenEmbedder createEmbedder(final String mavenHome, final String userSettings, ClassLoader classLoader)
    throws MavenEmbedderException {

    Configuration configuration = new DefaultConfiguration();

    final File userSettingsFile = resolveUserSettingsFile(userSettings);
    if (userSettingsFile != null) {
      configuration.setUserSettingsFile(userSettingsFile);
    }

    final File globalSettingsFile = resolveGlobalSettingsFile(mavenHome);
    if (globalSettingsFile != null) {
      configuration.setGlobalSettingsFile(globalSettingsFile);
    }

    configuration.setClassLoader(classLoader);

    ConfigurationValidationResult validationResult = MavenEmbedder.validateConfiguration(configuration);

    if (!validationResult.isValid()) {
      throw new MavenEmbedderException(getErrorString(validationResult, globalSettingsFile, userSettingsFile));
    }

    return new MavenEmbedder(configuration);
  }

  @NonNls
  private static String getErrorString(final ConfigurationValidationResult validationResult,
                                       final File globalSettingsFile,
                                       final File userSettingsFile) {
    return getErrorString(validationResult.isGlobalSettingsFilePresent(), "Global settings file missing", globalSettingsFile) +
           getErrorString(validationResult.isGlobalSettingsFileParses(), "Global settings file malformed", globalSettingsFile) +
           getErrorString(validationResult.isUserSettingsFilePresent(), "User settings file missing", userSettingsFile) +
           getErrorString(validationResult.isUserSettingsFileParses(), "User settings file malformed", userSettingsFile);
  }

  @NonNls
  private static String getErrorString(final boolean flag, @NonNls final String message, final File file) {
    return flag ? "" : (message + " (" + (file != null ? file.getPath() : "null") + ").");
  }
}
