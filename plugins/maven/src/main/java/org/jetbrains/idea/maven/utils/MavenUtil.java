// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.utils;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.SimpleJavaParameters;
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
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
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
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.*;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeProjectLifecycleListener;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectReaderResult;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenServerEmbedder;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.server.MavenServerProgressIndicator;
import org.jetbrains.idea.maven.server.MavenServerUtil;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import static com.intellij.openapi.util.io.JarUtil.getJarAttribute;
import static com.intellij.openapi.util.io.JarUtil.loadProperties;
import static com.intellij.openapi.util.text.StringUtil.*;
import static com.intellij.util.xml.NanoXmlBuilder.stop;
import static icons.ExternalSystemIcons.Task;

public class MavenUtil {
  @ApiStatus.Experimental
  @NonNls public static final String MAVEN_NAME = "Maven";
  @NonNls public static final String MAVEN_NAME_PRESENTABLE = MAVEN_NAME;
  @NonNls public static final String MAVEN_NAME_UPCASE = MAVEN_NAME.toUpperCase();
  public static final ProjectSystemId SYSTEM_ID = new ProjectSystemId(MAVEN_NAME_UPCASE);
  public static final String MAVEN_NOTIFICATION_GROUP = MAVEN_NAME;
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
  public static final String MAVEN_NEW_PROJECT_MODEL_KEY = "maven.new.project.model";

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
      ApplicationManager.getApplication().invokeLater(r, state, p.getDisposed());
    }
  }

  public static void invokeAndWait(@NotNull Project p, @NotNull Runnable r) {
    invokeAndWait(p, ModalityState.defaultModalityState(), r);
  }

  public static void invokeAndWait(final Project p, final ModalityState state, @NotNull Runnable r) {
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

  public static void invokeAndWaitWriteAction(@NotNull Project p, @NotNull Runnable r) {
    invokeAndWait(p, () -> ApplicationManager.getApplication().runWriteAction(r));
  }

  public static void runDumbAware(@NotNull Project project, @NotNull Runnable r) {
    if (DumbService.isDumbAware(r)) {
      r.run();
    }
    else {
      DumbService.getInstance(project).runWhenSmart(DisposeAwareRunnable.create(r, project));
    }
  }

  public static void runWhenInitialized(@NotNull Project project, @NotNull Runnable runnable) {
    if (project.isDisposed()) return;

    if (isNoBackgroundMode()) {
      runnable.run();
      return;
    }

    if (!project.isInitialized()) {
      StartupManager.getInstance(project).runAfterOpened(runnable);
      return;
    }

    runDumbAware(project, runnable);
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
    if (parent == null || !parent.isValid()) return null;
    return parent.findChild(MavenConstants.PROFILES_XML);
  }

  @Nullable
  public static File getProfilesXmlIoFile(VirtualFile pomFile) {
    if (pomFile == null) return null;
    VirtualFile parent = pomFile.getParent();
    if (parent == null) return null;
    return new File(parent.getPath(), MavenConstants.PROFILES_XML);
  }

  public static <T, U> List<T> collectFirsts(List<? extends Pair<T, U>> pairs) {
    List<T> result = new ArrayList<>(pairs.size());
    for (Pair<T, ?> each : pairs) {
      result.add(each.first);
    }
    return result;
  }

  public static <T, U> List<U> collectSeconds(List<? extends Pair<T, U>> pairs) {
    List<U> result = new ArrayList<>(pairs.size());
    for (Pair<T, U> each : pairs) {
      result.add(each.second);
    }
    return result;
  }

  public static List<String> collectPaths(List<? extends VirtualFile> files) {
    return ContainerUtil.map(files, file -> file.getPath());
  }

  public static List<VirtualFile> collectFiles(Collection<? extends MavenProject> projects) {
    return ContainerUtil.map(projects, project -> project.getFile());
  }

  public static <T> boolean equalAsSets(final Collection<T> collection1, final Collection<T> collection2) {
    return toSet(collection1).equals(toSet(collection2));
  }

  private static <T> Collection<T> toSet(final Collection<T> collection) {
    return (collection instanceof Set ? collection : new THashSet<>(collection));
  }

  @NotNull
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

        if (!Comparing.equal(modulePath.getParent(), parentModulePath) ||
            !FileUtil.namesEqual(MavenConstants.POM_XML, parentFile.getName())) {
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
      matcher.appendReplacement(builder, "\\$" + toUpperCase(matcher.group(1)) + "\\$");
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

  public static void run(Project project, String title, final MavenTask task)
    throws MavenProcessCanceledException {
    final Exception[] canceledEx = new Exception[1];
    final RuntimeException[] runtimeEx = new RuntimeException[1];
    final Error[] errorEx = new Error[1];

    ProgressManager.getInstance().run(new Task.Modal(project, title, true) {
      @Override
      public void run(@NotNull ProgressIndicator i) {
        try {
          task.run(new MavenProgressIndicator(i, null));
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


  @NotNull
  public static MavenTaskHandler runInBackground(@NotNull final Project project,
                                                 @NotNull final String title,
                                                 final boolean cancellable,
                                                 @NotNull final MavenTask task) {
    return runInBackground(project, title, cancellable, task, null);

  }

  @NotNull
  public static MavenTaskHandler runInBackground(@NotNull final Project project,
                                                 @NotNull final String title,
                                                 final boolean cancellable,
                                                 @NotNull final MavenTask task,
                                                 @Nullable("null means application pooled thread")
                                                     ExecutorService executorService) {
    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);

    final MavenProgressIndicator indicator = new MavenProgressIndicator(manager::getSyncConsole);

    Runnable runnable = () -> {
      if (project.isDisposed()) return;

      try {
        task.run(indicator);
      }
      catch (MavenProcessCanceledException | ProcessCanceledException e) {
        indicator.cancel();
      }
    };

    if (isNoBackgroundMode()) {
      runnable.run();
      return new MavenTaskHandler() {
        @Override
        public void waitFor() {
        }
      };
    }
    else {
      final Future<?> future;
      if (executorService == null) {
        future = ApplicationManager.getApplication().executeOnPooledThread(runnable);
      } else {
        future = executorService.submit(runnable);
      }
      final MavenTaskHandler handler = new MavenTaskHandler() {
        @Override
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
          @Override
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

  public static void addEventListener(@NotNull String mavenVersion, @NotNull SimpleJavaParameters params) {
    if (VersionComparatorUtil.compare(mavenVersion, "3.0.2") < 0) {
      MavenLog.LOG.warn("Maven version less than 3.0.2 are not correctly displayed in Build Window");
      return;
    }
    String listenerPath = MavenServerManager.getMavenEventListener().getAbsolutePath();
    String extClassPath = params.getVMParametersList().getPropertyValue(MavenServerEmbedder.MAVEN_EXT_CLASS_PATH);
    if (isEmpty(extClassPath)) {
      params.getVMParametersList()
        .addProperty(MavenServerEmbedder.MAVEN_EXT_CLASS_PATH, listenerPath);
    }
    else {
      params.getVMParametersList()
        .addProperty(MavenServerEmbedder.MAVEN_EXT_CLASS_PATH, extClassPath + File.pathSeparatorChar + listenerPath);
    }
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
          (home == null || compareVersionNumbers(path.substring(versionIndex), home.substring(versionIndex)) > 0)) {
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
      Arrays.sort(list, (o1, o2) -> compareVersionNumbers(o2, o1));
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
    if (mavenHome == null) return null;
    String[] libs = new File(mavenHome, "lib").list();


    if (libs != null) {
      for (String lib : libs) {
        File mavenLibFile = new File(mavenHome, "lib/" + lib);

        if (lib.equals("maven-core.jar")) {
          MavenLog.LOG.debug("Choosing version by maven-core.jar");
          return getMavenLibVersion(mavenLibFile);
        }
        if (lib.startsWith("maven-core-") && lib.endsWith(".jar")) {
          MavenLog.LOG.debug("Choosing version by maven-core.xxx.jar");
          String version = lib.substring("maven-core-".length(), lib.length() - ".jar".length());
          return contains(version, ".x") ? getMavenLibVersion(mavenLibFile) : version;
        }
        if (lib.startsWith("maven-") && lib.endsWith("-uber.jar")) {
          MavenLog.LOG.debug("Choosing version by maven-xxx-uber.jar");
          return lib.substring("maven-".length(), lib.length() - "-uber.jar".length());
        }
      }
    }
    MavenLog.LOG.warn("Cannot resolve maven version for " + mavenHome);
    return null;
  }

  private static String getMavenLibVersion(File file) {
    Properties props = loadProperties(file, "META-INF/maven/org.apache.maven/maven-core/pom.properties");
    return props != null
           ? nullize(props.getProperty("version"))
           : nullize(getJarAttribute(file, java.util.jar.Attributes.Name.IMPLEMENTATION_VERSION));
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

  @Nullable
  public static VirtualFile getRepositoryFile(@NotNull Project project,
                                              @NotNull MavenId id,
                                              @NotNull String extension,
                                              @Nullable String classifier) {
    if (id.getGroupId() == null || id.getArtifactId() == null || id.getVersion() == null) {
      return null;
    }
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    File file = makeLocalRepositoryFile(id, projectsManager.getLocalRepository(), extension, classifier);
    return LocalFileSystem.getInstance().findFileByIoFile(file);
  }

  private static File makeLocalRepositoryFile(MavenId id,
                                              File localRepository,
                                              @NotNull String extension,
                                              @Nullable String classifier) {
    String relPath = id.getGroupId().replace(".", "/");

    relPath += "/" + id.getArtifactId();
    relPath += "/" + id.getVersion();
    relPath += "/" + id.getArtifactId() + "-" + id.getVersion();
    relPath = classifier == null ? relPath + "." + extension : relPath + "-" + classifier + "." + extension;

    return new File(localRepository, relPath);
  }

  @NotNull
  public static File doResolveLocalRepository(@Nullable File userSettingsFile, @Nullable File globalSettingsFile) {
    if (userSettingsFile != null) {
      final String fromUserSettings = getRepositoryFromSettings(userSettingsFile);
      if (!isEmpty(fromUserSettings)) {
        return new File(fromUserSettings);
      }
    }

    if (globalSettingsFile != null) {
      final String fromGlobalSettings = getRepositoryFromSettings(globalSettingsFile);
      if (!isEmpty(fromGlobalSettings)) {
        return new File(fromGlobalSettings);
      }
    }

    return new File(resolveM2Dir(), REPOSITORY_DIR);
  }

  @Nullable
  public static String getRepositoryFromSettings(final File file) {
    try {
      Element repository = getRepositoryElement(file);

      if (repository == null) {
        return null;
      }
      String text = repository.getText();
      if (isEmptyOrSpaces(text)) {
        return null;
      }
      return expandProperties(text.trim());
    }
    catch (Exception e) {
      return null;
    }
  }

  public static String getMirroredUrl(final File settingsFile, String url, String id) {
    try {
      Element mirrorParent = getElementWithRegardToNamespace(JDOMUtil.load(settingsFile), "mirrors");
      if (mirrorParent == null) {
        return url;
      }
      List<Element> mirrors = getElementsWithRegardToNamespace(mirrorParent, "mirror");
      for (Element el : mirrors) {
        Element mirrorOfElement = getElementWithRegardToNamespace(el, "mirrorOf");
        Element mirrorUrlElement = getElementWithRegardToNamespace(el, "url");
        if (mirrorOfElement == null) continue;
        if (mirrorUrlElement == null) continue;

        String mirrorOf = mirrorOfElement.getTextTrim();
        String mirrorUrl = mirrorUrlElement.getTextTrim();

        if (StringUtil.isEmptyOrSpaces(mirrorOf) || StringUtil.isEmptyOrSpaces(mirrorUrl)) {
          continue;
        }

        if (isMirrorApplicable(mirrorOf, url, id)) {
          return mirrorUrl;
        }
      }
    }
    catch (Exception ignore) {

    }

    return url;
  }

  private static boolean isMirrorApplicable(String mirrorOf, String url, String id) {
    HashSet<String> patterns = new HashSet<>(split(mirrorOf, ","));

    if (patterns.contains("!" + id)) {
      return false;
    }

    if (patterns.contains("*")) {
      return true;
    }
    if (patterns.contains(id)) {
      return true;
    }
    if (patterns.contains("external:*")) {
      try {
        URI uri = URI.create(url);
        if ("file".equals(uri.getScheme())) return false;
        if ("localhost".equals(uri.getHost())) return false;
        if ("127.0.0.1".equals(uri.getHost())) return false;
        return true;
      }
      catch (IllegalArgumentException e) {
        MavenLog.LOG.warn("cannot parse uri " + url, e);
        return false;
      }
    }
    return false;
  }

  @Nullable
  private static Element getRepositoryElement(File file) throws JDOMException, IOException {
    return getElementWithRegardToNamespace(JDOMUtil.load(file), "localRepository");
  }

  @Nullable
  private static Element getElementWithRegardToNamespace(@NotNull Element parent, String childName) {

    Element element = parent.getChild(childName);
    if (element == null) {
      element = parent.getChild(childName, Namespace.getNamespace("http://maven.apache.org/SETTINGS/1.0.0"));
    }
    if (element == null) {
      element = parent.getChild(childName, Namespace.getNamespace("http://maven.apache.org/SETTINGS/1.1.0"));
    }
    return element;
  }

  private static List<Element> getElementsWithRegardToNamespace(@NotNull Element parent, String childrenName) {
    List<Element> elements = parent.getChildren(childrenName);
    if (elements.isEmpty()) {
      elements = parent.getChildren(childrenName, Namespace.getNamespace("http://maven.apache.org/SETTINGS/1.0.0"));
    }
    if (elements.isEmpty()) {
      elements = parent.getChildren(childrenName, Namespace.getNamespace("http://maven.apache.org/SETTINGS/1.1.0"));
    }
    return elements;
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
    return result == null ? doResolveSuperPomFile(new File(MavenServerManager.getMavenHomeFile(MavenServerManager.BUNDLED_MAVEN_3), LIB_DIR)) : result;
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
      res.add(LookupElementBuilder.create(goal).withIcon(Task));
    }

    return res;
  }

  public static void notifySyncForUnresolved(@NotNull Project project, @NotNull Collection<MavenProjectReaderResult> results) {
    Set<MavenId> unresolvedIds = new HashSet<>();
    for (MavenProjectReaderResult result : results) {
      if (result.mavenModel.getDependencies() != null) {
        for (MavenArtifact artifact : result.mavenModel.getDependencies()) {
          if (!artifact.isResolved()) {
            unresolvedIds.add(artifact.getMavenId());
          }
        }
      }
    }

    if (unresolvedIds.isEmpty()) {
      return;
    }

    MavenSyncConsole syncConsole = MavenProjectsManager.getInstance(project).getSyncConsole();
    for (MavenId id : unresolvedIds) {
      syncConsole.getListener(MavenServerProgressIndicator.ResolveType.DEPENDENCY).showError(id.getKey());
    }
  }

  public static boolean newModelEnabled(Project project) {
    return LegacyBridgeProjectLifecycleListener.Companion.enabled(project) &&
           (Boolean.valueOf(System.getProperty(MAVEN_NEW_PROJECT_MODEL_KEY))
            || Registry.is(MAVEN_NEW_PROJECT_MODEL_KEY));
  }

  public interface MavenTaskHandler {
    void waitFor();
  }

  public static int crcWithoutSpaces(@NotNull InputStream in) throws IOException {
    try {
      final CRC32 crc = new CRC32();

      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
      parser.getXMLReader().setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      parser.parse(in, new DefaultHandler() {

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

  public static boolean isMavenModule(@Nullable Module module) {
    return module != null && MavenProjectsManager.getInstance(module.getProject()).isMavenizedModule(module);
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

  public static boolean isPotentialPomFile(String nameOrPath) {
    return ArrayUtil.contains(FileUtilRt.getExtension(nameOrPath), MavenConstants.POM_EXTENSIONS);
  }

  public static boolean isPomFile(@Nullable VirtualFile file) {
    return isPomFile(null, file);
  }

  public static boolean isPomFile(@Nullable Project project, @Nullable VirtualFile file) {
    if (file == null) return false;

    String name = file.getName();
    if (isPomFileName(name)) return true;
    if (!isPotentialPomFile(name)) return false;

    return isPomFileIgnoringName(project, file);
  }

  public static boolean isPomFileIgnoringName(@Nullable Project project, @NotNull VirtualFile file) {
    if (project == null || !project.isInitialized()) {
      if (!FileUtil.extensionEquals(file.getName(), "xml")) return false;
      try {
        try (InputStream in = file.getInputStream()) {
          Ref<Boolean> isPomFile = Ref.create(false);
          Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
          NanoXmlUtil.parse(reader, new NanoXmlBuilder() {
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
      if (project.isDisposed()) return false;
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile == null) return false;
      return MavenDomUtil.isProjectFile(psiFile);
    });
  }

  public static Stream<VirtualFile> streamPomFiles(@Nullable Project project, @Nullable VirtualFile root) {
    if (root == null) return Stream.empty();
    return Stream.of(root.getChildren()).filter(file -> isPomFile(project, file));
  }

  public static void restartConfigHighlightning(Project project, Collection<MavenProject> projects) {
    invokeLater(project, () -> {
      FileContentUtilCore.reparseFiles(getConfigFiles(projects));
    });
  }

  public static VirtualFile[] getConfigFiles(Collection<MavenProject> projects) {
    List<VirtualFile> result = new SmartList<>();
    for (MavenProject project : projects) {
      VirtualFile file = getConfigFile(project, MavenConstants.MAVEN_CONFIG_RELATIVE_PATH);
      if (file != null) {
        result.add(file);
      }
    }
    if (result.isEmpty()) {
      return VirtualFile.EMPTY_ARRAY;
    }
    return result.toArray(VirtualFile.EMPTY_ARRAY);
  }

  public static VirtualFile getConfigFile(MavenProject mavenProject, String fileRelativePath) {
    VirtualFile baseDir = VfsUtil.findFileByIoFile(getBaseDir(mavenProject.getDirectoryFile()), false);
    if (baseDir != null) {
      return baseDir.findFileByRelativePath(fileRelativePath);
    }
    return null;
  }

  public static Path toPath(@Nullable MavenProject mavenProject, String path) {
    if (!FileUtil.isAbsolute(path)) {
      if (mavenProject == null) {
        throw new IllegalArgumentException("Project should be not-nul for non-absolute paths");
      }
      path = new File(mavenProject.getDirectory(), path).getPath();
    }
    return new Path(path);
  }
}
