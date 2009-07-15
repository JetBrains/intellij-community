package org.jetbrains.idea.maven.embedder;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import org.apache.maven.embedder.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.utils.JDOMReader;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.net.URL;

public class MavenEmbedderFactory {
  @NonNls private static final String PROP_MAVEN_HOME = "maven.home";
  @NonNls private static final String PROP_USER_HOME = "user.home";
  @NonNls private static final String ENV_M2_HOME = "M2_HOME";

  @NonNls private static final String M2_DIR = "m2";
  @NonNls private static final String BIN_DIR = "bin";
  @NonNls private static final String DOT_M2_DIR = ".m2";
  @NonNls private static final String CONF_DIR = "conf";
  @NonNls private static final String LIB_DIR = "lib";
  @NonNls private static final String M2_CONF_FILE = "m2.conf";

  @NonNls private static final String REPOSITORY_DIR = "repository";

  @NonNls private static final String LOCAL_REPOSITORY_TAG = "localRepository";

  @NonNls private static final String[] basicPhases = {"clean", "validate", "compile", "test", "package", "install", "deploy", "site"};
  @NonNls private static final String[] phases = {"clean", "validate", "generate-sources", "process-sources", "generate-resources",
    "process-resources", "compile", "process-classes", "generate-test-sources", "process-test-sources", "generate-test-resources",
    "process-test-resources", "test-compile", "test", "package", "pre-integration-test", "integration-test", "post-integration-test",
    "verify", "install", "site", "deploy"};

  private static volatile Properties mySystemPropertiesCache;
  private static final String SUPER_POM_PATH = "org/apache/maven/project/" + MavenConstants.SUPER_POM_XML;

  @Nullable
  public static File resolveMavenHomeDirectory(@Nullable String overrideMavenHome) {
    if (!StringUtil.isEmptyOrSpaces(overrideMavenHome)) {
      return new File(overrideMavenHome);
    }

    final String m2home = System.getenv(ENV_M2_HOME);
    if (!StringUtil.isEmptyOrSpaces(m2home)) {
      final File homeFromEnv = new File(m2home);
      if (isValidMavenHome(homeFromEnv)) {
        return homeFromEnv;
      }
    }

    String userHome = System.getProperty(PROP_USER_HOME);
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
  public static File resolveGlobalSettingsFile(@Nullable String overrideMavenHome) {
    File directory = resolveMavenHomeDirectory(overrideMavenHome);
    if (directory == null) return null;

    return new File(new File(directory, CONF_DIR), MavenConstants.SETTINGS_XML);
  }

  @NotNull
  public static VirtualFile resolveSuperPomFile(@Nullable String overrideMavenHome) {
    VirtualFile result = doResolveSuperPomFile(overrideMavenHome);
    if (result == null) {
      URL resource = MavenEmbedderFactory.class.getResource("/" + SUPER_POM_PATH);
      return VfsUtil.findFileByURL(resource);
    }
    return result;
  }

  @Nullable
  private static VirtualFile doResolveSuperPomFile(String overrideMavenHome) {
    File lib = resolveMavenLib(overrideMavenHome);
    if (lib == null) return null;

    VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(lib);
    if (file == null) return null;

    VirtualFile root = JarFileSystem.getInstance().getJarRootForLocalFile(file);
    if (root == null) return null;

    return root.findFileByRelativePath(SUPER_POM_PATH);
  }

  private static File resolveMavenLib(String overrideMavenHome) {
    File directory = resolveMavenHomeDirectory(overrideMavenHome);
    if (directory == null) return null;
    File libs = new File(directory, LIB_DIR);
    Pattern pattern = Pattern.compile("maven-\\d+\\.\\d+\\.\\d+-uber\\.jar");
    for (File each : libs.listFiles()) {
      if (pattern.matcher(each.getName()).matches()) {
        return each;
      }
    }
    return null;
  }

  @Nullable
  public static File resolveUserSettingsFile(@Nullable String overrideSettingsFile) {
    if (!StringUtil.isEmptyOrSpaces(overrideSettingsFile)) return new File(overrideSettingsFile);

    String userHome = System.getProperty(PROP_USER_HOME);
    if (StringUtil.isEmptyOrSpaces(userHome)) return null;

    return new File(new File(userHome, DOT_M2_DIR), MavenConstants.SETTINGS_XML);
  }

  @Nullable
  public static File resolveLocalRepository(@Nullable String mavenHome, @Nullable String userSettings, @Nullable String override) {
    if (!StringUtil.isEmpty(override)) {
      return new File(override);
    }

    final File userSettingsFile = resolveUserSettingsFile(userSettings);
    if (userSettingsFile != null) {
      final String fromUserSettings = getRepositoryFromSettings(userSettingsFile);
      if (!StringUtil.isEmpty(fromUserSettings)) {
        return new File(fromUserSettings);
      }
    }

    final File globalSettingsFile = resolveGlobalSettingsFile(mavenHome);
    if (globalSettingsFile != null) {
      final String fromGlobalSettings = getRepositoryFromSettings(globalSettingsFile);
      if (!StringUtil.isEmpty(fromGlobalSettings)) {
        return new File(fromGlobalSettings);
      }
    }

    return new File(new File(System.getProperty(PROP_USER_HOME), DOT_M2_DIR), REPOSITORY_DIR);
  }

  private static String getRepositoryFromSettings(File file) {
    try {
      FileInputStream is = new FileInputStream(file);
      try {
        JDOMReader reader = new JDOMReader(is);
        return reader.getChildText(reader.getRootElement(), LOCAL_REPOSITORY_TAG);
      }
      finally {
        is.close();
      }
    }
    catch (IOException ignore) {
      return null;
    }
  }

  public static List<String> getBasicPhasesList() {
    return Arrays.asList(basicPhases);
  }

  public static List<String> getPhasesList() {
    return Arrays.asList(phases);
  }

  public static MavenEmbedderWrapper createEmbedder(MavenGeneralSettings settings, Map context) {
    Configuration configuration = new DefaultConfiguration();

    configuration.setConfigurationCustomizer(MavenEmbedderWrapper.createCustomizer(context));
    configuration.setClassLoader(settings.getClass().getClassLoader());
    configuration.setLocalRepository(settings.getEffectiveLocalRepository());

    MavenEmbedderLogger logger = new MavenConsoleLogger();
    logger.setThreshold(settings.getOutputLevel());
    configuration.setMavenEmbedderLogger(logger);

    File userSettingsFile = resolveUserSettingsFile(settings.getMavenSettingsFile());
    if (userSettingsFile != null) {
      configuration.setUserSettingsFile(userSettingsFile);
    }

    File globalSettingsFile = resolveGlobalSettingsFile(settings.getMavenHome());
    if (globalSettingsFile != null) {
      configuration.setGlobalSettingsFile(globalSettingsFile);
    }

    configuration.setSystemProperties(collectSystemProperties());

    validate(configuration);

    System.setProperty(PROP_MAVEN_HOME, settings.getMavenHome());

    try {
      MavenEmbedder e = new MavenEmbedder(configuration);
      e.getSettings().setUsePluginRegistry(settings.isUsePluginRegistry());
      return new MavenEmbedderWrapper(e);
    }
    catch (MavenEmbedderException e) {
      MavenLog.LOG.info(e);
      throw new RuntimeException(e);
    }
  }

  public static Properties collectSystemProperties() {
    if (mySystemPropertiesCache == null) {
      Properties result = new Properties();
      result.putAll(MavenUtil.getSystemProperties());

      Properties envVars = MavenUtil.getEnvProperties();
      for (Map.Entry<Object, Object> each : envVars.entrySet()) {
        result.setProperty("env." + each.getKey().toString(), each.getValue().toString());
      }
      mySystemPropertiesCache = result;
    }

    return mySystemPropertiesCache;
  }

  private static void validate(Configuration configuration) {
    ConfigurationValidationResult result = MavenEmbedder.validateConfiguration(configuration);

    if (!result.isValid()) {
      if (result.getGlobalSettingsException() != null) {
        configuration.setGlobalSettingsFile(null);
      }
      if (result.getUserSettingsException() != null) {
        configuration.setUserSettingsFile(null);
      }
    }
  }
}