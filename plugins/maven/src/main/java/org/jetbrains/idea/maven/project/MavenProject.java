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
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType;
import org.jetbrains.idea.maven.importing.MavenImporter;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.*;

import java.io.*;
import java.util.*;

public class MavenProject {
  @NotNull private final VirtualFile myFile;
  @NotNull private volatile State myState = new State();

  @Nullable
  public static MavenProject read(DataInputStream in) throws IOException {
    String path = in.readUTF();
    int length = in.readInt();
    byte[] bytes = new byte[length];
    in.readFully(bytes);

    // should read full byte content first!!!

    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    if (file == null) return null;

    ByteArrayInputStream bs = new ByteArrayInputStream(bytes);
    ObjectInputStream os = new ObjectInputStream(bs);
    try {
      try {
        MavenProject result = new MavenProject(file);
        result.myState = (State)os.readObject();
        return result;
      }
      catch (ClassNotFoundException e) {
        IOException ioException = new IOException();
        ioException.initCause(e);
        throw ioException;
      }
    }
    finally {
      os.close();
      bs.close();
    }
  }

  public void write(@NotNull DataOutputStream out) throws IOException {
    out.writeUTF(getPath());

    BufferExposingByteArrayOutputStream bs = new BufferExposingByteArrayOutputStream();
    ObjectOutputStream os = new ObjectOutputStream(bs);
    try {
      os.writeObject(myState);

      out.writeInt(bs.size());
      out.write(bs.getInternalBuffer(), 0, bs.size());
    }
    finally {
      os.close();
      bs.close();
    }
  }

  public MavenProject(@NotNull VirtualFile file) {
    myFile = file;
  }

  @NotNull
  private MavenProjectChanges set(@NotNull MavenProjectReaderResult readerResult,
                                  @NotNull MavenGeneralSettings settings,
                                  boolean updateLastReadStamp,
                                  boolean resetArtifacts,
                                  boolean resetProfiles) {
    State newState = myState.clone();

    if (updateLastReadStamp) newState.myLastReadStamp = myState.myLastReadStamp + 1;

    newState.myReadingProblems = readerResult.readingProblems;
    newState.myLocalRepository = settings.getEffectiveLocalRepository();

    newState.myActivatedProfilesIds = readerResult.activatedProfiles;

    MavenModel model = readerResult.mavenModel;

    newState.myMavenId = model.getMavenId();
    if (model.getParent() != null) {
      newState.myParentId = model.getParent().getMavenId();
    }

    newState.myPackaging = model.getPackaging();
    newState.myName = model.getName();

    newState.myFinalName = model.getBuild().getFinalName();
    newState.myDefaultGoal = model.getBuild().getDefaultGoal();

    newState.myBuildDirectory = model.getBuild().getDirectory();
    newState.myOutputDirectory = model.getBuild().getOutputDirectory();
    newState.myTestOutputDirectory = model.getBuild().getTestOutputDirectory();

    doSetFolders(newState, readerResult);

    newState.myFilters = model.getBuild().getFilters();
    newState.myProperties = model.getProperties();

    doSetResolvedAttributes(newState, readerResult, resetArtifacts);

    newState.myModulesPathsAndNames = collectModulePathsAndNames(model, getDirectory());
    Collection<String> newProfiles = collectProfilesIds(model.getProfiles());
    if (resetProfiles || newState.myProfilesIds == null) {
      newState.myProfilesIds = newProfiles;
    }
    else {
      Set<String> mergedProfiles = new THashSet<String>(newState.myProfilesIds);
      mergedProfiles.addAll(newProfiles);
      newState.myProfilesIds = new ArrayList<String>(mergedProfiles);
    }

    newState.myModelMap = readerResult.nativeModelMap;

    return setState(newState);
  }

  private MavenProjectChanges setState(State newState) {
    MavenProjectChanges changes = myState.getChanges(newState);
    myState = newState;
    return changes;
  }

  private static void doSetResolvedAttributes(State state,
                                              MavenProjectReaderResult readerResult,
                                              boolean reset) {
    MavenModel model = readerResult.mavenModel;

    Set<MavenId> newUnresolvedArtifacts = new THashSet<MavenId>();
    LinkedHashSet<MavenRemoteRepository> newRepositories = new LinkedHashSet<MavenRemoteRepository>();
    LinkedHashSet<MavenArtifact> newDependencies = new LinkedHashSet<MavenArtifact>();
    LinkedHashSet<MavenArtifactNode> newDependencyTree = new LinkedHashSet<MavenArtifactNode>();
    LinkedHashSet<MavenPlugin> newPlugins = new LinkedHashSet<MavenPlugin>();
    LinkedHashSet<MavenArtifact> newExtensions = new LinkedHashSet<MavenArtifact>();

    if (!reset) {
      if (state.myUnresolvedArtifactIds != null) newUnresolvedArtifacts.addAll(state.myUnresolvedArtifactIds);
      if (state.myRemoteRepositories != null) newRepositories.addAll(state.myRemoteRepositories);
      if (state.myDependencies != null) newDependencies.addAll(state.myDependencies);
      if (state.myDependencyTree != null) newDependencyTree.addAll(state.myDependencyTree);
      if (state.myPlugins != null) newPlugins.addAll(state.myPlugins);
      if (state.myExtensions != null) newExtensions.addAll(state.myExtensions);
    }

    newUnresolvedArtifacts.addAll(readerResult.unresolvedArtifactIds);
    newRepositories.addAll(model.getRemoteRepositories());
    newDependencyTree.addAll(model.getDependencyTree());
    newDependencies.addAll(model.getDependencies());
    newPlugins.addAll(model.getPlugins());
    newExtensions.addAll(model.getExtensions());

    state.myUnresolvedArtifactIds = newUnresolvedArtifacts;
    state.myRemoteRepositories = new ArrayList<MavenRemoteRepository>(newRepositories);
    state.myDependencies = new ArrayList<MavenArtifact>(newDependencies);
    state.myDependencyTree = new ArrayList<MavenArtifactNode>(newDependencyTree);
    state.myPlugins = new ArrayList<MavenPlugin>(newPlugins);
    state.myExtensions = new ArrayList<MavenArtifact>(newExtensions);
  }

  private MavenProjectChanges setFolders(MavenProjectReaderResult readerResult) {
    State newState = myState.clone();
    doSetFolders(newState, readerResult);
    return setState(newState);
  }

  private static void doSetFolders(State newState, MavenProjectReaderResult readerResult) {
    MavenModel model = readerResult.mavenModel;
    newState.mySources = model.getBuild().getSources();
    newState.myTestSources = model.getBuild().getTestSources();

    newState.myResources = model.getBuild().getResources();
    newState.myTestResources = model.getBuild().getTestResources();
  }

  private static Map<String, String> collectModulePathsAndNames(MavenModel mavenModel, String baseDir) {
    String basePath = baseDir + "/";
    Map<String, String> result = new LinkedHashMap<String, String>();
    for (Map.Entry<String, String> each : collectModulesRelativePathsAndNames(mavenModel).entrySet()) {
      result.put(new Path(basePath + each.getKey()).getPath(), each.getValue());
    }
    return result;
  }

  private static Map<String, String> collectModulesRelativePathsAndNames(MavenModel mavenModel) {
    LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
    for (String name : mavenModel.getModules()) {
      name = name.trim();

      if (name.length() == 0) continue;

      String originalName = name;
      // module name can be relative and contain either / of \\ separators

      name = FileUtil.toSystemIndependentName(name);
      if (!name.endsWith("/")) name += "/";
      name += MavenConstants.POM_XML;

      result.put(name, originalName);
    }
    return result;
  }

  private static Collection<String> collectProfilesIds(Collection<MavenProfile> profiles) {
    if (profiles == null) return Collections.emptyList();

    Set<String> result = new THashSet<String>(profiles.size());
    for (MavenProfile each : profiles) {
      result.add(each.getId());
    }
    return result;
  }

  public long getLastReadStamp() {
    return myState.myLastReadStamp;
  }

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  public String getPath() {
    return myFile.getPath();
  }

  @NotNull
  public String getDirectory() {
    return myFile.getParent().getPath();
  }

  @NotNull
  public VirtualFile getDirectoryFile() {
    return myFile.getParent();
  }

  @Nullable
  public VirtualFile getProfilesXmlFile() {
    return MavenUtil.findProfilesXmlFile(myFile);
  }

  @NotNull
  public File getProfilesXmlIoFile() {
    return MavenUtil.getProfilesXmlIoFile(myFile);
  }

  public boolean hasReadingProblems() {
    return !myState.myReadingProblems.isEmpty();
  }

  @Nullable
  public String getName() {
    return myState.myName;
  }

  @NotNull
  public String getDisplayName() {
    State state = myState;
    if (StringUtil.isEmptyOrSpaces(state.myName)) return state.myMavenId.getArtifactId();
    return state.myName;
  }

  @NotNull
  public Map<String, String> getModelMap() {
    return myState.myModelMap;
  }

  @NotNull
  public MavenId getMavenId() {
    return myState.myMavenId;
  }

  @Nullable
  public MavenId getParentId() {
    return myState.myParentId;
  }

  @NotNull
  public String getPackaging() {
    return myState.myPackaging;
  }

  @NotNull
  public String getFinalName() {
    return myState.myFinalName;
  }

  @Nullable
  public String getDefaultGoal() {
    return myState.myDefaultGoal;
  }

  @NotNull
  public String getBuildDirectory() {
    return myState.myBuildDirectory;
  }

  @NotNull
  public String getGeneratedSourcesDirectory(boolean testSources) {
    return getBuildDirectory() + (testSources ? "/generated-test-sources" : "/generated-sources");
  }

  @NotNull
  public String getAnnotationProcessorDirectory(boolean testSources) {
    String def = getGeneratedSourcesDirectory(false) + (testSources ? "/test-annotations" : "/annotations");
    return MavenJDOMUtil.findChildValueByPath(getCompilerConfig(),
                                              testSources ? "generatedTestSourcesDirectory" : "generatedSourcesDirectory",
                                              def);
  }

  @NotNull
  public String getOutputDirectory() {
    return myState.myOutputDirectory;
  }

  @NotNull
  public String getTestOutputDirectory() {
    return myState.myTestOutputDirectory;
  }

  @NotNull
  public List<String> getSources() {
    return myState.mySources;
  }

  @NotNull
  public List<String> getTestSources() {
    return myState.myTestSources;
  }

  @NotNull
  public List<MavenResource> getResources() {
    return myState.myResources;
  }

  @NotNull
  public List<MavenResource> getTestResources() {
    return myState.myTestResources;
  }

  @NotNull
  public List<String> getFilters() {
    return myState.myFilters;
  }

  @NotNull
  public MavenProjectChanges read(@NotNull MavenGeneralSettings generalSettings,
                                  @NotNull Collection<String> profiles,
                                  @NotNull MavenProjectReader reader,
                                  @NotNull MavenProjectReaderProjectLocator locator) {
    return set(reader.readProject(generalSettings, myFile, profiles, locator), generalSettings, true, false, true);
  }

  @NotNull
  public Pair<MavenProjectChanges, NativeMavenProjectHolder> resolve(@NotNull MavenGeneralSettings generalSettings,
                                                                     @NotNull MavenEmbedderWrapper embedder,
                                                                     @NotNull MavenProjectReader reader,
                                                                     @NotNull MavenProjectReaderProjectLocator locator)
    throws MavenProcessCanceledException {
    MavenProjectReaderResult result = reader.resolveProject(generalSettings,
                                                            embedder,
                                                            getFile(),
                                                            getActivatedProfilesIds(),
                                                            locator);
    MavenProjectChanges changes = set(result, generalSettings, false, result.readingProblems.isEmpty(), false);

    if (result.nativeMavenProject != null) {
      for (MavenImporter eachImporter : getSuitableImporters()) {
        eachImporter.resolve(this, result.nativeMavenProject, embedder);
      }
    }
    return Pair.create(changes, result.nativeMavenProject);
  }

  @NotNull
  public Pair<Boolean, MavenProjectChanges> resolveFolders(@NotNull MavenEmbedderWrapper embedder,
                                                           @NotNull MavenImportingSettings importingSettings,
                                                           @NotNull MavenProjectReader reader,
                                                           @NotNull MavenConsole console) throws MavenProcessCanceledException {
    MavenProjectReaderResult result = reader.generateSources(embedder,
                                                             importingSettings,
                                                             getFile(),
                                                             getActivatedProfilesIds(),
                                                             console);
    if (result == null || !result.readingProblems.isEmpty()) return Pair.create(false, MavenProjectChanges.NONE);
    MavenProjectChanges changes = setFolders(result);
    return Pair.create(true, changes);
  }

  public void resetCache() {
    // todo a bit hacky
    synchronized (myState) {
      myState.resetCache();
    }
  }

  public boolean isAggregator() {
    return "pom".equals(getPackaging()) || !getModulePaths().isEmpty();
  }

  @NotNull
  public List<MavenProjectProblem> getProblems() {
    State state = myState;
    synchronized (state) {
      if (state.myProblemsCache == null) {
        state.myProblemsCache = collectProblems(myFile, state);
      }
      return state.myProblemsCache;
    }
  }

  private static List<MavenProjectProblem> collectProblems(VirtualFile file, State state) {
    List<MavenProjectProblem> result = new ArrayList<MavenProjectProblem>();

    validateParent(file, state, result);
    result.addAll(state.myReadingProblems);

    for (Map.Entry<String, String> each : state.myModulesPathsAndNames.entrySet()) {
      if (LocalFileSystem.getInstance().findFileByPath(each.getKey()) == null) {
        result.add(createDependencyProblem(file, ProjectBundle.message("maven.project.problem.moduleNotFound", each.getValue())));
      }
    }

    validateDependencies(file, state, result);
    validateExtensions(file, state, result);
    validatePlugins(file, state, result);

    return result;
  }

  private static void validateParent(VirtualFile file, State state, List<MavenProjectProblem> result) {
    if (!isParentResolved(state)) {
      result.add(createDependencyProblem(file, ProjectBundle.message("maven.project.problem.parentNotFound", state.myParentId)));
    }
  }

  private static void validateDependencies(VirtualFile file, State state, List<MavenProjectProblem> result) {
    for (MavenArtifact each : getUnresolvedDependencies(state)) {
      result.add(createDependencyProblem(file, ProjectBundle.message("maven.project.problem.unresolvedDependency",
                                                                     each.getDisplayStringWithType())));
    }
  }

  private static void validateExtensions(VirtualFile file, State state, List<MavenProjectProblem> result) {
    for (MavenArtifact each : getUnresolvedExtensions(state)) {
      result.add(createDependencyProblem(file, ProjectBundle.message("maven.project.problem.unresolvedExtension",
                                                                     each.getDisplayStringSimple())));
    }
  }

  private static void validatePlugins(VirtualFile file, State state, List<MavenProjectProblem> result) {
    for (MavenPlugin each : getUnresolvedPlugins(state)) {
      result.add(createDependencyProblem(file, ProjectBundle.message("maven.project.problem.unresolvedPlugin", each)));
    }
  }

  private static MavenProjectProblem createDependencyProblem(VirtualFile file, String description) {
    return new MavenProjectProblem(file.getPath(), description, MavenProjectProblem.ProblemType.DEPENDENCY);
  }

  private static boolean isParentResolved(State state) {
    return !state.myUnresolvedArtifactIds.contains(state.myParentId);
  }

  private static List<MavenArtifact> getUnresolvedDependencies(State state) {
    synchronized (state) {
      if (state.myUnresolvedDependenciesCache == null) {
        List<MavenArtifact> result = new ArrayList<MavenArtifact>();
        for (MavenArtifact each : state.myDependencies) {
          if (!each.isResolved()) result.add(each);
        }
        state.myUnresolvedDependenciesCache = result;
      }
      return state.myUnresolvedDependenciesCache;
    }
  }

  private static List<MavenArtifact> getUnresolvedExtensions(State state) {
    synchronized (state) {
      if (state.myUnresolvedExtensionsCache == null) {
        List<MavenArtifact> result = new ArrayList<MavenArtifact>();
        for (MavenArtifact each : state.myExtensions) {
          // Collect only extensions that were attempted to be resolved.
          // It is because embedder does not even try to resolve extensions that
          // are not necessary.
          if (state.myUnresolvedArtifactIds.contains(each.getMavenId())
              && !pomFileExists(state.myLocalRepository, each)) {
            result.add(each);
          }
        }
        state.myUnresolvedExtensionsCache = result;
      }
      return state.myUnresolvedExtensionsCache;
    }
  }

  private static boolean pomFileExists(File localRepository, MavenArtifact artifact) {
    return MavenArtifactUtil.hasArtifactFile(localRepository, artifact.getMavenId(), "pom");
  }

  private static List<MavenPlugin> getUnresolvedPlugins(State state) {
    synchronized (state) {
      if (state.myUnresolvedPluginsCache == null) {
        List<MavenPlugin> result = new ArrayList<MavenPlugin>();
        for (MavenPlugin each : getDeclaredPlugins(state)) {
          if (!MavenArtifactUtil.hasArtifactFile(state.myLocalRepository, each.getMavenId())) {
            result.add(each);
          }
        }
        state.myUnresolvedPluginsCache = result;
      }
      return state.myUnresolvedPluginsCache;
    }
  }

  @NotNull
  public List<VirtualFile> getExistingModuleFiles() {
    LocalFileSystem fs = LocalFileSystem.getInstance();

    List<VirtualFile> result = new ArrayList<VirtualFile>();
    Set<String> pathsInStack = getModulePaths();
    for (String each : pathsInStack) {
      VirtualFile f = fs.findFileByPath(each);
      if (f != null) result.add(f);
    }
    return result;
  }

  @NotNull
  public Set<String> getModulePaths() {
    return getModulesPathsAndNames().keySet();
  }

  @NotNull
  public Map<String, String> getModulesPathsAndNames() {
    return myState.myModulesPathsAndNames;
  }

  @NotNull
  public Collection<String> getProfilesIds() {
    return myState.myProfilesIds;
  }

  @NotNull
  public Collection<String> getActivatedProfilesIds() {
    return myState.myActivatedProfilesIds;
  }

  @NotNull
  public List<MavenArtifact> getDependencies() {
    return myState.myDependencies;
  }

  @NotNull
  public List<MavenArtifactNode> getDependencyTree() {
    return myState.myDependencyTree;
  }

  public boolean isSupportedDependency(@NotNull MavenArtifact artifact, @NotNull SupportedRequestType type) {
    return getSupportedDependencyTypes(type).contains(artifact.getType());
  }

  @NotNull
  public Set<String> getSupportedPackagings() {
    Set<String> result = new THashSet<String>(Arrays.asList(MavenConstants.TYPE_POM,
                                                            MavenConstants.TYPE_JAR,
                                                            "ejb", "ejb-client", "war", "ear"));
    for (MavenImporter each : getSuitableImporters()) {
      each.getSupportedPackagings(result);
    }
    return result;
  }

  @NotNull
  public Set<String> getSupportedDependencyTypes(@NotNull SupportedRequestType type) {
    Set<String> result = new THashSet<String>(Arrays.asList(MavenConstants.TYPE_JAR,
                                                            MavenConstants.TYPE_TEST_JAR,
                                                            "ejb", "ejb-client", "war", "ear"));
    if (type == SupportedRequestType.FOR_COMPLETION) {
      result.add(MavenConstants.TYPE_POM);
    }

    for (MavenImporter each : getSuitableImporters()) {
      each.getSupportedDependencyTypes(result, type);
    }
    return result;
  }

  @NotNull
  public Set<String> getSupportedDependencyScopes() {
    Set<String> result = new THashSet<String>(Arrays.asList(MavenConstants.SCOPE_COMPILE,
                                                            MavenConstants.SCOPE_PROVIDEED,
                                                            MavenConstants.SCOPE_RUNTIME,
                                                            MavenConstants.SCOPE_TEST,
                                                            MavenConstants.SCOPE_SYSTEM));
    for (MavenImporter each : getSuitableImporters()) {
      each.getSupportedDependencyScopes(result);
    }
    return result;
  }

  public void addDependency(@NotNull MavenArtifact dependency) {
    State state = myState;
    List<MavenArtifact> dependenciesCopy = new ArrayList<MavenArtifact>(state.myDependencies);
    dependenciesCopy.add(dependency);
    state.myDependencies = dependenciesCopy;
  }

  @NotNull
  public List<MavenArtifact> findDependencies(@NotNull MavenProject depProject) {
    return findDependencies(depProject.getMavenId());
  }

  public List<MavenArtifact> findDependencies(MavenId id) {
    List<MavenArtifact> result = new SmartList<MavenArtifact>();
    for (MavenArtifact each : getDependencies()) {
      if (each.getMavenId().equals(id)) result.add(each);
    }
    return result;
  }

  @NotNull
  public List<MavenArtifact> findDependencies(@Nullable String groupId, @Nullable String artifactId) {
    List<MavenArtifact> result = new SmartList<MavenArtifact>();
    for (MavenArtifact each : getDependencies()) {
      if (each.getMavenId().equals(groupId, artifactId)) result.add(each);
    }
    return result;
  }

  public boolean hasUnresolvedArtifacts() {
    State state = myState;
    return !isParentResolved(state)
           || !getUnresolvedDependencies(state).isEmpty()
           || !getUnresolvedExtensions(state).isEmpty();
  }

  public boolean hasUnresolvedPlugins() {
    return !getUnresolvedPlugins(myState).isEmpty();
  }

  @NotNull
  public List<MavenPlugin> getPlugins() {
    return myState.myPlugins;
  }

  @NotNull
  public List<MavenPlugin> getDeclaredPlugins() {
    return getDeclaredPlugins(myState);
  }

  private static List<MavenPlugin> getDeclaredPlugins(State state) {
    return ContainerUtil.findAll(state.myPlugins, new Condition<MavenPlugin>() {
      public boolean value(MavenPlugin mavenPlugin) {
        return !mavenPlugin.isDefault();
      }
    });
  }

  @Nullable
  public Element getPluginConfiguration(@Nullable String groupId, @Nullable String artifactId) {
    return doGetPluginOrGoalConfiguration(groupId, artifactId, null);
  }

  @Nullable
  public Element getPluginGoalConfiguration(@Nullable String groupId, @Nullable String artifactId, @Nullable String goal) {
    return doGetPluginOrGoalConfiguration(groupId, artifactId, goal);
  }

  @Nullable
  private Element doGetPluginOrGoalConfiguration(@Nullable String groupId, @Nullable String artifactId, @Nullable String goalOrNull) {
    MavenPlugin plugin = findPlugin(groupId, artifactId);
    if (plugin == null) return null;

    Element configElement = null;
    if (goalOrNull == null) {
      configElement = plugin.getConfigurationElement();
    }
    else {
      for (MavenPlugin.Execution each : plugin.getExecutions()) {
        if (each.getGoals().contains(goalOrNull)) {
          configElement = each.getConfigurationElement();
        }
      }
    }
    return configElement;
  }

  @Nullable
  public MavenPlugin findPlugin(@Nullable String groupId, @Nullable String artifactId) {
    for (MavenPlugin each : getPlugins()) {
      if (each.getMavenId().equals(groupId, artifactId)) return each;
    }
    return null;
  }

  @Nullable
  public String getSourceLevel() {
    return getCompilerLevel("source");
  }

  @Nullable
  public String getTargetLevel() {
    return getCompilerLevel("target");
  }

  @Nullable
  private String getCompilerLevel(String level) {
    String result = MavenJDOMUtil.findChildValueByPath(getCompilerConfig(), level);
    return normalizeCompilerLevel(result);
  }

  @Nullable
  private Element getCompilerConfig() {
    return getPluginConfiguration("org.apache.maven.plugins", "maven-compiler-plugin");
  }

  private static class CompilerLevelTable {
    public static Map<String, String> table = new THashMap<String, String>();

    static {
      table.put("1.1", "1.1");
      table.put("1.2", "1.2");
      table.put("1.3", "1.3");
      table.put("1.4", "1.4");
      table.put("1.5", "1.5");
      table.put("5", "1.5");
      table.put("1.6", "1.6");
      table.put("1.7", "1.7");
      table.put("7", "1.7");
    }
  }

  @Nullable
  public static String normalizeCompilerLevel(String level) {
    if (level == null) return null;
    return CompilerLevelTable.table.get(level);
  }

  @NotNull
  public Properties getProperties() {
    return myState.myProperties;
  }

  @NotNull
  public File getLocalRepository() {
    return myState.myLocalRepository;
  }

  @NotNull
  public List<MavenRemoteRepository> getRemoteRepositories() {
    return myState.myRemoteRepositories;
  }

  @NotNull
  public List<MavenImporter> getSuitableImporters() {
    return MavenImporter.getSuitableImporters(this);
  }

  @NotNull
  public Pair<String, String> getClassifierAndExtension(@NotNull MavenArtifact artifact, @NotNull MavenExtraArtifactType type) {
    for (MavenImporter each : getSuitableImporters()) {
      Pair<String, String> result = each.getExtraArtifactClassifierAndExtension(artifact, type);
      if (result != null) return result;
    }
    return Pair.create(type.getDefaultClassifier(), type.getDefaultExtension());
  }

  @Override
  public String toString() {
    return getMavenId().toString();
  }

  private static class State implements Cloneable, Serializable {
    long myLastReadStamp = 0;

    MavenId myMavenId;
    MavenId myParentId;
    String myPackaging;
    String myName;

    String myFinalName;
    String myDefaultGoal;

    String myBuildDirectory;
    String myOutputDirectory;
    String myTestOutputDirectory;

    List<String> mySources;
    List<String> myTestSources;
    List<MavenResource> myResources;
    List<MavenResource> myTestResources;

    List<String> myFilters;
    Properties myProperties;
    List<MavenPlugin> myPlugins;
    List<MavenArtifact> myExtensions;

    List<MavenArtifact> myDependencies;
    List<MavenArtifactNode> myDependencyTree;
    List<MavenRemoteRepository> myRemoteRepositories;

    Map<String, String> myModulesPathsAndNames;

    Map<String, String> myModelMap;

    Collection<String> myProfilesIds;
    Collection<String> myActivatedProfilesIds;

    Collection<MavenProjectProblem> myReadingProblems;
    Set<MavenId> myUnresolvedArtifactIds;
    File myLocalRepository;

    volatile List<MavenProjectProblem> myProblemsCache;
    volatile List<MavenArtifact> myUnresolvedDependenciesCache;
    volatile List<MavenPlugin> myUnresolvedPluginsCache;
    volatile List<MavenArtifact> myUnresolvedExtensionsCache;

    @Override
    public State clone() {
      try {
        State result = (State)super.clone();
        result.resetCache();
        return result;
      }
      catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
    }

    private void resetCache() {
      myProblemsCache = null;
      myUnresolvedDependenciesCache = null;
      myUnresolvedPluginsCache = null;
      myUnresolvedExtensionsCache = null;
    }

    public MavenProjectChanges getChanges(State other) {
      if (myLastReadStamp == 0) return MavenProjectChanges.ALL;

      MavenProjectChanges result = new MavenProjectChanges();

      result.packaging |= !Comparing.equal(myPackaging, other.myPackaging);

      result.output |= !Comparing.equal(myFinalName, other.myFinalName);
      result.output |= !Comparing.equal(myBuildDirectory, other.myBuildDirectory);
      result.output |= !Comparing.equal(myOutputDirectory, other.myOutputDirectory);
      result.output |= !Comparing.equal(myTestOutputDirectory, other.myTestOutputDirectory);

      result.sources |= !Comparing.equal(mySources, other.mySources);
      result.sources |= !Comparing.equal(myTestSources, other.myTestSources);
      result.sources |= !Comparing.equal(myResources, other.myResources);
      result.sources |= !Comparing.equal(myTestResources, other.myTestResources);

      boolean repositoryChanged = !Comparing.equal(myLocalRepository, other.myLocalRepository);

      result.dependencies |= repositoryChanged;
      result.dependencies |= !Comparing.equal(myDependencies, other.myDependencies);

      result.plugins |= repositoryChanged;
      result.plugins |= !Comparing.equal(myPlugins, other.myPlugins);

      return result;
    }
  }
}
