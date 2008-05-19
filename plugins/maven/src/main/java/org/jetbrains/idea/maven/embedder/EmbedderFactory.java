package org.jetbrains.idea.maven.embedder;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.manager.WagonManager;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.embedder.*;
import org.apache.maven.extension.ExtensionManager;
import org.apache.maven.project.build.model.ModelLineageBuilder;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.MavenCoreSettings;
import org.jetbrains.idea.maven.core.MavenLog;
import org.jetbrains.idea.maven.core.util.JDOMReader;
import org.jetbrains.idea.maven.core.util.ProjectId;
import org.jetbrains.idea.maven.project.MavenException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class EmbedderFactory {
  private static final Logger LOG = Logger.getInstance("#" + EmbedderFactory.class.getName());

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
      final String fromUserSettings = EmbedderFactory.getRepositoryFromSettings(userSettingsFile);
      if (!StringUtil.isEmpty(fromUserSettings)) {
        return new File(fromUserSettings);
      }
    }

    final File globalSettingsFile = resolveGlobalSettingsFile(mavenHome);
    if (globalSettingsFile != null) {
      final String fromGlobalSettings = EmbedderFactory.getRepositoryFromSettings(globalSettingsFile);
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

  public static MavenEmbedder createEmbedderForRead(MavenCoreSettings settings) throws MavenException {
    return createEmbedderForRead(settings, null);
  }

  public static MavenEmbedder createEmbedderForRead(MavenCoreSettings settings, Map<ProjectId, VirtualFile> projectMapping) throws MavenException {
    return createEmbedder(settings, new ReadingCustomizer(projectMapping));
  }

  public static MavenEmbedder createEmbedderForResolve(MavenCoreSettings settings, Map<ProjectId, VirtualFile> projectMapping)
      throws MavenException {
    return createEmbedder(settings, new ResolvingCustomizer(projectMapping));
  }

  public static MavenEmbedder createEmbedderForExecute(MavenCoreSettings settings) throws MavenException {
    return createEmbedder(settings, null);
  }

  private static MavenEmbedder createEmbedder(MavenCoreSettings settings, ContainerCustomizer customizer) throws MavenException {
    return createEmbedder(settings.getMavenHome(), settings.getEffectiveLocalRepository(), settings.getMavenSettingsFile(),
                          settings.getClass().getClassLoader(), customizer);
  }

  private static MavenEmbedder createEmbedder(String mavenHome,
                                              File localRepo,
                                              String userSettings,
                                              ClassLoader classLoader,
                                              ContainerCustomizer customizer) throws MavenException {
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
      return new MavenEmbedder(configuration);
    }
    catch (MavenEmbedderException e) {
      LOG.info(e);
      throw new MavenException(e);
    }
  }

  private static Properties collectSystemProperties() {
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

    return result;
  }

  private static void validate(Configuration configuration) throws MavenException {
    ConfigurationValidationResult result = MavenEmbedder.validateConfiguration(configuration);

    if (!result.isValid()) {
      List<Exception> ee = new ArrayList<Exception>();

      Exception ex1 = result.getGlobalSettingsException();
      Exception ex2 = result.getUserSettingsException();
      if (ex1 != null) ee.add(ex1);
      if (ex2 != null) ee.add(ex2);

      throw new MavenException(ee);
    }
  }

  public static void releaseEmbedder(MavenEmbedder mavenEmbedder) {
    if (mavenEmbedder != null) {
      try {
        mavenEmbedder.stop();
      }
      catch (MavenEmbedderException ignore) {
      }
    }
  }

  public static CustomExtensionManager getExtensionManager(MavenEmbedder e) {
    try {
      return (CustomExtensionManager)e.getPlexusContainer().lookup(ExtensionManager.class.getName());
    }
    catch (ComponentLookupException ex) {
      throw new RuntimeException(ex);
    }
  }

  public static class ReadingCustomizer implements ContainerCustomizer {
    private Map<ProjectId, VirtualFile> myProjectMapping;

    public ReadingCustomizer(Map<ProjectId, VirtualFile> projectMapping) {

      myProjectMapping = projectMapping;
    }

    public void customize(PlexusContainer c) {
      c.getContext().put("MavenProjectsMapping", myProjectMapping);

      ComponentDescriptor d = c.getComponentDescriptor(WagonManager.ROLE);
      d.setImplementation(CustomWagonManager.class.getName());

      d = c.getComponentDescriptor(ArtifactFactory.ROLE);
      d.setImplementation(CustomArtifactFactory.class.getName());

      d = c.getComponentDescriptor(ArtifactResolver.ROLE);
      d.setImplementation(CustomArtifactResolver.class.getName());

      d = c.getComponentDescriptor(ModelLineageBuilder.ROLE);
      d.setImplementation(CustomLineageBuilder.class.getName());
    }
  }

  public static class ResolvingCustomizer implements ContainerCustomizer {
    private Map<ProjectId, VirtualFile> myMapping;

    public ResolvingCustomizer(Map<ProjectId, VirtualFile> projectMapping) {
      myMapping = projectMapping;
    }

    public void customize(PlexusContainer c) {
      c.getContext().put("MavenProjectsMapping", myMapping);

      ComponentDescriptor d = c.getComponentDescriptor(ArtifactFactory.ROLE);
      d.setImplementation(CustomArtifactFactory.class.getName());

      d = c.getComponentDescriptor(ArtifactResolver.ROLE);
      d.setImplementation(CustomArtifactResolver.class.getName());
    }
  }
}
