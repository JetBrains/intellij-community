/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.importing.configurers;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.jps.model.java.impl.compiler.ProcessorConfigProfileImpl;

import java.io.File;
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

  @Override
  public void configure(@NotNull MavenProject mavenProject, @NotNull Project project, @Nullable Module module) {
    if (module == null) return;

    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk != null) {
      String versionString = sdk.getVersionString();
      if (versionString != null) {
        if (versionString.contains("1.5") || versionString.contains("1.4") || versionString.contains("1.3") || versionString.contains("1.2")) {
          return;
        }
      }
    }

    CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);

    ProcessorConfigProfile currentProfile = compilerConfiguration.getAnnotationProcessingConfiguration(module);

    String moduleProfileName = PROFILE_PREFIX + module.getName();

    if (currentProfile != compilerConfiguration.getDefaultProcessorProfile()
        && !MAVEN_DEFAULT_ANNOTATION_PROFILE.equals(currentProfile.getName())
        && !moduleProfileName.equals(currentProfile.getName())) {
      return;
    }

    ProcessorConfigProfile moduleProfile = compilerConfiguration.findModuleProcessorProfile(moduleProfileName);

    ProcessorConfigProfile defaultMavenProfile = compilerConfiguration.findModuleProcessorProfile(MAVEN_DEFAULT_ANNOTATION_PROFILE);

    if (shouldEnableAnnotationProcessors(mavenProject)) {
      String annotationProcessorDirectory = getRelativeAnnotationProcessorDirectory(mavenProject, false);
      if (annotationProcessorDirectory == null) {
        annotationProcessorDirectory = DEFAULT_ANNOTATION_PATH_OUTPUT;
      }

      String testAnnotationProcessorDirectory = getRelativeAnnotationProcessorDirectory(mavenProject, true);
      if (testAnnotationProcessorDirectory == null) {
        testAnnotationProcessorDirectory = DEFAULT_TEST_ANNOTATION_OUTPUT;
      }

      Map<String, String> options = mavenProject.getAnnotationProcessorOptions();

      List<String> processors = mavenProject.getDeclaredAnnotationProcessors();

      if (processors == null
          && options.isEmpty()
          && DEFAULT_ANNOTATION_PATH_OUTPUT.equals(annotationProcessorDirectory.replace('\\', '/'))
          && DEFAULT_TEST_ANNOTATION_OUTPUT.equals(testAnnotationProcessorDirectory.replace('\\', '/'))) {
        if (moduleProfile != null) {
          compilerConfiguration.removeModuleProcessorProfile(moduleProfile);
        }

        if (defaultMavenProfile == null) {
          defaultMavenProfile = new ProcessorConfigProfileImpl(MAVEN_DEFAULT_ANNOTATION_PROFILE);
          defaultMavenProfile.setEnabled(true);
          defaultMavenProfile.setOutputRelativeToContentRoot(true);
          defaultMavenProfile.setObtainProcessorsFromClasspath(true);
          defaultMavenProfile.setGeneratedSourcesDirectoryName(DEFAULT_ANNOTATION_PATH_OUTPUT, false);
          defaultMavenProfile.setGeneratedSourcesDirectoryName(DEFAULT_TEST_ANNOTATION_OUTPUT, true);
          compilerConfiguration.addModuleProcessorProfile(defaultMavenProfile);
        }

        defaultMavenProfile.addModuleName(module.getName());
      }
      else {
        if (defaultMavenProfile != null) {
          defaultMavenProfile.removeModuleName(module.getName());

          if (defaultMavenProfile.getModuleNames().isEmpty()) {
            compilerConfiguration.removeModuleProcessorProfile(defaultMavenProfile);
          }
        }

        if (moduleProfile == null) {
          moduleProfile = new ProcessorConfigProfileImpl(moduleProfileName);
          moduleProfile.setOutputRelativeToContentRoot(true);
          moduleProfile.setEnabled(true);
          moduleProfile.setObtainProcessorsFromClasspath(true);
          moduleProfile.addModuleName(module.getName());
          compilerConfiguration.addModuleProcessorProfile(moduleProfile);
        }

        moduleProfile.setGeneratedSourcesDirectoryName(annotationProcessorDirectory, false);
        moduleProfile.setGeneratedSourcesDirectoryName(testAnnotationProcessorDirectory, true);

        moduleProfile.clearProcessorOptions();
        for (Map.Entry<String, String> entry : options.entrySet()) {
          moduleProfile.setOption(entry.getKey(), entry.getValue());
        }

        moduleProfile.clearProcessors();

        if (processors != null) {
          for (String processor : processors) {
            moduleProfile.addProcessor(processor);
          }
        }
      }
    }
    else {
      if (defaultMavenProfile != null) {
        defaultMavenProfile.removeModuleName(module.getName());

        if (defaultMavenProfile.getModuleNames().isEmpty()) {
          compilerConfiguration.removeModuleProcessorProfile(defaultMavenProfile);
        }
      }

      if (moduleProfile != null) {
        compilerConfiguration.removeModuleProcessorProfile(moduleProfile);
      }
    }
  }

  @Nullable
  private static String getRelativeAnnotationProcessorDirectory(MavenProject mavenProject, boolean isTest) {
    String annotationProcessorDirectory = mavenProject.getAnnotationProcessorDirectory(isTest);
    File annotationProcessorDirectoryFile = new File(annotationProcessorDirectory);
    if (!annotationProcessorDirectoryFile.isAbsolute()) {
      return annotationProcessorDirectory;
    }

    String absoluteProjectDirectory = mavenProject.getDirectory();
    return FileUtil.getRelativePath(new File(absoluteProjectDirectory), annotationProcessorDirectoryFile);
  }

  private static boolean shouldEnableAnnotationProcessors(MavenProject mavenProject) {
    if ("pom".equals(mavenProject.getPackaging())) return false;

    return mavenProject.getProcMode() != MavenProject.ProcMode.NONE
           || mavenProject.getPluginConfiguration("org.bsc.maven", "maven-processor-plugin") != null;
  }

}
