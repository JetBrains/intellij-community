// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.configurers;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.MavenProjectImporter;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.jps.model.java.impl.compiler.ProcessorConfigProfileImpl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class MavenAnnotationProcessorConfigurer extends MavenModuleConfigurer {

  public static final String PROFILE_PREFIX = "Annotation profile for ";
  public static final String MAVEN_DEFAULT_ANNOTATION_PROFILE = "Maven default annotation processors profile";
  public static final String DEFAULT_ANNOTATION_PATH_OUTPUT = "target/generated-sources/annotations";
  public static final String DEFAULT_TEST_ANNOTATION_OUTPUT = "target/generated-test-sources/test-annotations";

  public static final String MAVEN_BSC_DEFAULT_ANNOTATION_PROFILE = PROFILE_PREFIX + "maven-processor-plugin default configuration";
  public static final String DEFAULT_BSC_ANNOTATION_PATH_OUTPUT = "target/generated-sources/apt";
  public static final String DEFAULT_BSC_TEST_ANNOTATION_OUTPUT = "target/generated-sources/apt-test";

  @Override
  public void configure(@NotNull MavenProject mavenProject, @NotNull Project project, @NotNull Module module) {
    WriteAction.runAndWait(() -> doConfigure(mavenProject, project, module));
  }

  private static void doConfigure(@NotNull MavenProject mavenProject, @NotNull Project project, @NotNull Module module) {
    if (project.isDisposed() || module.isDisposed()) {
      return;
    }
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk !=null) {
      String versionString = sdk.getVersionString();
      LanguageLevel languageLevel = LanguageLevel.parse(versionString);
      if (languageLevel != null && languageLevel.isLessThan(LanguageLevel.JDK_1_6)) return;
    }

    final CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    final MavenProject rootProject =
      ObjectUtils.notNull(MavenProjectsManager.getInstance(project).findRootProject(mavenProject), mavenProject);

    if (shouldEnableAnnotationProcessors(mavenProject)) {
      final String moduleProfileName;
      String annotationProcessorDirectory = getRelativeAnnotationProcessorDirectory(mavenProject, false);
      if (annotationProcessorDirectory == null) {
        annotationProcessorDirectory = DEFAULT_ANNOTATION_PATH_OUTPUT;
      }

      String testAnnotationProcessorDirectory = getRelativeAnnotationProcessorDirectory(mavenProject, true);
      if (testAnnotationProcessorDirectory == null) {
        testAnnotationProcessorDirectory = DEFAULT_TEST_ANNOTATION_OUTPUT;
      }

      final boolean isDefault;
      if (isMavenDefaultAnnotationProcessorConfiguration(annotationProcessorDirectory, testAnnotationProcessorDirectory, mavenProject, project)) {
        moduleProfileName = MAVEN_DEFAULT_ANNOTATION_PROFILE;
        isDefault = true;
      }
      else if (isMavenProcessorPluginDefaultConfiguration(annotationProcessorDirectory, testAnnotationProcessorDirectory, mavenProject, project)) {
        moduleProfileName = MAVEN_BSC_DEFAULT_ANNOTATION_PROFILE;
        isDefault = true;
      }
      else {
        moduleProfileName = PROFILE_PREFIX + module.getName();
        isDefault = false;
      }
      ProcessorConfigProfile moduleProfile = compilerConfiguration.findModuleProcessorProfile(moduleProfileName);

      if (moduleProfile == null) {
        moduleProfile = new ProcessorConfigProfileImpl(moduleProfileName);
        moduleProfile.setEnabled(true);
        compilerConfiguration.addModuleProcessorProfile(moduleProfile);
      }
      if (!moduleProfile.isEnabled()) return;

      if (MavenProjectImporter.isImportToTreeStructureEnabled()) {
        moduleProfile.setOutputRelativeToContentRoot(false);
      } else {
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
      configureAnnotationProcessorPath(moduleProfile, mavenProject, project);
      cleanAndMergeModuleProfiles(rootProject, compilerConfiguration, moduleProfile, isDefault, module);
    }
    else {
      cleanAndMergeModuleProfiles(rootProject, compilerConfiguration, null, false, module);
    }
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
          if(moduleProfile != mergedProfile) {
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
