// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.plugins.api.MavenModelPropertiesPatcher;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenPathWrapper;

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

class MavenProjectState implements Cloneable, Serializable {
  private long myLastReadStamp = 0;

  private MavenId myMavenId;
  private MavenId myParentId;
  private String myPackaging;
  private String myName;

  private String myFinalName;
  private String myDefaultGoal;

  private String myBuildDirectory;
  private String myOutputDirectory;
  private String myTestOutputDirectory;

  private List<String> mySources;
  private List<String> myTestSources;
  private List<MavenResource> myResources;
  private List<MavenResource> myTestResources;

  private List<String> myFilters;
  private Properties myProperties;
  private List<MavenPlugin> myPlugins;
  private List<MavenArtifact> myExtensions;

  private List<MavenArtifact> myDependencies;
  private List<MavenArtifactNode> myDependencyTree;
  private List<MavenRemoteRepository> myRemoteRepositories;
  private List<MavenArtifact> myAnnotationProcessors;

  private Map<String, String> myModulesPathsAndNames;

  private Map<String, String> myModelMap;

  private Collection<String> myProfilesIds;
  private MavenExplicitProfiles myActivatedProfilesIds;
  private String myDependencyHash;

  private Collection<MavenProjectProblem> myReadingProblems;
  private Set<MavenId> myUnresolvedArtifactIds;
  private File myLocalRepository;

  private volatile List<MavenProjectProblem> myProblemsCache;
  private volatile List<MavenArtifact> myUnresolvedDependenciesCache;
  private volatile List<MavenPlugin> myUnresolvedPluginsCache;
  private volatile List<MavenArtifact> myUnresolvedExtensionsCache;
  private volatile List<MavenArtifact> myUnresolvedAnnotationProcessors;

  private transient ConcurrentHashMap<Key<?>, Object> myCache = new ConcurrentHashMap<>();

  long getLastReadStamp() {
    return myLastReadStamp;
  }

  MavenId getMavenId() {
    return myMavenId;
  }

  MavenId getParentId() {
    return myParentId;
  }

  String getPackaging() {
    return myPackaging;
  }

  String getName() {
    return myName;
  }

  String getFinalName() {
    return myFinalName;
  }

  String getDefaultGoal() {
    return myDefaultGoal;
  }

  String getBuildDirectory() {
    return myBuildDirectory;
  }

  String getOutputDirectory() {
    return myOutputDirectory;
  }

  String getTestOutputDirectory() {
    return myTestOutputDirectory;
  }

  List<String> getSources() {
    return mySources;
  }

  List<String> getTestSources() {
    return myTestSources;
  }

  List<MavenResource> getResources() {
    return myResources;
  }

  List<MavenResource> getTestResources() {
    return myTestResources;
  }

  List<String> getFilters() {
    return myFilters;
  }

  Properties getProperties() {
    return myProperties;
  }

  List<MavenPlugin> getPlugins() {
    return myPlugins;
  }

  List<MavenArtifact> getDependencies() {
    return myDependencies;
  }

  List<MavenArtifactNode> getDependencyTree() {
    return myDependencyTree;
  }

  List<MavenRemoteRepository> getRemoteRepositories() {
    return myRemoteRepositories;
  }

  List<MavenArtifact> getAnnotationProcessors() {
    return myAnnotationProcessors;
  }

  Map<String, String> getModulesPathsAndNames() {
    return myModulesPathsAndNames;
  }

  Map<String, String> getModelMap() {
    return myModelMap;
  }

  Collection<String> getProfilesIds() {
    return myProfilesIds;
  }

  MavenExplicitProfiles getActivatedProfilesIds() {
    return myActivatedProfilesIds;
  }

  String getDependencyHash() {
    return myDependencyHash;
  }

  Collection<MavenProjectProblem> getReadingProblems() {
    return myReadingProblems;
  }

  File getLocalRepository() {
    return myLocalRepository;
  }

  List<MavenProjectProblem> getProblemsCache() {
    return myProblemsCache;
  }

  ConcurrentHashMap<Key<?>, Object> getCache() {
    return myCache;
  }

  @Override
  public MavenProjectState clone() {
    try {
      MavenProjectState result = (MavenProjectState)super.clone();
      myCache = new ConcurrentHashMap<>();
      result.resetCache();
      return result;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  void resetCache() {
    myProblemsCache = null;
    myUnresolvedDependenciesCache = null;
    myUnresolvedPluginsCache = null;
    myUnresolvedExtensionsCache = null;
    myUnresolvedAnnotationProcessors = null;

    myCache.clear();
  }

  public MavenProjectChanges getChanges(MavenProjectState newState) {
    if (myLastReadStamp == 0) return MavenProjectChanges.ALL;

    MavenProjectChangesBuilder result = new MavenProjectChangesBuilder();

    result.setHasPackagingChanges(!Objects.equals(myPackaging, newState.myPackaging));

    result.setHasOutputChanges(!Objects.equals(myFinalName, newState.myFinalName)
                               || !Objects.equals(myBuildDirectory, newState.myBuildDirectory)
                               || !Objects.equals(myOutputDirectory, newState.myOutputDirectory)
                               || !Objects.equals(myTestOutputDirectory, newState.myTestOutputDirectory));

    result.setHasSourceChanges(!Comparing.equal(mySources, newState.mySources)
                               || !Comparing.equal(myTestSources, newState.myTestSources)
                               || !Comparing.equal(myResources, newState.myResources)
                               || !Comparing.equal(myTestResources, newState.myTestResources));

    boolean repositoryChanged = !Comparing.equal(myLocalRepository, newState.myLocalRepository);

    result.setHasDependencyChanges(repositoryChanged || !Comparing.equal(myDependencies, newState.myDependencies));
    result.setHasPluginChanges(repositoryChanged || !Comparing.equal(myPlugins, newState.myPlugins));
    result.setHasPropertyChanges(!Comparing.equal(myProperties, newState.myProperties));
    return result;
  }

  void doUpdateState(@NotNull MavenModel model,
                     @NotNull Collection<@NotNull MavenProjectProblem> readingProblems,
                     @NotNull MavenExplicitProfiles activatedProfiles,
                     @NotNull Set<MavenId> unresolvedArtifactIds,
                     @NotNull Map<@NotNull String, @Nullable String> nativeModelMap,
                     @NotNull MavenGeneralSettings settings,
                     boolean keepPreviousArtifacts,
                     boolean keepPreviousProfiles,
                     boolean keepPreviousPlugins,
                     @NotNull String directory,
                     @Nullable String fileExtension) {
    myReadingProblems = readingProblems;
    myLocalRepository = settings.getEffectiveLocalRepository();
    myActivatedProfilesIds = activatedProfiles;

    myMavenId = model.getMavenId();
    if (model.getParent() != null) {
      myParentId = model.getParent().getMavenId();
    }

    myPackaging = model.getPackaging();
    myName = model.getName();

    myFinalName = model.getBuild().getFinalName();
    myDefaultGoal = model.getBuild().getDefaultGoal();

    myBuildDirectory = model.getBuild().getDirectory();
    myOutputDirectory = model.getBuild().getOutputDirectory();
    myTestOutputDirectory = model.getBuild().getTestOutputDirectory();

    doSetFolders(model.getBuild());

    myFilters = model.getBuild().getFilters();
    myProperties = model.getProperties();

    doSetResolvedAttributes(model, unresolvedArtifactIds, keepPreviousArtifacts, keepPreviousPlugins);

    MavenModelPropertiesPatcher.patch(myProperties, myPlugins);

    myModulesPathsAndNames = collectModulePathsAndNames(model, directory, fileExtension);
    Collection<String> newProfiles = collectProfilesIds(model.getProfiles());
    if (keepPreviousProfiles && myProfilesIds != null) {
      Set<String> mergedProfiles = new HashSet<>(myProfilesIds);
      mergedProfiles.addAll(newProfiles);
      myProfilesIds = new ArrayList<>(mergedProfiles);
    }
    else {
      myProfilesIds = newProfiles;
    }

    myModelMap = nativeModelMap;
  }

  private Map<String, String> collectModulePathsAndNames(MavenModel mavenModel, String baseDir, String fileExtension) {
    String basePath = baseDir + "/";
    Map<String, String> result = new LinkedHashMap<>();
    for (Map.Entry<String, String> each : collectModulesRelativePathsAndNames(mavenModel, basePath, fileExtension).entrySet()) {
      result.put(new MavenPathWrapper(basePath + each.getKey()).getPath(), each.getValue());
    }
    return result;
  }


  private Map<String, String> collectModulesRelativePathsAndNames(MavenModel mavenModel, String basePath, String fileExtension) {
    String extension = StringUtil.notNullize(fileExtension);
    LinkedHashMap<String, String> result = new LinkedHashMap<>();
    for (String name : mavenModel.getModules()) {
      name = name.trim();

      if (name.length() == 0) continue;

      String originalName = name;
      // module name can be relative and contain either / of \\ separators

      name = FileUtil.toSystemIndependentName(name);

      String finalName = name;
      boolean fullPathInModuleName = ContainerUtil.exists(MavenConstants.POM_EXTENSIONS, ext -> finalName.endsWith('.' + ext));
      if (!fullPathInModuleName) {
        if (!name.endsWith("/")) name += "/";
        name += MavenConstants.POM_EXTENSION + '.' + extension;
      }
      else {
        String systemDependentName = FileUtil.toSystemDependentName(basePath + name);
        if (new File(systemDependentName).isDirectory()) {
          name += "/" + MavenConstants.POM_XML;
        }
      }

      result.put(name, originalName);
    }
    return result;
  }

  private void doSetResolvedAttributes(MavenModel model,
                                       Set<MavenId> unresolvedArtifactIds,
                                       boolean keepPreviousArtifacts,
                                       boolean keepPreviousPlugins) {
    Set<MavenId> newUnresolvedArtifacts = new HashSet<>();
    LinkedHashSet<MavenRemoteRepository> newRepositories = new LinkedHashSet<>();
    LinkedHashSet<MavenArtifact> newDependencies = new LinkedHashSet<>();
    LinkedHashSet<MavenArtifactNode> newDependencyTree = new LinkedHashSet<>();
    LinkedHashSet<MavenPlugin> newPlugins = new LinkedHashSet<>();
    LinkedHashSet<MavenArtifact> newExtensions = new LinkedHashSet<>();
    LinkedHashSet<MavenArtifact> newAnnotationProcessors = new LinkedHashSet<>();

    if (keepPreviousArtifacts) {
      if (myUnresolvedArtifactIds != null) newUnresolvedArtifacts.addAll(myUnresolvedArtifactIds);
      if (myRemoteRepositories != null) newRepositories.addAll(myRemoteRepositories);
      if (myDependencies != null) newDependencies.addAll(myDependencies);
      if (myDependencyTree != null) newDependencyTree.addAll(myDependencyTree);
      if (myExtensions != null) newExtensions.addAll(myExtensions);
      if (myAnnotationProcessors != null) newAnnotationProcessors.addAll(myAnnotationProcessors);
    }

    if (keepPreviousPlugins) {
      if (myPlugins != null) newPlugins.addAll(myPlugins);
    }

    newUnresolvedArtifacts.addAll(unresolvedArtifactIds);
    newRepositories.addAll(model.getRemoteRepositories());
    newDependencyTree.addAll(model.getDependencyTree());
    newDependencies.addAll(model.getDependencies());
    newPlugins.addAll(model.getPlugins());
    newExtensions.addAll(model.getExtensions());

    myUnresolvedArtifactIds = newUnresolvedArtifacts;
    myRemoteRepositories = new ArrayList<>(newRepositories);
    myDependencies = new ArrayList<>(newDependencies);
    myDependencyTree = new ArrayList<>(newDependencyTree);
    myPlugins = new ArrayList<>(newPlugins);
    myExtensions = new ArrayList<>(newExtensions);
    myAnnotationProcessors = new ArrayList<>(newAnnotationProcessors);
  }

  void incLastReadStamp() {
    myLastReadStamp++;
  }

  void setDependencyHash(@Nullable String dependencyHash) {
    this.myDependencyHash = dependencyHash;
  }

  void setReadingProblems(@NotNull Collection<MavenProjectProblem> readingProblems) {
    this.myReadingProblems = readingProblems;
  }

  private static Collection<String> collectProfilesIds(Collection<MavenProfile> profiles) {
    if (profiles == null) return Collections.emptyList();

    Set<String> result = new HashSet<>(profiles.size());
    for (MavenProfile each : profiles) {
      result.add(each.getId());
    }
    return result;
  }

  private void doSetFolders(MavenBuild build) {
    doSetFolders(build.getSources(), build.getTestSources(), build.getResources(), build.getTestResources());
  }

  void doSetFolders(List<String> sources,
                           List<String> testSources,
                           List<MavenResource> resources,
                           List<MavenResource> testResources) {
    mySources = sources;
    myTestSources = testSources;

    myResources = resources;
    myTestResources = testResources;
  }

  private List<MavenProjectProblem> doCollectProblems(VirtualFile file, Predicate<File> fileExistsPredicate) {
    List<MavenProjectProblem> result = new ArrayList<>();

    validateParent(file, result);
    result.addAll(myReadingProblems);

    for (Map.Entry<String, String> each : myModulesPathsAndNames.entrySet()) {
      if (LocalFileSystem.getInstance().findFileByPath(each.getKey()) == null) {
        result.add(createDependencyProblem(file, MavenProjectBundle.message("maven.project.problem.moduleNotFound", each.getValue())));
      }
    }

    validateDependencies(file, result, fileExistsPredicate);
    validateExtensions(file, result);
    validatePlugins(file, result);

    return result;
  }

  private void validateParent(VirtualFile file, List<MavenProjectProblem> result) {
    if (!isParentResolved()) {
      result.add(createDependencyProblem(file, MavenProjectBundle.message("maven.project.problem.parentNotFound", myParentId)));
    }
  }

  private void validateDependencies(VirtualFile file,
                                    List<MavenProjectProblem> result,
                                    Predicate<File> fileExistsPredicate) {
    for (MavenArtifact each : getUnresolvedDependencies(fileExistsPredicate)) {
      result.add(createDependencyProblem(file, MavenProjectBundle.message("maven.project.problem.unresolvedDependency",
                                                                          each.getDisplayStringWithType())));
    }
  }

  private void validateExtensions(VirtualFile file, List<MavenProjectProblem> result) {
    for (MavenArtifact each : getUnresolvedExtensions()) {
      result.add(createDependencyProblem(file, MavenProjectBundle.message("maven.project.problem.unresolvedExtension",
                                                                          each.getDisplayStringSimple())));
    }
  }

  private void validatePlugins(VirtualFile file, List<MavenProjectProblem> result) {
    for (MavenPlugin each : getUnresolvedPlugins()) {
      result.add(createDependencyProblem(file, MavenProjectBundle.message("maven.project.problem.unresolvedPlugin", each)));
    }
  }

  private static MavenProjectProblem createDependencyProblem(VirtualFile file, String description) {
    return new MavenProjectProblem(file.getPath(), description, MavenProjectProblem.ProblemType.DEPENDENCY, false);
  }

  private boolean isParentResolved() {
    return !myUnresolvedArtifactIds.contains(myParentId);
  }

  boolean hasUnresolvedArtifacts() {
    return !isParentResolved()
           || !getUnresolvedDependencies(null).isEmpty()
           || !getUnresolvedExtensions().isEmpty()
           || !getUnresolvedAnnotationProcessors().isEmpty();
  }

  private List<MavenArtifact> getUnresolvedDependencies(Predicate<File> fileExistsPredicate) {
    synchronized (this) {
      if (myUnresolvedDependenciesCache == null) {
        List<MavenArtifact> result = new ArrayList<>();
        for (MavenArtifact each : myDependencies) {
          boolean resolved = each.isResolved(fileExistsPredicate);
          each.setFileUnresolved(!resolved);
          if (!resolved) result.add(each);
        }
        myUnresolvedDependenciesCache = result;
      }
      return myUnresolvedDependenciesCache;
    }
  }

  private List<MavenArtifact> getUnresolvedExtensions() {
    synchronized (this) {
      if (myUnresolvedExtensionsCache == null) {
        List<MavenArtifact> result = new ArrayList<>();
        for (MavenArtifact each : myExtensions) {
          // Collect only extensions that were attempted to be resolved.
          // It is because embedder does not even try to resolve extensions that
          // are not necessary.
          if (myUnresolvedArtifactIds.contains(each.getMavenId())
              && !pomFileExists(myLocalRepository, each)) {
            result.add(each);
          }
        }
        myUnresolvedExtensionsCache = result;
      }
      return myUnresolvedExtensionsCache;
    }
  }

  private static boolean pomFileExists(File localRepository, MavenArtifact artifact) {
    return MavenArtifactUtil.hasArtifactFile(localRepository, artifact.getMavenId(), "pom");
  }

  private List<MavenArtifact> getUnresolvedAnnotationProcessors() {
    synchronized (this) {
      if (myUnresolvedAnnotationProcessors == null) {
        List<MavenArtifact> result = new ArrayList<>();
        for (MavenArtifact each : myAnnotationProcessors) {
          if (!each.isResolved()) result.add(each);
        }
        myUnresolvedAnnotationProcessors = result;
      }
      return myUnresolvedAnnotationProcessors;
    }
  }

  List<MavenPlugin> getUnresolvedPlugins() {
    synchronized (this) {
      if (myUnresolvedPluginsCache == null) {
        List<MavenPlugin> result = new ArrayList<>();
        for (MavenPlugin each : getDeclaredPlugins()) {
          if (!MavenArtifactUtil.hasArtifactFile(myLocalRepository, each.getMavenId())) {
            result.add(each);
          }
        }
        myUnresolvedPluginsCache = result;
      }
      return myUnresolvedPluginsCache;
    }
  }

  List<MavenPlugin> getDeclaredPlugins() {
    return ContainerUtil.findAll(myPlugins, mavenPlugin -> !mavenPlugin.isDefault());
  }

  @NotNull List<MavenProjectProblem> collectProblems(VirtualFile file, Predicate<File> fileExistsPredicate) {
    synchronized (this) {
      if (myProblemsCache == null) {
        myProblemsCache = doCollectProblems(file, fileExistsPredicate);
      }
      return myProblemsCache;
    }
  }

  void doUpdateState(
    @NotNull List<MavenArtifact> dependencies,
    @NotNull Properties properties,
    @NotNull List<MavenPlugin> plugins
  ) {
    myDependencies = dependencies;
    myProperties = properties;
    myPlugins.clear();
    myPlugins.addAll(plugins);
  }
}
