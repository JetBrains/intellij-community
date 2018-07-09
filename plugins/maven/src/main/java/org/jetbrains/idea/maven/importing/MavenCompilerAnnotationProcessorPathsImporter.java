// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MavenCompilerAnnotationProcessorPathsImporter extends MavenImporter {

  private Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.importing.MavenCompilerAnnotationProcessorPathsImporter");

  public MavenCompilerAnnotationProcessorPathsImporter() {
    super("org.apache.maven.plugins", "maven-compiler-plugin");
  }

  @Override
  public boolean isApplicable(MavenProject mavenProject) {
    return getConfig(mavenProject, "annotationProcessorPaths") != null;
  }

  @Override
  public void preProcess(Module module,
                         MavenProject mavenProject,
                         MavenProjectChanges changes,
                         IdeModifiableModelsProvider modifiableModelsProvider) {

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
  public void resolve(Project project,
                      MavenProject mavenProject,
                      NativeMavenProjectHolder nativeMavenProject,
                      MavenEmbedderWrapper embedder,
                      ResolveContext context) throws MavenProcessCanceledException {
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

    List<MavenArtifact> annotationProcessors = embedder.resolveTransitively(externalArtifacts, mavenProject.getRemoteRepositories());
    mavenProject.addAnnotationProcessors(annotationProcessors);
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

    for (Element annotationProcessorPath : config.getChildren("annotationProcessorPath")) {
      addToArtifacts.consume(annotationProcessorPath);
    }
    return artifacts;
  }
}
