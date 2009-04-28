package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.facets.FacetImporter;
import org.jetbrains.idea.maven.utils.*;

import java.io.*;
import java.util.*;

public class MavenProject implements Serializable {
  private transient VirtualFile myFile;
  private long myLastReadStamp = 0;

  private boolean myValid;
  private boolean myIncluded;

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

  private Map<String, String> myModulesPathsAndNames;

  private List<String> myProfilesIds;

  private Model myStrippedMavenModel;
  private List<MavenRemoteRepository> myRemoteRepositories;

  private List<String> myActiveProfilesIds;
  private List<MavenProjectProblem> myReadingProblems;
  private Set<MavenId> myUnresolvedArtifactIds;
  private File myLocalRepository;

  private volatile List<MavenProjectProblem> myAllProblemsCache;

  public static MavenProject read(DataInputStream in) throws IOException {
    String path = in.readUTF();
    int length = in.readInt();
    byte[] bytes = new byte[length];
    in.readFully(bytes);

    ByteArrayInputStream bs = new ByteArrayInputStream(bytes);
    ObjectInputStream os = new ObjectInputStream(bs);
    try {
      MavenProject result;
      try {
        result = (MavenProject)os.readObject();
        result.myFile = LocalFileSystem.getInstance().findFileByPath(path);
        return result.myFile == null ? null : result;
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

  public synchronized void write(DataOutputStream out) throws IOException {
    out.writeUTF(getPath());

    ByteArrayOutputStream bs = new ByteArrayOutputStream();
    ObjectOutputStream os = new ObjectOutputStream(bs);
    try {
      os.writeObject(this);

      byte[] bytes = bs.toByteArray();
      out.writeInt(bytes.length);
      out.write(bytes);
    }
    finally {
      os.close();
      bs.close();
    }
  }

  protected MavenProject() {
  }

  public MavenProject(VirtualFile file) {
    myFile = file;
    myIncluded = true;
  }

  private synchronized void set(MavenProjectReaderResult readerResult, boolean resetDependencies) {
    resetProblemsCache();

    myLastReadStamp++;

    myValid = readerResult.isValid;
    myActiveProfilesIds = readerResult.activeProfiles;
    myReadingProblems = readerResult.readingProblems;
    myUnresolvedArtifactIds = readerResult.unresolvedArtifactIds;
    myLocalRepository = readerResult.localRepository;

    org.apache.maven.project.MavenProject nativeMavenProject = readerResult.nativeMavenProject;
    Model model = nativeMavenProject.getModel();

    myMavenId = new MavenId(model.getGroupId(),
                            model.getArtifactId(),
                            model.getVersion());

    Parent parent = model.getParent();
    myParentId = parent != null
                 ? new MavenId(parent.getGroupId(), parent.getArtifactId(), parent.getVersion())
                 : null;

    myPackaging = model.getPackaging();
    myName = model.getName();

    Build build = model.getBuild();

    myFinalName = build.getFinalName();
    myDefaultGoal = build.getDefaultGoal();

    myBuildDirectory = build.getDirectory();
    myOutputDirectory = build.getOutputDirectory();
    myTestOutputDirectory = build.getTestOutputDirectory();

    setFolders(readerResult);

    myFilters = nonNull(build, build.getFilters());
    myProperties = model.getProperties() != null ? model.getProperties() : new Properties();

    myRemoteRepositories = convertRepositories(model.getRepositories());
    myPlugins = collectPlugins(model);
    myExtensions = convertArtifacts(nativeMavenProject.getExtensionArtifacts());
    setDependencies(readerResult, resetDependencies);

    myModulesPathsAndNames = collectModulePathsAndNames(model);

    myProfilesIds = collectProfilesIds(model);

    myStrippedMavenModel = MavenUtil.cloneObject(model);
    MavenUtil.stripDown(myStrippedMavenModel);
  }

  private List nonNull(Build build, List list) {
    return list == null ? Collections.<String>emptyList() : list;
  }

  private synchronized void setDependencies(MavenProjectReaderResult readerResult, boolean reset) {
    resetProblemsCache();

    org.apache.maven.project.MavenProject nativeMavenProject = readerResult.nativeMavenProject;
    List<MavenArtifact> newDependencies = convertArtifacts(nativeMavenProject.getArtifacts());
    if (!reset && myDependencies != null) {
      LinkedHashSet<MavenArtifact> set = new LinkedHashSet<MavenArtifact>(newDependencies);
      set.addAll(myDependencies);
      newDependencies = new ArrayList<MavenArtifact>(set);
    }
    myDependencies = newDependencies;
  }

  private synchronized void setFolders(MavenProjectReaderResult readerResult) {
    resetProblemsCache();

    org.apache.maven.project.MavenProject nativeMavenProject = readerResult.nativeMavenProject;

    mySources = new ArrayList<String>(nativeMavenProject.getCompileSourceRoots());
    myTestSources = new ArrayList<String>(nativeMavenProject.getTestCompileSourceRoots());

    myResources = convertResources(nativeMavenProject.getResources());
    myTestResources = convertResources(nativeMavenProject.getTestResources());
  }

  private void resetProblemsCache() {
    myAllProblemsCache = null;
  }

  private List<MavenResource> convertResources(List<Resource> resources) {
    if (resources == null) return new ArrayList<MavenResource>();

    List<MavenResource> result = new ArrayList<MavenResource>(resources.size());
    for (Resource each : resources) {
      result.add(new MavenResource(each));
    }
    return result;
  }

  private List<MavenRemoteRepository> convertRepositories(List<Repository> repositories) {
    if (repositories == null) return new ArrayList<MavenRemoteRepository>();

    List<MavenRemoteRepository> result = new ArrayList<MavenRemoteRepository>(repositories.size());
    for (Repository each : repositories) {
      result.add(new MavenRemoteRepository(each));
    }
    return result;
  }

  private List<MavenArtifact> convertArtifacts(Collection<Artifact> artifacts) {
    if (artifacts == null) return new ArrayList<MavenArtifact>();

    List<MavenArtifact> result = new ArrayList<MavenArtifact>(artifacts.size());
    for (Artifact each : artifacts) {
      result.add(new MavenArtifact(each));
    }
    return result;
  }

  private List<MavenPlugin> collectPlugins(Model mavenModel) {
    List<MavenPlugin> result = new ArrayList<MavenPlugin>();
    collectPlugins(mavenModel.getBuild(), result);
    for (Profile profile : collectActiveProfiles(mavenModel)) {
      if (getActiveProfilesIds().contains(profile.getId())) {
        collectPlugins(profile.getBuild(), result);
      }
    }
    return result;
  }

  private void collectPlugins(BuildBase build, List<MavenPlugin> result) {
    if (build == null) return;

    List<Plugin> plugins = (List<Plugin>)build.getPlugins();
    if (plugins == null) return;

    for (Plugin each : plugins) {
      result.add(new MavenPlugin(each));
    }
  }

  private Map<String, String> collectModulePathsAndNames(Model mavenModel) {
    String basePath = getDirectory() + "/";
    Map<String, String> result = new LinkedHashMap<String, String>();
    for (Map.Entry<String, String> each : collectModulesRelativePathsAndNames(mavenModel).entrySet()) {
      result.put(new Path(basePath + each.getKey()).getPath(), each.getValue());
    }
    return result;
  }

  private Map<String, String> collectModulesRelativePathsAndNames(Model mavenModel) {
    LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
    addModulesToList(mavenModel.getModules(), result);
    for (Profile profile : collectActiveProfiles(mavenModel)) {
      addModulesToList(profile.getModules(), result);
    }
    return result;
  }

  private void addModulesToList(List moduleNames, LinkedHashMap<String, String> result) {
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

  private List<Profile> collectActiveProfiles(Model mavenModel) {
    List<Profile> result = new ArrayList<Profile>(myActiveProfilesIds.size());
    for (Profile each : collectProfiles(mavenModel)) {
      if (myActiveProfilesIds.contains(each.getId())) result.add(each);
    }
    return result;
  }

  private List<String> collectProfilesIds(Model mavenModel) {
    List<Profile> profiles = collectProfiles(mavenModel);
    Set<String> result = new HashSet<String>(profiles.size());
    for (Profile each : profiles) {
      result.add(each.getId());
    }
    return new ArrayList<String>(result);
  }

  private List<Profile> collectProfiles(Model mavenModel) {
    List<Profile> profiles = (List<Profile>)mavenModel.getProfiles();
    return profiles == null ? Collections.<Profile>emptyList() : profiles;
  }

  public synchronized boolean isIncluded() {
    return myIncluded;
  }

  public synchronized void setIncluded(boolean included) {
    myIncluded = included;
  }

  public synchronized boolean isValid() {
    return myValid;
  }

  public long getLastReadStamp() {
    return myLastReadStamp;
  }

  public synchronized VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  public synchronized String getPath() {
    return myFile.getPath();
  }

  public synchronized String getDirectory() {
    return myFile.getParent().getPath();
  }

  public synchronized VirtualFile getDirectoryFile() {
    return myFile.getParent();
  }

  public synchronized List<String> getActiveProfilesIds() {
    return myActiveProfilesIds;
  }

  public synchronized String getName() {
    return myName;
  }

  public synchronized String getDisplayName() {
    if (StringUtil.isEmptyOrSpaces(myName)) return myMavenId.artifactId;
    return myName;
  }

  public synchronized Model getMavenModel() {
    return myStrippedMavenModel;
  }

  public synchronized MavenId getMavenId() {
    return myMavenId;
  }

  public synchronized MavenId getParentId() {
    return myParentId;
  }

  public synchronized String getPackaging() {
    return myPackaging;
  }

  public synchronized String getFinalName() {
    return myFinalName;
  }

  public synchronized String getDefaultGoal() {
    return myDefaultGoal;
  }

  public synchronized String getBuildDirectory() {
    return myBuildDirectory;
  }

  public synchronized String getGeneratedSourcesDirectory() {
    return getBuildDirectory() + "/generated-sources";
  }

  public synchronized String getOutputDirectory() {
    return myOutputDirectory;
  }

  public synchronized String getTestOutputDirectory() {
    return myTestOutputDirectory;
  }

  public synchronized List<String> getSources() {
    return mySources;
  }

  public synchronized List<String> getTestSources() {
    return myTestSources;
  }

  public synchronized List<MavenResource> getResources() {
    return myResources;
  }

  public synchronized List<MavenResource> getTestResources() {
    return myTestResources;
  }

  public synchronized List<String> getFilters() {
    return myFilters;
  }

  public void read(MavenGeneralSettings generalSettings, List<String> profiles, MavenProjectReaderProjectLocator locator) {
    set(new MavenProjectReader().readProject(generalSettings, myFile, profiles, locator), false);
  }

  public org.apache.maven.project.MavenProject resolve(MavenGeneralSettings generalSettings,
                                                       MavenEmbedderWrapper embedder,
                                                       MavenProjectReaderProjectLocator locator,
                                                       MavenProcess process) throws MavenProcessCanceledException {
    MavenProjectReaderResult result = new MavenProjectReader().resolveProject(generalSettings,
                                                                              embedder,
                                                                              getFile(),
                                                                              getActiveProfilesIds(),
                                                                              locator,
                                                                              process);
    set(result, result.isValid);
    return result.nativeMavenProject;
  }

  public void generateSources(MavenEmbedderWrapper embedder, MavenImportingSettings importingSettings, MavenConsole console, MavenProcess p)
    throws MavenProcessCanceledException {
    MavenProjectReaderResult result = new MavenProjectReader().generateSources(embedder,
                                                                               importingSettings,
                                                                               getFile(),
                                                                               getActiveProfilesIds(),
                                                                               console,
                                                                               p);
    if (result != null && result.isValid) setFolders(result);
  }

  public synchronized boolean isAggregator() {
    return "pom".equals(getPackaging()) || !getModulePaths().isEmpty();
  }

  public synchronized List<MavenProjectProblem> getProblems() {
    if (myAllProblemsCache == null) {
      myAllProblemsCache = collectProblems(myReadingProblems);
    }
    return myAllProblemsCache;
  }

  private List<MavenProjectProblem> collectProblems(List<MavenProjectProblem> readingProblems) {
    List<MavenProjectProblem> result = new ArrayList<MavenProjectProblem>();

    validateParent(result);
    result.addAll(readingProblems);

    for (Map.Entry<String, String> each : getModulesPathsAndNames().entrySet()) {
      if (LocalFileSystem.getInstance().findFileByPath(each.getKey()) == null) {
        result.add(new MavenProjectProblem(ProjectBundle.message("maven.project.problem.missingModule", each.getValue()), false));
      }
    }

    validate(getDependencies(), "maven.project.problem.unresolvedDependency", result);
    validateExtensions(result);
    validatePlugins(result);

    return result;
  }

  private void validateParent(List<MavenProjectProblem> result) {
    // todo try to check the parent the other way
    MavenId parentId = getParentId();
    if (myUnresolvedArtifactIds.contains(parentId)) {
      result.add(new MavenProjectProblem(ProjectBundle.message("maven.project.problem.parentNotFound", parentId), true));
    }
  }

  private void validate(List<MavenArtifact> artifacts, String messageKey, List<MavenProjectProblem> result) {
    for (MavenArtifact each : artifacts) {
      if (!each.isResolved()) {
        result.add(new MavenProjectProblem(ProjectBundle.message(messageKey, each.displayStringWithType()), false));
      }
    }
  }

  private void validateExtensions(List<MavenProjectProblem> result) {
    Set<MavenId> unresolvedArtifacts = myUnresolvedArtifactIds;

    for (MavenArtifact each : myExtensions) {
      // Only collect extensions that were attempted to be resolved.
      // It is because embedder does not even try to resolve extensions that
      // are not necessary.
      if (unresolvedArtifacts.contains(each.getMavenId()) && !pomFileExists(each)) {
        result.add(new MavenProjectProblem(ProjectBundle.message("maven.project.problem.unresolvedExtension", each.displayStringSimple()),
                                           false));
      }
    }
  }

  private boolean pomFileExists(MavenArtifact artifact) {
    return MavenArtifactUtil.hasArtifactFile(myLocalRepository, artifact.getMavenId(), "pom");
  }

  private void validatePlugins(List<MavenProjectProblem> result) {
    for (MavenPlugin each : getPlugins()) {
      if (!MavenArtifactUtil.hasArtifactFile(myLocalRepository, each.getMavenId())) {
        String string = each.getGroupId() + ":" + each.getArtifactId() + ":" + each.getVersion();
        result.add(new MavenProjectProblem(ProjectBundle.message("maven.project.problem.unresolvedPlugin", each), false));
      }
    }
  }

  public synchronized List<VirtualFile> getExistingModuleFiles() {
    LocalFileSystem fs = LocalFileSystem.getInstance();

    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (String each : getModulePaths()) {
      VirtualFile f = fs.findFileByPath(each);
      if (f != null) result.add(f);
    }
    return result;
  }

  public synchronized List<String> getModulePaths() {
    return new ArrayList<String>(getModulesPathsAndNames().keySet());
  }

  public synchronized Map<String, String> getModulesPathsAndNames() {
    return myModulesPathsAndNames;
  }

  public synchronized List<String> getProfilesIds() {
    return myProfilesIds;
  }

  public synchronized List<MavenArtifact> getDependencies() {
    return myDependencies;
  }

  public synchronized boolean isSupportedDependency(MavenArtifact artifact) {
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

  public synchronized MavenArtifact findDependency(String groupId, String artifactId) {
    for (MavenArtifact each : getDependencies()) {
      if (groupId.equals(each.getGroupId()) && artifactId.equals((artifactId))) return each;
    }
    return null;
  }

  public synchronized MavenDomDependency addDependency(Project p, MavenArtifact artifact) {
    MavenDomProjectModel model = MavenUtil.getMavenModel(p, getFile());

    MavenDomDependency result = model.getDependencies().addDependency();
    result.getGroupId().setStringValue(artifact.getGroupId());
    result.getArtifactId().setStringValue(artifact.getArtifactId());
    result.getVersion().setStringValue(artifact.getVersion());

    myDependencies.add(artifact);
    return result;
  }

  public synchronized List<MavenPlugin> getPlugins() {
    return myPlugins;
  }

  @Nullable
  public synchronized String findPluginConfigurationValue(String groupId,
                                                          String artifactId,
                                                          String path) {
    Element node = findPluginConfigurationElement(groupId, artifactId, path);
    return node == null ? null : node.getValue();
  }

  @Nullable
  public synchronized Element findPluginConfigurationElement(String groupId, String artifactId, String path) {
    return doFindPluginOrGoalConfigurationElement(groupId, artifactId, null, path);
  }

  @Nullable
  public synchronized String findPluginGoalConfigurationValue(String groupId, String artifactId, String goal, String path) {
    Element node = findPluginGoalConfigurationElement(groupId, artifactId, goal, path);
    return node == null ? null : node.getValue();
  }

  @Nullable
  public synchronized Element findPluginGoalConfigurationElement(String groupId, String artifactId, String goal, String path) {
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
  public synchronized MavenPlugin findPlugin(String groupId, String artifactId) {
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
    public static Map<String, String> table = new HashMap<String, String>();

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

  public synchronized Properties getProperties() {
    return myProperties;
  }

  public synchronized List<MavenRemoteRepository> getRemoteRepositories() {
    return myRemoteRepositories;
  }

  public synchronized List<FacetImporter> getSuitableFacetImporters() {
    return FacetImporter.getSuitableFacetImporters(this);
  }
}
