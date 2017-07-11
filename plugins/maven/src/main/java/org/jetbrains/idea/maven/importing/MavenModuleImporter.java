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
package org.jetbrains.idea.maven.importing;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.externalSystem.model.project.ProjectId;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class MavenModuleImporter {

  public static final String SUREFIRE_PLUGIN_LIBRARY_NAME = "maven-surefire-plugin urls";

  private static final Set<String> IMPORTED_CLASSIFIERS = ImmutableSet.of("client");

  private static final Map<String, LanguageLevel> MAVEN_IDEA_PLUGIN_LEVELS = ImmutableMap.of(
    "JDK_1_3", LanguageLevel.JDK_1_3,
    "JDK_1_4", LanguageLevel.JDK_1_4,
    "JDK_1_5", LanguageLevel.JDK_1_5,
    "JDK_1_6", LanguageLevel.JDK_1_6,
    "JDK_1_7", LanguageLevel.JDK_1_7);

  private final Module myModule;
  private final MavenProjectsTree myMavenTree;
  private final MavenProject myMavenProject;

  @Nullable
  private final MavenProjectChanges myMavenProjectChanges;
  private final Map<MavenProject, String> myMavenProjectToModuleName;
  private final MavenImportingSettings mySettings;
  private final IdeModifiableModelsProvider myModifiableModelsProvider;
  private MavenRootModelAdapter myRootModelAdapter;

  public MavenModuleImporter(Module module,
                             MavenProjectsTree mavenTree,
                             MavenProject mavenProject,
                             @Nullable MavenProjectChanges changes,
                             Map<MavenProject, String> mavenProjectToModuleName,
                             MavenImportingSettings settings,
                             IdeModifiableModelsProvider modifiableModelsProvider) {
    myModule = module;
    myMavenTree = mavenTree;
    myMavenProject = mavenProject;
    myMavenProjectChanges = changes;
    myMavenProjectToModuleName = mavenProjectToModuleName;
    mySettings = settings;
    myModifiableModelsProvider = modifiableModelsProvider;

    VirtualFile pomFile = mavenProject.getFile();
    if (!FileUtil.namesEqual("pom", pomFile.getNameWithoutExtension())) {
      MavenPomPathModuleService.getInstance(module).setPomFileUrl(pomFile.getUrl());
    }
  }

  public ModifiableRootModel getRootModel() {
    return myRootModelAdapter.getRootModel();
  }

  public void config(boolean isNewlyCreatedModule) {
    myRootModelAdapter = new MavenRootModelAdapter(myMavenProject, myModule, myModifiableModelsProvider);
    myRootModelAdapter.init(isNewlyCreatedModule);

    configFolders();
    configDependencies();
    configLanguageLevel();
  }

  public void preConfigFacets() {
    MavenUtil.invokeAndWaitWriteAction(myModule.getProject(), () -> {
      if (myModule.isDisposed()) return;

      final ModuleType moduleType = ModuleType.get(myModule);

      for (final MavenImporter importer : getSuitableImporters()) {
        final MavenProjectChanges changes;
        if (myMavenProjectChanges == null) {
          if (importer.processChangedModulesOnly()) continue;
          changes = MavenProjectChanges.NONE;
        }
        else {
          changes = myMavenProjectChanges;
        }

        if (importer.getModuleType() == moduleType) {
          importer.preProcess(myModule, myMavenProject, changes, myModifiableModelsProvider);
        }
      }
    });
  }

  public void configFacets(final List<MavenProjectsProcessorTask> postTasks) {
    MavenUtil.smartInvokeAndWait(myModule.getProject(), ModalityState.defaultModalityState(), () -> {
      if (myModule.isDisposed()) return;

      final ModuleType moduleType = ModuleType.get(myModule);

      ApplicationManager.getApplication().runWriteAction(() -> {
        for (final MavenImporter importer : getSuitableImporters()) {
          final MavenProjectChanges changes;
          if (myMavenProjectChanges == null) {
            if (importer.processChangedModulesOnly()) continue;
            changes = MavenProjectChanges.NONE;
          }
          else {
            changes = myMavenProjectChanges;
          }

          if (importer.getModuleType() == moduleType) {
            importer.process(myModifiableModelsProvider,
                             myModule,
                             myRootModelAdapter,
                             myMavenTree,
                             myMavenProject,
                             changes,
                             myMavenProjectToModuleName,
                             postTasks);
          }
        }
      });
    });
  }

  public void postConfigFacets() {
    MavenUtil.invokeAndWaitWriteAction(myModule.getProject(), () -> {
      if (myModule.isDisposed()) return;

      final ModuleType moduleType = ModuleType.get(myModule);

      for (final MavenImporter importer : getSuitableImporters()) {
        final MavenProjectChanges changes;
        if (myMavenProjectChanges == null) {
          if (importer.processChangedModulesOnly()) continue;
          changes = MavenProjectChanges.NONE;
        }
        else {
          changes = myMavenProjectChanges;
        }

        if (importer.getModuleType() == moduleType) {
          importer.postProcess(myModule, myMavenProject, changes, myModifiableModelsProvider);
        }
      }
    });
  }

  private List<MavenImporter> getSuitableImporters() {
    return myMavenProject.getSuitableImporters();
  }

  private void configFolders() {
    new MavenFoldersImporter(myMavenProject, mySettings, myRootModelAdapter).config();
  }

  private void configDependencies() {
    THashSet<String> dependencyTypesFromSettings = new THashSet<>();

    AccessToken accessToken = ReadAction.start();
    try {
      if (myModule.getProject().isDisposed()) return;

      dependencyTypesFromSettings.addAll(MavenProjectsManager.getInstance(myModule.getProject()).getImportingSettings().getDependencyTypesAsSet());
    }
    finally {
      accessToken.finish();
    }


    for (MavenArtifact artifact : myMavenProject.getDependencies()) {
      String dependencyType = artifact.getType();

      if (!dependencyTypesFromSettings.contains(dependencyType)
          && !myMavenProject.getDependencyTypesFromImporters(SupportedRequestType.FOR_IMPORT).contains(dependencyType)) {
        continue;
      }

      DependencyScope scope = selectScope(artifact.getScope());

      MavenProject depProject = myMavenTree.findProject(artifact.getMavenId());

      if (depProject != null) {
        if (depProject == myMavenProject) continue;

        String moduleName = myMavenProjectToModuleName.get(depProject);

        if (moduleName == null || myMavenTree.isIgnored(depProject)) {
          MavenArtifact projectsArtifactInRepository = new MavenArtifact(
            artifact.getGroupId(),
            artifact.getArtifactId(),
            artifact.getVersion(),
            artifact.getBaseVersion(),
            dependencyType,
            artifact.getClassifier(),
            artifact.getScope(),
            artifact.isOptional(),
            artifact.getExtension(),
            null,
            myMavenProject.getLocalRepository(),
            false, false
          );

          myRootModelAdapter.addLibraryDependency(projectsArtifactInRepository, scope, myModifiableModelsProvider, myMavenProject);
        }
        else {
          boolean isTestJar = MavenConstants.TYPE_TEST_JAR.equals(dependencyType) || "tests".equals(artifact.getClassifier());
          myRootModelAdapter.addModuleDependency(moduleName, scope, isTestJar);

          Element buildHelperCfg = depProject.getPluginGoalConfiguration("org.codehaus.mojo", "build-helper-maven-plugin", "attach-artifact");
          if (buildHelperCfg != null) {
            addAttachArtifactDependency(buildHelperCfg, scope, depProject, artifact);
          }

          if (IMPORTED_CLASSIFIERS.contains(artifact.getClassifier())
              && !isTestJar
              && !"system".equals(artifact.getScope())
              && !"false".equals(System.getProperty("idea.maven.classifier.dep"))) {
            MavenArtifact a = new MavenArtifact(
              artifact.getGroupId(),
              artifact.getArtifactId(),
              artifact.getVersion(),
              artifact.getBaseVersion(),
              dependencyType,
              artifact.getClassifier(),
              artifact.getScope(),
              artifact.isOptional(),
              artifact.getExtension(),
              null,
              myMavenProject.getLocalRepository(),
              false, false
            );

            myRootModelAdapter.addLibraryDependency(a, scope, myModifiableModelsProvider, myMavenProject);
          }
        }
      }
      else if ("system".equals(artifact.getScope())) {
        myRootModelAdapter.addSystemDependency(artifact, scope);
      }
      else {
        LibraryOrderEntry libraryOrderEntry =
          myRootModelAdapter.addLibraryDependency(artifact, scope, myModifiableModelsProvider, myMavenProject);
        myModifiableModelsProvider.trySubstitute(
          myModule, libraryOrderEntry, new ProjectId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
      }
    }

    configSurefirePlugin();
  }

  private void configSurefirePlugin() {
    // Remove "maven-surefire-plugin urls" library created by previous version of IDEA.
    // todo remove this code after 01.06.2013
    LibraryTable moduleLibraryTable = myRootModelAdapter.getRootModel().getModuleLibraryTable();

    Library library = moduleLibraryTable.getLibraryByName(SUREFIRE_PLUGIN_LIBRARY_NAME);
    if (library != null) {
      moduleLibraryTable.removeLibrary(library);
    }
  }

  private void addAttachArtifactDependency(@NotNull Element buildHelperCfg,
                                           @NotNull DependencyScope scope,
                                           @NotNull MavenProject mavenProject,
                                           @NotNull MavenArtifact artifact) {
    Library.ModifiableModel libraryModel = null;

    for (Element artifactsElement : buildHelperCfg.getChildren("artifacts")) {
      for (Element artifactElement : artifactsElement.getChildren("artifact")) {
        String typeString = artifactElement.getChildTextTrim("type");
        if (typeString != null && !typeString.equals("jar")) continue;

        OrderRootType rootType = OrderRootType.CLASSES;

        String classifier = artifactElement.getChildTextTrim("classifier");
        if ("sources".equals(classifier)) {
          rootType = OrderRootType.SOURCES;
        }
        else if ("javadoc".equals(classifier)) {
          rootType = JavadocOrderRootType.getInstance();
        }

        String filePath = artifactElement.getChildTextTrim("file");
        if (StringUtil.isEmpty(filePath)) continue;

        VirtualFile file = VfsUtil.findRelativeFile(filePath, mavenProject.getDirectoryFile());
        if (file == null) continue;

        file = JarFileSystem.getInstance().getJarRootForLocalFile(file);
        if (file == null) continue;

        if (libraryModel == null) {
          String libraryName = getAttachedJarsLibName(artifact);

          Library library = myModifiableModelsProvider.getLibraryByName(libraryName);
          if (library == null) {
            library = myModifiableModelsProvider.createLibrary(libraryName, MavenRootModelAdapter.getMavenExternalSource());
          }
          libraryModel = myModifiableModelsProvider.getModifiableLibraryModel(library);

          LibraryOrderEntry entry = myRootModelAdapter.getRootModel().addLibraryEntry(library);
          entry.setScope(scope);
        }

        libraryModel.addRoot(file, rootType);
      }
    }
  }

  @NotNull
  public static String getAttachedJarsLibName(@NotNull MavenArtifact artifact) {
    String libraryName = artifact.getLibraryName();
    assert libraryName.startsWith(MavenArtifact.MAVEN_LIB_PREFIX);
    libraryName = MavenArtifact.MAVEN_LIB_PREFIX + "ATTACHED-JAR: " + libraryName.substring(MavenArtifact.MAVEN_LIB_PREFIX.length());
    return libraryName;
  }

  @NotNull
  public static DependencyScope selectScope(String mavenScope) {
    if (MavenConstants.SCOPE_RUNTIME.equals(mavenScope)) return DependencyScope.RUNTIME;
    if (MavenConstants.SCOPE_TEST.equals(mavenScope)) return DependencyScope.TEST;
    if (MavenConstants.SCOPE_PROVIDED.equals(mavenScope)) return DependencyScope.PROVIDED;
    return DependencyScope.COMPILE;
  }

  private void configLanguageLevel() {
    if ("false".equalsIgnoreCase(System.getProperty("idea.maven.configure.language.level"))) return;

    LanguageLevel level = null;

    Element cfg = myMavenProject.getPluginConfiguration("com.googlecode", "maven-idea-plugin");
    if (cfg != null) {
      level = MAVEN_IDEA_PLUGIN_LEVELS.get(cfg.getChildTextTrim("jdkLevel"));
    }

    if (level == null) {
      level = LanguageLevel.parse(myMavenProject.getSourceLevel());
    }

    // default source and target settings of maven-compiler-plugin is 1.5, see details at http://maven.apache.org/plugins/maven-compiler-plugin
    if (level == null) {
      level = LanguageLevel.JDK_1_5;
    }

    myRootModelAdapter.setLanguageLevel(level);
  }
}
