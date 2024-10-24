// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaAwareProjectJdkTableImpl;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.PathUtil;
import com.intellij.util.io.Compressor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.artifactResolver.MavenArtifactResolvedM31RtMarker;
import org.jetbrains.idea.maven.artifactResolver.common.MavenModuleMap;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.server.MavenDistribution;
import org.jetbrains.idea.maven.server.MavenDistributionsCache;
import org.jetbrains.idea.maven.server.MavenServerUtil;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.intellij.execution.util.ProgramParametersUtil.expandPathAndMacros;
import static org.jetbrains.idea.maven.utils.MavenUtil.verifyMavenSdkRequirements;

public final class MavenExternalParameters {

  private static final Logger LOG = Logger.getInstance(MavenExternalParameters.class);

  public static final String MAVEN_LAUNCHER_CLASS = "org.codehaus.classworlds.Launcher";

  @NonNls public static final String MAVEN_OPTS = "MAVEN_OPTS";

  /**
   * @param runConfiguration used to creation fix if maven home not found
   */
  public static JavaParameters createJavaParameters(@NotNull final Project project,
                                                    @NotNull final MavenRunnerParameters parameters,
                                                    @Nullable MavenGeneralSettings coreSettings,
                                                    @Nullable MavenRunnerSettings runnerSettings,
                                                    @Nullable MavenRunConfiguration runConfiguration) throws ExecutionException {
    final JavaParameters params = new JavaParameters();


    if (coreSettings == null) {
      coreSettings = MavenProjectsManager.getInstance(project).getGeneralSettings();
    }
    final String mavenHome = resolveMavenHome(coreSettings, project, parameters.getWorkingDirPath(), runConfiguration);
    final String mavenVersion = MavenUtil.getMavenVersion(mavenHome);
    if (mavenVersion == null) {
      throw new ExecutionException(MavenProjectBundle.message("dialog.message.maven.home.directory.invalid", mavenHome));
    }

    if (runnerSettings == null) {
      runnerSettings = MavenRunner.getInstance(project).getState();
    }
    params.getProgramParametersList().patchMacroWithEnvs(runnerSettings.getEnvironmentProperties());

    params.setWorkingDirectory(expandPathAndMacros(parameters.getWorkingDirPath(), null, project));


    String jreName = runnerSettings.getJreName();
    boolean isGlobalRunnerSettings = MavenRunner.getInstance(project).getState() == runnerSettings;
    Sdk jdk = ReadAction.compute(()->getJdk(project, jreName, isGlobalRunnerSettings ));
    params.setJdk(jdk);

    if(!verifyMavenSdkRequirements(jdk, mavenVersion)){
      throw new ExecutionException(RunnerBundle.message("maven.3.3.1.bad.jdk"));
    }

    params.getProgramParametersList().addProperty("idea.version", MavenUtil.getIdeaVersionToPassToMavenProcess());
    if (StringUtil.compareVersionNumbers(mavenVersion, "3.3") >= 0) {
      String mavenMultimoduleDir;

      if (!StringUtil.isEmptyOrSpaces(parameters.getMultimoduleDir())) {
        mavenMultimoduleDir = expandPathAndMacros(parameters.getMultimoduleDir(), null, project);
      }
      else {
        mavenMultimoduleDir = MavenServerUtil.findMavenBasedir(parameters.getWorkingDirFile()).getPath();
      }


      params.getVMParametersList().addProperty("maven.multiModuleProjectDirectory", mavenMultimoduleDir);
    }

    if (StringUtil.compareVersionNumbers(mavenVersion, "3.5") >= 0) {
      params.getVMParametersList().addProperty("jansi.passthrough", "true");
    }

    if (StringUtil.compareVersionNumbers(mavenVersion, "4") >= 0) {
      params.getVMParametersList().addProperty("maven.mainClass", "org.apache.maven.cli.MavenCli");
    }

    String vmOptions = getRunVmOptions(runnerSettings, project, parameters.getWorkingDirPath());
    vmOptions = expandPathAndMacros(vmOptions, null, project);
    addVMParameters(params.getVMParametersList(), mavenHome, vmOptions);

    File confFile = MavenUtil.getMavenConfFile(new File(mavenHome));
    if (!confFile.isFile()) {
      throw new ExecutionException(
        MavenProjectBundle.message("dialog.message.configuration.file.not.exists.in.maven.home", confFile.getAbsolutePath()));
    }

    if (parameters.isResolveToWorkspace()) {
      try {
        String resolverJar = getArtifactResolverJar(mavenVersion);
        confFile = patchConfFile(confFile, resolverJar);

        File modulesPathsFile = dumpModulesPaths(project);
        params.getVMParametersList().addProperty(MavenModuleMap.PATHS_FILE_PROPERTY, modulesPathsFile.getAbsolutePath());
      }
      catch (IOException e) {
        LOG.error(e);
        throw new ExecutionException(MavenProjectBundle.message("dialog.message.failed.to.run.maven.configuration"), e);
      }
    }

    params.getVMParametersList().addProperty("classworlds.conf", confFile.getPath());

    for (String path : getMavenClasspathEntries(mavenHome)) {
      params.getClassPath().add(path);
    }

    params.setEnv(new HashMap<>(runnerSettings.getEnvironmentProperties()));
    params.setPassParentEnvs(runnerSettings.isPassParentEnv());

    params.setMainClass(MAVEN_LAUNCHER_CLASS);
    EncodingManager encodingManager = EncodingProjectManager.getInstance(project);
    params.setCharset(encodingManager.getDefaultCharset());

    addMavenParameters(params.getProgramParametersList(), mavenHome, coreSettings, runnerSettings, parameters);
    MavenUtil.addEventListener(mavenVersion, params);

    return params;
  }

  static @Nullable String getRunVmOptions(@Nullable MavenRunnerSettings runnerSettings,
                                          @Nullable Project project,
                                          @NotNull String workingDirPath) {
    if (runnerSettings != null && !StringUtil.isEmptyOrSpaces(runnerSettings.getVmOptions())) return runnerSettings.getVmOptions();
    if (project == null) return null;
    return readJvmConfigOptions(workingDirPath);
  }

  @NotNull
  public static String readJvmConfigOptions(@NotNull String workingDirPath) {
    return Optional.ofNullable(getJvmConfig(workingDirPath))
      .map(jdkOpts -> toVmString(jdkOpts))
      .orElse("");
  }

  @Nullable
  public static VirtualFile getJvmConfig(@NotNull String workingDirPath) {
    return Optional.ofNullable(LocalFileSystem.getInstance().findFileByPath(workingDirPath))
      .map(baseDir -> baseDir.findChild(".mvn"))
      .map(mvn -> mvn.findChild("jvm.config"))
      .orElse(null);
  }

  private static String toVmString(VirtualFile jdkOpts) {
    try {
      return new String(jdkOpts.contentsToByteArray(true), jdkOpts.getCharset());
    }
    catch (IOException e) {
      MavenLog.LOG.warn(e);
      return null;
    }
  }

  private static File patchConfFile(File conf, String library) throws IOException {
    File tmpConf = FileUtil.createTempFile("idea-", "-mvn.conf");
    tmpConf.deleteOnExit();
    patchConfFile(conf, tmpConf, library);

    return tmpConf;
  }

  private static void patchConfFile(File originalConf, File dest, String library) throws IOException {

    try (Scanner sc = new Scanner(originalConf);
         BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dest), StandardCharsets.UTF_8))) {
      boolean patched = false;

      while (sc.hasNextLine()) {
        String line = sc.nextLine();

        out.append(line);
        out.newLine();

        if (!patched && "[plexus.core]".equals(line)) {
          out.append("load ").append(library);
          out.newLine();

          patched = true;
        }
      }
    }
  }

  private static String getArtifactResolverJar(@Nullable String mavenVersion) throws IOException {
    Class<?> marker = MavenArtifactResolvedM31RtMarker.class;

    File classDirOrJar = new File(PathUtil.getJarPathForClass(marker));

    if (!classDirOrJar.isDirectory()) {
      return classDirOrJar.getAbsolutePath(); // it's a jar in IDEA installation.
    }

    // it's a classes directory, we are in development mode.
    File tempFile = FileUtil.createTempFile("idea-", "-artifactResolver.jar", true);

    try (Compressor zip = new Compressor.Zip(tempFile)) {
      zip.addDirectory(classDirOrJar);

      String m2Module = PathUtil.getJarPathForClass(MavenModuleMap.class);
      String commonClassesPath = MavenModuleMap.class.getPackage().getName().replace('.', '/');
      zip.addDirectory(commonClassesPath, new File(m2Module, commonClassesPath));
    }

    return tempFile.getAbsolutePath();
  }

  private static File dumpModulesPaths(@NotNull Project project) throws IOException {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    Properties res = new Properties();

    MavenProjectsManager manager = MavenProjectsManager.getInstance(project);

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (manager.isMavenizedModule(module)) {
        MavenProject mavenProject = manager.findProject(module);
        if (mavenProject != null && !manager.isIgnored(mavenProject)) {
          res.setProperty(mavenProject.getMavenId().getGroupId()
                          + ':' + mavenProject.getMavenId().getArtifactId()
                          + ":pom"
                          + ':' + mavenProject.getMavenId().getVersion(),
                          mavenProject.getFile().getPath());

          res.setProperty(mavenProject.getMavenId().getGroupId()
                          + ':' + mavenProject.getMavenId().getArtifactId()
                          + ':' + mavenProject.getPackaging()
                          + ':' + mavenProject.getMavenId().getVersion(),
                          mavenProject.getOutputDirectory());

          res.setProperty(mavenProject.getMavenId().getGroupId()
                          + ':' + mavenProject.getMavenId().getArtifactId()
                          + ":test-jar"
                          + ':' + mavenProject.getMavenId().getVersion(),
                          mavenProject.getTestOutputDirectory());

          addArtifactFileMapping(res, mavenProject, "sources");
          addArtifactFileMapping(res, mavenProject, "test-sources");
          addArtifactFileMapping(res, mavenProject, "javadoc");
          addArtifactFileMapping(res, mavenProject, "test-javadoc");
        }
      }
    }

    File file = new File(PathManager.getSystemPath(), "Maven/idea-projects-state-" + project.getLocationHash() + ".properties");
    FileUtil.ensureExists(file.getParentFile());

    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
      res.store(out, null);
    }

    return file;
  }

  private static void addArtifactFileMapping(@NotNull Properties res, @NotNull MavenProject mavenProject, @NotNull String classifier) {
    File file = new File(mavenProject.getBuildDirectory(), mavenProject.getFinalName() + '-' + classifier + ".jar");
    if (file.exists()) {
      res.setProperty(mavenProject.getMavenId().getGroupId()
                      + ':' + mavenProject.getMavenId().getArtifactId()
                      + ':' + classifier
                      + ':' + mavenProject.getMavenId().getVersion(),
                      file.getPath());
    }
  }

  @NotNull
  private static Sdk getJdk(@Nullable Project project, String jreName, boolean isGlobalRunnerSettings)
    throws ExecutionException {
    if (jreName.equals(MavenRunnerSettings.USE_INTERNAL_JAVA)) {
      return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
    }

    if (jreName.equals(MavenRunnerSettings.USE_PROJECT_JDK)) {
      if (project != null) {
        Sdk res = ProjectRootManager.getInstance(project).getProjectSdk();
        if (res != null) {
          return res;
        }
        Module[] modules = ModuleManager.getInstance(project).getModules();
        for (Module module : modules) {
          Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
          if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) {
            return sdk;
          }
        }
      }

      if (project == null) {
        Sdk recent = ProjectJdkTable.getInstance().findMostRecentSdkOfType(JavaSdk.getInstance());
        if (recent != null) return recent;
        return JavaAwareProjectJdkTableImpl.getInstanceEx().getInternalJdk();
      }

      throw new ProjectJdkSettingsOpenerExecutionException(
       RunnerBundle.message("dialog.message.project.jdk.not.specified.href.configure"), project);
    }

    if (jreName.equals(MavenRunnerSettings.USE_JAVA_HOME)) {
      final String javaHome = ExternalSystemJdkUtil.getJavaHome();
      if (StringUtil.isEmptyOrSpaces(javaHome)) {
        throw new ExecutionException(RunnerBundle.message("maven.java.home.undefined"));
      }
      return JavaSdk.getInstance().createJdk("", javaHome);
    }

    for (Sdk projectJdk : ProjectJdkTable.getInstance().getAllJdks()) {
      if (projectJdk.getName().equals(jreName)) {
        return projectJdk;
      }
    }

    if (isGlobalRunnerSettings) {
      throw new ExecutionException(RunnerBundle.message("maven.java.not.found.default.config", jreName));
    }
    else {
      throw new ExecutionException(RunnerBundle.message("maven.java.not.found", jreName));
    }
  }

  public static void addVMParameters(ParametersList parametersList, String mavenHome, String vmOptionsSettings) {
    parametersList.addParametersString(System.getenv(MAVEN_OPTS));

    parametersList.addParametersString(vmOptionsSettings);

    parametersList.addProperty(MavenConstants.HOME_PROPERTY, mavenHome);
  }

  private static void addMavenParameters(ParametersList parametersList,
                                         String mavenHome,
                                         MavenGeneralSettings coreSettings,
                                         MavenRunnerSettings runnerSettings,
                                         MavenRunnerParameters parameters) {
    encodeCoreAndRunnerSettings(coreSettings, mavenHome, parametersList);

    if (runnerSettings.isSkipTests()) {
      parametersList.addProperty("skipTests", "true");
    }

    for (Map.Entry<String, String> entry : runnerSettings.getMavenProperties().entrySet()) {
      if (entry.getKey().length() > 0) {
        parametersList.addProperty(entry.getKey(), entry.getValue());
      }
    }

    for (String goal : parameters.getGoals()) {
      parametersList.add(goal);
    }

    for (var cmdOption : parameters.getOptions()) {
      parametersList.add(cmdOption);
    }

    if (parameters.getPomFileName() != null && !FileUtil.namesEqual(MavenConstants.POM_XML, parameters.getPomFileName())) {
      parametersList.add("-f");
      parametersList.add(parameters.getPomFileName());
    }

    addOption(parametersList, "P", encodeProfiles(parameters.getProfilesMap()));
  }

  private static void addOption(ParametersList cmdList, @NonNls String key, @NonNls String value) {
    if (!StringUtil.isEmptyOrSpaces(value)) {
      cmdList.add("-" + key);
      cmdList.add(value);
    }
  }

  /**
   * @param project          used to creation fix if maven home not found
   * @param runConfiguration used to creation fix if maven home not found
   */
  @NotNull
  @NlsSafe
  public static String resolveMavenHome(@NotNull MavenGeneralSettings coreSettings,
                                        @NotNull Project project,
                                        @NotNull String workingDir, @Nullable MavenRunConfiguration runConfiguration)
    throws ExecutionException {
    MavenHomeType type = coreSettings.getMavenHomeType();
    File file = null;
    if (type instanceof StaticResolvedMavenHomeType st) {
      file = MavenUtil.getMavenHomeFile(st);
    }
    if (type instanceof MavenWrapper) {
      MavenDistribution distribution = MavenDistributionsCache.getInstance(project).getWrapper(workingDir);
      if (distribution != null) {
        file = distribution.getMavenHome().toFile();
      } else {
        file = MavenDistributionsCache.resolveEmbeddedMavenHome().getMavenHome().toFile();
      }
    }

    if (file == null) {
      throw createExecutionException(RunnerBundle.message("external.maven.home.no.default"),
                                     RunnerBundle.message("external.maven.home.no.default.with.fix"),
                                     coreSettings, project, runConfiguration);
    }

    if (!file.exists()) {
      throw createExecutionException(RunnerBundle.message("external.maven.home.does.not.exist", file.getPath()),
                                     RunnerBundle.message("external.maven.home.does.not.exist.with.fix", file.getPath()),
                                     coreSettings, project, runConfiguration);
    }

    if (!MavenUtil.isValidMavenHome(file)) {
      throw createExecutionException(RunnerBundle.message("external.maven.home.invalid", file.getPath()),
                                     RunnerBundle.message("external.maven.home.invalid.with.fix", file.getPath()),
                                     coreSettings, project, runConfiguration);
    }

    try {
      return file.getCanonicalPath();
    }
    catch (IOException e) {
      throw new ExecutionException(e.getMessage(), e);
    }
  }

  private static ExecutionException createExecutionException(@NlsContexts.DialogMessage String text,
                                                             @NlsContexts.DialogMessage String textWithFix,
                                                             @NotNull MavenGeneralSettings coreSettings,
                                                             @Nullable Project project,
                                                             @Nullable MavenRunConfiguration runConfiguration) {
    Project notNullProject = project;
    if (notNullProject == null) {
      if (runConfiguration == null) return new ExecutionException(text);
      notNullProject = runConfiguration.getProject();
    }

    if (coreSettings == MavenProjectsManager.getInstance(notNullProject).getGeneralSettings()) {
      return new ProjectSettingsOpenerExecutionException(textWithFix, notNullProject);
    }

    if (runConfiguration != null) {
      Project runCfgProject = runConfiguration.getProject();
      if (((RunManagerImpl)RunManager.getInstance(runCfgProject)).getSettings(runConfiguration) != null) {
        return new RunConfigurationOpenerExecutionException(textWithFix, runConfiguration);
      }
    }

    return new ExecutionException(text);
  }

  @SuppressWarnings("HardCodedStringLiteral")
  private static List<String> getMavenClasspathEntries(final String mavenHome) {
    File mavenHomeBootAsFile = new File(new File(mavenHome, "core"), "boot");
    // if the dir "core/boot" does not exist we are using a Maven version > 2.0.5
    // in this case the classpath must be constructed from the dir "boot"
    if (!mavenHomeBootAsFile.exists()) {
      mavenHomeBootAsFile = new File(mavenHome, "boot");
    }

    List<String> classpathEntries = new ArrayList<>();

    File[] files = mavenHomeBootAsFile.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.getName().contains("classworlds")) {
          classpathEntries.add(file.getAbsolutePath());
        }
      }
    }

    return classpathEntries;
  }

  private static void encodeCoreAndRunnerSettings(MavenGeneralSettings coreSettings, String mavenHome,
                                                  ParametersList cmdList) {
    if (coreSettings.isWorkOffline()) {
      cmdList.add("--offline");
    }

    if (coreSettings.getOutputLevel() == MavenExecutionOptions.LoggingLevel.DEBUG) {
      cmdList.add("--debug");
    }
    if (coreSettings.isNonRecursive()) {
      cmdList.add("--non-recursive");
    }
    if (coreSettings.isPrintErrorStackTraces()) {
      cmdList.add("--errors");
    }

    if (coreSettings.isAlwaysUpdateSnapshots()) {
      cmdList.add("--update-snapshots");
    }

    if (StringUtil.isNotEmpty(coreSettings.getThreads())) {
      cmdList.add("-T", coreSettings.getThreads());
    }

    addIfNotEmpty(cmdList, coreSettings.getFailureBehavior().getCommandLineOption());
    addIfNotEmpty(cmdList, coreSettings.getChecksumPolicy().getCommandLineOption());

    addOption(cmdList, "s", coreSettings.getUserSettingsFile());
    if (!StringUtil.isEmptyOrSpaces(coreSettings.getLocalRepository())) {
      cmdList.addProperty("maven.repo.local", coreSettings.getLocalRepository());
    }
  }

  private static void addIfNotEmpty(ParametersList parametersList, @Nullable String value) {
    if (!StringUtil.isEmptyOrSpaces(value)) {
      parametersList.add(value);
    }
  }

  @ApiStatus.Internal
  public static String encodeProfiles(Map<String, Boolean> profiles) {
    StringBuilder stringBuilder = new StringBuilder();
    for (Map.Entry<String, Boolean> entry : profiles.entrySet()) {
      if (stringBuilder.length() != 0) {
        stringBuilder.append(",");
      }
      if (!entry.getValue()) {
        stringBuilder.append("!");
      }
      stringBuilder.append(entry.getKey());
    }
    return stringBuilder.toString();
  }

  private static class ProjectSettingsOpenerExecutionException extends WithHyperlinkExecutionException {

    private final Project myProject;

    ProjectSettingsOpenerExecutionException(@NlsContexts.DialogMessage final String s, Project project) {
      super(s);
      myProject = project;
    }

    @Override
    protected void hyperlinkClicked() {
      ShowSettingsUtil.getInstance().showSettingsDialog(myProject, MavenProjectBundle.message("configurable.MavenSettings.display.name"));
    }
  }

  private static class ProjectJdkSettingsOpenerExecutionException extends WithHyperlinkExecutionException {

    private final Project myProject;

    ProjectJdkSettingsOpenerExecutionException(@NlsContexts.DialogMessage final String s, Project project) {
      super(s);
      myProject = project;
    }

    @Override
    protected void hyperlinkClicked() {
      ProjectSettingsService.getInstance(myProject).openProjectSettings();
    }
  }

  private static class RunConfigurationOpenerExecutionException extends WithHyperlinkExecutionException {

    private final MavenRunConfiguration myRunConfiguration;

    RunConfigurationOpenerExecutionException(@NlsContexts.DialogMessage final String s, MavenRunConfiguration runConfiguration) {
      super(s);
      myRunConfiguration = runConfiguration;
    }

    @Override
    protected void hyperlinkClicked() {
      Project project = myRunConfiguration.getProject();
      EditConfigurationsDialog dialog = new EditConfigurationsDialog(project);
      dialog.show();
    }
  }

  private static abstract class WithHyperlinkExecutionException extends ExecutionException
    implements HyperlinkListener, NotificationListener {

    WithHyperlinkExecutionException(@NlsContexts.DialogMessage String s) {
      super(s);
    }

    protected abstract void hyperlinkClicked();

    @Override
    public final void hyperlinkUpdate(HyperlinkEvent e) {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        hyperlinkClicked();
      }
    }

    @Override
    public final void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      hyperlinkUpdate(event);
    }
  }
}
