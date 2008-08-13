package org.jetbrains.idea.maven.embedder;

import com.intellij.openapi.util.text.StringUtil;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.embedder.*;
import org.apache.maven.extension.ExtensionManager;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.JDOMReader;
import org.jetbrains.idea.maven.project.MavenProjectsTree;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class MavenEmbedderFactory {
  @NonNls private static final String PROP_MAVEN_HOME = "maven.home";
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

  private static volatile Properties mySystemPropertiesCache;

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
      final File file = new File(new File(directory, CONF_DIR), SETTINGS_FILE);
      if (file.exists()) {
        return file;
      }
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
      final File file = new File(new File(userHome, DOT_M2_DIR), SETTINGS_FILE);
      if (file.exists()) {
        return file;
      }
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
      final String fromUserSettings = MavenEmbedderFactory.getRepositoryFromSettings(userSettingsFile);
      if (!StringUtil.isEmpty(fromUserSettings)) {
        return new File(fromUserSettings);
      }
    }

    final File globalSettingsFile = resolveGlobalSettingsFile(mavenHome);
    if (globalSettingsFile != null) {
      final String fromGlobalSettings = MavenEmbedderFactory.getRepositoryFromSettings(globalSettingsFile);
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

  public static List<String> getStandardPhasesList() {
    return Arrays.asList(standardPhases);
  }

  public static List<String> getStandardGoalsList() {
    return Arrays.asList(standardGoals);
  }

  public static MavenEmbedderWrapper createEmbedderForRead(MavenCoreSettings settings) {
    return createEmbedderForRead(settings, null);
  }

  public static MavenEmbedderWrapper createEmbedderForRead(MavenCoreSettings settings,
                                                           MavenProjectsTree projectsTree) {
    return createEmbedder(settings, new MyCustomizer(projectsTree, false));
  }

  public static MavenEmbedderWrapper createEmbedderForResolve(MavenCoreSettings settings,
                                                              MavenProjectsTree projectsTree) {
    return createEmbedder(settings, new MyCustomizer(projectsTree, true));
  }

  public static MavenEmbedderWrapper createEmbedderForExecute(MavenCoreSettings settings) {
    return createEmbedder(settings, null);
  }

  private static MavenEmbedderWrapper createEmbedder(MavenCoreSettings settings, ContainerCustomizer customizer) {
    return createEmbedder(settings.getMavenHome(),
                          settings.getEffectiveLocalRepository(),
                          settings.getMavenSettingsFile(),
                          settings.getClass().getClassLoader(),
                          customizer);
  }

  private static MavenEmbedderWrapper createEmbedder(String mavenHome,
                                                     File localRepo,
                                                     String userSettings,
                                                     ClassLoader classLoader,
                                                     ContainerCustomizer customizer) {
    Configuration configuration = new DefaultConfiguration();

    configuration.setConfigurationCustomizer(customizer);
    configuration.setClassLoader(classLoader);
    configuration.setLocalRepository(localRepo);

    MavenEmbedderConsoleLogger l = new MavenEmbedderConsoleLogger();
    l.setThreshold(MavenEmbedderLogger.LEVEL_WARN);
    configuration.setMavenEmbedderLogger(l);

    File userSettingsFile = resolveUserSettingsFile(userSettings);
    if (userSettingsFile != null) {
      configuration.setUserSettingsFile(userSettingsFile);
    }

    File globalSettingsFile = resolveGlobalSettingsFile(mavenHome);
    if (globalSettingsFile != null) {
      configuration.setGlobalSettingsFile(globalSettingsFile);
    }

    configuration.setSystemProperties(collectSystemProperties());

    validate(configuration);

    System.setProperty(PROP_MAVEN_HOME, mavenHome);

    try {
      return new MavenEmbedderWrapper(new MavenEmbedder(configuration));
    }
    catch (MavenEmbedderException e) {
      MavenLog.LOG.info(e);
      throw new RuntimeException(e);
    }
  }

  public static Properties collectSystemProperties() {
    if (mySystemPropertiesCache == null) {
      Properties result = new Properties();
      result.putAll(System.getProperties());

      try {
        Properties envVars = CommandLineUtils.getSystemEnvVars();
        for (Map.Entry<Object, Object> each : envVars.entrySet()) {
          result.setProperty("env." + each.getKey().toString(), each.getValue().toString());
        }
      }
      catch (IOException e) {
        MavenLog.LOG.warn(e);
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

  public static class MyCustomizer implements ContainerCustomizer {
    private MavenProjectsTree myProjectsTree;
    private boolean isOnline;

    public MyCustomizer(MavenProjectsTree projectsTree, boolean online) {
      myProjectsTree = projectsTree;
      isOnline = online;
    }

    public void customize(PlexusContainer c) {
      ComponentDescriptor d;

      d = c.getComponentDescriptor(ArtifactFactory.ROLE);
      d.setImplementation(CustomArtifactFactory.class.getName());

      if (myProjectsTree != null) {
        c.getContext().put(CustomArtifactResolver.MAVEN_PROJECT_MODEL_MANAGER, myProjectsTree);

        d = c.getComponentDescriptor(ArtifactResolver.ROLE);
        d.setImplementation(CustomArtifactResolver.class.getName());
      }

      d = c.getComponentDescriptor(WagonManager.ROLE);
      d.setImplementation(CustomWagonManager.class.getName());
      c.getContext().put(CustomWagonManager.IS_ONLINE, isOnline);

      d = c.getComponentDescriptor(ExtensionManager.class.getName());
      d.setImplementation(CustomExtensionManager.class.getName());
    }
  }
}
