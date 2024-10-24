// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.execution.configurations.CompositeParameterTargetedValue;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.service.execution.InvalidJavaHomeException;
import com.intellij.openapi.externalSystem.service.execution.InvalidSdkException;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.platform.eel.EelApi;
import com.intellij.platform.eel.EelPlatform;
import com.intellij.platform.eel.LocalEelApi;
import com.intellij.platform.eel.provider.utils.EelPathUtilsKt;
import com.intellij.platform.eel.provider.utils.EelUtilsKt;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import org.apache.commons.lang3.StringUtils;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jetbrains.annotations.*;
import org.jetbrains.idea.maven.MavenVersionAwareSupportExtension;
import org.jetbrains.idea.maven.buildtool.MavenSyncConsole;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.server.*;
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
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import static com.intellij.openapi.util.text.StringUtil.*;
import static com.intellij.platform.eel.fs.EelFileSystemApiKt.getPath;
import static com.intellij.platform.eel.provider.EelProviderUtil.getEelApiBlocking;
import static com.intellij.util.xml.NanoXmlBuilder.stop;
import static icons.ExternalSystemIcons.Task;
import static org.jetbrains.idea.maven.project.MavenHomeKt.resolveMavenHomeType;
import static org.jetbrains.idea.maven.project.MavenHomeKt.staticOrBundled;

public class MavenUtil {

  private static final List<String> settingsListNamespaces = List.of(
    "http://maven.apache.org/SETTINGS/1.0.0",
    "http://maven.apache.org/SETTINGS/1.1.0",
    "http://maven.apache.org/SETTINGS/1.2.0"
  );

  private static final List<String> extensionListNamespaces = List.of(
    "http://maven.apache.org/EXTENSIONS/1.0.0",
    "http://maven.apache.org/EXTENSIONS/1.1.0",
    "http://maven.apache.org/EXTENSIONS/1.2.0"
  );
  private static final Set<Runnable> runnables = Collections.newSetFromMap(new IdentityHashMap<>());
  public static final String INTELLIJ_PLUGIN_ID = "org.jetbrains.idea.maven";
  @ApiStatus.Experimental
  public static final @NlsSafe String MAVEN_NAME = "Maven";
  @NonNls public static final String MAVEN_NAME_UPCASE = MAVEN_NAME.toUpperCase();
  public static final @NotNull ProjectSystemId SYSTEM_ID = new ProjectSystemId(MAVEN_NAME_UPCASE);
  public static final String MAVEN_NOTIFICATION_GROUP = MAVEN_NAME;
  public static final String SETTINGS_XML = "settings.xml";
  public static final String DOT_M2_DIR = ".m2";
  public static final String ENV_M2_HOME = "M2_HOME";
  public static final String M2_DIR = "m2";
  public static final String BIN_DIR = "bin";
  public static final String CONF_DIR = "conf";
  public static final String M2_CONF_FILE = "m2.conf";
  public static final String MVN_FILE = "mvn";
  public static final String REPOSITORY_DIR = "repository";
  public static final String LIB_DIR = "lib";
  public static final String CLIENT_ARTIFACT_SUFFIX = "-client";
  public static final String CLIENT_EXPLODED_ARTIFACT_SUFFIX = CLIENT_ARTIFACT_SUFFIX + " exploded";
  @Deprecated
  private static final String PROP_FORCED_M2_HOME = "idea.force.m2.home";
  public static final String MAVEN_REPO_LOCAL = "maven.repo.local";


  @SuppressWarnings("unchecked")
  private static final Pair<Pattern, String>[] SUPER_POM_PATHS = new Pair[]{
    Pair.create(Pattern.compile("maven-\\d+\\.\\d+\\.\\d+-uber\\.jar"), "org/apache/maven/project/" + MavenConstants.SUPER_POM_4_0_XML),
    Pair.create(Pattern.compile("maven-model-builder-\\d+\\.\\d+\\.\\d+\\.jar"),
                "org/apache/maven/model/" + MavenConstants.SUPER_POM_4_0_XML)
  };

  private static volatile Map<String, String> ourPropertiesFromMvnOpts;

  public static boolean enablePreimport() {
    return Registry.is("maven.preimport.project") &&
           !ApplicationManager.getApplication().isUnitTestMode() &&
           !ApplicationManager.getApplication().isHeadlessEnvironment();
  }

  public static boolean enablePreimportOnly() {
    return Registry.is("maven.preimport.only");
  }

  public static Map<String, String> getPropertiesFromMavenOpts() {
    Map<String, String> res = ourPropertiesFromMvnOpts;
    if (res == null) {
      res = parseMavenProperties(System.getenv("MAVEN_OPTS"));
      ourPropertiesFromMvnOpts = res;
    }
    return res;
  }

  public static @NotNull Map<String, String> parseMavenProperties(@Nullable String mavenOpts) {
    if (mavenOpts != null) {
      ParametersList mavenOptsList = new ParametersList();
      mavenOptsList.addParametersString(mavenOpts);
      return mavenOptsList.getProperties();
    }
    return Collections.emptyMap();
  }


  public static void invokeLater(Project p, Runnable r) {
    invokeLater(p, ModalityState.defaultModalityState(), r);
  }

  public static void invokeLater(final Project p, final ModalityState state, final Runnable r) {
    startTestRunnable(r);
    ApplicationManager.getApplication().invokeLater(() -> {
      runAndFinishTestRunnable(r);
    }, state, p.getDisposed());
  }


  private static void startTestRunnable(Runnable r) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) return;
    synchronized (runnables) {
      runnables.add(r);
    }
  }

  private static void runAndFinishTestRunnable(Runnable r) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      r.run();
      return;
    }

    try {
      r.run();
    }
    finally {
      synchronized (runnables) {
        runnables.remove(r);
      }
    }
  }

  @TestOnly
  public static boolean noUncompletedRunnables() {
    synchronized (runnables) {
      return runnables.isEmpty();
    }
  }

  public static void cleanAllRunnables() {
    synchronized (runnables) {
      runnables.clear();
    }
  }

  @TestOnly
  public static List<Runnable> getUncompletedRunnables() {
    List<Runnable> result;
    synchronized (runnables) {
      result = new ArrayList<>(runnables);
    }
    return result;
  }

  public static void invokeAndWait(@NotNull Project p, @NotNull Runnable r) {
    invokeAndWait(p, ModalityState.defaultModalityState(), r);
  }

  public static void invokeAndWait(final Project p, final ModalityState state, @NotNull Runnable r) {
    startTestRunnable(r);
    ApplicationManager.getApplication().invokeAndWait(DisposeAwareRunnable.create(() -> runAndFinishTestRunnable(r), p), state);
  }


  public static void invokeAndWaitWriteAction(@NotNull Project p, @NotNull Runnable r) {
    startTestRunnable(r);
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      runAndFinishTestRunnable(r);
    }
    else if (ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().runWriteAction(r);
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(DisposeAwareRunnable.create(
                                                          () -> ApplicationManager.getApplication().runWriteAction(() -> runAndFinishTestRunnable(r)), p),
                                                        ModalityState.defaultModalityState());
    }
  }

  public static void runDumbAware(@NotNull Project project, @NotNull Runnable r) {
    startTestRunnable(r);
    if (DumbService.isDumbAware(r)) {
      runAndFinishTestRunnable(r);
    }
    else {
      DumbService.getInstance(project).runWhenSmart(DisposeAwareRunnable.create(() -> runAndFinishTestRunnable(r), project));
    }
  }

  public static void runWhenInitialized(@NotNull Project project, @NotNull Runnable runnable) {
    if (project.isDisposed()) {
      return;
    }

    if (project.isInitialized()) {
      runDumbAware(project, runnable);
    }
    else {
      startTestRunnable(runnable);
      StartupManager.getInstance(project).runAfterOpened(() -> runAndFinishTestRunnable(runnable));
    }
  }

  public static boolean isInModalContext() {
    return LaterInvocator.isInModalContext();
  }

  public static void showError(Project project, @NlsContexts.NotificationTitle String title, Throwable e) {
    MavenLog.LOG.warn(title, e);
    Notifications.Bus.notify(new Notification(MAVEN_NOTIFICATION_GROUP, title, e.getMessage(), NotificationType.ERROR), project);
  }

  public static void showError(Project project,
                               @NlsContexts.NotificationTitle String title,
                               @NlsContexts.NotificationContent String message) {
    MavenLog.LOG.warn(title);
    Notifications.Bus.notify(new Notification(MAVEN_NOTIFICATION_GROUP, title, message, NotificationType.ERROR), project);
  }

  @NotNull
  public static Path getPluginSystemDir(@NotNull String folder) {
    return PathManagerEx.getAppSystemDir().resolve("Maven").resolve(folder);
  }

  @NotNull
  public static Path getBaseDir(@NotNull VirtualFile file) {
    VirtualFile virtualBaseDir = getVFileBaseDir(file);
    return virtualBaseDir.toNioPath();
  }

  public static MultiMap<String, MavenProject> groupByBasedir(@NotNull Collection<MavenProject> projects, @NotNull MavenProjectsTree tree) {
    return ContainerUtil.groupBy(projects, p -> getBaseDir(tree.findRootProject(p).getDirectoryFile()).toString());
  }

  @NotNull
  public static VirtualFile getVFileBaseDir(@NotNull VirtualFile file) {
    VirtualFile baseDir = file.isDirectory() || file.getParent() == null ? file : file.getParent();
    VirtualFile dir = baseDir;
    do {
      VirtualFile child = dir.findChild(".mvn");

      if (child != null && child.isDirectory()) {
        if (MavenLog.LOG.isTraceEnabled()) {
          MavenLog.LOG.trace("found .mvn in " + child);
        }
        baseDir = dir;
        break;
      }
    }
    while ((dir = dir.getParent()) != null);
    if (MavenLog.LOG.isTraceEnabled()) {
      MavenLog.LOG.trace("return " + baseDir + " as baseDir");
    }
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
  public static Path getProfilesXmlNioFile(VirtualFile pomFile) {
    if (pomFile == null) return null;
    VirtualFile parent = pomFile.getParent();
    if (parent == null) return null;
    return parent.toNioPath().resolve(MavenConstants.PROFILES_XML);
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
    return (collection instanceof Set ? collection : new HashSet<>(collection));
  }

  @NotNull
  public static <T, U> List<Pair<T, U>> mapToList(Map<T, U> map) {
    return ContainerUtil.map(map.entrySet(), tuEntry -> Pair.create(tuEntry.getKey(), tuEntry.getValue()));
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
    runOrApplyMavenProjectFileTemplate(project, file, projectId, parentId, parentFile, new Properties(), new Properties(),
                                       MavenFileTemplateGroupFactory.MAVEN_PROJECT_XML_TEMPLATE, interactive);
  }

  public static void runOrApplyMavenProjectFileTemplate(Project project,
                                                        VirtualFile file,
                                                        @NotNull MavenId projectId,
                                                        MavenId parentId,
                                                        @Nullable VirtualFile parentFile,
                                                        @NotNull Properties properties,
                                                        @NotNull Properties conditions,
                                                        @NonNls @NotNull String template,
                                                        boolean interactive) throws IOException {
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
    else {
      //set language level only for root pom
      Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
      if (sdk != null && sdk.getSdkType() instanceof JavaSdk javaSdk) {
        JavaSdkVersion version = javaSdk.getVersion(sdk);
        String description = version == null ? null : version.getDescription();
        boolean shouldSetLangLevel = version != null && version.isAtLeast(JavaSdkVersion.JDK_1_6);
        conditions.setProperty("SHOULD_SET_LANG_LEVEL", String.valueOf(shouldSetLangLevel));
        properties.setProperty("COMPILER_LEVEL_SOURCE", description);
        properties.setProperty("COMPILER_LEVEL_TARGET", description);
      }
    }
    runOrApplyFileTemplate(project, file, template, properties, conditions, interactive);
  }

  public static void runFileTemplate(Project project,
                                     VirtualFile file,
                                     String templateName) throws IOException {
    runOrApplyFileTemplate(project, file, templateName, new Properties(), new Properties(), true);
  }

  public static void runOrApplyFileTemplate(Project project,
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
    Pattern pattern = Pattern.compile("\\$\\{(.*)}");
    Matcher matcher = pattern.matcher(text);
    StringBuilder builder = new StringBuilder();
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
        if (project.isInitialized()) {
          new ReformatCodeProcessor(project, psiFile, null, false).run();
        }
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

  public static void run(@NlsContexts.DialogTitle String title, final MavenTask task)
    throws MavenProcessCanceledException {
    final Exception[] canceledEx = new Exception[1];
    final RuntimeException[] runtimeEx = new RuntimeException[1];
    final Error[] errorEx = new Error[1];

    ProgressManager.getInstance().run(new Task.Modal(null, title, true) {
      @Override
      public void run(@NotNull ProgressIndicator i) {
        try {
          task.run(new MavenProgressIndicator(null, i, null));
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
  // used in third-party plugins
  public static MavenTaskHandler runInBackground(@NotNull Project project,
                                                 @NotNull @NlsContexts.Command String title,
                                                 boolean cancellable,
                                                 @NotNull MavenTask task) {
    MavenProjectsManager manager = MavenProjectsManager.getInstanceIfCreated(project);
    Supplier<MavenSyncConsole> syncConsoleSupplier = manager == null ? null : () -> manager.getSyncConsole();
    MavenProgressIndicator indicator = new MavenProgressIndicator(project, syncConsoleSupplier);

    Runnable runnable = () -> {
      if (project.isDisposed()) return;

      try {
        task.run(indicator);
      }
      catch (MavenProcessCanceledException | ProcessCanceledException e) {
        indicator.cancel();
      }
    };

    Future<?> future;
    future = ApplicationManager.getApplication().executeOnPooledThread(runnable);
    MavenTaskHandler handler = new MavenTaskHandler() {
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

  /**
   * @deprecated do not use this method, it mixes path to maven home and labels like "Use bundled maven"
   * use {@link MavenUtil#getMavenHomePath(StaticResolvedMavenHomeType) getMavenHomePath(StaticResolvedMavenHomeType} instead
   */
  @Nullable
  @Deprecated(forRemoval = true)
  public static File resolveMavenHomeDirectory(@Nullable String overrideMavenHome) {
    if (!isEmptyOrSpaces(overrideMavenHome)) {
      //noinspection HardCodedStringLiteral
      return getMavenHomePath(staticOrBundled(resolveMavenHomeType(overrideMavenHome))).toFile();
    }

    String m2home = System.getenv(ENV_M2_HOME);
    if (!isEmptyOrSpaces(m2home)) {
      final File homeFromEnv = new File(m2home);
      if (isValidMavenHome(homeFromEnv.toPath())) {
        return homeFromEnv;
      }
    }

    String mavenHome = System.getenv("MAVEN_HOME");
    if (!isEmptyOrSpaces(mavenHome)) {
      final File mavenHomeFile = new File(mavenHome);
      if (isValidMavenHome(mavenHomeFile.toPath())) {
        return mavenHomeFile;
      }
    }

    String userHome = SystemProperties.getUserHome();
    if (!isEmptyOrSpaces(userHome)) {
      final File underUserHome = new File(userHome, M2_DIR);
      if (isValidMavenHome(underUserHome.toPath())) {
        return underUserHome;
      }
    }

    if (SystemInfo.isMac) {
      Path home = fromBrew(null);
      if (home != null) {
        return home.toFile();
      }

      if ((home = fromMacSystemJavaTools(null)) != null) {
        return home.toFile();
      }
    }
    else if (SystemInfo.isLinux) {
      File home = new File("/usr/share/maven");
      if (isValidMavenHome(home.toPath())) {
        return home;
      }

      home = new File("/usr/share/maven2");
      if (isValidMavenHome(home.toPath())) {
        return home;
      }
    }

    return MavenDistributionsCache.resolveEmbeddedMavenHome().getMavenHome().toFile();
  }

  public static List<MavenHomeType> getSystemMavenHomeVariants(Project project) {
    List<MavenHomeType> result = new ArrayList<>();

    var eel = getEelApiBlocking(project);
    var envs = EelUtilsKt.fetchLoginShellEnvVariablesBlocking(eel.getExec());

    String m2home = envs.get(ENV_M2_HOME);
    if (!isEmptyOrSpaces(m2home)) {
      final Path homeFromEnv = eel.getMapper().toNioPath(getPath(eel.getFs(), m2home));
      if (isValidMavenHome(homeFromEnv)) {
        result.add(new MavenInSpecificPath(m2home));
      }
    }

    String mavenHome = envs.get("MAVEN_HOME");
    if (!isEmptyOrSpaces(mavenHome)) {
      final Path mavenHomeFile = eel.getMapper().toNioPath(getPath(eel.getFs(), mavenHome));
      if (isValidMavenHome(mavenHomeFile)) {
        result.add(new MavenInSpecificPath(mavenHome));
      }
    }

    var userHome = EelPathUtilsKt.userHomeBlocking(eel.getFs());
    if (userHome != null && !isEmptyOrSpaces(userHome.toString())) {
      var nioUserHome = eel.getMapper().toNioPath(userHome);
      final Path underUserHome = nioUserHome.resolve(M2_DIR);
      if (isValidMavenHome(underUserHome)) {
        result.add(new MavenInSpecificPath(userHome.toString()));
      }
    }

    // TODO: eel
    if (eel instanceof LocalEelApi && SystemInfo.isMac) {
      Path home = fromBrew(eel);
      if (home != null) {
        result.add(new MavenInSpecificPath(home.toAbsolutePath().toString()));
      }

      if ((home = fromMacSystemJavaTools(eel)) != null) {
        result.add(new MavenInSpecificPath(home.toAbsolutePath().toString()));
      }
    }
    else if (eel.getPlatform() instanceof EelPlatform.Linux) {
      Path home = eel.getMapper().toNioPath(getPath(eel.getFs(), "/usr/share/maven"));
      if (isValidMavenHome(home)) {
        result.add(new MavenInSpecificPath(home.toAbsolutePath().toString()));
      }

      home = eel.getMapper().toNioPath(getPath(eel.getFs(), "/usr/share/maven2"));
      if (isValidMavenHome(home)) {
        result.add(new MavenInSpecificPath(home.toAbsolutePath().toString()));
      }
    }

    result.add(BundledMaven3.INSTANCE);
    return result;
  }

  public static void addEventListener(@NotNull String mavenVersion, @NotNull SimpleJavaParameters params) {
    if (VersionComparatorUtil.compare(mavenVersion, "3.0.2") < 0) {
      MavenLog.LOG.warn("Maven version less than 3.0.2 are not correctly displayed in Build Window");
      return;
    }
    String listenerPath = MavenServerManager.getInstance().getMavenEventListener().getAbsolutePath();
    String userExtClassPath =
      StringUtils.stripToEmpty(params.getVMParametersList().getPropertyValue(MavenServerEmbedder.MAVEN_EXT_CLASS_PATH));
    String vmParameter = "-D" + MavenServerEmbedder.MAVEN_EXT_CLASS_PATH + "=";
    String[] userListeners = userExtClassPath.split(File.pathSeparator);
    CompositeParameterTargetedValue targetedValue = new CompositeParameterTargetedValue(vmParameter)
      .addPathPart(listenerPath);

    for (String path : userListeners) {
      if (StringUtil.isEmptyOrSpaces(path)) continue;
      targetedValue = targetedValue.addPathSeparator().addPathPart(path);
    }
    params.getVMParametersList().add(targetedValue);
  }

  @Nullable
  private static Path fromMacSystemJavaTools(@Nullable EelApi eelApi) {
    final Path symlinkDir;
    if (eelApi == null) {
      symlinkDir = Path.of("/usr/share/maven");
    }
    else {
      symlinkDir = eelApi.getMapper().toNioPath(getPath(eelApi.getFs(), "/usr/share/maven"));
    }

    if (isValidMavenHome(symlinkDir)) {
      return symlinkDir;
    }

    // well, try to search
    final Path dir;

    if (eelApi == null) {
      dir = Path.of("/usr/share/java");
    }
    else {
      dir = eelApi.getMapper().toNioPath(getPath(eelApi.getFs(), "/usr/share/java"));
    }

    List<Path> list = new ArrayList<>();

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
      for (Path path : stream) {
        list.add(path);
      }
    }
    catch (IOException e) {
      return null;
    }

    if (list.isEmpty()) {
      return null;
    }

    Path home = null;
    final String prefix = "maven-";
    final int versionIndex = prefix.length();
    for (Path path : list) {
      if (path.startsWith(prefix) &&
          (home == null || compareVersionNumbers(path.toString().substring(versionIndex), home.toString().substring(versionIndex)) > 0)) {
        home = path;
      }
    }

    if (home != null) {
      Path file = dir.resolve(home);
      if (isValidMavenHome(file)) {
        return file;
      }
    }

    return null;
  }

  @Nullable
  private static Path fromBrew(EelApi eelApi) {
    final Path brewDir = eelApi.getMapper().toNioPath(getPath(eelApi.getFs(), "/usr/local/Cellar/maven"));

    List<Path> list = new ArrayList<>();

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(brewDir)) {
      for (Path path : stream) {
        list.add(path);
      }
    }
    catch (IOException e) {
      return null;
    }

    if (list.isEmpty()) {
      return null;
    }


    if (list.size() > 1) {
      list.sort(Comparator.comparing(Path::toString));
    }

    final Path file = brewDir.resolve(list.get(0).toString() + "/libexec");
    return isValidMavenHome(file) ? file : null;
  }

  public static boolean isEmptyOrSpaces(@Nullable String str) {
    return str == null || str.isBlank();
  }


  public static boolean isValidMavenHome(@Nullable Path home) {
    if (home == null) return false;
    try {
      Path binDir = home.resolve(BIN_DIR);
      if (!Files.isDirectory(binDir)) return false;

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(binDir)) {
        Set<String> set = new HashSet<>();
        for (Path entry : stream) {
          set.add(entry.getFileName().toString());
        }
        return set.contains(M2_CONF_FILE) && set.contains(MVN_FILE);
      }
    }
    catch (Exception ignored) {
    }
    return false;
  }

  @Deprecated
  public static File getMavenConfFile(File mavenHome) {
    return new File(new File(mavenHome, BIN_DIR), M2_CONF_FILE);
  }

  public static Path getMavenConfFilePath(Path mavenHome) {
    return mavenHome.resolve(BIN_DIR).resolve(M2_CONF_FILE);
  }

  @Deprecated
  public static @Nullable File getMavenHomeFile(@NotNull StaticResolvedMavenHomeType mavenHome) {
    return Optional.ofNullable(getMavenHomePath(mavenHome)).map(Path::toFile).orElse(null);
  }

  public static @Nullable Path getMavenHomePath(@NotNull StaticResolvedMavenHomeType mavenHome) {
    if (mavenHome instanceof MavenInSpecificPath mp) {
      Path file = Path.of(mp.getMavenHome());
      return isValidMavenHome(file) ? file : null;
    }
    for (MavenVersionAwareSupportExtension e : MavenVersionAwareSupportExtension.MAVEN_VERSION_SUPPORT.getExtensionList()) {
      Path file = e.getMavenHomeFile(mavenHome);
      if (file != null) return file;
    }
    return null;
  }


  @Nullable
  public static String getMavenVersion(@Nullable Path mavenHome) {
    if (mavenHome == null) return null;
    Path libDir = mavenHome.resolve("lib");
    if (!Files.isDirectory(libDir)) {
      MavenLog.LOG.warn("Cannot find lib directory in " + mavenHome);
      return null;
    }

    try (DirectoryStream<Path> stream = Files.newDirectoryStream(libDir)) {
      for (Path mavenLibPath : stream) {
        String lib = mavenLibPath.getFileName().toString();
        if (lib.equals("maven-core.jar")) {
          MavenLog.LOG.trace("Choosing version by maven-core.jar");
          return getMavenLibVersion(mavenLibPath);
        }
        if (lib.startsWith("maven-core-") && lib.endsWith(".jar")) {
          MavenLog.LOG.trace("Choosing version by maven-core.xxx.jar");
          String version = lib.substring("maven-core-".length(), lib.length() - ".jar".length());
          return version.contains(".x") ? getMavenLibVersion(mavenLibPath) : version;
        }
        if (lib.startsWith("maven-") && lib.endsWith("-uber.jar")) {
          MavenLog.LOG.trace("Choosing version by maven-xxx-uber.jar");
          return lib.substring("maven-".length(), lib.length() - "-uber.jar".length());
        }
      }
    }
    catch (IOException e) {
      MavenLog.LOG.warn("Cannot read lib directory in " + mavenHome, e);
      return null;
    }

    MavenLog.LOG.warn("Cannot resolve maven version for " + mavenHome);
    return null;
  }

  private static String getMavenLibVersion(final Path file) {
    Properties props = JarUtils.loadProperties(file, "META-INF/maven/org.apache.maven/maven-core/pom.properties");
    return props != null
           ? nullize(props.getProperty("version"))
           : nullize(JarUtils.getJarAttribute(file, null, java.util.jar.Attributes.Name.IMPLEMENTATION_VERSION));
  }

  @Nullable
  public static String getMavenVersion(String mavenHome) {
    return getMavenVersion(Path.of(mavenHome));
  }

  @Nullable
  public static String getMavenVersion(Project project, String workingDir) {
    MavenHomeType homeType = MavenWorkspaceSettingsComponent.getInstance(project).getSettings().getGeneralSettings().getMavenHomeType();
    if (homeType instanceof StaticResolvedMavenHomeType srmt) {
      return getMavenVersion(srmt);
    }
    MavenDistribution distribution = MavenDistributionsCache.getInstance(project).getWrapper(workingDir);
    if (distribution != null) return distribution.getVersion();
    return null;
  }

  @Nullable
  public static String getMavenVersion(StaticResolvedMavenHomeType mavenHomeType) {
    return getMavenVersion(getMavenHomePath(mavenHomeType));
  }

  public static boolean isMaven3(String mavenHome) {
    String version = getMavenVersion(mavenHome);
    return version != null && version.compareTo("3.0.0") >= 0;
  }

  @Nullable
  public static Path resolveGlobalSettingsFile(@NotNull StaticResolvedMavenHomeType mavenHomeType) {
    Path directory = getMavenHomePath(mavenHomeType);
    if (directory == null) return null;
    return directory.resolve(CONF_DIR).resolve(SETTINGS_XML);
  }

  @NotNull
  public static Path resolveGlobalSettingsFile(@NotNull Path mavenHome) {
    return mavenHome.resolve(CONF_DIR).resolve(SETTINGS_XML);
  }

  @NotNull
  @Deprecated
  public static File resolveUserSettingsFile(@Nullable String overriddenUserSettingsFile) {
    return resolveUserSettingsPath(overriddenUserSettingsFile, null).toFile();
  }

  @NotNull
  public static Path resolveUserSettingsPath(@Nullable String overriddenUserSettingsFile, @Nullable Project project) {
    if (!isEmptyOrSpaces(overriddenUserSettingsFile)) return Path.of(overriddenUserSettingsFile);
    return resolveM2Dir(project).resolve(SETTINGS_XML);
  }

  @NotNull
  public static Path resolveM2Dir(@Nullable Project project) {
    var eel = project != null ? getEelApiBlocking(project) : null;
    return MavenEelUtil.resolveM2Dir(eel);
  }

  /**
   * @deprecated do not use this method, it mixes path to maven home and labels like "Use bundled maven" in overriddenMavenHome variable
   * use {@link MavenUtil#resolveLocalRepository(String, StaticResolvedMavenHomeType, String) resolveLocalRepository(String, StaticResolvedMavenHomeType, String)}
   * or {@link MavenUtil#resolveDefaultLocalRepository() resolveDefaultLocalRepository()} instead
   */
  @NotNull
  @Deprecated(forRemoval = true)
  public static File resolveLocalRepository(@Nullable String overriddenLocalRepository,
                                            @Nullable String overriddenMavenHome,
                                            @Nullable String overriddenUserSettingsFile) {
    return resolveLocalRepository(null, overriddenLocalRepository, overriddenMavenHome, overriddenUserSettingsFile).toFile();
  }

  /**
   * @deprecated do not use this method, it mixes path to maven home and labels like "Use bundled maven" in overriddenMavenHome variable
   * use {@link MavenUtil#resolveLocalRepository(String, StaticResolvedMavenHomeType, String) resolveLocalRepository(String, StaticResolvedMavenHomeType, String)}
   * or {@link MavenUtil#resolveDefaultLocalRepository() resolveDefaultLocalRepository()} instead
   */
  @NotNull
  @Deprecated(forRemoval = true)
  public static Path resolveLocalRepository(@Nullable Project project,
                                            @Nullable String overriddenLocalRepository,
                                            @Nullable String overriddenMavenHome,
                                            @Nullable String overriddenUserSettingsFile) {
    //noinspection HardCodedStringLiteral
    MavenHomeType type = resolveMavenHomeType(overriddenMavenHome);
    if (type instanceof StaticResolvedMavenHomeType st) {
      return resolveLocalRepository(project, overriddenLocalRepository, st, overriddenUserSettingsFile);
    }
    throw new IllegalArgumentException("Cannot resolve local repository for wrapped maven, this API is deprecated");
  }

  @NotNull
  public static Path resolveDefaultLocalRepository(@Nullable Project project) {
    String mavenRepoLocal = System.getProperty(MAVEN_REPO_LOCAL);

    if (mavenRepoLocal != null) {
      MavenLog.LOG.info("using " + MAVEN_REPO_LOCAL + "=" + mavenRepoLocal + " as maven home");
      return Path.of(mavenRepoLocal);
    }

    String forcedM2Home = System.getProperty(PROP_FORCED_M2_HOME);
    if (forcedM2Home != null) {
      MavenLog.LOG.error(PROP_FORCED_M2_HOME + " is deprecated, use maven.repo.local property instead");
      return Path.of(forcedM2Home);
    }

    Path result = doResolveLocalRepository(resolveUserSettingsPath(null, project), null);

    if (result == null) {
      result = resolveM2Dir(project).resolve(REPOSITORY_DIR);
    }

    try {
      return result.toRealPath();
    }
    catch (IOException e) {
      return result;
    }
  }

  @NotNull
  public static Path resolveLocalRepository(@Nullable Project project,
                                            @Nullable String overriddenLocalRepository,
                                            @NotNull StaticResolvedMavenHomeType mavenHomeType,
                                            @Nullable String overriddenUserSettingsFile) {
    String forcedM2Home = System.getProperty(PROP_FORCED_M2_HOME);
    if (forcedM2Home != null) {
      MavenLog.LOG.error(PROP_FORCED_M2_HOME + " is deprecated, use maven.repo.local property instead");
      return Path.of(forcedM2Home);
    }
    Path result = null;
    if (!isEmptyOrSpaces(overriddenLocalRepository)) result = Path.of(overriddenLocalRepository);

    String localRepoHome = System.getProperty(MAVEN_REPO_LOCAL);
    if (localRepoHome != null) {
      MavenLog.LOG.debug("Using " + MAVEN_REPO_LOCAL + "=" + localRepoHome);
      return Path.of(localRepoHome);
    }
    if (result == null) {
      result = doResolveLocalRepository(resolveUserSettingsPath(overriddenUserSettingsFile, project),
                                        resolveGlobalSettingsFile(mavenHomeType));

      if (result == null) {
        result = resolveM2Dir(project).resolve(REPOSITORY_DIR);
      }
    }
    try {
      return result.toRealPath();
    }
    catch (IOException e) {
      return result;
    }
  }

  @Nullable
  public static Path getRepositoryFile(@NotNull Project project,
                                       @NotNull MavenId id,
                                       @NotNull String extension,
                                       @Nullable String classifier) {
    if (id.getGroupId() == null || id.getArtifactId() == null || id.getVersion() == null) {
      return null;
    }
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    return makeLocalRepositoryFile(id, projectsManager.getReposirotyPath(), extension, classifier);
  }

  @NotNull
  public static Path makeLocalRepositoryFile(MavenId id,
                                             Path localRepository,
                                             @NotNull String extension,
                                             @Nullable String classifier) {
    String relPath = id.getGroupId().replace(".", "/");

    relPath += "/" + id.getArtifactId();
    relPath += "/" + id.getVersion();
    relPath += "/" + id.getArtifactId() + "-" + id.getVersion();
    relPath = classifier == null ? relPath + "." + extension : relPath + "-" + classifier + "." + extension;

    return localRepository.resolve(relPath);
  }

  @Nullable
  public static Path getArtifactPath(@NotNull Path localRepository,
                                     @NotNull MavenId id,
                                     @NotNull String extension,
                                     @Nullable String classifier) {
    if (id.getGroupId() == null || id.getArtifactId() == null || id.getVersion() == null) {
      return null;
    }
    String[] artifactPath = id.getGroupId().split("\\.");
    try {
      for (String path : artifactPath) {
        localRepository = localRepository.resolve(path);
      }
      return localRepository
        .resolve(id.getArtifactId())
        .resolve(id.getVersion())
        .resolve(id.getArtifactId() + "-" + id.getVersion() + (classifier == null ? "." + extension : "-" + classifier + "." + extension));
    }
    catch (InvalidPathException e) {
      return null;
    }
  }

  @Nullable
  public static Path getRepositoryParentFile(@NotNull Project project, @NotNull MavenId id) {
    if (id.getGroupId() == null || id.getArtifactId() == null || id.getVersion() == null) {
      return null;
    }
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    return getParentFile(id, projectsManager.getReposirotyPath());
  }

  private static Path getParentFile(@NotNull MavenId id, Path localRepository) {
    assert id.getGroupId() != null;
    String[] pathParts = id.getGroupId().split("\\.");
    Path path = Paths.get(localRepository.toAbsolutePath().toString(), pathParts);
    path = Paths.get(path.toString(), id.getArtifactId(), id.getVersion());
    return path;
  }

  @Nullable
  protected static Path doResolveLocalRepository(@Nullable Path userSettingsFile, @Nullable Path globalSettingsFile) {
    if (userSettingsFile != null) {
      final String fromUserSettings = getRepositoryFromSettings(userSettingsFile);
      if (!isEmpty(fromUserSettings)) {
        return Path.of(fromUserSettings);
      }
    }

    if (globalSettingsFile != null) {
      final String fromGlobalSettings = getRepositoryFromSettings(globalSettingsFile);
      if (!isEmpty(fromGlobalSettings)) {
        return Path.of(fromGlobalSettings);
      }
    }

    return null;
  }

  @Nullable
  public static String getRepositoryFromSettings(final Path file) {
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

  public static String getMirroredUrl(final Path settingsFile, String url, String id) {
    try {
      Element mirrorParent = getElementWithRegardToNamespace(getDomRootElement(settingsFile), "mirrors", settingsListNamespaces);
      if (mirrorParent == null) {
        return url;
      }
      List<Element> mirrors = getElementsWithRegardToNamespace(mirrorParent, "mirror", settingsListNamespaces);
      for (Element el : mirrors) {
        Element mirrorOfElement = getElementWithRegardToNamespace(el, "mirrorOf", settingsListNamespaces);
        Element mirrorUrlElement = getElementWithRegardToNamespace(el, "url", settingsListNamespaces);
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
  private static Element getRepositoryElement(Path file) throws JDOMException, IOException {
    return getElementWithRegardToNamespace(getDomRootElement(file), "localRepository", settingsListNamespaces);
  }

  private static Element getDomRootElement(Path file) throws IOException, JDOMException {
    InputStreamReader reader = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8);
    return JDOMUtil.load(reader);
  }

  @Nullable
  private static Element getElementWithRegardToNamespace(@NotNull Element parent, String childName, List<String> namespaces) {
    Element element = parent.getChild(childName);
    if (element != null) return element;
    for (String namespace : namespaces) {
      element = parent.getChild(childName, Namespace.getNamespace(namespace));
      if (element != null) return element;
    }
    return null;
  }

  private static List<Element> getElementsWithRegardToNamespace(@NotNull Element parent, String childrenName, List<String> namespaces) {
    List<Element> elements = parent.getChildren(childrenName);
    if (!elements.isEmpty()) return elements;
    for (String namespace : namespaces) {
      elements = parent.getChildren(childrenName, Namespace.getNamespace(namespace));
      if (!elements.isEmpty()) return elements;
    }
    return Collections.emptyList();
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

  /**
   * Retrieves the effective SuperPOM as a virtual file.
   *
   * @param mavenDistribution A valid Maven distribution.
   * @param superPomName      The name of the POM file. MavenConstants#SUPER_POM_4_0_XML for Maven 3 and either MavenConstants#SUPER_POM_4_0_XML or MavenConstants#SUPER_POM_4_1_XML for Maven 4.
   * @return A {@link VirtualFile} representing the SuperPOM located inside the jar if found, False otherwise.
   */

  @Nullable
  public static VirtualFile resolveSuperPomFile(@NotNull Path mavenHome, String superPomName) {
    return doResolveSuperPomFile(mavenHome.resolve(LIB_DIR), superPomName);
  }

  @Nullable
  public static VirtualFile resolveSuperPomFile(@NotNull Project project, VirtualFile projectFile) {
    MavenDistribution distribution = MavenDistributionsCache.getInstance(project).getMavenDistribution(projectFile.getParent().getPath());
    String superPomName = resolveMavenSchema(projectFile);
    return resolveSuperPomFile(distribution.getMavenHome(), superPomName);
  }

  private static String resolveMavenSchema(VirtualFile file) {
    return MavenConstants.SUPER_POM_4_0_XML; //todo
  }

  @Nullable
  private static VirtualFile doResolveSuperPomFile(@NotNull Path libDir, String superPomName) {
    Path[] libraries;
    try {
      libraries = Files.list(libDir).toArray(Path[]::new);
    }
    catch (IOException e) {
      return null;
    }

    for (Path library : libraries) {
      if ((library.getFileName().toString().startsWith("maven-model-builder-") && library.getFileName().toString().endsWith(".jar"))) {
        VirtualFile result = tryReadFromLib(library, "org/apache/maven/model/" + superPomName);
        if (result != null) {
          return result;
        }
      }
      else if ((library.getFileName().toString().startsWith("maven-") && library.getFileName().getFileName().toString().endsWith("-uber.jar"))) {
        //old maven versions
        VirtualFile result = tryReadFromLib(library, "org/apache/maven/project/" + superPomName);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  private static @Nullable VirtualFile tryReadFromLib(Path library, @NotNull String pathInJar) {
    VirtualFile libraryVirtualFile = LocalFileSystem.getInstance().findFileByNioFile(library);
    if (libraryVirtualFile == null) return null;
    VirtualFile root = JarFileSystem.getInstance().getJarRootForLocalFile(libraryVirtualFile);
    if (root == null) return null;
    return root.findFileByRelativePath(pathInJar);
  }

  public static List<LookupElement> getPhaseVariants(MavenProjectsManager manager) {
    Set<String> goals = new HashSet<>(MavenConstants.PHASES);

    for (MavenProject mavenProject : manager.getProjects()) {
      for (var mavenProjectPluginInfo : mavenProject.getPluginInfos()) {
        MavenPluginInfo pluginInfo = MavenArtifactUtil.readPluginInfo(mavenProjectPluginInfo.getArtifact());
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

  public static boolean isProjectTrustedEnoughToImport(Project project) {
    return ExternalSystemUtil.confirmLoadingUntrustedProject(project, SYSTEM_ID);
  }

  /**
   * @param project   Project required to restart connectors
   * @param wait      if true, then maven server(s) restarted synchronously
   * @param condition only connectors satisfied for this predicate will be restarted
   */
  public static void restartMavenConnectors(@NotNull Project project, boolean wait, Predicate<@NotNull MavenServerConnector> condition) {
    MavenServerManager.getInstance().restartMavenConnectors(project, wait, condition);
  }

  public static void restartMavenConnectors(@NotNull Project project, boolean wait) {
    restartMavenConnectors(project, wait, c -> Boolean.TRUE);
  }

  public static boolean verifyMavenSdkRequirements(@NotNull Sdk jdk, String mavenVersion) {
    if (compareVersionNumbers(mavenVersion, "3.3.1") < 0) {
      return true;
    }
    SdkTypeId sdkType = jdk.getSdkType();
    if (sdkType instanceof JavaSdk) {
      JavaSdkVersion version = ((JavaSdk)sdkType).getVersion(jdk);
      if (version == null || version.isAtLeast(JavaSdkVersion.JDK_1_7)) {
        return true;
      }
    }
    return false;
  }

  public interface MavenTaskHandler {
    void waitFor();
  }

  public static int crcWithoutSpaces(@NotNull InputStream in) throws IOException {
    try {
      final CRC32 crc = new CRC32();

      SAXParser parser = SAXParserFactory.newDefaultInstance().newSAXParser();
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
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
          textContentOccur = false;

          crc.update(1);
          putString(qName);

          for (int i = 0; i < attributes.getLength(); i++) {
            putString(attributes.getQName(i));
            putString(attributes.getValue(i));
          }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
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
        public void characters(char[] ch, int start, int length) {
          processTextOrSpaces(ch, start, length);
        }

        @Override
        public void ignorableWhitespace(char[] ch, int start, int length) {
          processTextOrSpaces(ch, start, length);
        }

        @Override
        public void processingInstruction(String target, String data) {
          putString(target);
          putString(data);
        }

        @Override
        public void skippedEntity(String name) {
          putString(name);
        }

        @Override
        public void error(SAXParseException e) {
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
  public static <K, V extends Map<?, ?>> V getOrCreate(Map<K, V> map, K key) {
    V res = map.get(key);
    if (res == null) {
      //noinspection unchecked
      res = (V)new HashMap<>();
      map.put(key, res);
    }

    return res;
  }

  public static boolean isMavenModule(@Nullable Module module) {
    return module != null && MavenProjectsManager.getInstance(module.getProject()).isMavenizedModule(module);
  }

  public static String getArtifactName(String packaging, Module module, boolean exploded) {
    return getArtifactName(packaging, module.getName(), exploded);
  }

  public static String getArtifactName(String packaging, String moduleName, boolean exploded) {
    return moduleName + ":" + packaging + (exploded ? " exploded" : "");
  }

  public static String getEjbClientArtifactName(Module module, boolean exploded) {
    return getEjbClientArtifactName(module.getName(), exploded);
  }

  public static String getEjbClientArtifactName(String moduleName, boolean exploded) {
    return moduleName + ":ejb" + (exploded ? CLIENT_EXPLODED_ARTIFACT_SUFFIX : CLIENT_ARTIFACT_SUFFIX);
  }

  public static String getIdeaVersionToPassToMavenProcess() {
    return ApplicationInfoImpl.getShadowInstance().getMajorVersion() + "." + ApplicationInfoImpl.getShadowInstance().getMinorVersion();
  }

  public static boolean isPomFileName(String fileName) {
    return fileName.equals(MavenConstants.POM_XML) ||
           fileName.endsWith(".pom") || fileName.startsWith("pom.") ||
           fileName.equals(MavenConstants.SUPER_POM_4_0_XML);
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


  public static boolean containsDeclaredExtension(final Path extensionFile, @NotNull MavenId mavenId) {
    try {

      Element extensions = getDomRootElement(extensionFile);
      if (extensions == null) return false;
      if (!extensions.getName().equals("extensions")) return false;
      for (Element extension : getElementsWithRegardToNamespace(extensions, "extension", extensionListNamespaces)) {
        Element groupId = getElementWithRegardToNamespace(extension, "groupId", extensionListNamespaces);
        Element artifactId = getElementWithRegardToNamespace(extension, "artifactId", extensionListNamespaces);
        Element version = getElementWithRegardToNamespace(extension, "version", extensionListNamespaces);

        if (groupId != null &&
            groupId.getTextTrim().equals(mavenId.getGroupId()) &&
            artifactId != null &&
            artifactId.getTextTrim().equals(mavenId.getArtifactId()) &&
            version != null &&
            version.getTextTrim().equals(mavenId.getVersion())) {
          return true;
        }
      }
    }
    catch (IOException | JDOMException e) {
      return false;
    }
    return false;
  }

  public static boolean isPomFileIgnoringName(@Nullable Project project, @NotNull VirtualFile file) {
    if (project == null || !project.isInitialized()) {
      if (!FileUtilRt.extensionEquals(file.getName(), "xml")) return false;
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

  public static void restartConfigHighlighting(Collection<MavenProject> projects) {
    VirtualFile[] configFiles = getConfigFiles(projects);
    ApplicationManager.getApplication().invokeLater(() -> {
      FileContentUtilCore.reparseFiles(configFiles);
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
    VirtualFile baseDir = getVFileBaseDir(mavenProject.getDirectoryFile());
    return baseDir.findFileByRelativePath(fileRelativePath);
  }

  public static MavenPathWrapper toPath(@Nullable MavenProject mavenProject, String path) {
    if (!Paths.get(path).isAbsolute()) {
      if (mavenProject == null) {
        throw new IllegalArgumentException("Project should be not-nul for non-absolute paths");
      }
      path = Path.of(mavenProject.getDirectory(), path).toString();
    }
    return new MavenPathWrapper(path);
  }

  public static @NotNull Sdk getJdk(@NotNull Project project, @NotNull String name) throws ExternalSystemJdkException {
    if (name.equals(MavenRunnerSettings.USE_INTERNAL_JAVA) || project.isDefault()) {
      return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    }

    if (name.equals(MavenRunnerSettings.USE_PROJECT_JDK)) {
      Sdk res = ProjectRootManager.getInstance(project).getProjectSdk();

      if (res == null) {
        res = suggestProjectSdk(project);
      }

      if (res != null && res.getSdkType() instanceof JavaSdkType) {
        return res;
      }
      return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    }

    if (name.equals(MavenRunnerSettings.USE_JAVA_HOME)) {
      final String javaHome = ExternalSystemJdkUtil.getJavaHome();
      if (StringUtil.isEmptyOrSpaces(javaHome)) {
        throw new InvalidJavaHomeException(javaHome);
      }
      try {
        return JavaSdk.getInstance().createJdk("", javaHome);
      }
      catch (IllegalArgumentException e) {
        throw new InvalidJavaHomeException(javaHome);
      }
    }

    Sdk projectJdk = getSdkByExactName(name);
    if (projectJdk != null) return projectJdk;
    throw new InvalidSdkException(name);
  }


  @Nullable
  protected static Sdk getSdkByExactName(@NotNull String name) {
    for (Sdk projectJdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (projectJdk.getName().equals(name)) {
        if (projectJdk.getSdkType() instanceof JavaSdkType) {
          return projectJdk;
        }
      }
    }
    return null;
  }

  public static Path getMavenPluginParentFile() {
    return Paths.get(PathManager.getCommunityHomePath(), "plugins", "maven");
  }

  @ApiStatus.Internal
  //temporary api
  public static boolean isMavenUnitTestModeEnabled() {
    if (shouldRunTasksAsynchronouslyInTests()) {
      return false;
    }
    return ApplicationManager.getApplication().isUnitTestMode();
  }

  private static boolean shouldRunTasksAsynchronouslyInTests() {
    return Boolean.getBoolean("maven.unit.tests.remove");
  }

  @NotNull
  public static String getCompilerPluginVersion(@NotNull MavenProject mavenProject) {
    MavenPlugin plugin = mavenProject.findPlugin("org.apache.maven.plugins", "maven-compiler-plugin");
    return plugin != null ? plugin.getVersion() : "";
  }

  public static boolean isWrapper(@NotNull MavenGeneralSettings settings) {
    return settings.getMavenHomeType() instanceof MavenWrapper;
  }

  public static void setupProjectSdk(@NotNull Project project) {
    if (ProjectRootManager.getInstance(project).getProjectSdk() == null) {
      Sdk projectSdk = suggestProjectSdk(project);
      if (projectSdk == null) return;
      ApplicationManager.getApplication().runWriteAction(() -> {
        JavaSdkUtil.applyJdkToProject(project, projectSdk);
      });
    }
  }

  @Nullable
  public static Sdk suggestProjectSdk(@NotNull Project project) {
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    ProjectRootManager defaultProjectManager = ProjectRootManager.getInstance(defaultProject);
    Sdk defaultProjectSdk = defaultProjectManager.getProjectSdk();
    if (defaultProjectSdk != null) return null;
    ProjectJdkTable projectJdkTable = ProjectJdkTable.getInstance();
    SdkType sdkType = ExternalSystemJdkUtil.getJavaSdkType();
    return projectJdkTable.getSdksOfType(sdkType).stream()
      .filter(it -> it.getHomePath() != null && JdkUtil.checkForJre(it.getHomePath()))
      .max(sdkType.versionComparator())
      .orElse(null);
  }

  @NotNull
  public static Set<MavenRemoteRepository> getRemoteResolvedRepositories(@NotNull Project project) {
    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    Set<MavenRemoteRepository> repositories = projectsManager.getRemoteRepositories();
    MavenEmbeddersManager embeddersManager = projectsManager.getEmbeddersManager();

    String baseDir = project.getBasePath();
    List<MavenProject> projects = projectsManager.getRootProjects();
    if (!projects.isEmpty()) {
      baseDir = getBaseDir(projects.get(0).getDirectoryFile()).toString();
    }
    if (null == baseDir) {
      baseDir = "";
    }

    MavenEmbedderWrapper embedderWrapper = embeddersManager.getEmbedder(MavenEmbeddersManager.FOR_POST_PROCESSING, baseDir);
    try {
      Set<MavenRemoteRepository> resolvedRepositories = embedderWrapper.resolveRepositories(repositories);
      return resolvedRepositories.isEmpty() ? repositories : resolvedRepositories;
    }
    catch (Exception e) {
      MavenLog.LOG.warn("resolve remote repo error", e);
    }
    finally {
      embeddersManager.release(embedderWrapper);
    }
    return repositories;
  }

  public static boolean isMavenizedModule(@NotNull Module m) {
    try {
      return !m.isDisposed() && ExternalSystemModulePropertyManager.getInstance(m).isMavenized();
    }
    catch (AlreadyDisposedException e) {
      return false;
    }
  }

  @ApiStatus.Internal
  public static boolean shouldResetDependenciesAndFolders(Collection<MavenProjectProblem> readingProblems) {
    if (Registry.is("maven.always.reset")) return true;
    MavenProjectProblem unrecoverable = ContainerUtil.find(readingProblems, it -> !it.isRecoverable());
    return unrecoverable == null;
  }

  @ApiStatus.Internal
  public static boolean shouldKeepPreviousResolutionResults(Collection<MavenProjectProblem> readingProblems) {
    return !shouldResetDependenciesAndFolders(readingProblems);
  }

  /**
   * @deprecated use MavenUtil.resolveSuperPomFile
   */
  @Deprecated(forRemoval = true)
  public static @Nullable VirtualFile getEffectiveSuperPom(Project project, @NotNull String workingDir) {
    MavenDistribution distribution = MavenDistributionsCache.getInstance(project).getMavenDistribution(workingDir);
    return resolveSuperPomFile(distribution.getMavenHome(), MavenConstants.SUPER_POM_4_0_XML);
  }


  /**
   * @deprecated use MavenUtil.resolveSuperPomFile
   */
  @Deprecated(forRemoval = true)
  public static @Nullable VirtualFile getEffectiveSuperPomWithNoRespectToWrapper(Project project) {
    MavenDistribution distribution = MavenDistributionsCache.getInstance(project).getSettingsDistribution();
    return resolveSuperPomFile(distribution.getMavenHome(), MavenConstants.SUPER_POM_4_0_XML);
  }

  public static MavenProjectModelReadHelper createModelReadHelper(Project project) {
    return MavenProjectModelReadHelper.getInstance(project);
  }

  public static Collection<Path> collectClasspath(Collection<Class<?>> classes) {
    var result = new ArrayList<Path>();
    for (Class<?> c : classes) {
      result.add(Path.of(PathUtil.getJarPathForClass(c)));
    }
    return result;
  }
}
