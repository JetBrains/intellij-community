/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.pom.java.LanguageLevel;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.List;
import java.util.Map;

public class MavenModuleImporter {
  private final Module myModule;
  private final MavenProjectsTree myMavenTree;
  private final MavenProject myMavenProject;

  @Nullable
  private final MavenProjectChanges myMavenProjectChanges;
  private final Map<MavenProject, String> myMavenProjectToModuleName;
  private final MavenImportingSettings mySettings;
  private final MavenModifiableModelsProvider myModifiableModelsProvider;
  private MavenRootModelAdapter myRootModelAdapter;

  public MavenModuleImporter(Module module,
                             MavenProjectsTree mavenTree,
                             MavenProject mavenProject,
                             @Nullable MavenProjectChanges changes,
                             Map<MavenProject, String> mavenProjectToModuleName,
                             MavenImportingSettings settings,
                             MavenModifiableModelsProvider modifiableModelsProvider) {
    myModule = module;
    myMavenTree = mavenTree;
    myMavenProject = mavenProject;
    myMavenProjectChanges = changes;
    myMavenProjectToModuleName = mavenProjectToModuleName;
    mySettings = settings;
    myModifiableModelsProvider = modifiableModelsProvider;
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
    configEncoding();
  }

  public void preConfigFacets() {
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
        // facets use FacetConfiguration and like that do not have modifiable models,
        // therefore we have to take write lock
        MavenUtil.invokeAndWaitWriteAction(myModule.getProject(), new Runnable() {
          public void run() {
            importer.preProcess(myModule, myMavenProject, changes, myModifiableModelsProvider);
          }
        });
      }
    }
  }

  public void configFacets(final List<MavenProjectsProcessorTask> postTasks) {
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
        // facets use FacetConfiguration and like that do not have modifiable models,
        // therefore we have to take write lock
        MavenUtil.invokeAndWaitWriteAction(myModule.getProject(), new Runnable() {
          public void run() {
            importer.process(myModifiableModelsProvider,
                             myModule,
                             myRootModelAdapter,
                             myMavenTree,
                             myMavenProject,
                             changes,
                             myMavenProjectToModuleName,
                             postTasks);
          }
        });
      }
    }
  }

  private List<MavenImporter> getSuitableImporters() {
    return myMavenProject.getSuitableImporters();
  }

  private void configFolders() {
    new MavenFoldersImporter(myMavenProject, mySettings, myRootModelAdapter).config();
  }

  private void configDependencies() {
    for (MavenArtifact artifact : myMavenProject.getDependencies()) {
      if (!myMavenProject.isSupportedDependency(artifact, SupportedRequestType.FOR_IMPORT)) continue;

      DependencyScope scope = selectScope(artifact.getScope());
      MavenProject depProject = myMavenTree.findProject(artifact.getMavenId());

      if (depProject != null) {
        if (depProject == myMavenProject) continue;
        myRootModelAdapter.addModuleDependency(myMavenProjectToModuleName.get(depProject), scope);

        Element buildHelperCfg = depProject.getPluginGoalConfiguration("org.codehaus.mojo", "build-helper-maven-plugin", "attach-artifact");
        if (buildHelperCfg != null) {
          addAttachArtifactDependency(buildHelperCfg, scope, depProject, artifact);
        }
      }
      else {
        myRootModelAdapter.addLibraryDependency(artifact, scope, myModifiableModelsProvider, myMavenProject);
      }
    }
  }

  private void addAttachArtifactDependency(@NotNull Element buildHelperCfg,
                                           @NotNull DependencyScope scope,
                                           @NotNull MavenProject mavenProject,
                                           @NotNull MavenArtifact artifact) {
    Library.ModifiableModel libraryModel = null;

    for (Element artifactsElement : (List<Element>)buildHelperCfg.getChildren("artifacts")) {
      for (Element artifactElement : (List<Element>)artifactsElement.getChildren("artifact")) {
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
          String libraryName = artifact.getLibraryName();
          assert libraryName.startsWith(MavenArtifact.MAVEN_LIB_PREFIX);
          libraryName = MavenArtifact.MAVEN_LIB_PREFIX + "ATTACHED-JAR: " + libraryName.substring(MavenArtifact.MAVEN_LIB_PREFIX.length());

          Library library = myModifiableModelsProvider.getLibraryByName(libraryName);
          if (library == null) {
            library = myModifiableModelsProvider.createLibrary(libraryName);
          }
          libraryModel = myModifiableModelsProvider.getLibraryModel(library);

          LibraryOrderEntry entry = myRootModelAdapter.getRootModel().addLibraryEntry(library);
          entry.setScope(scope);
        }

        libraryModel.addRoot(file, rootType);
      }
    }
  }

  @NotNull
  private static DependencyScope selectScope(String mavenScope) {
    if (MavenConstants.SCOPE_RUNTIME.equals(mavenScope)) return DependencyScope.RUNTIME;
    if (MavenConstants.SCOPE_TEST.equals(mavenScope)) return DependencyScope.TEST;
    if (MavenConstants.SCOPE_PROVIDEED.equals(mavenScope)) return DependencyScope.PROVIDED;
    return DependencyScope.COMPILE;
  }

  private void configEncoding() {
    if (Boolean.parseBoolean(System.getProperty("maven.disable.encode.import"))) return;

    String encoding = myMavenProject.getEncoding();
    if (encoding != null) {
      try {
        EncodingProjectManager.getInstance(myModule.getProject()).setEncoding(myMavenProject.getDirectoryFile(), Charset.forName(encoding));
      }
      catch (UnsupportedCharsetException ignored) {/**/}
      catch (IllegalCharsetNameException ignored) {/**/}
    }
  }

  private void configLanguageLevel() {
    final LanguageLevel level = LanguageLevel.parse(myMavenProject.getSourceLevel());
    myRootModelAdapter.setLanguageLevel(level);
  }
}
