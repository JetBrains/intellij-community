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
package org.jetbrains.idea.maven.utils;

import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.facade.MavenFacadeManager;
import org.jetbrains.idea.maven.facade.MavenFacadeUtil;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class MavenUtil {
  public static final String MAVEN_NOTIFICATION_GROUP = "Maven";
  public static final String SETTINGS_XML = "settings.xml";
  public static final String DOT_M2_DIR = ".m2";
  public static final String PROP_USER_HOME = "user.home";
  public static final String ENV_M2_HOME = "M2_HOME";
  public static final String M2_DIR = "m2";
  public static final String BIN_DIR = "bin";
  public static final String CONF_DIR = "conf";
  public static final String M2_CONF_FILE = "m2.conf";
  public static final String REPOSITORY_DIR = "repository";
  public static final String LIB_DIR = "lib";
  public static final String SUPER_POM_PATH = "org/apache/maven/project/" + MavenConstants.SUPER_POM_XML;

  public static void invokeLater(Project p, Runnable r) {
    invokeLater(p, ModalityState.defaultModalityState(), r);
  }

  public static void invokeLater(final Project p, final ModalityState state, final Runnable r) {
    if (isNoBackgroundMode()) {
      r.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (p.isDisposed()) return;
          r.run();
        }
      }, state);
    }
  }

  public static void invokeAndWait(Project p, Runnable r) {
    invokeAndWait(p, ModalityState.defaultModalityState(), r);
  }

  public static void invokeAndWait(final Project p, final ModalityState state, final Runnable r) {
    if (isNoBackgroundMode()) {
      r.run();
    }
    else {
      if (ApplicationManager.getApplication().isDispatchThread()) {
        r.run();
      }
      else {
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          public void run() {
            if (p.isDisposed()) return;
            r.run();
          }
        }, state);
      }
    }
  }

  public static void invokeAndWaitWriteAction(Project p, final Runnable r) {
    invokeAndWait(p, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(r);
      }
    });
  }

  public static void runDumbAware(final Project project, final Runnable r) {
    if (DumbService.isDumbAware(r)) {
      r.run();
    }
    else {
      DumbService.getInstance(project).runWhenSmart(new Runnable() {
        public void run() {
          if (project.isDisposed()) return;
          r.run();
        }
      });
    }
  }

  public static void runWhenInitialized(final Project project, final Runnable r) {
    if (project.isDisposed()) return;

    if (isNoBackgroundMode()) {
      r.run();
      return;
    }

    if (!project.isInitialized()) {
      StartupManager.getInstance(project).registerPostStartupActivity(r);
      return;
    }

    runDumbAware(project, r);
  }

  public static boolean isNoBackgroundMode() {
    return ApplicationManager.getApplication().isUnitTestMode()
           || ApplicationManager.getApplication().isHeadlessEnvironment();
  }

  public static boolean isInModalContext() {
    if (isNoBackgroundMode()) return false;
    return LaterInvocator.isInModalContext();
  }

  public static void showError(Project project, String title, Throwable e) {
    MavenLog.LOG.warn(title, e);
    Notifications.Bus.notify(new Notification(MAVEN_NOTIFICATION_GROUP, title, e.getMessage(), NotificationType.ERROR), project);
  }

  public static Properties getSystemProperties() {
    Properties result = (Properties)System.getProperties().clone();
    for (String each : new THashSet<String>((Set)result.keySet())) {
      if (each.startsWith("idea.")) {
        result.remove(each);
      }
    }
    return result;
  }

  public static Properties getEnvProperties() {
    Properties reuslt = new Properties();
    for (Map.Entry<String, String> each : System.getenv().entrySet()) {
      if (isMagicalProperty(each.getKey())) continue;
      reuslt.put(each.getKey(), each.getValue());
    }
    return reuslt;
  }

  private static boolean isMagicalProperty(String key) {
    return key.startsWith("=");
  }

  public static File getPluginSystemDir(String folder) {
    // PathManager.getSystemPath() may return relative path
    return new File(PathManager.getSystemPath(), "Maven" + "/" + folder).getAbsoluteFile();
  }

  public static VirtualFile findProfilesXmlFile(VirtualFile pomFile) {
    return pomFile.getParent().findChild(MavenConstants.PROFILES_XML);
  }

  public static File getProfilesXmlIoFile(VirtualFile pomFile) {
    return new File(pomFile.getParent().getPath(), MavenConstants.PROFILES_XML);
  }

  public static <T, U> List<T> collectFirsts(List<Pair<T, U>> pairs) {
    List<T> result = new ArrayList<T>(pairs.size());
    for (Pair<T, ?> each : pairs) {
      result.add(each.first);
    }
    return result;
  }

  public static <T, U> List<U> collectSeconds(List<Pair<T, U>> pairs) {
    List<U> result = new ArrayList<U>(pairs.size());
    for (Pair<T, U> each : pairs) {
      result.add(each.second);
    }
    return result;
  }

  public static List<String> collectPaths(List<VirtualFile> files) {
    return ContainerUtil.map(files, new Function<VirtualFile, String>() {
      public String fun(VirtualFile file) {
        return file.getPath();
      }
    });
  }

  public static List<VirtualFile> collectFiles(Collection<MavenProject> projects) {
    return ContainerUtil.map(projects, new Function<MavenProject, VirtualFile>() {
      public VirtualFile fun(MavenProject project) {
        return project.getFile();
      }
    });
  }

  public static <T> boolean equalAsSets(final Collection<T> collection1, final Collection<T> collection2) {
    return toSet(collection1).equals(toSet(collection2));
  }

  private static <T> Collection<T> toSet(final Collection<T> collection) {
    return (collection instanceof Set ? collection : new THashSet<T>(collection));
  }

  public static <T, U> List<Pair<T, U>> mapToList(Map<T, U> map) {
    return ContainerUtil.map2List(map.entrySet(), new Function<Map.Entry<T, U>, Pair<T, U>>() {
      public Pair<T, U> fun(Map.Entry<T, U> tuEntry) {
        return Pair.create(tuEntry.getKey(), tuEntry.getValue());
      }
    });
  }

  public static String formatHtmlImage(URL url) {
    return "<img src=\"" + url + "\"> ";
  }

  public static void runOrApplyMavenProjectFileTemplate(Project project,
                                                        VirtualFile file,
                                                        MavenId projectId,
                                                        MavenId parentId,
                                                        boolean interactive) throws IOException {
    Properties properties = new Properties();
    Properties conditions = new Properties();
    properties.setProperty("GROUP_ID", projectId.getGroupId());
    properties.setProperty("ARTIFACT_ID", projectId.getArtifactId());
    properties.setProperty("VERSION", projectId.getVersion());
    if (parentId != null) {
      conditions.setProperty("HAS_PARENT", "true");
      properties.setProperty("PARENT_GROUP_ID", parentId.getGroupId());
      properties.setProperty("PARENT_ARTIFACT_ID", parentId.getArtifactId());
      properties.setProperty("PARENT_VERSION", parentId.getVersion());
    }
    runOrApplyFileTemplate(project, file, MavenFileTemplateGroupFactory.MAVEN_PROJECT_XML_TEMPLATE, properties, conditions, interactive);
  }

  public static void runFileTemplate(Project project,
                                     VirtualFile file,
                                     String templateName) throws IOException {
    runOrApplyFileTemplate(project, file, templateName, new Properties(), new Properties(), true);
  }

  private static void runOrApplyFileTemplate(Project project,
                                             VirtualFile file,
                                             String templateName,
                                             Properties properties,
                                             Properties conditions,
                                             boolean interactive) throws IOException {
    FileTemplateManager manager = FileTemplateManager.getInstance();
    FileTemplate fileTemplate = manager.getJ2eeTemplate(templateName);
    Properties allProperties = manager.getDefaultProperties();
    if (!interactive) {
      allProperties.putAll(properties);
    }
    allProperties.putAll(conditions);
    String text = fileTemplate.getText(allProperties);
    Pattern pattern = Pattern.compile("\\$\\{(.*)\\}");
    Matcher matcher = pattern.matcher(text);
    StringBuffer builder = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(builder, "\\$" + matcher.group(1).toUpperCase() + "\\$");
    }
    matcher.appendTail(builder);
    text = builder.toString();

    TemplateImpl template = (TemplateImpl)TemplateManager.getInstance(project).createTemplate("", "", text);
    for (int i = 0; i < template.getSegmentsCount(); i++) {
      if (i == template.getEndSegmentNumber()) continue;
      String name = template.getSegmentName(i);
      String value = "\"" + properties.getProperty(name, "") + "\"";
      template.addVariable(name, value, value, true);
    }

    if (interactive) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file);
      Editor editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
      editor.getDocument().setText("");
      TemplateManager.getInstance(project).startTemplate(editor, template);
    }
    else {
      VfsUtil.saveText(file, template.getTemplateText());
    }
  }

  public static <T extends Collection<Pattern>> T collectPattern(String text, T result) {
    String antPattern = FileUtil.convertAntToRegexp(text.trim());
    try {
      result.add(Pattern.compile(antPattern));
    }
    catch (PatternSyntaxException ignore) {
    }
    return result;
  }

  public static boolean isIncluded(String relativeName, List<Pattern> includes, List<Pattern> excludes) {
    boolean result = false;
    for (Pattern each : includes) {
      if (each.matcher(relativeName).matches()) {
        result = true;
        break;
      }
    }
    if (!result) return false;
    for (Pattern each : excludes) {
      if (each.matcher(relativeName).matches()) return false;
    }
    return true;
  }

  public static void run(Project project, String title, final MavenTask task) throws MavenProcessCanceledException {
    final Exception[] canceledEx = new Exception[1];

    ProgressManager.getInstance().run(new Task.Modal(project, title, true) {
      public void run(@NotNull ProgressIndicator i) {
        try {
          task.run(new MavenProgressIndicator(i));
        }
        catch (MavenProcessCanceledException e) {
          canceledEx[0] = e;
        }
        catch (ProcessCanceledException e) {
          canceledEx[0] = e;
        }
      }
    });
    if (canceledEx[0] instanceof MavenProcessCanceledException) throw (MavenProcessCanceledException)canceledEx[0];
    if (canceledEx[0] instanceof ProcessCanceledException) throw new MavenProcessCanceledException();
  }

  public static MavenTaskHandler runInBackground(final Project project,
                                                 final String title,
                                                 final boolean cancellable,
                                                 final MavenTask task) {
    final MavenProgressIndicator indicator = new MavenProgressIndicator();

    Runnable runnable = new Runnable() {
      public void run() {
        try {
          task.run(indicator);
        }
        catch (MavenProcessCanceledException ignore) {
          indicator.cancel();
        }
        catch (ProcessCanceledException ignore) {
          indicator.cancel();
        }
      }
    };

    if (isNoBackgroundMode()) {
      runnable.run();
      return new MavenTaskHandler() {
        public void waitFor() {
        }
      };
    }
    else {
      final Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(runnable);
      final MavenTaskHandler handler = new MavenTaskHandler() {
        public void waitFor() {
          try {
            future.get();
          }
          catch (InterruptedException e) {
            MavenLog.LOG.error(e);
          }
          catch (ExecutionException e) {
            MavenLog.LOG.error(e);
          }
        }
      };
      invokeLater(project, new Runnable() {
        public void run() {
          if (future.isDone()) return;
          new Task.Backgroundable(project, title, cancellable) {
            public void run(@NotNull ProgressIndicator i) {
              indicator.setIndicator(i);
              handler.waitFor();
            }
          }.queue();
        }
      });
      return handler;
    }
  }

  @Nullable
  public static File resolveMavenHomeDirectory(@Nullable String overrideMavenHome) {
    if (!isEmptyOrSpaces(overrideMavenHome)) {
      return new File(overrideMavenHome);
    }

    String m2home = System.getenv(ENV_M2_HOME);
    if (!isEmptyOrSpaces(m2home)) {
      final File homeFromEnv = new File(m2home);
      if (isValidMavenHome(homeFromEnv)) {
        return homeFromEnv;
      }
    }

    String userHome = System.getProperty(PROP_USER_HOME);
    if (!isEmptyOrSpaces(userHome)) {
      final File underUserHome = new File(userHome, M2_DIR);
      if (isValidMavenHome(underUserHome)) {
        return underUserHome;
      }
    }

    return null;
  }

  public static boolean isEmptyOrSpaces(@Nullable String str) {
    return str == null || str.length() == 0 || str.trim().length() == 0;
  }

  public static boolean isValidMavenHome(File home) {
    return getMavenConfFile(home).exists();
  }

  public static File getMavenConfFile(File mavenHome) {
    return new File(new File(mavenHome, BIN_DIR), M2_CONF_FILE);
  }

  @Nullable
  public static File resolveGlobalSettingsFile(@Nullable String overriddenMavenHome) {
    File directory = resolveMavenHomeDirectory(overriddenMavenHome);
    if (directory == null) return null;

    return new File(new File(directory, CONF_DIR), SETTINGS_XML);
  }

  @NotNull
  public static File resolveUserSettingsFile(@Nullable String overriddenUserSettingsFile) {
    if (!isEmptyOrSpaces(overriddenUserSettingsFile)) return new File(overriddenUserSettingsFile);
    return new File(resolveM2Dir(), SETTINGS_XML);
  }

  @NotNull
  public static File resolveM2Dir() {
    return new File(System.getProperty(PROP_USER_HOME), DOT_M2_DIR);
  }

  @NotNull
  public static File resolveLocalRepository(@Nullable String overridenLocalRepository,
                                            @Nullable String overridenMavenHome,
                                            @Nullable String overriddenUserSettingsFile) {
    File result = null;
    if (!isEmptyOrSpaces(overridenLocalRepository)) result = new File(overridenLocalRepository);
    if (result == null) {
      result = doResolveLocalRepository(resolveUserSettingsFile(overriddenUserSettingsFile),
                                        resolveGlobalSettingsFile(overridenMavenHome));
    }
    try {
      return result.getCanonicalFile();
    }
    catch (IOException e) {
      return result;
    }
  }

  @NotNull
  public static File doResolveLocalRepository(@Nullable File userSettingsFile, @Nullable File globalSettingsFile) {
    if (userSettingsFile != null) {
      final String fromUserSettings = getRepositoryFromSettings(userSettingsFile);
      if (!StringUtil.isEmpty(fromUserSettings)) {
        return new File(fromUserSettings);
      }
    }

    if (globalSettingsFile != null) {
      final String fromGlobalSettings = getRepositoryFromSettings(globalSettingsFile);
      if (!StringUtil.isEmpty(fromGlobalSettings)) {
        return new File(fromGlobalSettings);
      }
    }

    return new File(resolveM2Dir(), REPOSITORY_DIR);
  }

  @Nullable
  public static String getRepositoryFromSettings(final File file) {
    try {
      byte[] bytes = FileUtil.loadFileBytes(file);
      return expandProperties(MavenJDOMUtil.findChildValueByPath(MavenJDOMUtil.read(bytes, null), "localRepository", null));
    }
    catch (IOException e) {
      return null;
    }
  }

  public static String expandProperties(String text) {
    if (StringUtil.isEmptyOrSpaces(text)) return text;
    Properties props = MavenFacadeUtil.collectSystemProperties();
    for (Map.Entry<Object, Object> each : props.entrySet()) {
      Object val = each.getValue();
      text = text.replace("${" + each.getKey() + "}", val instanceof CharSequence ? (CharSequence)val : val.toString());
    }
    return text;
  }

  @NotNull
  public static VirtualFile resolveSuperPomFile(@Nullable File mavenHome) {
    VirtualFile result = null;
    if (mavenHome != null) {
      result = doResolveSuperPomFile(new File(mavenHome, LIB_DIR));
    }
    if (result == null) {
      result = doResolveSuperPomFile(MavenFacadeManager.collectClassPathAndLIbsFolder().second);
    }
    return result;
  }

  @Nullable
  public static VirtualFile doResolveSuperPomFile(@Nullable File mavenHome) {
    File lib = resolveMavenLib(mavenHome);
    if (lib == null) return null;

    VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(lib);
    if (file == null) return null;

    VirtualFile root = JarFileSystem.getInstance().getJarRootForLocalFile(file);
    if (root == null) return null;

    return root.findFileByRelativePath(SUPER_POM_PATH);
  }

  @Nullable
  public static File resolveMavenLib(@NotNull File dir) {
    File[] files = dir.listFiles();
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

  public interface MavenTaskHandler {
    void waitFor();
  }
}
