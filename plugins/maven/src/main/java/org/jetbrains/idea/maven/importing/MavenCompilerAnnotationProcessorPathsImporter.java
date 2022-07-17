// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.SyncBundle;
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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MavenCompilerAnnotationProcessorPathsImporter extends MavenImporter {

  public static final String PROFILE_PREFIX = "Annotation profile for ";
  public static final String MAVEN_DEFAULT_ANNOTATION_PROFILE = "Maven default annotation processors profile";
  public static final String DEFAULT_ANNOTATION_PATH_OUTPUT = "target/generated-sources/annotations";
  public static final String DEFAULT_TEST_ANNOTATION_OUTPUT = "target/generated-test-sources/test-annotations";

  public static final String MAVEN_BSC_DEFAULT_ANNOTATION_PROFILE = PROFILE_PREFIX + "maven-processor-plugin default configuration";
  public static final String DEFAULT_BSC_ANNOTATION_PATH_OUTPUT = "target/generated-sources/apt";
  public static final String DEFAULT_BSC_TEST_ANNOTATION_OUTPUT = "target/generated-sources/apt-test";

  private final Logger LOG = Logger.getInstance(MavenCompilerAnnotationProcessorPathsImporter.class);

  public MavenCompilerAnnotationProcessorPathsImporter() {
    super("org.apache.maven.plugins", "maven-compiler-plugin");
  }

  @Override
  public boolean isApplicable(MavenProject mavenProject) {
    return true;
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
    if(getConfig(mavenProject, "annotationProcessorPaths") == null) return;
    String annotationTargetDir = mavenProject.getAnnotationProcessorDirectory(false);
    // directory must exist before compilation start to be recognized as source root
    new File(rootModel.toPath(annotationTargetDir).getPath()).mkdirs();
    rootModel.addGeneratedJavaSourceFolder(annotationTargetDir, JavaSourceRootType.SOURCE, false);

    Element config = getConfig(mavenProject, "annotationProcessorPaths");
    LOG.assertTrue(config != null);

    List<MavenArtifactInfo> artifactsInfo = getArtifactsInfo(config);
    if (artifactsInfo.isEmpty()) {
      return;
    }

    ArrayList<String> moduleNames = new ArrayList<>();

    for (MavenArtifactInfo info : artifactsInfo) {
      MavenProject mavenArtifact = mavenModel.findProject(new MavenId(info.getGroupId(), info.getArtifactId(), info.getVersion()));
      if (mavenArtifact != null) {
        ContainerUtil.addIfNotNull(moduleNames, mavenProjectToModuleName.get(mavenArtifact));
      }
    }

    moduleNames.trimToSize();
    MavenAnnotationProcessorsModuleService.getInstance(module).setAnnotationProcessorModules(moduleNames);
  }

  @Override
  public void postProcess(Module module,
                          MavenProject mavenProject,
                          MavenProjectChanges changes,
                          IdeModifiableModelsProvider modifiableModelsProvider) {
    if (!isLevelMoreThan6(module)) {
      return;
    }
    Project project = module.getProject();

    final CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    final MavenProject rootProject =
      ObjectUtils.notNull(MavenProjectsManager.getInstance(module.getProject()).findRootProject(mavenProject), mavenProject);

    if (shouldEnableAnnotationProcessors(mavenProject)) {

      String annotationProcessorDirectory = getAnnotationProcessorDirectory(mavenProject, false, DEFAULT_ANNOTATION_PATH_OUTPUT);
      String testAnnotationProcessorDirectory = getAnnotationProcessorDirectory(mavenProject, true, DEFAULT_TEST_ANNOTATION_OUTPUT);

      final boolean isDefault;
      final String moduleProfileName;

      if (isMavenDefaultAnnotationProcessorConfiguration(annotationProcessorDirectory, testAnnotationProcessorDirectory, mavenProject,
                                                         project)) {
        moduleProfileName = MAVEN_DEFAULT_ANNOTATION_PROFILE;
        isDefault = true;
      }
      else if (isMavenProcessorPluginDefaultConfiguration(annotationProcessorDirectory, testAnnotationProcessorDirectory, mavenProject,
                                                          project)) {
        moduleProfileName = MAVEN_BSC_DEFAULT_ANNOTATION_PROFILE;
        isDefault = true;
      }
      else {
        moduleProfileName = PROFILE_PREFIX + module.getName();
        isDefault = false;
      }

      ProcessorConfigProfile moduleProfile =
        getModuleProfile(module, mavenProject, project, compilerConfiguration, moduleProfileName, annotationProcessorDirectory,
                         testAnnotationProcessorDirectory);
      if (moduleProfile == null) return;
      configureAnnotationProcessorPath(moduleProfile, mavenProject, project);
      WriteAction.runAndWait(() -> cleanAndMergeModuleProfiles(rootProject, compilerConfiguration, moduleProfile, isDefault, module));
    }
    else {
      WriteAction.runAndWait(() -> cleanAndMergeModuleProfiles(rootProject, compilerConfiguration, null, false, module));
    }
  }

  @Override
  public void resolve(Project project,
                      MavenProject mavenProject,
                      NativeMavenProjectHolder nativeMavenProject,
                      MavenEmbedderWrapper embedder,
                      ResolveContext context) throws MavenProcessCanceledException {
    if(getConfig(mavenProject, "annotationProcessorPaths") == null) return;
    Element config = getConfig(mavenProject, "annotationProcessorPaths");
    LOG.assertTrue(config != null);

    List<MavenArtifactInfo> artifactsInfo = getArtifactsInfo(config);
    if (artifactsInfo.isEmpty()) {
      return;
    }

    MavenProjectsManager projectsManager = MavenProjectsManager.getInstance(project);
    List<MavenArtifactInfo> externalArtifacts = new ArrayList<>();
    for (MavenArtifactInfo info : artifactsInfo) {
      MavenProject mavenArtifact = projectsManager.findProject(new MavenId(info.getGroupId(), info.getArtifactId(), info.getVersion()));
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
  private static List<MavenArtifactInfo> getArtifactsInfo(Element config) {
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

  private String getAnnotationProcessorDirectory(MavenProject mavenProject, boolean isTest, String defaultTestAnnotationOutput) {
    String relativeAnotationProcessorDirectory = getRelativeAnnotationProcessorDirectory(mavenProject, isTest);
    if (relativeAnotationProcessorDirectory == null) {
      relativeAnotationProcessorDirectory = defaultTestAnnotationOutput;
    }
    return relativeAnotationProcessorDirectory;
  }

  @Nullable
  private static ProcessorConfigProfile getModuleProfile(Module module,
                                                         MavenProject mavenProject,
                                                         Project project,
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

  private static void configureAnnotationProcessorPath(ProcessorConfigProfile profile, MavenProject mavenProject, Project project) {
    String annotationProcessorPath = mavenProject.getAnnotationProcessorPath(project);
    if (StringUtil.isNotEmpty(annotationProcessorPath)) {
      profile.setObtainProcessorsFromClasspath(false);
      profile.setProcessorPath(annotationProcessorPath);
    }
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
        final String mavenProjectRootProfileName = PROFILE_PREFIX + rootProject.getDisplayName();
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

  private static boolean isMavenDefaultAnnotationProcessorConfiguration(@NotNull String annotationProcessorDirectory,
                                                                        @NotNull String testAnnotationProcessorDirectory,
                                                                        @NotNull MavenProject mavenProject,
                                                                        @NotNull Project project) {
    Map<String, String> options = mavenProject.getAnnotationProcessorOptions();
    List<String> processors = mavenProject.getDeclaredAnnotationProcessors();
    return ContainerUtil.isEmpty(processors)
           && options.isEmpty()
           && StringUtil.isEmpty(mavenProject.getAnnotationProcessorPath(project))
           && DEFAULT_ANNOTATION_PATH_OUTPUT.equals(annotationProcessorDirectory.replace('\\', '/'))
           && DEFAULT_TEST_ANNOTATION_OUTPUT.equals(testAnnotationProcessorDirectory.replace('\\', '/'));
  }

  private static boolean isMavenProcessorPluginDefaultConfiguration(@NotNull String annotationProcessorDirectory,
                                                                    @NotNull String testAnnotationProcessorDirectory,
                                                                    @NotNull MavenProject mavenProject,
                                                                    @NotNull Project project) {
    Map<String, String> options = mavenProject.getAnnotationProcessorOptions();
    List<String> processors = mavenProject.getDeclaredAnnotationProcessors();
    return ContainerUtil.isEmpty(processors)
           && options.isEmpty()
           && StringUtil.isEmpty(mavenProject.getAnnotationProcessorPath(project))
           && DEFAULT_BSC_ANNOTATION_PATH_OUTPUT.equals(annotationProcessorDirectory.replace('\\', '/'))
           && DEFAULT_BSC_TEST_ANNOTATION_OUTPUT.equals(testAnnotationProcessorDirectory.replace('\\', '/'));
  }

  @Nullable
  private static String getRelativeAnnotationProcessorDirectory(MavenProject mavenProject, boolean isTest) {
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
      return null;
    }
  }

  private static boolean shouldEnableAnnotationProcessors(MavenProject mavenProject) {
    if ("pom".equals(mavenProject.getPackaging())) return false;

    return mavenProject.getProcMode() != MavenProject.ProcMode.NONE
           || mavenProject.findPlugin("org.bsc.maven", "maven-processor-plugin") != null;
  }
}
