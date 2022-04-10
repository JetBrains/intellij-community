// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.ide.util.projectWizard.importSources.JavaModuleSourceRoot;
import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetectionUtil;
import com.intellij.openapi.application.WriteAction;
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
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.NotNullList;
import org.apache.commons.lang.StringUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.tree.MavenModuleType;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.model.MavenResource;
import org.jetbrains.idea.maven.project.MavenImportingSettings;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.Path;
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

        MavenRootModelAdapter a = new MavenRootModelAdapter(
          new MavenRootModelAdapterLegacyImpl(mavenProject, each, new ModifiableModelsProviderProxyWrapper(project)));
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

  public void configMainAndTestAggregator() {
    myModel.clearSourceFolders();
    myModel.useModuleOutput(null, null);
    configExcludedAggregatorFolders();
  }

  public void configMainAndTest(MavenModuleType type) {
    if (!myImportingSettings.isKeepSourceFolders()) {
      myModel.clearSourceFolders();
    }
    boolean isTest = type == MavenModuleType.TEST;
    Map<String, JpsModuleSourceRootType<?>> sourceFolders = isTest
                                                            ? getTestSourceFolders(myMavenProject)
                                                            : getMainSourceFolders(myMavenProject);

    java.nio.file.Path contentRootPath = java.nio.file.Path.of(myMavenProject.getDirectory(), "src", isTest ? "test" : "main");
    String defaultContentRoot = contentRootPath.toString();
    boolean containDefaultContentRoot = false;
    for (String path : sourceFolders.keySet()) {
      if (!containDefaultContentRoot) {
        containDefaultContentRoot = path.contains(defaultContentRoot);
      }
    }
    sourceFolders.forEach((p, t) -> myModel.addSourceFolder(p, t));

    configOutputFolders(isTest);
    configGeneratedFolders(isTest);

    generateNewContentRoots(containDefaultContentRoot ? contentRootPath : null);
  }

  private void configSourceFolders() {
    Map<String, JpsModuleSourceRootType<?>> sourceFolders = getSourceFolders(myMavenProject);

    sourceFolders.forEach((p, t) -> myModel.addSourceFolder(p, t));
  }

  private static boolean alreadyAdded(String canonicalPath, Map<String, ?> addedPaths) {
    for (String existing : addedPaths.keySet()) {
      if (VfsUtilCore.isEqualOrAncestor(existing, canonicalPath)
          || VfsUtilCore.isEqualOrAncestor(canonicalPath, existing)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static Map<String, JpsModuleSourceRootType<?>> getSourceFolders(MavenProject mavenProject) {
    final MultiMap<JpsModuleSourceRootType<?>, String> roots = MultiMap.createLinked();
    addMainSourcePath(mavenProject, roots);
    addTestSourcePath(mavenProject, roots);
    return getPathMap(mavenProject, roots);
  }

  @NotNull
  public static Map<String, JpsModuleSourceRootType<?>> getMainSourceFolders(MavenProject mavenProject) {
    final MultiMap<JpsModuleSourceRootType<?>, String> roots = MultiMap.createLinked();
    addMainSourcePath(mavenProject, roots);
    return getPathMap(mavenProject, roots);
  }

  @NotNull
  public static Map<String, JpsModuleSourceRootType<?>> getTestSourceFolders(MavenProject mavenProject) {
    final MultiMap<JpsModuleSourceRootType<?>, String> roots = MultiMap.createLinked();
    addTestSourcePath(mavenProject, roots);
    return getPathMap(mavenProject, roots);
  }

  private static void addMainSourcePath(MavenProject mavenProject, MultiMap<JpsModuleSourceRootType<?>, String> roots) {
    roots.putValues(JavaSourceRootType.SOURCE, mavenProject.getSources());

    for (MavenImporter each : MavenImporter.getSuitableImporters(mavenProject)) {
      each.collectSourceRoots(mavenProject, (s, type) -> roots.putValue(type, s));
    }
    for (MavenResource each : mavenProject.getResources()) {
      roots.putValue(JavaResourceRootType.RESOURCE, each.getDirectory());
    }

    addBuilderHelperPaths(mavenProject, "add-source", roots.getModifiable(JavaSourceRootType.SOURCE));
    addBuilderHelperResourcesPaths(mavenProject, "add-resource", roots.getModifiable(JavaResourceRootType.RESOURCE));
  }

  private static void addTestSourcePath(MavenProject mavenProject, MultiMap<JpsModuleSourceRootType<?>, String> roots) {
    roots.putValues(JavaSourceRootType.TEST_SOURCE, mavenProject.getTestSources());
    for (MavenResource each : mavenProject.getTestResources()) {
      roots.putValue(JavaResourceRootType.TEST_RESOURCE, each.getDirectory());
    }

    addBuilderHelperPaths(mavenProject, "add-test-source", roots.getModifiable(JavaSourceRootType.TEST_SOURCE));
    addBuilderHelperResourcesPaths(mavenProject, "add-test-resource", roots.getModifiable(JavaResourceRootType.TEST_RESOURCE));
  }

  @NotNull
  private static Map<String, JpsModuleSourceRootType<?>> getPathMap(@NotNull MavenProject mavenProject,
                                                                    @NotNull MultiMap<JpsModuleSourceRootType<?>, String> roots) {
    Map<String, JpsModuleSourceRootType<?>> addedPaths = new LinkedHashMap<>();
    for (JpsModuleSourceRootType<?> type : roots.keySet()) {
      for (String path : roots.get(type)) {
        if (path != null) {
          Path nioPath = MavenUtil.toPath(mavenProject, path);
          String canonicalPath = nioPath.getPath();
          if (!alreadyAdded(canonicalPath, addedPaths)) {
            addedPaths.put(canonicalPath, type);
          }
        }
      }
    }
    return addedPaths;
  }

  private static void addBuilderHelperPaths(MavenProject mavenProject, String goal, Collection<String> folders) {
    final MavenPlugin plugin = mavenProject.findPlugin("org.codehaus.mojo", "build-helper-maven-plugin");
    if (plugin != null) {
      for (MavenPlugin.Execution execution : plugin.getExecutions()) {
        if (execution.getGoals().contains(goal)) {
          final Element configurationElement = execution.getConfigurationElement();
          if (configurationElement != null) {
            final Element sourcesElement = configurationElement.getChild("sources");
            if (sourcesElement != null) {
              for (Element element : sourcesElement.getChildren()) {
                folders.add(element.getTextTrim());
              }
            }
          }
        }
      }
    }
  }

  private static void addBuilderHelperResourcesPaths(MavenProject mavenProject, String goal, Collection<String> folders) {
    final MavenPlugin plugin = mavenProject.findPlugin("org.codehaus.mojo", "build-helper-maven-plugin");
    if (plugin != null) {
      for (MavenPlugin.Execution execution : plugin.getExecutions()) {
        if (execution.getGoals().contains(goal)) {
          final Element configurationElement = execution.getConfigurationElement();
          if (configurationElement != null) {
            final Element sourcesElement = configurationElement.getChild("resources");
            if (sourcesElement != null) {
              for (Element element : sourcesElement.getChildren()) {
                Element directory = element.getChild("directory");
                if (directory != null) folders.add(directory.getTextTrim());
              }
            }
          }
        }
      }
    }
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

  private void configOutputFolders(boolean test) {
    if (myImportingSettings.isUseMavenOutput()) {
      if (test) {
        myModel.useModuleOutput(StringUtils.EMPTY, myMavenProject.getTestOutputDirectory());
      }
      else {
        myModel.useModuleOutput(myMavenProject.getOutputDirectory(), StringUtils.EMPTY);
      }
    }

    String buildDirPath = myModel.toPath(myMavenProject.getBuildDirectory()).getPath();
    String outputDirPath = myModel.toPath(myMavenProject.getOutputDirectory()).getPath();

    if (test) {
      String testOutputDirPath = myModel.toPath(myMavenProject.getTestOutputDirectory()).getPath();
      if ((!VfsUtilCore.isEqualOrAncestor(buildDirPath, testOutputDirPath))) {
        myModel.addExcludedFolder(myMavenProject.getTestOutputDirectory());
      }
    } else {
      if ((!VfsUtilCore.isEqualOrAncestor(buildDirPath, outputDirPath))) {
        myModel.addExcludedFolder(myMavenProject.getOutputDirectory());
      }
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

  private void configGeneratedFolders(boolean test) {
    File targetDir = new File(myMavenProject.getBuildDirectory());

    String generatedDir = test ? myMavenProject.getGeneratedSourcesDirectory(true) : myMavenProject.getGeneratedSourcesDirectory(false);

    myModel.unregisterAll(targetDir.getPath(), false, false);

    if (myImportingSettings.getGeneratedSourcesFolder() != MavenImportingSettings.GeneratedSourcesFolder.IGNORE) {
      if (test) {
        myModel.addGeneratedJavaSourceFolder(myMavenProject.getAnnotationProcessorDirectory(true), JavaSourceRootType.TEST_SOURCE);
      }
      else {
        myModel.addGeneratedJavaSourceFolder(myMavenProject.getAnnotationProcessorDirectory(false), JavaSourceRootType.SOURCE);
      }
    }

    File[] targetChildren = targetDir.listFiles();

    if (targetChildren != null) {
      for (File f : targetChildren) {
        if (!f.isDirectory()) continue;

        if (!test && FileUtil.pathsEqual(generatedDir, f.getPath())) {
          configGeneratedSourceFolder(f, JavaSourceRootType.SOURCE);
        }
        else if (test && FileUtil.pathsEqual(generatedDir, f.getPath())) {
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

    Set<String> topLevelSourceFolderUrls = new HashSet<>();
    for (String sourceRoot : sourceFoldersMap.keySet()) {
      if (!ContainerUtil.exists(topLevelSourceFolderUrls, root -> FileUtil.isAncestor(root, sourceRoot, false))) {
        topLevelSourceFolderUrls.add(sourceRoot);
      }
    }

    setContentRootData(topLevelSourceFolderUrls, rootModel, sourceFoldersMap);
  }

  private void generateNewContentRoots(@Nullable java.nio.file.Path defaultContentRootPath) {
    Map<String, SourceFolder> sourceFoldersMap = new TreeMap<>(FileUtil::comparePaths);
    for (String sourceRootUrl : myModel.getSourceRootUrls(true)) {
      String sourceRootPath = FileUtil.toSystemDependentName(VfsUtilCore.urlToPath(sourceRootUrl));
      SourceFolder sourceFolder = myModel.getSourceFolder(new File(sourceRootPath));
      if (sourceFolder != null) {
        sourceFoldersMap.put(sourceRootUrl, sourceFolder);
      }
    }

    ModifiableRootModel rootModel = myModel.getRootModel();

    for (ContentEntry contentEntry : rootModel.getContentEntries()) {
      rootModel.removeContentEntry(contentEntry);
    }

    Set<String> topLevelSourceFolderUrls = new HashSet<>();
    if (defaultContentRootPath != null) {
      String defaultContentRootUrl = MavenUtil.toPath(myMavenProject, defaultContentRootPath.toString()).toUrl().getUrl();
      topLevelSourceFolderUrls.add(defaultContentRootUrl);
    }
    for (String sourceRoot : sourceFoldersMap.keySet()) {
      if (!ContainerUtil.exists(topLevelSourceFolderUrls, root -> FileUtil.isAncestor(root, sourceRoot, false))) {
        topLevelSourceFolderUrls.add(sourceRoot);
      }
    }

    setContentRootData(topLevelSourceFolderUrls, rootModel, sourceFoldersMap);
  }

  private static void setContentRootData(Set<String> topLevelSourceFolderUrls,
                                         ModifiableRootModel rootModel,
                                         Map<String, SourceFolder> sourceFoldersMap) {
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

  private void configExcludedAggregatorFolders() {
    File targetDir = new File(myMavenProject.getBuildDirectory());

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
    return result == null ? ArrayUtilRt.EMPTY_FILE_ARRAY : result;
  }
}
