package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.facets.FacetImporter;
import org.jetbrains.idea.maven.utils.*;

import java.io.*;
import java.util.*;

public class MavenProject {
  private final VirtualFile myFile;
  private volatile State myState = new State();

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

  public void write(DataOutputStream out) throws IOException {
    out.writeUTF(getPath());

    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    ObjectOutputStream os = new ObjectOutputStream(bs);
    try {
      os.writeObject(myState);

      byte[] bytes = bs.toByteArray();
      out.writeInt(bytes.length);
      out.write(bytes);
    }
    finally {
      os.close();
      bs.close();
    }
  }

  public MavenProject(VirtualFile file) {
    myFile = file;
  }

  private MavenProjectChanges set(MavenProjectReaderResult readerResult,
                                  boolean updateLastReadStamp,
                                  boolean resetArtifacts,
                                  boolean resetProfiles) {
    State newState = myState.clone();

    if (updateLastReadStamp) newState.myLastReadStamp++;

    newState.myValid = readerResult.isValid;
    newState.myActiveProfilesIds = readerResult.activeProfiles;
    newState.myReadingProblems = readerResult.readingProblems;
    newState.myLocalRepository = readerResult.localRepository;

    org.apache.maven.project.MavenProject nativeMavenProject = readerResult.nativeMavenProject;
    Model model = nativeMavenProject.getModel();

    newState.myMavenId = new MavenId(model.getGroupId(),
                                     model.getArtifactId(),
                                     model.getVersion());

    Parent parent = model.getParent();
    newState.myParentId = parent != null
                          ? new MavenId(parent.getGroupId(), parent.getArtifactId(), parent.getVersion())
                          : null;

    newState.myPackaging = model.getPackaging();
    newState.myName = model.getName();

    Build build = model.getBuild();

    newState.myFinalName = build.getFinalName();
    newState.myDefaultGoal = build.getDefaultGoal();

    newState.myBuildDirectory = build.getDirectory();
    newState.myOutputDirectory = build.getOutputDirectory();
    newState.myTestOutputDirectory = build.getTestOutputDirectory();

    doSetFolders(newState, readerResult);

    newState.myFilters = build.getFilters() == null ? Collections.EMPTY_LIST : build.getFilters();
    newState.myProperties = model.getProperties() != null ? model.getProperties() : new Properties();

    doSetResolvedAttributes(newState, readerResult, resetArtifacts);

    newState.myModulesPathsAndNames = collectModulePathsAndNames(model, getDirectory(), newState.myActiveProfilesIds);
    List<String> newProfiles = collectProfilesIds(model);
    if (resetProfiles || newState.myProfilesIds == null) {
      newState.myProfilesIds = newProfiles;
    }
    else {
      Set<String> mergedProfiles = new THashSet<String>(newState.myProfilesIds);
      mergedProfiles.addAll(newProfiles);
      newState.myProfilesIds = new ArrayList<String>(mergedProfiles);
    }

    newState.myStrippedMavenModel = MavenUtil.cloneObject(model);
    MavenUtil.stripDown(newState.myStrippedMavenModel);

    return setState(newState);
  }

  private MavenProjectChanges setState(State newState) {
    MavenProjectChanges changes = myState.getChanges(newState);
    myState = newState;
    return changes;
  }

  private static void doSetResolvedAttributes(State state, MavenProjectReaderResult readerResult, boolean reset) {
    org.apache.maven.project.MavenProject nativeMavenProject = readerResult.nativeMavenProject;
    Model model = nativeMavenProject.getModel();

    Set<MavenId> newUnresolvedArtifacts = new THashSet<MavenId>();
    LinkedHashSet<MavenRemoteRepository> newRepositories = new LinkedHashSet<MavenRemoteRepository>();
    LinkedHashSet<MavenArtifact> newDependencies = new LinkedHashSet<MavenArtifact>();
    LinkedHashSet<MavenPlugin> newPlugins = new LinkedHashSet<MavenPlugin>();
    LinkedHashSet<MavenArtifact> newExtensions = new LinkedHashSet<MavenArtifact>();

    if (!reset) {
      if (state.myUnresolvedArtifactIds != null) newUnresolvedArtifacts.addAll(state.myUnresolvedArtifactIds);
      if (state.myRemoteRepositories != null) newRepositories.addAll(state.myRemoteRepositories);
      if (state.myDependencies != null) newDependencies.addAll(state.myDependencies);
      if (state.myPlugins != null) newPlugins.addAll(state.myPlugins);
      if (state.myExtensions != null) newExtensions.addAll(state.myExtensions);
    }

    newUnresolvedArtifacts.addAll(readerResult.unresolvedArtifactIds);
    newRepositories.addAll(convertRepositories(model.getRepositories()));
    newDependencies.addAll(convertArtifacts(nativeMavenProject.getArtifacts(), state.myLocalRepository));
    newPlugins.addAll(collectPlugins(model, state.myActiveProfilesIds));
    newExtensions.addAll(convertArtifacts(nativeMavenProject.getExtensionArtifacts(), state.myLocalRepository));

    state.myUnresolvedArtifactIds = newUnresolvedArtifacts;
    state.myRemoteRepositories = new ArrayList<MavenRemoteRepository>(newRepositories);
    state.myDependencies = new ArrayList<MavenArtifact>(newDependencies);
    state.myPlugins = new ArrayList<MavenPlugin>(newPlugins);
    state.myExtensions = new ArrayList<MavenArtifact>(newExtensions);
  }

  private MavenProjectChanges setFolders(MavenProjectReaderResult readerResult) {
    State newState = myState.clone();
    doSetFolders(newState, readerResult);
    return setState(newState);
  }

  private static void doSetFolders(State newState, MavenProjectReaderResult readerResult) {
    org.apache.maven.project.MavenProject nativeMavenProject = readerResult.nativeMavenProject;

    newState.mySources = new ArrayList<String>(nativeMavenProject.getCompileSourceRoots());
    newState.myTestSources = new ArrayList<String>(nativeMavenProject.getTestCompileSourceRoots());

    newState.myResources = convertResources(nativeMavenProject.getResources());
    newState.myTestResources = convertResources(nativeMavenProject.getTestResources());
  }

  private static List<MavenResource> convertResources(List<Resource> resources) {
    if (resources == null) return new ArrayList<MavenResource>();

    List<MavenResource> result = new ArrayList<MavenResource>(resources.size());
    for (Resource each : resources) {
      result.add(new MavenResource(each));
    }
    return result;
  }

  private static List<MavenRemoteRepository> convertRepositories(List<Repository> repositories) {
    if (repositories == null) return new ArrayList<MavenRemoteRepository>();

    List<MavenRemoteRepository> result = new ArrayList<MavenRemoteRepository>(repositories.size());
    for (Repository each : repositories) {
      result.add(new MavenRemoteRepository(each));
    }
    return result;
  }

  private static List<MavenArtifact> convertArtifacts(Collection<Artifact> artifacts, File localRepository) {
    if (artifacts == null) return new ArrayList<MavenArtifact>();

    List<MavenArtifact> result = new ArrayList<MavenArtifact>(artifacts.size());
    for (Artifact each : artifacts) {
      result.add(new MavenArtifact(each, localRepository));
    }
    return result;
  }

  private static List<MavenPlugin> collectPlugins(Model mavenModel, List<String> activeProfilesIds) {
    List<MavenPlugin> result = new ArrayList<MavenPlugin>();
    collectPlugins(mavenModel.getBuild(), result);
    for (Profile profile : collectActiveProfiles(mavenModel, activeProfilesIds)) {
      if (activeProfilesIds.contains(profile.getId())) {
        collectPlugins(profile.getBuild(), result);
      }
    }
    return result;
  }

  private static void collectPlugins(BuildBase build, List<MavenPlugin> result) {
    if (build == null) return;

    List<Plugin> plugins = (List<Plugin>)build.getPlugins();
    if (plugins == null) return;

    for (Plugin each : plugins) {
      result.add(new MavenPlugin(each));
    }
  }

  private static Map<String, String> collectModulePathsAndNames(Model mavenModel,
                                                                String baseDir,
                                                                List<String> activeProfilesIds) {
    String basePath = baseDir + "/";
    Map<String, String> result = new LinkedHashMap<String, String>();
    for (Map.Entry<String, String> each : collectModulesRelativePathsAndNames(mavenModel, activeProfilesIds).entrySet()) {
      result.put(new Path(basePath + each.getKey()).getPath(), each.getValue());
    }
    return result;
  }

  private static Map<String, String> collectModulesRelativePathsAndNames(Model mavenModel, List<String> activeProfilesIds) {
    LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
    addModulesToList(mavenModel.getModules(), result);
    for (Profile profile : collectActiveProfiles(mavenModel, activeProfilesIds)) {
      addModulesToList(profile.getModules(), result);
    }
    return result;
  }

  private static void addModulesToList(List moduleNames, LinkedHashMap<String, String> result) {
    for (String name : (List<String>)moduleNames) {
      if (name.trim().length() == 0) continue;

      String originalName = name;
      // module name can be relative and contain either / of \\ separators

      name = FileUtil.toSystemIndependentName(name);
      if (!name.endsWith("/")) name += "/";
      name += MavenConstants.POM_XML;

      result.put(name, originalName);
    }
  }

  private static List<Profile> collectActiveProfiles(Model mavenModel, List<String> activeProfilesIds) {
    List<Profile> result = new ArrayList<Profile>(activeProfilesIds.size());
    for (Profile each : collectProfiles(mavenModel)) {
      if (activeProfilesIds.contains(each.getId())) result.add(each);
    }
    return result;
  }

  private static List<String> collectProfilesIds(Model mavenModel) {
    List<Profile> profiles = collectProfiles(mavenModel);
    Set<String> result = new THashSet<String>(profiles.size());
    for (Profile each : profiles) {
      result.add(each.getId());
    }
    return new ArrayList<String>(result);
  }

  private static List<Profile> collectProfiles(Model mavenModel) {
    List<Profile> profiles = (List<Profile>)mavenModel.getProfiles();
    return profiles == null ? Collections.<Profile>emptyList() : profiles;
  }

  public long getLastReadStamp() {
    return myState.myLastReadStamp;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  public String getPath() {
    return myFile.getPath();
  }

  public String getDirectory() {
    return myFile.getParent().getPath();
  }

  public VirtualFile getDirectoryFile() {
    return myFile.getParent();
  }

  public VirtualFile getProfilesXmlFile() {
    return MavenUtil.findProfilesXmlFile(myFile);
  }

  public File getProfilesXmlIoFile() {
    return MavenUtil.getProfilesXmlIoFile(myFile);
  }

  public boolean hasErrors() {
    return !myState.myValid;
  }

  public List<String> getActiveProfilesIds() {
    return myState.myActiveProfilesIds;
  }

  public String getName() {
    return myState.myName;
  }

  public String getDisplayName() {
    State state = myState;
    if (StringUtil.isEmptyOrSpaces(state.myName)) return state.myMavenId.getArtifactId();
    return state.myName;
  }

  public Model getMavenModel() {
    return myState.myStrippedMavenModel;
  }

  public MavenId getMavenId() {
    return myState.myMavenId;
  }

  public MavenId getParentId() {
    return myState.myParentId;
  }

  public String getPackaging() {
    return myState.myPackaging;
  }

  public String getFinalName() {
    return myState.myFinalName;
  }

  public String getDefaultGoal() {
    return myState.myDefaultGoal;
  }

  public String getBuildDirectory() {
    return myState.myBuildDirectory;
  }

  public String getGeneratedSourcesDirectory() {
    return getBuildDirectory() + "/generated-sources";
  }

  public String getOutputDirectory() {
    return myState.myOutputDirectory;
  }

  public String getTestOutputDirectory() {
    return myState.myTestOutputDirectory;
  }

  public List<String> getSources() {
    return myState.mySources;
  }

  public List<String> getTestSources() {
    return myState.myTestSources;
  }

  public List<MavenResource> getResources() {
    return myState.myResources;
  }

  public List<MavenResource> getTestResources() {
    return myState.myTestResources;
  }

  public List<String> getFilters() {
    return myState.myFilters;
  }

  public MavenProjectChanges read(MavenGeneralSettings generalSettings,
                                  List<String> profiles,
                                  MavenProjectReader reader,
                                  MavenProjectReaderProjectLocator locator) {
    return set(reader.readProject(generalSettings, myFile, profiles, locator), true, false, true);
  }

  public Pair<MavenProjectChanges, org.apache.maven.project.MavenProject> resolve(MavenGeneralSettings generalSettings,
                                                                                  MavenEmbedderWrapper embedder,
                                                                                  MavenProjectReader reader,
                                                                                  MavenProjectReaderProjectLocator locator)
    throws MavenProcessCanceledException {
    MavenProjectReaderResult result = reader.resolveProject(generalSettings,
                                                            embedder,
                                                            getFile(),
                                                            getActiveProfilesIds(),
                                                            locator);
    MavenProjectChanges changes = set(result, false, result.isValid, false);
    return Pair.create(changes, result.nativeMavenProject);
  }

  public Pair<Boolean, MavenProjectChanges> resolveFolders(MavenEmbedderWrapper embedder,
                                                           MavenImportingSettings importingSettings,
                                                           MavenProjectReader reader,
                                                           MavenConsole console) throws MavenProcessCanceledException {
    MavenProjectReaderResult result = reader.generateSources(embedder,
                                                             importingSettings,
                                                             getFile(),
                                                             getActiveProfilesIds(),
                                                             console);
    if (result == null || !result.isValid) return Pair.create(false, MavenProjectChanges.NONE);
    MavenProjectChanges changes = setFolders(result);
    return Pair.create(true, changes);
  }

  public boolean isAggregator() {
    return "pom".equals(getPackaging()) || !getModulePaths().isEmpty();
  }

  public List<MavenProjectProblem> getProblems() {
    State state = myState;
    if (state.myProblemsCache == null) {
      synchronized (state) {
        if (state.myProblemsCache == null) {
          state.myProblemsCache = collectProblems(state);
        }
      }
    }
    return state.myProblemsCache;
  }

  private static List<MavenProjectProblem> collectProblems(State state) {
    List<MavenProjectProblem> result = new ArrayList<MavenProjectProblem>();

    validateParent(state, result);
    result.addAll(state.myReadingProblems);

    for (Map.Entry<String, String> each : state.myModulesPathsAndNames.entrySet()) {
      if (LocalFileSystem.getInstance().findFileByPath(each.getKey()) == null) {
        result.add(new MavenProjectProblem(ProjectBundle.message("maven.project.problem.missingModule", each.getValue()), false));
      }
    }

    validateDependencies(state, result);
    validateExtensions(state, result);
    validatePlugins(state, result);

    return result;
  }

  private static void validateParent(State state, List<MavenProjectProblem> result) {
    if (!isParentResolved(state)) {
      result.add(new MavenProjectProblem(ProjectBundle.message("maven.project.problem.parentNotFound", state.myParentId), true));
    }
  }

  private static void validateDependencies(State state, List<MavenProjectProblem> result) {
    for (MavenArtifact each : getUnresolvedDependencies(state)) {
      result.add(new MavenProjectProblem(
        ProjectBundle.message("maven.project.problem.unresolvedDependency", each.getDisplayStringWithType()), false));
    }
  }

  private static void validateExtensions(State state, List<MavenProjectProblem> result) {
    for (MavenArtifact each : getUnresolvedExtensions(state)) {
      result.add(new MavenProjectProblem(
        ProjectBundle.message("maven.project.problem.unresolvedExtension", each.getDisplayStringSimple()), false));
    }
  }

  private static void validatePlugins(State state, List<MavenProjectProblem> result) {
    for (MavenPlugin each : getUnresolvedPlugins(state)) {
      result.add(new MavenProjectProblem(ProjectBundle.message("maven.project.problem.unresolvedPlugin", each), false));
    }
  }

  private static boolean isParentResolved(State state) {
    return !state.myUnresolvedArtifactIds.contains(state.myParentId);
  }

  private static List<MavenArtifact> getUnresolvedDependencies(State state) {
    if (state.myUnresolvedDependenciesCache == null) {
      synchronized (state) {
        if (state.myUnresolvedDependenciesCache == null) {
          List<MavenArtifact> result = new ArrayList<MavenArtifact>();
          for (MavenArtifact each : state.myDependencies) {
            if (!each.isResolved()) result.add(each);
          }
          state.myUnresolvedDependenciesCache = result;
        }
      }
    }
    return state.myUnresolvedDependenciesCache;
  }

  private static List<MavenArtifact> getUnresolvedExtensions(State state) {
    if (state.myUnresolvedExtensionsCache == null) {
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
      }
    }
    return state.myUnresolvedExtensionsCache;
  }

  private static boolean pomFileExists(File localRepository, MavenArtifact artifact) {
    return MavenArtifactUtil.hasArtifactFile(localRepository, artifact.getMavenId(), "pom");
  }

  private static List<MavenPlugin> getUnresolvedPlugins(State state) {
    if (state.myUnresolvedPluginsCache == null) {
      synchronized (state) {
        if (state.myUnresolvedPluginsCache == null) {
          List<MavenPlugin> result = new ArrayList<MavenPlugin>();
          for (MavenPlugin each : state.myPlugins) {
            if (!MavenArtifactUtil.hasArtifactFile(state.myLocalRepository, each.getMavenId())) {
              result.add(each);
            }
          }
          state.myUnresolvedPluginsCache = result;
        }
      }
    }
    return state.myUnresolvedPluginsCache;
  }

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

  public Set<String> getModulePaths() {
    return getModulesPathsAndNames().keySet();
  }

  public Map<String, String> getModulesPathsAndNames() {
    return myState.myModulesPathsAndNames;
  }

  public List<String> getProfilesIds() {
    return myState.myProfilesIds;
  }

  public List<MavenArtifact> getDependencies() {
    return myState.myDependencies;
  }

  public List<MavenArtifactNode> getDependenciesNodes() {
    return buildDependenciesNodes(myState.myDependencies);
  }

  private static List<MavenArtifactNode> buildDependenciesNodes(List<MavenArtifact> artifacts) {
    List<MavenArtifactNode> result = new ArrayList<MavenArtifactNode>();
    for (MavenArtifact each : artifacts) {
      List<MavenArtifactNode> currentScope = result;
      for (String eachKey : each.getTrail()) {
        MavenArtifactNode node = findNodeFor(eachKey, currentScope, true);
        if (node == null) {
          node = findNodeFor(eachKey, result, false);
          if (node == null) {
            MavenArtifact artifact = findArtifactFor(eachKey, artifacts);
            if (artifact == null) break;
            node = new MavenArtifactNode(artifact, new ArrayList<MavenArtifactNode>());
          }
          currentScope.add(node);
        }
        currentScope = node.getDependencies();
      }
    }
    return result;
  }

  private static MavenArtifactNode findNodeFor(String artifactKey, Collection<MavenArtifactNode> nodes, boolean strict) {
    for (MavenArtifactNode each : nodes) {
      if (each.getArtifact().getDisplayStringWithTypeAndClassifier().equals(artifactKey)) {
        return each;
      }
    }
    if (strict) return null;

    for (MavenArtifactNode each : nodes) {
      MavenArtifactNode result = findNodeFor(artifactKey, each.getDependencies(), strict);
      if (result != null) return result;
    }
    return null;
  }

  private static MavenArtifact findArtifactFor(String artifactKey, Collection<MavenArtifact> artifacts) {
    for (MavenArtifact each : artifacts) {
      if (each.getDisplayStringWithTypeAndClassifier().equals(artifactKey)) {
        return each;
      }
    }
    return null;
  }

  public boolean isSupportedDependency(MavenArtifact artifact) {
    String t = artifact.getType();
    if (t.equalsIgnoreCase(MavenConstants.TYPE_JAR)
        || t.equalsIgnoreCase("test-jar")
        || t.equalsIgnoreCase("ejb")
        || t.equalsIgnoreCase("ejb-client")) {
      return true;
    }

    for (FacetImporter each : getSuitableFacetImporters()) {
      if (each.isSupportedDependency(artifact)) return true;
    }
    return false;
  }

  public boolean isOptionalDependency(MavenId id) {
    for (MavenArtifact each : getDependencies()) {
      if (each.getMavenId().equals(id) && each.isOptional()) return true;
    }
    return false;
  }

  public void addDependency(MavenArtifact dependency) {
    State state = myState;
    List<MavenArtifact> dependenciesCopy = new ArrayList<MavenArtifact>(state.myDependencies);
    dependenciesCopy.add(dependency);
    state.myDependencies = dependenciesCopy;
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

  public List<MavenPlugin> getPlugins() {
    return myState.myPlugins;
  }

  @Nullable
  public String findPluginConfigurationValue(String groupId,
                                             String artifactId,
                                             String path) {
    Element node = findPluginConfigurationElement(groupId, artifactId, path);
    return node == null ? null : node.getValue();
  }

  @Nullable
  public Element findPluginConfigurationElement(String groupId, String artifactId, String path) {
    return doFindPluginOrGoalConfigurationElement(groupId, artifactId, null, path);
  }

  @Nullable
  public String findPluginGoalConfigurationValue(String groupId, String artifactId, String goal, String path) {
    Element node = findPluginGoalConfigurationElement(groupId, artifactId, goal, path);
    return node == null ? null : node.getValue();
  }

  @Nullable
  public Element findPluginGoalConfigurationElement(String groupId, String artifactId, String goal, String path) {
    return doFindPluginOrGoalConfigurationElement(groupId, artifactId, goal, path);
  }

  @Nullable
  private Element doFindPluginOrGoalConfigurationElement(String groupId,
                                                         String artifactId,
                                                         String goalOrNull,
                                                         String path) {
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
    if (configElement == null) return null;

    for (String name : StringUtil.split(path, ".")) {
      configElement = configElement.getChild(name);
      if (configElement == null) return null;
    }

    return configElement;
  }

  @Nullable
  public MavenPlugin findPlugin(String groupId, String artifactId) {
    for (MavenPlugin each : getPlugins()) {
      if (groupId.equals(each.getGroupId()) && artifactId.equals(each.getArtifactId())) return each;
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

  private String getCompilerLevel(String level) {
    String result = findPluginConfigurationValue("org.apache.maven.plugins",
                                                 "maven-compiler-plugin",
                                                 level);
    return normalizeCompilerLevel(result);
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
      table.put("6", "1.6");
    }
  }

  public static String normalizeCompilerLevel(String level) {
    if (level == null) return null;
    return CompilerLevelTable.table.get(level);
  }

  public Properties getProperties() {
    return myState.myProperties;
  }

  public File getLocalRepository() {
    return myState.myLocalRepository;
  }

  public List<MavenRemoteRepository> getRemoteRepositories() {
    return myState.myRemoteRepositories;
  }

  public List<FacetImporter> getSuitableFacetImporters() {
    return FacetImporter.getSuitableFacetImporters(this);
  }

  @Override
  public String toString() {
    return getMavenId().toString();
  }

  private static class State implements Cloneable, Serializable {
    long myLastReadStamp = 0;

    boolean myValid;

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

    Map<String, String> myModulesPathsAndNames;

    List<String> myProfilesIds;

    Model myStrippedMavenModel;
    List<MavenRemoteRepository> myRemoteRepositories;

    List<String> myActiveProfilesIds;
    List<MavenProjectProblem> myReadingProblems;
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
        result.myProblemsCache = null;
        result.myUnresolvedDependenciesCache = null;
        result.myUnresolvedPluginsCache = null;
        result.myUnresolvedExtensionsCache = null;
        return result;
      }
      catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
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
