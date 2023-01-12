// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.externalSystem.model.project.ProjectId;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.tree.MavenTreeModuleImportData;
import org.jetbrains.idea.maven.importing.tree.dependency.*;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MavenLegacyModuleImporter {
  public static final String SUREFIRE_PLUGIN_LIBRARY_NAME = "maven-surefire-plugin urls";
  public static final Set<String> IMPORTED_CLASSIFIERS = Set.of("client");

  private static final Map<String, LanguageLevel> MAVEN_IDEA_PLUGIN_LEVELS = ImmutableMap.of(
    "JDK_1_3", LanguageLevel.JDK_1_3,
    "JDK_1_4", LanguageLevel.JDK_1_4,
    "JDK_1_5", LanguageLevel.JDK_1_5,
    "JDK_1_6", LanguageLevel.JDK_1_6,
    "JDK_1_7", LanguageLevel.JDK_1_7);

  private final Module myModule;
  private final MavenProjectsTree myMavenTree;
  private final MavenProject myMavenProject;

  private final Map<MavenProject, String> myMavenProjectToModuleName;
  private final MavenImportingSettings mySettings;
  private final ModifiableModelsProviderProxy myModifiableModelsProvider;
  @Nullable
  private MavenRootModelAdapter myRootModelAdapter;

  public MavenLegacyModuleImporter(Module module,
                                   MavenProjectsTree mavenTree,
                                   MavenProject mavenProject,
                                   Map<MavenProject, String> mavenProjectToModuleName,
                                   MavenImportingSettings settings,
                                   ModifiableModelsProviderProxy modifiableModelsProvider) {
    myModule = module;
    myMavenTree = mavenTree;
    myMavenProject = mavenProject;
    myMavenProjectToModuleName = mavenProjectToModuleName;
    mySettings = settings;
    myModifiableModelsProvider = modifiableModelsProvider;
    VirtualFile pomFile = mavenProject.getFile();
    if (!FileUtil.namesEqual("pom", pomFile.getNameWithoutExtension())) {
      MavenPomPathModuleService.getInstance(module).setPomFileUrl(pomFile.getUrl());
    }
  }

  public void config(MavenRootModelAdapter mavenRootModelAdapter) {
    myRootModelAdapter = mavenRootModelAdapter;

    configFolders();
    configDependencies();
    configLanguageLevel();
  }

  public void config(MavenRootModelAdapter mavenRootModelAdapter, MavenTreeModuleImportData importData) {
    myRootModelAdapter = mavenRootModelAdapter;

    configFolders();
    configDependencies(importData.getDependencies());
    LanguageLevel level = MavenImportUtil.getLanguageLevel(myMavenProject, () -> importData.getModuleData().getSourceLanguageLevel());
    configLanguageLevel(level);
  }

  public void configMainAndTestAggregator(MavenRootModelAdapter mavenRootModelAdapter, MavenTreeModuleImportData importData) {
    assert importData.getModuleData().getType() == StandardMavenModuleType.COMPOUND_MODULE;
    myRootModelAdapter = mavenRootModelAdapter;

    new MavenLegacyFoldersImporter(myMavenProject, mySettings, myRootModelAdapter).configMainAndTestAggregator();
    configDependencies(importData.getDependencies());
    LanguageLevel level = MavenImportUtil.getLanguageLevel(myMavenProject, () -> importData.getModuleData().getSourceLanguageLevel());
    configLanguageLevel(level);
  }

  public void configMainAndTest(MavenRootModelAdapter mavenRootModelAdapter, MavenTreeModuleImportData importData) {
    StandardMavenModuleType type = importData.getModuleData().getType();
    assert type == StandardMavenModuleType.MAIN_ONLY || type == StandardMavenModuleType.TEST_ONLY;
    myRootModelAdapter = mavenRootModelAdapter;
    new MavenLegacyFoldersImporter(myMavenProject, mySettings, myRootModelAdapter).configMainAndTest(type);
    configDependencies(importData.getDependencies());
    LanguageLevel level = MavenImportUtil.getLanguageLevel(myMavenProject, () -> importData.getModuleData().getSourceLanguageLevel());
    configLanguageLevel(level);
  }

  public static class ExtensionImporter {
    private final Module myModule;
    private final MavenProjectsTree myMavenProjectsTree;
    private final MavenProject myMavenProject;
    private final MavenProjectChanges myMavenProjectChanges;
    private final Map<MavenProject, String> myMavenProjectToModuleName;
    @NotNull private final List<MavenImporter> myImporters;

    private MavenRootModelAdapter myRootModelAdapter;
    private IdeModifiableModelsProvider myModifiableModelsProvider;

    @Nullable
    public static MavenLegacyModuleImporter.ExtensionImporter createIfApplicable(@NotNull MavenProject mavenProject,
                                                                                 @NotNull Module module,
                                                                                 @NotNull StandardMavenModuleType moduleType,
                                                                                 @NotNull MavenProjectsTree mavenTree,
                                                                                 @NotNull MavenProjectChanges changes,
                                                                                 @NotNull Map<MavenProject, String> mavenProjectToModuleName,
                                                                                 boolean isWorkspaceImport) {
      if (moduleType == StandardMavenModuleType.COMPOUND_MODULE) return null;

      var suitableImporters = MavenImporter.getSuitableImporters(mavenProject, isWorkspaceImport);

      // We must run all importers when we import into Workspace Model:
      //  in Workspace model the project is recreated from scratch. But for the importers for which processChangedModulesOnly = true,
      //  we don't know whether they rely on the fact, that previously imported data is kept in the project model on reimport.
      if (!isWorkspaceImport && !changes.hasChanges()) {
        suitableImporters = ContainerUtil.filter(suitableImporters, (it) -> !it.processChangedModulesOnly());
      }

      if (suitableImporters.isEmpty()) return null;

      return new ExtensionImporter(module, mavenTree, mavenProject, changes, mavenProjectToModuleName, suitableImporters);
    }

    private ExtensionImporter(@NotNull Module module,
                              @NotNull MavenProjectsTree mavenTree,
                              @NotNull MavenProject mavenProject,
                              @NotNull MavenProjectChanges changes,
                              @NotNull Map<MavenProject, String> mavenProjectToModuleName,
                              @NotNull List<MavenImporter> importers) {

      myModule = module;
      myMavenProject = mavenProject;
      myMavenProjectsTree = mavenTree;
      myMavenProjectChanges = changes;
      myMavenProjectToModuleName = mavenProjectToModuleName;
      myImporters = importers;
    }

    boolean isModuleDisposed() {
      return myModule.isDisposed();
    }

    void init(@NotNull IdeModifiableModelsProvider ideModelsProvider) {
      myModifiableModelsProvider = ideModelsProvider;
      myRootModelAdapter = new MavenRootModelAdapter(
        new MavenRootModelAdapterLegacyImpl(myMavenProject, myModule,
                                            new ModifiableModelsProviderProxyWrapper(myModifiableModelsProvider)));
    }

    void preConfig(Map<Class<? extends MavenImporter>, CountAndTime> counters) {
      MavenUtil.invokeAndWaitWriteAction(myModule.getProject(), () -> {
        if (myModule.isDisposed()) return;

        final ModuleType moduleType = ModuleType.get(myModule);

        for (final MavenImporter importer : myImporters) {
          try {
            if (importer.getModuleType() == moduleType) {
              measureImporterTime(importer, counters, true, () -> {
                importer.preProcess(myModule, myMavenProject, myMavenProjectChanges, myModifiableModelsProvider);
              });
            }
          }
          catch (Exception e) {
            MavenLog.LOG.error("Exception in MavenImporter.preConfig, skipping it.", e);
          }
        }
      });
    }

    void config(final List<MavenProjectsProcessorTask> postTasks, Map<Class<? extends MavenImporter>, CountAndTime> counters) {
      MavenUtil.invokeAndWaitWriteAction(myModule.getProject(), () -> {
        if (myModule.isDisposed()) return;
        final ModuleType<?> moduleType = ModuleType.get(myModule);
        for (final MavenImporter importer : myImporters) {
          if (importer.getModuleType() == moduleType) {
            try {
              measureImporterTime(importer, counters, false, () -> {
                importer.process(myModifiableModelsProvider,
                                 myModule,
                                 myRootModelAdapter,
                                   myMavenProjectsTree,
                                   myMavenProject,
                                   myMavenProjectChanges,
                                   myMavenProjectToModuleName,
                                   postTasks);
                });
              }
              catch (Exception e) {
                MavenLog.LOG.error("Exception in MavenImporter.config, skipping it.", e);
              }
            }
          }
        });
    }

    void postConfig(Map<Class<? extends MavenImporter>, CountAndTime> counters) {
      MavenUtil.invokeAndWaitWriteAction(myModule.getProject(), () -> {
        if (myModule.isDisposed()) return;

        final ModuleType moduleType = ModuleType.get(myModule);

        for (final MavenImporter importer : myImporters) {
          try {
            if (importer.getModuleType() == moduleType) {
              measureImporterTime(importer, counters, false, () -> {
                importer.postProcess(myModule, myMavenProject, myMavenProjectChanges, myModifiableModelsProvider);
              });
            }
          }
          catch (Exception e) {
            MavenLog.LOG.error("Exception in MavenImporter.postConfig, skipping it.", e);
          }
        }
      });
    }

    private static void measureImporterTime(MavenImporter importer,
                                            Map<Class<? extends MavenImporter>, CountAndTime> counters,
                                            boolean increaseModuleCounter,
                                            Runnable r) {
      long before = System.nanoTime();
      try {
        r.run();
      }
      finally {
        CountAndTime countAndTime = counters.computeIfAbsent(importer.getClass(), __ -> new CountAndTime());
        if (increaseModuleCounter) countAndTime.count++;
        countAndTime.timeNano += System.nanoTime() - before;
      }
    }

    static class CountAndTime {
      int count = 0;
      long timeNano = 0;
    }
  }

  private void configFolders() {
    new MavenLegacyFoldersImporter(myMavenProject, mySettings, myRootModelAdapter).config();
  }

  private void configDependencies() {
    Set<String> dependencyTypesFromSettings = new HashSet<>();

    if (!ReadAction.compute(() -> {
      if (myModule.getProject().isDisposed()) return false;

      dependencyTypesFromSettings.addAll(
        MavenProjectsManager.getInstance(myModule.getProject()).getImportingSettings().getDependencyTypesAsSet());
      return true;
    })) {
      return;
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
          MavenArtifact projectsArtifactInRepository = createCopyForLocalRepo(artifact, myMavenProject);

          myRootModelAdapter.addLibraryDependency(projectsArtifactInRepository, scope, myModifiableModelsProvider, myMavenProject);
        }
        else {
          boolean isTestJar = MavenConstants.TYPE_TEST_JAR.equals(dependencyType) || "tests".equals(artifact.getClassifier());
          myRootModelAdapter.addModuleDependency(moduleName, scope, isTestJar);

          Element buildHelperCfg = depProject.getPluginGoalConfiguration("org.codehaus.mojo", "build-helper-maven-plugin", "attach-artifact");
          if (buildHelperCfg != null) {
            addAttachArtifactDependency(buildHelperCfg, scope, depProject, artifact);
          }

          String classifier = artifact.getClassifier();
          if (classifier != null && IMPORTED_CLASSIFIERS.contains(classifier)
              && !isTestJar
              && !"system".equals(artifact.getScope())
              && !"false".equals(System.getProperty("idea.maven.classifier.dep"))) {
            MavenArtifact a = createCopyForLocalRepo(artifact, myMavenProject);

            myRootModelAdapter.addLibraryDependency(a, scope, myModifiableModelsProvider, myMavenProject);
          }
        }
      }
      else if ("system".equals(artifact.getScope())) {
        myRootModelAdapter.addSystemDependency(artifact, scope);
      }
      else {
        if ("bundle".equals(dependencyType)) {
          artifact = new MavenArtifact(
            artifact.getGroupId(),
            artifact.getArtifactId(),
            artifact.getVersion(),
            artifact.getBaseVersion(),
            "jar",
            artifact.getClassifier(),
            artifact.getScope(),
            artifact.isOptional(),
            "jar",
            null,
            myMavenProject.getLocalRepository(),
            false, false
          );
        }
        LibraryOrderEntry libraryOrderEntry =
          myRootModelAdapter.addLibraryDependency(artifact, scope, myModifiableModelsProvider, myMavenProject);
        myModifiableModelsProvider.trySubstitute(
          myModule, libraryOrderEntry, new ProjectId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
      }
    }

    configSurefirePlugin();
  }

  public void configDependencies(@NotNull List<MavenImportDependency<?>> dependencies) {
    for (MavenImportDependency<?> dependency : dependencies) {
      if (dependency instanceof SystemDependency) {
        myRootModelAdapter.addSystemDependency(((SystemDependency)dependency).getArtifact(), dependency.getScope());
      }
      else if (dependency instanceof LibraryDependency) {
        myRootModelAdapter.addLibraryDependency(((LibraryDependency)dependency).getArtifact(), dependency.getScope(),
                                                myModifiableModelsProvider, myMavenProject);
      }
      else if (dependency instanceof ModuleDependency) {
        ModuleDependency moduleDependency = (ModuleDependency)dependency;
        myRootModelAdapter.addModuleDependency(moduleDependency.getArtifact(), dependency.getScope(), moduleDependency.isTestJar());
      }
      else if (dependency instanceof BaseDependency) {
        MavenArtifact artifact = ((BaseDependency)dependency).getArtifact();
        LibraryOrderEntry libraryOrderEntry =
          myRootModelAdapter.addLibraryDependency(artifact, dependency.getScope(), myModifiableModelsProvider, myMavenProject);
        myModifiableModelsProvider.trySubstitute(
          myModule, libraryOrderEntry, new ProjectId(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()));
      }
    }
  }

  @NotNull
  public static MavenArtifact createCopyForLocalRepo(@NotNull MavenArtifact artifact, @NotNull MavenProject project) {
    return new MavenArtifact(
      artifact.getGroupId(),
      artifact.getArtifactId(),
      artifact.getVersion(),
      artifact.getBaseVersion(),
      artifact.getType(),
      artifact.getClassifier(),
      artifact.getScope(),
      artifact.isOptional(),
      artifact.getExtension(),
      null,
      project.getLocalRepository(),
      false, false
    );
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

  //TODO: Rewrite
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

        if (libraryModel != null) {
          libraryModel.addRoot(file, rootType);
        }
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

    LanguageLevel level = getLanguageLevel(myMavenProject);
    myRootModelAdapter.setLanguageLevel(level);
  }

  private void configLanguageLevel(@NotNull LanguageLevel level) {
    if ("false".equalsIgnoreCase(System.getProperty("idea.maven.configure.language.level"))) return;
    myRootModelAdapter.setLanguageLevel(level);
  }

  /**
   * @deprecated use {@link MavenImportUtil#getSourceLanguageLevel(MavenProject)}
   */
  @Deprecated
  public static @NotNull LanguageLevel getLanguageLevel(MavenProject mavenProject) {
    return MavenImportUtil.getSourceLanguageLevel(mavenProject);
  }

  /**
   * @deprecated use {@link MavenImportUtil#getDefaultLevel(MavenProject)}
   */
  @Deprecated
  @NotNull
  public static LanguageLevel getDefaultLevel(MavenProject mavenProject) {
    return MavenImportUtil.getDefaultLevel(mavenProject);
  }
}
