/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.embedder;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashSet;
import org.apache.maven.settings.MavenSettingsBuilder;
import org.apache.maven.settings.RuntimeInfo;
import org.apache.maven.settings.Settings;
import org.codehaus.classworlds.ClassWorld;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenGeneralSettings;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

public class MavenEmbedderFactory {
  private static final String PROP_MAVEN_HOME = "maven.home";
  private static final String PROP_USER_HOME = "user.home";
  private static final String ENV_M2_HOME = "M2_HOME";

  private static final String M2_DIR = "m2";
  private static final String BIN_DIR = "bin";
  private static final String DOT_M2_DIR = ".m2";
  private static final String CONF_DIR = "conf";
  private static final String LIB_DIR = "lib";
  private static final String M2_CONF_FILE = "m2.conf";

  private static final String REPOSITORY_DIR = "repository";

  private static final List<String> PHASES =
    Arrays.asList("clean", "validate", "generate-sources", "process-sources", "generate-resources",
                  "process-resources", "compile", "process-classes", "generate-test-sources", "process-test-sources",
                  "generate-test-resources",
                  "process-test-resources", "test-compile", "test", "prepare-package", "package", "pre-integration-test", "integration-test",
                  "post-integration-test",
                  "verify", "install", "site", "deploy");
  private static final List<String> BASIC_PHASES =
    Arrays.asList("clean", "validate", "compile", "test", "package", "install", "deploy", "site");

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
    File[] files = libs.listFiles();
    if (files != null) {
      Pattern pattern = Pattern.compile("maven-\\d+\\.\\d+\\.\\d+-uber\\.jar");
      for (File each : files) {
        if (pattern.matcher(each.getName()).matches()) {
          return each;
        }
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

  @NotNull
  public static File resolveLocalRepository(@Nullable String mavenHome, @Nullable String userSettings, @Nullable String override) {
    File result = doResolveLocalRepository(mavenHome, userSettings, override);
    try {
      return result.getCanonicalFile();
    }
    catch (IOException e) {
      return result;
    }
  }

  @NotNull
  private static File doResolveLocalRepository(String mavenHome, String userSettings, String override) {
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

  @Nullable
  private static String getRepositoryFromSettings(final File file) {
    try {
      byte[] bytes = FileUtil.loadFileBytes(file);
      return expandProperties(MavenJDOMUtil.findChildValueByPath(MavenJDOMUtil.read(bytes, null), "localRepository", null));
    }
    catch (IOException e) {
      return null;
    }
  }

  private static String expandProperties(String text) {
    if (StringUtil.isEmptyOrSpaces(text)) return text;
    Properties props = collectSystemProperties();
    for (Map.Entry<Object, Object> each : props.entrySet()) {
      text = text.replace("${" + each.getKey() + "}", (CharSequence)each.getValue());
    }
    return text;
  }

  public static List<String> getBasicPhasesList() {
    return BASIC_PHASES;
  }

  public static List<String> getPhasesList() {
    return PHASES;
  }

  public static MavenEmbedderWrapper createEmbedder(MavenGeneralSettings generalSettings) {
    DefaultPlexusContainer container;
    try {
      container = new DefaultPlexusContainer();
    }
    catch (RuntimeException e) {
      String s = "Cannot initialize Maven. Please make sure that your IDEA installation is correct and has no old libraries.";
      throw new RuntimeException(s, e);
    }

    container.setClassWorld(new ClassWorld("plexus.core", generalSettings.getClass().getClassLoader()));
    CustomLoggerManager loggerManager = new CustomLoggerManager(generalSettings.getLoggingLevel());
    container.setLoggerManager(loggerManager);

    try {
      container.initialize();
      container.start();
    }
    catch (PlexusContainerException e) {
      MavenLog.LOG.error(e);
      throw new RuntimeException(e);
    }

    File mavenHome = generalSettings.getEffectiveMavenHome();
    if (mavenHome != null) {
      System.setProperty(PROP_MAVEN_HOME, mavenHome.getPath());
    }

    Settings settings = buildSettings(container, generalSettings);

    return new MavenEmbedderWrapper(container, settings, loggerManager.getLogger(), generalSettings);
  }

  private static Settings buildSettings(PlexusContainer container, MavenGeneralSettings generalSettings) {
    File file = generalSettings.getEffectiveGlobalSettingsIoFile();
    if (file != null) {
      System.setProperty(MavenSettingsBuilder.ALT_GLOBAL_SETTINGS_XML_LOCATION, file.getPath());
    }

    Settings settings = null;

    try {
      MavenSettingsBuilder builder = (MavenSettingsBuilder)container.lookup(MavenSettingsBuilder.ROLE);

      File userSettingsFile = generalSettings.getEffectiveUserSettingsIoFile();
      if (userSettingsFile != null && userSettingsFile.exists() && !userSettingsFile.isDirectory()) {
        settings = builder.buildSettings(userSettingsFile, false);
      }

      if (settings == null) {
        settings = builder.buildSettings();
      }
    }
    catch (ComponentLookupException e) {
      MavenLog.LOG.error(e);
    }
    catch (IOException e) {
      MavenLog.LOG.warn(e);
    }
    catch (XmlPullParserException e) {
      MavenLog.LOG.warn(e);
    }

    if (settings == null) {
      settings = new Settings();
    }

    File localRepository = generalSettings.getEffectiveLocalRepository();
    if (localRepository != null) {
      settings.setLocalRepository(localRepository.getPath());
    }

    settings.setOffline(generalSettings.isWorkOffline());
    settings.setInteractiveMode(false);
    settings.setUsePluginRegistry(generalSettings.isUsePluginRegistry());

    RuntimeInfo runtimeInfo = new RuntimeInfo(settings);
    runtimeInfo.setPluginUpdateOverride(generalSettings.getPluginUpdatePolicy() == MavenExecutionOptions.PluginUpdatePolicy.UPDATE);
    settings.setRuntimeInfo(runtimeInfo);

    return settings;
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

  public static void resetSystemPropertiesCacheInTests() {
    mySystemPropertiesCache = null;
  }
}