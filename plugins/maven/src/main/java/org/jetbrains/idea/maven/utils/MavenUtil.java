/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.idea.maven.utils;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManagerEx;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.io.JarUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.DisposeAwareRunnable;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.NanoXmlUtil;
import gnu.trove.THashSet;
import icons.MavenIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.server.MavenServerUtil;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import java.util.zip.CRC32;

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
  public static final String CLIENT_ARTIFACT_SUFFIX = "-client";
  public static final String CLIENT_EXPLODED_ARTIFACT_SUFFIX = CLIENT_ARTIFACT_SUFFIX + " exploded";

  @SuppressWarnings("unchecked")
  private static final Pair<Pattern, String>[] SUPER_POM_PATHS = new Pair[]{
    Pair.create(Pattern.compile("maven-\\d+\\.\\d+\\.\\d+-uber\\.jar"), "org/apache/maven/project/" + MavenConstants.SUPER_POM_XML),
    Pair.create(Pattern.compile("maven-model-builder-\\d+\\.\\d+\\.\\d+\\.jar"), "org/apache/maven/model/" + MavenConstants.SUPER_POM_XML)
  };

  private static volatile Map<String, String> ourPropertiesFromMvnOpts;

  public static Map<String, String> getPropertiesFromMavenOpts() {
    Map<String, String> res = ourPropertiesFromMvnOpts;
    if (res == null) {
      String mavenOpts = System.getenv("MAVEN_OPTS");
      if (mavenOpts != null) {
        ParametersList mavenOptsList = new ParametersList();
        mavenOptsList.addParametersString(mavenOpts);
        res = mavenOptsList.getProperties();
      }
      else {
        res = Collections.emptyMap();
      }

      ourPropertiesFromMvnOpts = res;
    }

    return res;
  }


  public static void invokeLater(Project p, Runnable r) {
    invokeLater(p, ModalityState.defaultModalityState(), r);
  }

  public static void invokeLater(final Project p, final ModalityState state, final Runnable r) {
    if (isNoBackgroundMode()) {
      r.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(DisposeAwareRunnable.create(r, p), state);
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
      ApplicationManager.getApplication().invokeAndWait(DisposeAwareRunnable.create(r, p), state);
    }
  }

  public static void smartInvokeAndWait(final Project p, final ModalityState state, final Runnable r) {
    if (isNoBackgroundMode() || ApplicationManager.getApplication().isDispatchThread()) {
      r.run();
    }
    else {
      final Semaphore semaphore = new Semaphore();
      semaphore.down();
      DumbService.getInstance(p).smartInvokeLater(() -> {
        try {
          r.run();
        }
        finally {
          semaphore.up();
        }
      }, state);
      semaphore.waitFor();
    }
  }

  public static void invokeAndWaitWriteAction(Project p, final Runnable r) {
    invokeAndWait(p, () -> ApplicationManager.getApplication().runWriteAction(r));
  }

  public static void runDumbAware(final Project project, final Runnable r) {
    if (DumbService.isDumbAware(r)) {
      r.run();
    }
    else {
      DumbService.getInstance(project).runWhenSmart(DisposeAwareRunnable.create(r, project));
    }
  }

  public static void runWhenInitialized(final Project project, final Runnable r) {
    if (project.isDisposed()) return;

    if (isNoBackgroundMode()) {
      r.run();
      return;
    }

    if (!project.isInitialized()) {
      StartupManager.getInstance(project).registerPostStartupActivity(DisposeAwareRunnable.create(r, project));
      return;
    }

    runDumbAware(project, r);
  }

  public static boolean isNoBackgroundMode() {
    return (ApplicationManager.getApplication().isUnitTestMode()
            || ApplicationManager.getApplication().isHeadlessEnvironment());
  }

  public static boolean isInModalContext() {
    if (isNoBackgroundMode()) return false;
    return LaterInvocator.isInModalContext();
  }

  public static void showError(Project project, String title, Throwable e) {
    MavenLog.LOG.warn(title, e);
    Notifications.Bus.notify(new Notification(MAVEN_NOTIFICATION_GROUP, title, e.getMessage(), NotificationType.ERROR), project);
  }

  @NotNull
  public static java.nio.file.Path getPluginSystemDir(@NotNull String folder) {
    return PathManagerEx.getAppSystemDir().resolve("Maven").resolve(folder);
  }

  public static File getBaseDir(@NotNull VirtualFile file) {
    File baseDir = VfsUtilCore.virtualToIoFile(file.isDirectory() || file.getParent() == null ? file : file.getParent());
    File dir = baseDir;
    do {
      if (new File(dir, ".mvn").isDirectory()) {
        baseDir = dir;
        break;
      }
    }
    while ((dir = dir.getParentFile()) != null);
    return baseDir;
  }

  @Nullable
  public static VirtualFile findProfilesXmlFile(VirtualFile pomFile) {
    if (pomFile == null) return null;
    VirtualFile parent = pomFile.getParent();
    if (parent == null) return null;
    return parent.findChild(MavenConstants.PROFILES_XML);
  }

  @Nullable
  public static File getProfilesXmlIoFile(VirtualFile pomFile) {
    if (pomFile == null) return null;
    VirtualFile parent = pomFile.getParent();
    if (parent == null) return null;
    return new File(parent.getPath(), MavenConstants.PROFILES_XML);
  }

  public static <T, U> List<T> collectFirsts(List<Pair<T, U>> pairs) {
    List<T> result = new ArrayList<>(pairs.size());
    for (Pair<T, ?> each : pairs) {
      result.add(each.first);
    }
    return result;
  }

  public static <T, U> List<U> collectSeconds(List<Pair<T, U>> pairs) {
    List<U> result = new ArrayList<>(pairs.size());
    for (Pair<T, U> each : pairs) {
      result.add(each.second);
    }
    return result;
  }

  public static List<String> collectPaths(List<VirtualFile> files) {
    return ContainerUtil.map(files, file -> file.getPath());
  }

  public static List<VirtualFile> collectFiles(Collection<MavenProject> projects) {
    return ContainerUtil.map(projects, project -> project.getFile());
  }

  public static <T> boolean equalAsSets(final Collection<T> collection1, final Collection<T> collection2) {
    return toSet(collection1).equals(toSet(collection2));
  }

  private static <T> Collection<T> toSet(final Collection<T> collection) {
    return (collection instanceof Set ? collection : new THashSet<>(collection));
  }

  public static <T, U> List<Pair<T, U>> mapToList(Map<T, U> map) {
    return ContainerUtil.map2List(map.entrySet(), tuEntry -> Pair.create(tuEntry.getKey(), tuEntry.getValue()));
  }

  public static String formatHtmlImage(URL url) {
    return "<img src=\"" + url + "\"> ";
  }

  public static void runOrApplyMavenProjectFileTemplate(Project project,
                                                        VirtualFile file,
                                                        @NotNull MavenId projectId,
                                                        boolean interactive) throws IOException {
    runOrApplyMavenProjectFileTemplate(project, file, projectId, null, null, interactive);
  }

  public static void runOrApplyMavenProjectFileTemplate(Project project,
                                                        VirtualFile file,
                                                        @NotNull MavenId projectId,
                                                        MavenId parentId,
                                                        @Nullable VirtualFile parentFile,
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

      if (parentFile != null) {
        VirtualFile modulePath = file.getParent();
        VirtualFile parentModulePath = parentFile.getParent();

        if (!Comparing.equal(modulePath.getParent(), parentModulePath) || !FileUtil.namesEqual(MavenConstants.POM_XML, parentFile.getName())) {
          String relativePath = VfsUtilCore.findRelativePath(file, parentModulePath, '/');
          if (relativePath != null) {
            conditions.setProperty("HAS_RELATIVE_PATH", "true");
            properties.setProperty("PARENT_RELATIVE_PATH", relativePath);
          }
        }
      }
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
    FileTemplateManager manager = FileTemplateManager.getInstance(project);
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

      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile != null) {
        new ReformatCodeProcessor(project, psiFile, null, false).run();
      }
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
    final RuntimeException[] runtimeEx = new RuntimeException[1];
    final Error[] errorEx = new Error[1];

    ProgressManager.getInstance().run(new Task.Modal(project, title, true) {
      public void run(@NotNull ProgressIndicator i) {
        try {
          task.run(new MavenProgressIndicator(i));
        }
        catch (MavenProcessCanceledException | ProcessCanceledException e) {
          canceledEx[0] = e;
        }
        catch (RuntimeException e) {
          runtimeEx[0] = e;
        }
        catch (Error e) {
          errorEx[0] = e;
        }
      }
    });
    if (canceledEx[0] instanceof MavenProcessCanceledException) throw (MavenProcessCanceledException)canceledEx[0];
    if (canceledEx[0] instanceof ProcessCanceledException) throw new MavenProcessCanceledException();

    if (runtimeEx[0] != null) throw runtimeEx[0];
    if (errorEx[0] != null) throw errorEx[0];
  }

  public static MavenTaskHandler runInBackground(final Project project,
                                                 final String title,
                                                 final boolean cancellable,
                                                 final MavenTask task) {
    final MavenProgressIndicator indicator = new MavenProgressIndicator();

    Runnable runnable = () -> {
      if (project.isDisposed()) return;

      try {
        task.run(indicator);
      }
      catch (MavenProcessCanceledException | ProcessCanceledException ignore) {
        indicator.cancel();
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
          catch (InterruptedException | ExecutionException e) {
            MavenLog.LOG.error(e);
          }
        }
      };
      invokeLater(project, () -> {
        if (future.isDone()) return;
        new Task.Backgroundable(project, title, cancellable) {
          public void run(@NotNull ProgressIndicator i) {
            indicator.setIndicator(i);
            handler.waitFor();
          }
        }.queue();
      });
      return handler;
    }
  }

  @Nullable
  public static File resolveMavenHomeDirectory(@Nullable String overrideMavenHome) {
    if (!isEmptyOrSpaces(overrideMavenHome)) {
      return MavenServerManager.getMavenHomeFile(overrideMavenHome);
    }

    String m2home = System.getenv(ENV_M2_HOME);
    if (!isEmptyOrSpaces(m2home)) {
      final File homeFromEnv = new File(m2home);
      if (isValidMavenHome(homeFromEnv)) {
        return homeFromEnv;
      }
    }

    String mavenHome = System.getenv("MAVEN_HOME");
    if (!isEmptyOrSpaces(mavenHome)) {
      final File mavenHomeFile = new File(mavenHome);
      if (isValidMavenHome(mavenHomeFile)) {
        return mavenHomeFile;
      }
    }

    String userHome = SystemProperties.getUserHome();
    if (!isEmptyOrSpaces(userHome)) {
      final File underUserHome = new File(userHome, M2_DIR);
      if (isValidMavenHome(underUserHome)) {
        return underUserHome;
      }
    }

    if (SystemInfo.isMac) {
      File home = fromBrew();
      if (home != null) {
        return home;
      }

      if ((home = fromMacSystemJavaTools()) != null) {
        return home;
      }
    }
    else if (SystemInfo.isLinux) {
      File home = new File("/usr/share/maven");
      if (isValidMavenHome(home)) {
        return home;
      }

      home = new File("/usr/share/maven2");
      if (isValidMavenHome(home)) {
        return home;
      }
    }

    return MavenServerManager.getMavenHomeFile(MavenServerManager.BUNDLED_MAVEN_3);
  }

  @Nullable
  private static File fromMacSystemJavaTools() {
    final File symlinkDir = new File("/usr/share/maven");
    if (isValidMavenHome(symlinkDir)) {
      return symlinkDir;
    }

    // well, try to search
    final File dir = new File("/usr/share/java");
    final String[] list = dir.list();
    if (list == null || list.length == 0) {
      return null;
    }

    String home = null;
    final String prefix = "maven-";
    final int versionIndex = prefix.length();
    for (String path : list) {
      if (path.startsWith(prefix) &&
          (home == null || StringUtil.compareVersionNumbers(path.substring(versionIndex), home.substring(versionIndex)) > 0)) {
        home = path;
      }
    }

    if (home != null) {
      File file = new File(dir, home);
      if (isValidMavenHome(file)) {
        return file;
      }
    }

    return null;
  }

  @Nullable
  private static File fromBrew() {
    final File brewDir = new File("/usr/local/Cellar/maven");
    final String[] list = brewDir.list();
    if (list == null || list.length == 0) {
      return null;
    }

    if (list.length > 1) {
      Arrays.sort(list, (o1, o2) -> StringUtil.compareVersionNumbers(o2, o1));
    }

    final File file = new File(brewDir, list[0] + "/libexec");
    return isValidMavenHome(file) ? file : null;
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
  public static String getMavenVersion(@Nullable File mavenHome) {
    if(mavenHome == null) return null;
    String[] libs = new File(mavenHome, "lib").list();

    if (libs != null) {
      for (String lib : libs) {
        if (lib.startsWith("maven-core-") && lib.endsWith(".jar")) {
          String version = lib.substring("maven-core-".length(), lib.length() - ".jar".length());
          if (StringUtil.contains(version, ".x")) {
            Properties props = JarUtil.loadProperties(new File(mavenHome, "lib/" + lib),
                                                      "META-INF/maven/org.apache.maven/maven-core/pom.properties");
            return props != null ? props.getProperty("version") : null;
          }
          else {
            return version;
          }
        }
        if (lib.startsWith("maven-") && lib.endsWith("-uber.jar")) {
          return lib.substring("maven-".length(), lib.length() - "-uber.jar".length());
        }
      }
    }
    return null;
  }

  @Nullable
  public static String getMavenVersion(String mavenHome) {
    return getMavenVersion(new File(mavenHome));
  }

  public static boolean isMaven3(String mavenHome) {
    String version = getMavenVersion(mavenHome);
    return version != null && version.compareTo("3.0.0") >= 0;
  }

  @Nullable
  public static File resolveGlobalSettingsFile(@Nullable String overriddenMavenHome) {
    File directory = resolveMavenHomeDirectory(overriddenMavenHome);
    return new File(new File(directory, CONF_DIR), SETTINGS_XML);
  }

  @NotNull
  public static File resolveUserSettingsFile(@Nullable String overriddenUserSettingsFile) {
    if (!isEmptyOrSpaces(overriddenUserSettingsFile)) return new File(overriddenUserSettingsFile);
    return new File(resolveM2Dir(), SETTINGS_XML);
  }

  @NotNull
  public static File resolveM2Dir() {
    return new File(SystemProperties.getUserHome(), DOT_M2_DIR);
  }

  @NotNull
  public static File resolveLocalRepository(@Nullable String overriddenLocalRepository,
                                            @Nullable String overriddenMavenHome,
                                            @Nullable String overriddenUserSettingsFile) {
    File result = null;
    if (!isEmptyOrSpaces(overriddenLocalRepository)) result = new File(overriddenLocalRepository);
    if (result == null) {
      result = doResolveLocalRepository(resolveUserSettingsFile(overriddenUserSettingsFile),
                                        resolveGlobalSettingsFile(overriddenMavenHome));
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

  public static String expandProperties(String text, Properties props) {
    if (StringUtil.isEmptyOrSpaces(text)) return text;
    for (Map.Entry<Object, Object> each : props.entrySet()) {
      Object val = each.getValue();
      text = text.replace("${" + each.getKey() + "}", val instanceof CharSequence ? (CharSequence)val : val.toString());
    }
    return text;
  }

  public static String expandProperties(String text) {
    return expandProperties(text, MavenServerUtil.collectSystemProperties());
  }

  @Nullable
  public static VirtualFile resolveSuperPomFile(@Nullable File mavenHome) {
    VirtualFile result = null;
    if (mavenHome != null) {
      result = doResolveSuperPomFile(new File(mavenHome, LIB_DIR));
    }
    return result == null ? doResolveSuperPomFile(MavenServerManager.getMavenLibDirectory()) : result;
  }

  @Nullable
  public static VirtualFile doResolveSuperPomFile(@NotNull File mavenHome) {
    File[] files = mavenHome.listFiles();
    if (files == null) return null;

    for (File library : files) {

      for (Pair<Pattern, String> path : SUPER_POM_PATHS) {
        if (path.first.matcher(library.getName()).matches()) {
          VirtualFile libraryVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(library);
          if (libraryVirtualFile == null) continue;

          VirtualFile root = JarFileSystem.getInstance().getJarRootForLocalFile(libraryVirtualFile);
          if (root == null) continue;

          VirtualFile pomFile = root.findFileByRelativePath(path.second);
          if (pomFile != null) {
            return pomFile;
          }
        }
      }
    }

    return null;
  }

  public static List<LookupElement> getPhaseVariants(MavenProjectsManager manager) {
    Set<String> goals = new HashSet<>(MavenConstants.PHASES);

    for (MavenProject mavenProject : manager.getProjects()) {
      for (MavenPlugin plugin : mavenProject.getPlugins()) {
        MavenPluginInfo pluginInfo = MavenArtifactUtil.readPluginInfo(manager.getLocalRepository(), plugin.getMavenId());
        if (pluginInfo != null) {
          for (MavenPluginInfo.Mojo mojo : pluginInfo.getMojos()) {
            goals.add(mojo.getDisplayName());
          }
        }
      }
    }

    List<LookupElement> res = new ArrayList<>(goals.size());
    for (String goal : goals) {
      res.add(LookupElementBuilder.create(goal).withIcon(MavenIcons.Phase));
    }

    return res;
  }

  public interface MavenTaskHandler {
    void waitFor();
  }

  public static int crcWithoutSpaces(@NotNull InputStream in) throws IOException {
    try {
      final CRC32 crc = new CRC32();

      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
      parser.getXMLReader().setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      parser.parse(in, new DefaultHandler(){

        boolean textContentOccur = false;
        int spacesCrc;

        private void putString(@Nullable String string) {
          if (string == null) return;

          for (int i = 0, end = string.length(); i < end; i++) {
            crc.update(string.charAt(i));
          }
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
          textContentOccur = false;

          crc.update(1);
          putString(qName);

          for (int i = 0; i < attributes.getLength(); i++) {
            putString(attributes.getQName(i));
            putString(attributes.getValue(i));
          }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
          textContentOccur = false;

          crc.update(2);
          putString(qName);
        }

        private void processTextOrSpaces(char[] ch, int start, int length) {
          for (int i = start, end = start + length; i < end; i++) {
            char a = ch[i];

            if (Character.isWhitespace(a)) {
              if (textContentOccur) {
                spacesCrc = spacesCrc * 31 + a;
              }
            }
            else {
              if (textContentOccur && spacesCrc != 0) {
                crc.update(spacesCrc);
                crc.update(spacesCrc >> 8);
              }

              crc.update(a);

              textContentOccur = true;
              spacesCrc = 0;
            }
          }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
          processTextOrSpaces(ch, start, length);
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
          processTextOrSpaces(ch, start, length);
        }

        @Override
        public void processingInstruction(String target, String data) throws SAXException {
          putString(target);
          putString(data);
        }

        @Override
        public void skippedEntity(String name) throws SAXException {
          putString(name);
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
          crc.update(100);
        }
      });

      return (int)crc.getValue();
    }
    catch (ParserConfigurationException e) {
      throw new RuntimeException(e);
    }
    catch (SAXException e) {
      return -1;
    }
  }

  public static int crcWithoutSpaces(@NotNull VirtualFile xmlFile) throws IOException {
    InputStream inputStream = xmlFile.getInputStream();
    try {
      return crcWithoutSpaces(inputStream);
    }
    finally {
      inputStream.close();
    }
  }

  public static String getSdkPath(@Nullable Sdk sdk) {
    if (sdk == null) return null;

    VirtualFile homeDirectory = sdk.getHomeDirectory();
    if (homeDirectory == null) return null;

    if (!"jre".equals(homeDirectory.getName())) {
      VirtualFile jreDir = homeDirectory.findChild("jre");
      if (jreDir != null) {
        homeDirectory = jreDir;
      }
    }

    return homeDirectory.getPath();
  }

  @Nullable
  public static String getModuleJreHome(@NotNull MavenProjectsManager mavenProjectsManager, @NotNull MavenProject mavenProject) {
    return getSdkPath(getModuleJdk(mavenProjectsManager, mavenProject));
  }

  @Nullable
  public static String getModuleJavaVersion(@NotNull MavenProjectsManager mavenProjectsManager, @NotNull MavenProject mavenProject) {
    Sdk sdk = getModuleJdk(mavenProjectsManager, mavenProject);
    if (sdk == null) return null;

    return sdk.getVersionString();
  }

  @Nullable
  public static Sdk getModuleJdk(@NotNull MavenProjectsManager mavenProjectsManager, @NotNull MavenProject mavenProject) {
    Module module = mavenProjectsManager.findModule(mavenProject);
    if (module == null) return null;

    return ModuleRootManager.getInstance(module).getSdk();
  }

  @NotNull
  public static <K, V extends Map> V getOrCreate(Map map, K key) {
    Map res = (Map)map.get(key);
    if (res == null) {
      res = new HashMap();
      map.put(key, res);
    }

    return (V)res;
  }

  public static String getArtifactName(String packaging, Module module, boolean exploded) {
    return module.getName() + ":" + packaging + (exploded ? " exploded" : "");
  }

  public static String getEjbClientArtifactName(Module module, boolean exploded) {
    return module.getName() + ":ejb" + (exploded ? CLIENT_EXPLODED_ARTIFACT_SUFFIX : CLIENT_ARTIFACT_SUFFIX);
  }

  public static String getIdeaVersionToPassToMavenProcess() {
    return ApplicationInfoImpl.getShadowInstance().getMajorVersion() + "." + ApplicationInfoImpl.getShadowInstance().getMinorVersion();
  }

  public static boolean isPomFileName(String fileName) {
    return fileName.equals(MavenConstants.POM_XML) ||
           fileName.endsWith(".pom") || fileName.startsWith("pom.") ||
           fileName.equals(MavenConstants.SUPER_POM_XML);
  }

  public static boolean isPotentialPomFile(String path) {
    return ArrayUtil.contains(FileUtilRt.getExtension(path), MavenConstants.POM_EXTENSIONS);
  }

  public static boolean isPomFile(@Nullable VirtualFile file) {
    return isPomFile(null, file);
  }

  public static boolean isPomFile(@Nullable Project project, @Nullable VirtualFile file) {
    if (file == null) return false;

    if (isPomFileName(file.getName())) return true;
    if (!isPotentialPomFile(file.getPath())) return false;

    if (project == null || !project.isInitialized()) {
      if (!FileUtil.extensionEquals(file.getName(), "xml")) return false;
      try {
        try (InputStream in = file.getInputStream()) {
          Ref<Boolean> isPomFile = Ref.create(false);
          Reader reader = new BufferedReader(new InputStreamReader(in, CharsetToolkit.UTF8_CHARSET));
          NanoXmlUtil.parse(reader, new NanoXmlUtil.IXMLBuilderAdapter() {

            @Override
            public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr) throws Exception {
              if ("project".equals(name)) {
                isPomFile.set(nsURI.startsWith("http://maven.apache.org/POM/"));
              }
              stop();
            }
          });
          return isPomFile.get();
        }
      }
      catch (IOException ignore) {
        return false;
      }
    }

    MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);
    if (mavenProjectsManager.findProject(file) != null) return true;

    return ReadAction.compute(() -> {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile == null) return false;
      return MavenDomUtil.isProjectFile(psiFile);
    });
  }

  public static Stream<VirtualFile> streamPomFiles(@Nullable Project project, @Nullable VirtualFile root) {
    if (root == null) return Stream.empty();
    return Stream.of(root.getChildren()).filter(file -> isPomFile(project, file));
  }
}
