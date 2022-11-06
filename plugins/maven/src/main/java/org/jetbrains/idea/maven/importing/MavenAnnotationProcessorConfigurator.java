// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import kotlin.sequences.SequencesKt;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.SyncBundle;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.server.MavenArtifactResolveResult;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.jps.model.java.impl.compiler.ProcessorConfigProfileImpl;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

import static com.intellij.openapi.roots.OrderEnumerator.orderEntries;

@ApiStatus.Internal
public class MavenAnnotationProcessorConfigurator extends MavenImporter implements MavenWorkspaceConfigurator {

  private static final String PROFILE_PREFIX = "Annotation profile for ";
  public static final String MAVEN_DEFAULT_ANNOTATION_PROFILE = "Maven default annotation processors profile";
  private static final String DEFAULT_ANNOTATION_PATH_OUTPUT = "target/generated-sources/annotations";
  private static final String DEFAULT_TEST_ANNOTATION_OUTPUT = "target/generated-test-sources/test-annotations";

  public static final String MAVEN_BSC_DEFAULT_ANNOTATION_PROFILE = getModuleProfileName("maven-processor-plugin default configuration");
  private static final String DEFAULT_BSC_ANNOTATION_PATH_OUTPUT = "target/generated-sources/apt";
  private static final String DEFAULT_BSC_TEST_ANNOTATION_OUTPUT = "target/generated-sources/apt-test";

  private static final Key<Map<MavenProject, List<String>>> ANNOTATION_PROCESSOR_MODULE_NAMES =
    Key.create("ANNOTATION_PROCESSOR_MODULE_NAMES");

  public MavenAnnotationProcessorConfigurator() {
    super("org.apache.maven.plugins", "maven-compiler-plugin");
  }

  @Override
  public boolean isApplicable(MavenProject mavenProject) {
    return true;
  }

  @Override
  public boolean isMigratedToConfigurator() {
    return true;
  }

  @Override
  public void beforeModelApplied(@NotNull MavenWorkspaceConfigurator.MutableModelContext context) {
    Map<MavenId, List<String>> mavenProjectToModuleNamesCache = new HashMap<>();
    for (MavenProjectWithModules<ModuleEntity> each : SequencesKt.asIterable(context.getMavenProjectsWithModules())) {
      List<String> moduleNames =
        ContainerUtil.mapNotNull(each.getModules(), it -> it.getType().getContainsCode() ? it.getModule().getName() : null);
      mavenProjectToModuleNamesCache.put(each.getMavenProject().getMavenId(), moduleNames);
    }

    var changedOnlyProjects = SequencesKt.mapNotNull(context.getMavenProjectsWithModules(), it -> {
      return it.getChanges().hasChanges() ? it.getMavenProject() : null;
    });

    var map = new HashMap<MavenProject, List<String>>();
    collectProcessorModuleNames(SequencesKt.asIterable(changedOnlyProjects),
                                moduleName -> mavenProjectToModuleNamesCache.get(moduleName),
                                map);
    ANNOTATION_PROCESSOR_MODULE_NAMES.set(context, map);
  }

  @Override
  public void process(IdeModifiableModelsProvider modifiableModelsProvider,
                      Module module,
                      MavenRootModelAdapter rootModel,
                      MavenProjectsTree mavenModel,
                      MavenProject mavenProject,
                      MavenProjectChanges changes,
                      Map<MavenProject, String> mavenProjectToModuleName,
                      List<MavenProjectsProcessorTask> postTasks) {
    Element config = getConfig(mavenProject, "annotationProcessorPaths");
    if (config == null) return;

    String annotationTargetDir = mavenProject.getAnnotationProcessorDirectory(false);
    // directory must exist before compilation start to be recognized as source root
    new File(rootModel.toPath(annotationTargetDir).getPath()).mkdirs();
    rootModel.addGeneratedJavaSourceFolder(annotationTargetDir, JavaSourceRootType.SOURCE, false);

    Map<MavenProject, List<String>> map = ANNOTATION_PROCESSOR_MODULE_NAMES.get(modifiableModelsProvider, new HashMap<>());
    collectProcessorModuleNames(List.of(mavenProject),
                                mavenId -> {
                                  MavenProject mavenArtifact = mavenModel.findProject(mavenId);
                                  if (mavenArtifact == null) return null;
                                  String moduleName = mavenProjectToModuleName.get(mavenArtifact);
                                  if (moduleName == null) return null;
                                  return List.of(moduleName);
                                },
                                map);
    ANNOTATION_PROCESSOR_MODULE_NAMES.set(modifiableModelsProvider, map);
  }

  private void collectProcessorModuleNames(Iterable<MavenProject> projects,
                                           Function<@NotNull MavenId, @Nullable List<String>> moduleNameByProjectId,
                                           Map<MavenProject, List<String>> result) {
    for (var mavenProject : projects) {
      if (!shouldEnableAnnotationProcessors(mavenProject)) continue;

      Element config = getConfig(mavenProject, "annotationProcessorPaths");
      if (config == null) continue;

      for (MavenArtifactInfo info : getProcessorArtifactInfos(config)) {
        var mavenId = new MavenId(info.getGroupId(), info.getArtifactId(), info.getVersion());

        var processorModuleNames = moduleNameByProjectId.apply(mavenId);
        if (processorModuleNames != null) {
          result.computeIfAbsent(mavenProject, __ -> new ArrayList<>()).addAll(processorModuleNames);
        }
      }
    }
  }

  @Override
  public void afterModelApplied(@NotNull MavenWorkspaceConfigurator.AppliedModelContext context) {
    Map<String, Module> nameToModuleCache = new HashMap<>();
    for (MavenProjectWithModules<Module> each : SequencesKt.asIterable(context.getMavenProjectsWithModules())) {
      for (ModuleWithType<Module> moduleWithType : each.getModules()) {
        Module module = moduleWithType.getModule();
        nameToModuleCache.put(module.getName(), module);
      }
    }
    Function<@NotNull String, @Nullable Module> moduleByName = moduleName -> nameToModuleCache.get(moduleName);

    Map<MavenProject, List<String>> perProjectProcessorModuleNames = ANNOTATION_PROCESSOR_MODULE_NAMES.get(context, Map.of());

    var changedOnly = SequencesKt.filter(context.getMavenProjectsWithModules(), it -> it.getChanges().hasChanges());
    var projectWithModules = SequencesKt.map(changedOnly, it -> {
      List<String> processorModuleNames = perProjectProcessorModuleNames.getOrDefault(it.getMavenProject(), List.of());
      return new MavenProjectWithProcessorModules(it.getMavenProject(), it.getModules(), processorModuleNames);
    });

    configureProfiles(context.getProject(),
                      context.getMavenProjectsTree(),
                      SequencesKt.asIterable(projectWithModules),
                      moduleByName);
  }

  @Override
  public void postProcess(Module module,
                          MavenProject mavenProject,
                          MavenProjectChanges changes,
                          IdeModifiableModelsProvider modifiableModelsProvider) {
    var processorModuleNames =
      ANNOTATION_PROCESSOR_MODULE_NAMES.get(modifiableModelsProvider, Map.of()).getOrDefault(mavenProject, List.of());

    var moduleWithType = new ModuleWithType<Module>() {
      @Override
      public Module getModule() {
        return module;
      }

      @NotNull
      @Override
      public MavenModuleType getType() {
        return StandardMavenModuleType.SINGLE_MODULE;
      }
    };
    var projectWithModules = new MavenProjectWithProcessorModules(mavenProject,
                                                                  List.of(moduleWithType),
                                                                  processorModuleNames);
    configureProfiles(module.getProject(),
                      MavenProjectsManager.getInstance(module.getProject()).getProjectsTree(),
                      List.of(projectWithModules),
                      moduleName -> modifiableModelsProvider.findIdeModule(moduleName));
  }


  private static class MavenProjectWithProcessorModules {
    private final MavenProject mavenProject;
    private final List<ModuleWithType<Module>> mavenProjectModules;
    private final List<String> processorModuleNames;

    private MavenProjectWithProcessorModules(MavenProject mavenProject,
                                             List<ModuleWithType<Module>> mavenProjectModules,
                                             List<String> processorModuleNames) {
      this.mavenProject = mavenProject;
      this.mavenProjectModules = mavenProjectModules;
      this.processorModuleNames = processorModuleNames;
    }
  }

  private static void configureProfiles(Project project,
                                        MavenProjectsTree tree,
                                        Iterable<MavenProjectWithProcessorModules> projectsWithModules,
                                        Function<@NotNull String, @Nullable Module> moduleByName) {
    CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);

    for (var it : projectsWithModules) {
      MavenProject rootProject = ObjectUtils.notNull(tree.findRootProject(it.mavenProject), it.mavenProject);

      for (var moduleWithType : it.mavenProjectModules) {
        Module module = moduleWithType.getModule();
        MavenModuleType moduleType = moduleWithType.getType();

        if (!isLevelMoreThan6(module)) continue;

        if (shouldEnableAnnotationProcessors(it.mavenProject) && moduleType.getContainsCode()) {
          var processorModules = ContainerUtil.mapNotNull(it.processorModuleNames, eachName -> moduleByName.apply(eachName));
          var profileIsDefault = createOrUpdateProfile(it.mavenProject, module, processorModules, compilerConfiguration);

          if (profileIsDefault != null) {
            cleanAndMergeModuleProfiles(rootProject, compilerConfiguration, profileIsDefault.first, profileIsDefault.second, module);
          }
        }
        else {
          cleanAndMergeModuleProfiles(rootProject, compilerConfiguration, null, false, module);
        }
      }
    }
  }

  @Nullable
  private static Pair<@NotNull ProcessorConfigProfile, @NotNull Boolean>
  createOrUpdateProfile(@NotNull MavenProject mavenProject,
                        @NotNull Module module,
                        @NotNull List<Module> processorModules,
                        @NotNull CompilerConfigurationImpl compilerConfiguration) {
    List<String> processors = mavenProject.getDeclaredAnnotationProcessors();
    Map<String, String> options = mavenProject.getAnnotationProcessorOptions();

    String annotationProcessorDirectory = getRelativeAnnotationProcessorDirectory(mavenProject, false, DEFAULT_ANNOTATION_PATH_OUTPUT);
    String testAnnotationProcessorDirectory = getRelativeAnnotationProcessorDirectory(mavenProject, true, DEFAULT_TEST_ANNOTATION_OUTPUT);

    String annotationProcessorPath = getAnnotationProcessorPath(mavenProject, processorModules);

    final boolean isDefault;
    final String moduleProfileName;

    boolean isDefaultSettings = ContainerUtil.isEmpty(processors)
                                && options.isEmpty()
                                && StringUtil.isEmpty(annotationProcessorPath);
    if (isDefaultSettings
        && DEFAULT_ANNOTATION_PATH_OUTPUT.equals(FileUtil.toSystemIndependentName(annotationProcessorDirectory))
        && DEFAULT_TEST_ANNOTATION_OUTPUT.equals(FileUtil.toSystemIndependentName(testAnnotationProcessorDirectory))) {
      moduleProfileName = MAVEN_DEFAULT_ANNOTATION_PROFILE;
      isDefault = true;
    }
    else if (isDefaultSettings
             && DEFAULT_BSC_ANNOTATION_PATH_OUTPUT.equals(FileUtil.toSystemIndependentName(annotationProcessorDirectory))
             && DEFAULT_BSC_TEST_ANNOTATION_OUTPUT.equals(FileUtil.toSystemIndependentName(testAnnotationProcessorDirectory))) {
      moduleProfileName = MAVEN_BSC_DEFAULT_ANNOTATION_PROFILE;
      isDefault = true;
    }
    else {
      moduleProfileName = getModuleProfileName(module.getName());
      isDefault = false;
    }

    ProcessorConfigProfile moduleProfile =
      getModuleProfile(module, mavenProject, compilerConfiguration, moduleProfileName, annotationProcessorDirectory,
                       testAnnotationProcessorDirectory);
    if (moduleProfile == null) return null;

    if (StringUtil.isNotEmpty(annotationProcessorPath)) {
      moduleProfile.setObtainProcessorsFromClasspath(false);
      moduleProfile.setProcessorPath(annotationProcessorPath);
    }

    return Pair.pair(moduleProfile, isDefault);
  }

  @NotNull
  public static String getModuleProfileName(@NotNull @NlsSafe String moduleName) {
    return PROFILE_PREFIX + moduleName;
  }

  @Override
  public void resolve(Project project,
                      MavenProject mavenProject,
                      NativeMavenProjectHolder nativeMavenProject,
                      MavenEmbedderWrapper embedder,
                      ResolveContext context) throws MavenProcessCanceledException {
    Element config = getConfig(mavenProject, "annotationProcessorPaths");
    if (config == null) return;

    List<MavenArtifactInfo> artifactsInfo = getProcessorArtifactInfos(config);
    if (artifactsInfo.isEmpty()) {
      return;
    }
    
    List<MavenArtifactInfo> externalArtifacts = new ArrayList<>();
    for (MavenArtifactInfo info : artifactsInfo) {
      MavenProject mavenArtifact = context.getMavenProjectsTree().findProject(new MavenId(info.getGroupId(), info.getArtifactId(), info.getVersion()));
      if (mavenArtifact == null) {
        externalArtifacts.add(info);
      }
    }

    try {
      MavenArtifactResolveResult annotationProcessors = embedder
        .resolveArtifactTransitively(externalArtifacts, mavenProject.getRemoteRepositories());
      if (annotationProcessors.problem != null) {
        MavenResolveResultProblemProcessor.notifySyncForProblem(project, annotationProcessors.problem);
      }
      else {
        mavenProject.addAnnotationProcessors(annotationProcessors.mavenResolvedArtifacts);
      }
    }
    catch (Exception e) {
      String message = e.getMessage() != null ? e.getMessage() : ExceptionUtil.getThrowableText(e);
      MavenProjectsManager.getInstance(project).getSyncConsole()
        .addWarning(SyncBundle.message("maven.sync.annotation.processor.problem"), message);
    }
  }

  @NotNull
  private static List<MavenArtifactInfo> getProcessorArtifactInfos(Element config) {
    List<MavenArtifactInfo> artifacts = new ArrayList<>();
    Consumer<Element> addToArtifacts = path -> {
      String groupId = path.getChildTextTrim("groupId");
      String artifactId = path.getChildTextTrim("artifactId");
      String version = path.getChildTextTrim("version");

      String classifier = path.getChildTextTrim("classifier");
      //String type = path.getChildTextTrim("type");

      artifacts.add(new MavenArtifactInfo(groupId, artifactId, version, "jar", classifier));
    };

    for (Element path : config.getChildren("path")) {
      addToArtifacts.consume(path);
    }

    for (Element dependency : config.getChildren("dependency")) {
      addToArtifacts.consume(dependency);
    }

    for (Element annotationProcessorPath : config.getChildren("annotationProcessorPath")) {
      addToArtifacts.consume(annotationProcessorPath);
    }
    return artifacts;
  }

  @Nullable
  private static ProcessorConfigProfile getModuleProfile(Module module,
                                                         MavenProject mavenProject,
                                                         CompilerConfigurationImpl compilerConfiguration,
                                                         String moduleProfileName,
                                                         String annotationProcessorDirectory,
                                                         String testAnnotationProcessorDirectory) {
    ProcessorConfigProfile moduleProfile = compilerConfiguration.findModuleProcessorProfile(moduleProfileName);

    if (moduleProfile == null) {
      moduleProfile = new ProcessorConfigProfileImpl(moduleProfileName);
      moduleProfile.setEnabled(true);
      compilerConfiguration.addModuleProcessorProfile(moduleProfile);
    }
    if (!moduleProfile.isEnabled()) return null;

    if (MavenImportUtil.isMainOrTestSubmodule(module.getName())) {
      moduleProfile.setOutputRelativeToContentRoot(false);
    }
    else {
      moduleProfile.setOutputRelativeToContentRoot(true);
    }
    moduleProfile.setObtainProcessorsFromClasspath(true);
    moduleProfile.setGeneratedSourcesDirectoryName(annotationProcessorDirectory, false);
    moduleProfile.setGeneratedSourcesDirectoryName(testAnnotationProcessorDirectory, true);

    moduleProfile.clearProcessorOptions();
    for (Map.Entry<String, String> entry : mavenProject.getAnnotationProcessorOptions().entrySet()) {
      moduleProfile.setOption(entry.getKey(), entry.getValue());
    }

    moduleProfile.clearProcessors();
    final List<String> processors = mavenProject.getDeclaredAnnotationProcessors();
    if (processors != null) {
      for (String processor : processors) {
        moduleProfile.addProcessor(processor);
      }
    }

    moduleProfile.addModuleName(module.getName());
    return moduleProfile;
  }


  private static boolean isLevelMoreThan6(Module module) {
    Sdk sdk = ReadAction.compute(() -> ModuleRootManager.getInstance(module).getSdk());
    if (sdk != null) {
      String versionString = sdk.getVersionString();
      LanguageLevel languageLevel = LanguageLevel.parse(versionString);
      if (languageLevel != null && languageLevel.isLessThan(LanguageLevel.JDK_1_6)) return false;
    }
    return true;
  }

  private static @NotNull String getAnnotationProcessorPath(@NotNull MavenProject mavenProject,
                                                            @NotNull List<Module> processorModules) {
    StringJoiner annotationProcessorPath = new StringJoiner(File.pathSeparator);

    Consumer<String> resultAppender = path -> annotationProcessorPath.add(FileUtil.toSystemDependentName(path));

    for (MavenArtifact artifact : mavenProject.getExternalAnnotationProcessors()) {
      resultAppender.consume(artifact.getPath());
    }

    for (Module module : processorModules) {
      OrderEnumerator enumerator = orderEntries(module).withoutSdk().productionOnly().runtimeOnly().recursively();

      for (String url : enumerator.classes().getUrls()) {
        resultAppender.consume(JpsPathUtil.urlToPath(url));
      }
    }

    return annotationProcessorPath.toString();
  }

  private static void cleanAndMergeModuleProfiles(@NotNull MavenProject rootProject,
                                                  @NotNull CompilerConfigurationImpl compilerConfiguration,
                                                  @Nullable ProcessorConfigProfile moduleProfile,
                                                  boolean isDefault,
                                                  @NotNull Module module) {
    List<ProcessorConfigProfile> profiles = new ArrayList<>(compilerConfiguration.getModuleProcessorProfiles());
    for (ProcessorConfigProfile p : profiles) {
      if (p != moduleProfile) {
        p.removeModuleName(module.getName());
        if (p.getModuleNames().isEmpty() && p.getName().startsWith(PROFILE_PREFIX)) {
          compilerConfiguration.removeModuleProcessorProfile(p);
        }
      }

      if (!isDefault && moduleProfile != null && isSimilarProfiles(p, moduleProfile)) {
        moduleProfile.setEnabled(p.isEnabled());
        final String mavenProjectRootProfileName = getModuleProfileName(rootProject.getDisplayName());
        ProcessorConfigProfile mergedProfile = compilerConfiguration.findModuleProcessorProfile(mavenProjectRootProfileName);
        if (mergedProfile == null) {
          mergedProfile = new ProcessorConfigProfileImpl(moduleProfile);
          mergedProfile.setName(mavenProjectRootProfileName);
          compilerConfiguration.addModuleProcessorProfile(mergedProfile);
          mergedProfile.addModuleNames(p.getModuleNames());
          p.clearModuleNames();
          compilerConfiguration.removeModuleProcessorProfile(p);
          moduleProfile.clearModuleNames();
          compilerConfiguration.removeModuleProcessorProfile(moduleProfile);
        }
        else if (p == mergedProfile || isSimilarProfiles(mergedProfile, moduleProfile)) {
          if (moduleProfile != mergedProfile) {
            mergedProfile.addModuleNames(moduleProfile.getModuleNames());
            moduleProfile.clearModuleNames();
            compilerConfiguration.removeModuleProcessorProfile(moduleProfile);
          }
          if (p != mergedProfile) {
            mergedProfile.addModuleNames(p.getModuleNames());
            p.clearModuleNames();
            compilerConfiguration.removeModuleProcessorProfile(p);
          }
        }
      }
    }
  }

  private static boolean isSimilarProfiles(@Nullable ProcessorConfigProfile profile1, @Nullable ProcessorConfigProfile profile2) {
    if (profile1 == null || profile2 == null) return false;

    ProcessorConfigProfileImpl p1 = new ProcessorConfigProfileImpl(profile1);
    p1.setName("tmp");
    p1.setEnabled(true);
    p1.clearModuleNames();
    ProcessorConfigProfileImpl p2 = new ProcessorConfigProfileImpl(profile2);
    p2.setName("tmp");
    p2.setEnabled(true);
    p2.clearModuleNames();
    return p1.equals(p2);
  }

  @NotNull
  private static String getRelativeAnnotationProcessorDirectory(MavenProject mavenProject, boolean isTest,
                                                                String defaultTestAnnotationOutput) {
    String annotationProcessorDirectory = mavenProject.getAnnotationProcessorDirectory(isTest);
    Path annotationProcessorDirectoryFile = Path.of(annotationProcessorDirectory);
    if (!annotationProcessorDirectoryFile.isAbsolute()) {
      return annotationProcessorDirectory;
    }

    String absoluteProjectDirectory = mavenProject.getDirectory();
    try {
      return Path.of(absoluteProjectDirectory).relativize(annotationProcessorDirectoryFile).toString();
    }
    catch (IllegalArgumentException e) {
      return defaultTestAnnotationOutput;
    }
  }

  private static boolean shouldEnableAnnotationProcessors(MavenProject mavenProject) {
    if ("pom".equals(mavenProject.getPackaging())) return false;

    return mavenProject.getProcMode() != MavenProject.ProcMode.NONE
           || mavenProject.findPlugin("org.bsc.maven", "maven-processor-plugin") != null;
  }
}
