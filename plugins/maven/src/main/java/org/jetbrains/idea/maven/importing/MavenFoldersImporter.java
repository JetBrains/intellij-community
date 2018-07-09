/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.importing;

import com.intellij.ide.util.projectWizard.importSources.JavaModuleSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.LinkedMultiMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.NotNullList;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenResource;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.net.URL;
import java.util.*;

public class MavenFoldersImporter {
  private final MavenProject myMavenProject;
  private final MavenImportingSettings myImportingSettings;
  private final MavenRootModelAdapter myModel;

  public static void updateProjectFolders(final Project project, final boolean updateTargetFoldersOnly) {
    final MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
    final MavenImportingSettings settings = manager.getImportingSettings();

    WriteAction.run(() -> {
      List<ModifiableRootModel> rootModels = new ArrayList<>();
      for (Module each : ModuleManager.getInstance(project).getModules()) {
        MavenProject mavenProject = manager.findProject(each);
        if (mavenProject == null) continue;

        MavenRootModelAdapter a = new MavenRootModelAdapter(mavenProject, each, new IdeModifiableModelsProviderImpl(project));
        new MavenFoldersImporter(mavenProject, settings, a).config(updateTargetFoldersOnly);

        ModifiableRootModel model = a.getRootModel();
        if (model.isChanged()) {
          rootModels.add(model);
        }
        else {
          model.dispose();
        }
      }

      if (!rootModels.isEmpty()) {
        ModifiableModelCommitter.multiCommit(rootModels, ModuleManager.getInstance(rootModels.get((0)).getProject()).getModifiableModel());
      }
    });
  }

  public MavenFoldersImporter(@NotNull MavenProject mavenProject, @NotNull MavenImportingSettings settings, MavenRootModelAdapter model) {
    myMavenProject = mavenProject;
    myImportingSettings = settings;
    myModel = model;
  }

  public void config() {
    config(false);
  }

  private void config(boolean updateTargetFoldersOnly) {
    if (!updateTargetFoldersOnly) {
      if (!myImportingSettings.isKeepSourceFolders()) {
        myModel.clearSourceFolders();
      }
      configSourceFolders();
      configOutputFolders();
    }
    configGeneratedFolders();
    if (!updateTargetFoldersOnly) {
      if (!FileUtil.namesEqual("pom", myMavenProject.getFile().getNameWithoutExtension()) &&
          MavenUtil.streamPomFiles(myModel.getModule().getProject(), myMavenProject.getDirectoryFile()).skip(1).findAny().isPresent()) {
        generateNewContentRoots(false);
      }
      else {
        generateNewContentRoots(true);
      }
    }
    configExcludedFolders();
  }

  private void configSourceFolders() {
    final MultiMap<JpsModuleSourceRootType<?>, String> roots = new LinkedMultiMap<>();

    roots.putValues(JavaSourceRootType.SOURCE, myMavenProject.getSources());
    roots.putValues(JavaSourceRootType.TEST_SOURCE, myMavenProject.getTestSources());

    for (MavenImporter each : MavenImporter.getSuitableImporters(myMavenProject)) {
      each.collectSourceRoots(myMavenProject, (s, type) -> roots.putValue(type, s));
    }

    for (MavenResource each : myMavenProject.getResources()) {
      roots.putValue(JavaResourceRootType.RESOURCE, each.getDirectory());
    }
    for (MavenResource each : myMavenProject.getTestResources()) {
      roots.putValue(JavaResourceRootType.TEST_RESOURCE, each.getDirectory());
    }

    addBuilderHelperPaths("add-source", roots.getModifiable(JavaSourceRootType.SOURCE));
    addBuilderHelperPaths("add-test-source", roots.getModifiable(JavaSourceRootType.TEST_SOURCE));

    List<String> addedPaths = new ArrayList<>();
    for (JpsModuleSourceRootType<?> type : roots.keySet()) {
      for (String path : roots.get(type)) {
        if (path != null) {
          addSourceFolderIfNotOverlap(path, type, addedPaths);
        }
      }
    }
  }

  private void addBuilderHelperPaths(String goal, Collection<String> folders) {
    final Element configurationElement = myMavenProject.getPluginGoalConfiguration("org.codehaus.mojo", "build-helper-maven-plugin", goal);
    if (configurationElement != null) {
      final Element sourcesElement = configurationElement.getChild("sources");
      if (sourcesElement != null) {
        for (Element element : sourcesElement.getChildren()) {
          folders.add(element.getTextTrim());
        }
      }
    }
  }

  private void addSourceFolderIfNotOverlap(String path, JpsModuleSourceRootType<?> type, List<String> addedPaths) {
    String canonicalPath = myModel.toPath(path).getPath();
    for (String existing : addedPaths) {
      if (VfsUtilCore.isEqualOrAncestor(existing, canonicalPath)
          || VfsUtilCore.isEqualOrAncestor(canonicalPath, existing)) {
        return;
      }
    }
    addedPaths.add(canonicalPath);
    myModel.addSourceFolder(canonicalPath, type);
  }

  private void configOutputFolders() {
    if (myImportingSettings.isUseMavenOutput()) {
      myModel.useModuleOutput(myMavenProject.getOutputDirectory(),
                              myMavenProject.getTestOutputDirectory());
    }

    String buildDirPath = myModel.toPath(myMavenProject.getBuildDirectory()).getPath();
    String outputDirPath = myModel.toPath(myMavenProject.getOutputDirectory()).getPath();

    if ((!VfsUtilCore.isEqualOrAncestor(buildDirPath, outputDirPath))) {
      myModel.addExcludedFolder(myMavenProject.getOutputDirectory());
    }

    String testOutputDirPath = myModel.toPath(myMavenProject.getTestOutputDirectory()).getPath();
    if ((!VfsUtilCore.isEqualOrAncestor(buildDirPath, testOutputDirPath))) {
      myModel.addExcludedFolder(myMavenProject.getTestOutputDirectory());
    }
  }

  private void configGeneratedFolders() {
    File targetDir = new File(myMavenProject.getBuildDirectory());

    String generatedDir = myMavenProject.getGeneratedSourcesDirectory(false);
    String generatedDirTest = myMavenProject.getGeneratedSourcesDirectory(true);

    myModel.unregisterAll(targetDir.getPath(), false, false);

    if (myImportingSettings.getGeneratedSourcesFolder() != MavenImportingSettings.GeneratedSourcesFolder.IGNORE) {
      myModel.addGeneratedJavaSourceFolder(myMavenProject.getAnnotationProcessorDirectory(true), JavaSourceRootType.TEST_SOURCE);
      myModel.addGeneratedJavaSourceFolder(myMavenProject.getAnnotationProcessorDirectory(false), JavaSourceRootType.SOURCE);
    }

    File[] targetChildren = targetDir.listFiles();

    if (targetChildren != null) {
      for (File f : targetChildren) {
        if (!f.isDirectory()) continue;

        if (FileUtil.pathsEqual(generatedDir, f.getPath())) {
          configGeneratedSourceFolder(f, JavaSourceRootType.SOURCE);
        }
        else if (FileUtil.pathsEqual(generatedDirTest, f.getPath())) {
          configGeneratedSourceFolder(f, JavaSourceRootType.TEST_SOURCE);
        }
      }
    }
  }

  private void generateNewContentRoots(boolean orphansOnly) {
    Map<String, SourceFolder> sourceFoldersMap = new TreeMap<>(FileUtil::comparePaths);
    for (String sourceRootUrl : myModel.getSourceRootUrls(true)) {
      String sourceRootPath = FileUtil.toSystemDependentName(VfsUtil.urlToPath(sourceRootUrl));
      SourceFolder sourceFolder = myModel.getSourceFolder(new File(sourceRootPath));
      if (sourceFolder != null) {
        sourceFoldersMap.put(sourceRootUrl, sourceFolder);
      }
    }

    ModifiableRootModel rootModel = myModel.getRootModel();

    if (orphansOnly) {
      for (ContentEntry contentEntry : rootModel.getContentEntries()) {
        sourceFoldersMap.keySet().removeIf(root -> FileUtil.isAncestor(contentEntry.getUrl(), root, false));
      }
    }
    else {
      for (ContentEntry contentEntry : rootModel.getContentEntries()) {
        rootModel.removeContentEntry(contentEntry);
      }
    }

    Set<String> topLevelSourceFolderUrls = ContainerUtil.newHashSet();
    for (String sourceRoot : sourceFoldersMap.keySet()) {
      if (topLevelSourceFolderUrls.stream().noneMatch(root -> FileUtil.isAncestor(root, sourceRoot, false))) {
        topLevelSourceFolderUrls.add(sourceRoot);
      }
    }

    for (String sourceFolderUrl : topLevelSourceFolderUrls) {
      if (isAlreadyContentRoot(sourceFolderUrl, rootModel.getProject())) continue;

      ContentEntry contentEntry = rootModel.addContentEntry(sourceFolderUrl);
      for (Map.Entry<String, SourceFolder> entry : sourceFoldersMap.entrySet()) {
        if (FileUtil.isAncestor(sourceFolderUrl, entry.getKey(), false)) {
          SourceFolder oldSourceFolder = entry.getValue();
          SourceFolder newSourceFolder = contentEntry.addSourceFolder(oldSourceFolder.getUrl(), oldSourceFolder.getRootType());
          newSourceFolder.setPackagePrefix(oldSourceFolder.getPackagePrefix());
        }
      }
    }
  }

  private static boolean isAlreadyContentRoot(String sourceFolderUrl, Project project) {
    URL url = VfsUtilCore.convertToURL(sourceFolderUrl);
    if (url == null) return false;

    VirtualFile sourceFolder = VfsUtil.findFileByURL(url);
    if (sourceFolder == null) return false;

    MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);
    MavenProject containingProject = mavenProjectsManager.findContainingProject(sourceFolder);
    if (containingProject != null) {
      Module module = mavenProjectsManager.findModule(containingProject);
      if (module == null) return false;

      for (ContentEntry contentEntry : ModuleRootManager.getInstance(module).getContentEntries()) {
        if (contentEntry.getUrl().equals(sourceFolderUrl)) {
          return true;
        }
      }
    }
    return false;
  }

  private void configExcludedFolders() {
    File targetDir = new File(myMavenProject.getBuildDirectory());

    String generatedDir = myMavenProject.getGeneratedSourcesDirectory(false);
    String generatedDirTest = myMavenProject.getGeneratedSourcesDirectory(true);

    File[] targetChildren = targetDir.listFiles();
    if (targetChildren != null) {
      for (File f : targetChildren) {
        if (!f.isDirectory()) continue;

        if (!FileUtil.pathsEqual(generatedDir, f.getPath()) && !FileUtil.pathsEqual(generatedDirTest, f.getPath())) {
          if (myImportingSettings.isExcludeTargetFolder()) {
            if (myModel.hasRegisteredSourceSubfolder(f)) continue;
            if (myModel.isAlreadyExcluded(f)) continue;
            myModel.addExcludedFolder(f.getPath());
          }
        }
      }
    }

    List<String> facetExcludes = new NotNullList<>();
    for (MavenImporter each : MavenImporter.getSuitableImporters(myMavenProject)) {
      each.collectExcludedFolders(myMavenProject, facetExcludes);
    }
    for (String eachFolder : facetExcludes) {
      myModel.unregisterAll(eachFolder, true, true);
      myModel.addExcludedFolder(eachFolder);
    }

    if (myImportingSettings.isExcludeTargetFolder()) {
        myModel.addExcludedFolder(targetDir.getPath());
    }
  }

  private void configGeneratedSourceFolder(@NotNull File targetDir, final JavaSourceRootType rootType) {
    switch (myImportingSettings.getGeneratedSourcesFolder()) {
      case GENERATED_SOURCE_FOLDER:
        myModel.addGeneratedJavaSourceFolder(targetDir.getPath(), rootType);
        break;

      case SUBFOLDER:
        addAllSubDirsAsGeneratedSources(targetDir, rootType);
        break;

      case AUTODETECT:
        Collection<JavaModuleSourceRoot> sourceRoots = JavaSourceRootDetectionUtil.suggestRoots(targetDir);

        for (JavaModuleSourceRoot root : sourceRoots) {
          if (FileUtil.filesEqual(targetDir, root.getDirectory())) {
            myModel.addGeneratedJavaSourceFolder(targetDir.getPath(), rootType);
            return;
          }

          addAsGeneratedSourceFolder(root.getDirectory(), rootType);
        }

        addAllSubDirsAsGeneratedSources(targetDir, rootType);
        break;

      case IGNORE:
        break; // Ignore.
    }
  }

  private void addAsGeneratedSourceFolder(@NotNull File dir, final JavaSourceRootType rootType) {
    SourceFolder folder = myModel.getSourceFolder(dir);
    if (!myModel.hasRegisteredSourceSubfolder(dir) || folder != null && !isGenerated(folder)) {
      myModel.addGeneratedJavaSourceFolder(dir.getPath(), rootType);
    }
  }

  private static boolean isGenerated(@NotNull  SourceFolder folder) {
    JavaSourceRootProperties properties = folder.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES);
    return properties != null && properties.isForGeneratedSources();
  }

  private void addAllSubDirsAsGeneratedSources(@NotNull File dir, final JavaSourceRootType rootType) {
    for (File f : getChildren(dir)) {
      if (f.isDirectory()) {
        addAsGeneratedSourceFolder(f, rootType);
      }
    }
  }

  private static File[] getChildren(File dir) {
    File[] result = dir.listFiles();
    return result == null ? ArrayUtil.EMPTY_FILE_ARRAY : result;
  }
}
