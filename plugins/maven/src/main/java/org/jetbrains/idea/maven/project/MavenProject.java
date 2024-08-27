// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType;
import org.jetbrains.idea.maven.importing.MavenImporter;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.MavenGoalExecutionResult;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.*;
import java.util.*;
import java.util.function.Predicate;

import static org.jetbrains.idea.maven.model.MavenProjectProblem.ProblemType.SYNTAX;

@SuppressWarnings({"SynchronizationOnLocalVariableOrMethodParameter", "SynchronizeOnNonFinalField"})
public class MavenProject {
  private static final Key<MavenArtifactIndex> DEPENDENCIES_CACHE_KEY = Key.create("MavenProject.DEPENDENCIES_CACHE_KEY");
  private static final Key<List<String>> FILTERS_CACHE_KEY = Key.create("MavenProject.FILTERS_CACHE_KEY");

  public enum ConfigFileKind {
    MAVEN_CONFIG(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH, "true"),
    JVM_CONFIG(MavenConstants.JVM_CONFIG_RELATIVE_PATH, "");
    final Key<Map<String, String>> CACHE_KEY = Key.create("MavenProject." + name());
    final String myRelativeFilePath;
    final String myValueIfMissing;

    ConfigFileKind(String relativeFilePath, String valueIfMissing) {
      myRelativeFilePath = relativeFilePath;
      myValueIfMissing = valueIfMissing;
    }
  }

  private final @NotNull VirtualFile myFile;
  private volatile @NotNull MavenProjectState myState = new MavenProjectState();

  public enum ProcMode {BOTH, ONLY, NONE}

  public static @Nullable MavenProject read(DataInputStream in) throws IOException {
    String path = in.readUTF();
    int length = in.readInt();
    byte[] bytes = new byte[length];
    in.readFully(bytes);

    // should read full byte content first!!!

    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    if (file == null) return null;

    try (ByteArrayInputStream bs = new ByteArrayInputStream(bytes);
         ObjectInputStream os = new ObjectInputStream(bs)) {
      MavenProject result = new MavenProject(file);
      result.myState = (MavenProjectState)os.readObject();
      return result;
    }
    catch (ClassNotFoundException e) {
      throw new IOException(e);
    }
  }

  public void write(@NotNull DataOutputStream out) throws IOException {
    out.writeUTF(getPath());

    try (BufferExposingByteArrayOutputStream bs = new BufferExposingByteArrayOutputStream();
         ObjectOutputStream os = new ObjectOutputStream(bs)) {
      os.writeObject(myState);

      out.writeInt(bs.size());
      out.write(bs.getInternalBuffer(), 0, bs.size());
    }
  }

  public MavenProject(@NotNull VirtualFile file) {
    myFile = file;
  }

  @NotNull
  @ApiStatus.Internal
  public MavenProjectChanges updateFromReaderResult(@NotNull MavenProjectReaderResult readerResult,
                                                    @NotNull MavenGeneralSettings settings,
                                                    boolean keepPreviousArtifacts) {
    MavenProjectState newState = myState.clone();

    newState.incLastReadStamp();

    boolean keepPreviousPlugins = keepPreviousArtifacts;

    newState.doUpdateState(
      readerResult.mavenModel,
      readerResult.readingProblems,
      readerResult.activatedProfiles,
      Set.of(),
      readerResult.nativeModelMap,
      settings,
      keepPreviousArtifacts,
      false,
      keepPreviousPlugins,
      getDirectory(),
      myFile.getExtension()
    );

    return setState(newState);
  }

  @NotNull
  @ApiStatus.Internal
  public MavenProjectChanges updateState(@NotNull MavenModel model,
                                         @Nullable String dependencyHash,
                                         @NotNull Collection<@NotNull MavenProjectProblem> readingProblems,
                                         @NotNull MavenExplicitProfiles activatedProfiles,
                                         @NotNull Set<MavenId> unresolvedArtifactIds,
                                         @NotNull Map<@NotNull String, @Nullable String> nativeModelMap,
                                         @NotNull MavenGeneralSettings settings,
                                         boolean keepPreviousArtifacts,
                                         boolean keepPreviousPlugins) {
    MavenProjectState newState = myState.clone();

    if (null != dependencyHash) {
      newState.setDependencyHash(dependencyHash);
    }

    newState.doUpdateState(
      model,
      readingProblems,
      activatedProfiles,
      unresolvedArtifactIds,
      nativeModelMap,
      settings,
      keepPreviousArtifacts,
      true,
      keepPreviousPlugins,
      getDirectory(),
      myFile.getExtension()
    );

    return setState(newState);
  }

  @NotNull
  @ApiStatus.Internal
  public MavenProjectChanges updateState(
    @NotNull List<MavenArtifact> dependencies,
    @NotNull Properties properties,
    @NotNull List<MavenPlugin> plugins
    ) {
    MavenProjectState newState = myState.clone();

    newState.doUpdateState(dependencies, properties, plugins);

    return setState(newState);
  }

  @NotNull
  @ApiStatus.Internal
  public MavenProjectChanges updateState(@NotNull Collection<@NotNull MavenProjectProblem> readingProblems) {
    MavenProjectState newState = myState.clone();

    newState.setReadingProblems(readingProblems);

    return setState(newState);
  }

  private MavenProjectChanges setState(MavenProjectState newState) {
    MavenProjectChanges changes = myState.getChanges(newState);
    myState = newState;
    return changes;
  }

  private void updateState(Consumer<MavenProjectState> updater) {
    MavenProjectState newState = myState.clone();
    updater.consume(newState);
    myState = newState;
  }

  static class Snapshot {
    @NotNull private final MavenProjectState myState;

    private Snapshot(@NotNull MavenProjectState state) {
      myState = state;
    }
  }

  @NotNull
  Snapshot getSnapshot() {
    return new Snapshot(myState);
  }

  MavenProjectChanges getChangesSinceSnapshot(@NotNull Snapshot snapshot) {
    return snapshot.myState.getChanges(myState);
  }

  @ApiStatus.Internal
  public MavenProjectChanges setFolders(MavenGoalExecutionResult.Folders folders) {
    MavenProjectState newState = myState.clone();
    newState.doSetFolders(folders.getSources(), folders.getTestSources(), folders.getResources(), folders.getTestResources());
    return setState(newState);
  }

  public long getLastReadStamp() {
    return myState.getLastReadStamp();
  }

  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  public @NotNull @NonNls String getPath() {
    return myFile.getPath();
  }

  public @NotNull @NonNls String getDirectory() {
    return myFile.getParent().getPath();
  }

  public @NotNull VirtualFile getDirectoryFile() {
    return myFile.getParent();
  }

  public @Nullable VirtualFile getProfilesXmlFile() {
    return MavenUtil.findProfilesXmlFile(myFile);
  }

  public @Nullable File getProfilesXmlIoFile() {
    return MavenUtil.getProfilesXmlIoFile(myFile);
  }

  public boolean hasReadingProblems() {
    return !myState.getReadingProblems().isEmpty();
  }

  public @Nullable @NlsSafe String getName() {
    return myState.getName();
  }

  public @NotNull @NlsSafe String getDisplayName() {
    MavenProjectState state = myState;
    if (StringUtil.isEmptyOrSpaces(state.getName())) {
      return StringUtil.notNullize(state.getMavenId().getArtifactId());
    }
    return state.getName();
  }

  public @NotNull Map<String, String> getModelMap() {
    return myState.getModelMap();
  }

  public @NotNull MavenId getMavenId() {
    return myState.getMavenId();
  }

  boolean isNew() {
    return null == myState.getMavenId();
  }

  public @Nullable MavenId getParentId() {
    return myState.getParentId();
  }

  public @NotNull @NlsSafe String getPackaging() {
    return myState.getPackaging();
  }

  public @NotNull @NlsSafe String getFinalName() {
    return myState.getFinalName();
  }

  public @Nullable @NlsSafe String getDefaultGoal() {
    return myState.getDefaultGoal();
  }

  public @NotNull @NlsSafe String getBuildDirectory() {
    return myState.getBuildDirectory();
  }

  public @NotNull @NlsSafe String getGeneratedSourcesDirectory(boolean testSources) {
    return getBuildDirectory() + (testSources ? "/generated-test-sources" : "/generated-sources");
  }

  public @NotNull @NlsSafe String getAnnotationProcessorDirectory(boolean testSources) {
    if (getProcMode() == ProcMode.NONE) {
      MavenPlugin bscMavenPlugin = findPlugin("org.bsc.maven", "maven-processor-plugin");
      Element cfg = getPluginGoalConfiguration(bscMavenPlugin, testSources ? "process-test" : "process");
      if (bscMavenPlugin != null && cfg == null) {
        return getBuildDirectory() + (testSources ? "/generated-sources/apt-test" : "/generated-sources/apt");
      }
      if (cfg != null) {
        String out = MavenJDOMUtil.findChildValueByPath(cfg, "outputDirectory");
        if (out == null) {
          out = MavenJDOMUtil.findChildValueByPath(cfg, "defaultOutputDirectory");
          if (out == null) {
            return getBuildDirectory() + (testSources ? "/generated-sources/apt-test" : "/generated-sources/apt");
          }
        }

        if (!new File(out).isAbsolute()) {
          out = getDirectory() + '/' + out;
        }

        return out;
      }
    }

    String def = getGeneratedSourcesDirectory(testSources) + (testSources ? "/test-annotations" : "/annotations");
    return MavenJDOMUtil.findChildValueByPath(
      getCompilerConfig(), testSources ? "generatedTestSourcesDirectory" : "generatedSourcesDirectory", def);
  }

  public @NotNull ProcMode getProcMode() {
    Element compilerConfiguration = getPluginExecutionConfiguration("org.apache.maven.plugins", "maven-compiler-plugin", "default-compile");
    if (compilerConfiguration == null) {
      compilerConfiguration = getCompilerConfig();
    }

    if (compilerConfiguration == null) {
      return ProcMode.BOTH;
    }

    Element procElement = compilerConfiguration.getChild("proc");
    if (procElement != null) {
      String procMode = procElement.getValue();
      return ("only".equalsIgnoreCase(procMode)) ? ProcMode.ONLY : ("none".equalsIgnoreCase(procMode)) ? ProcMode.NONE : ProcMode.BOTH;
    }

    String compilerArgument = compilerConfiguration.getChildTextTrim("compilerArgument");
    if ("-proc:none".equals(compilerArgument)) {
      return ProcMode.NONE;
    }
    if ("-proc:only".equals(compilerArgument)) {
      return ProcMode.ONLY;
    }

    Element compilerArguments = compilerConfiguration.getChild("compilerArgs");
    if (compilerArguments != null) {
      for (Element element : compilerArguments.getChildren()) {
        String arg = element.getValue();
        if ("-proc:none".equals(arg)) {
          return ProcMode.NONE;
        }
        if ("-proc:only".equals(arg)) {
          return ProcMode.ONLY;
        }
      }
    }

    return ProcMode.BOTH;
  }

  public Map<String, String> getAnnotationProcessorOptions() {
    Element compilerConfig = getCompilerConfig();
    if (compilerConfig == null) {
      return Collections.emptyMap();
    }
    if (getProcMode() != MavenProject.ProcMode.NONE) {
      return getAnnotationProcessorOptionsFromCompilerConfig(compilerConfig);
    }
    MavenPlugin bscMavenPlugin = findPlugin("org.bsc.maven", "maven-processor-plugin");
    if (bscMavenPlugin != null) {
      return getAnnotationProcessorOptionsFromProcessorPlugin(bscMavenPlugin);
    }
    return Collections.emptyMap();
  }

  private static Map<String, String> getAnnotationProcessorOptionsFromCompilerConfig(Element compilerConfig) {
    Map<String, String> res = new LinkedHashMap<>();

    String compilerArgument = compilerConfig.getChildText("compilerArgument");
    addAnnotationProcessorOptionFromParameterString(compilerArgument, res);

    Element compilerArgs = compilerConfig.getChild("compilerArgs");
    if (compilerArgs != null) {
      for (Element e : compilerArgs.getChildren()) {
        if (!StringUtil.equals(e.getName(), "arg")) continue;
        String arg = e.getTextTrim();
        addAnnotationProcessorOption(arg, res);
      }
    }

    Element compilerArguments = compilerConfig.getChild("compilerArguments");
    if (compilerArguments != null) {
      for (Element e : compilerArguments.getChildren()) {
        String name = e.getName();
        name = StringUtil.trimStart(name, "-");

        if (name.length() > 1 && name.charAt(0) == 'A') {
          res.put(name.substring(1), e.getTextTrim());
        }
      }
    }
    return res;
  }

  private static void addAnnotationProcessorOptionFromParameterString(String compilerArguments, Map<String, String> res) {
    if (!StringUtil.isEmptyOrSpaces(compilerArguments)) {
      ParametersList parametersList = new ParametersList();
      parametersList.addParametersString(compilerArguments);

      for (String param : parametersList.getParameters()) {
        addAnnotationProcessorOption(param, res);
      }
    }
  }

  private static void addAnnotationProcessorOption(String compilerArg, Map<String, String> optionsMap) {
    if (compilerArg == null || compilerArg.trim().isEmpty()) return;

    if (compilerArg.startsWith("-A")) {
      int idx = compilerArg.indexOf('=', 3);
      if (idx >= 0) {
        optionsMap.put(compilerArg.substring(2, idx), compilerArg.substring(idx + 1));
      }
      else {
        optionsMap.put(compilerArg.substring(2), "");
      }
    }
  }

  private static Map<String, String> getAnnotationProcessorOptionsFromProcessorPlugin(MavenPlugin bscMavenPlugin) {
    Element cfg = bscMavenPlugin.getGoalConfiguration("process");
    if (cfg == null) {
      cfg = bscMavenPlugin.getConfigurationElement();
    }
    LinkedHashMap<String, String> res = new LinkedHashMap<>();
    if (cfg != null) {
      String compilerArguments = cfg.getChildText("compilerArguments");
      addAnnotationProcessorOptionFromParameterString(compilerArguments, res);

      final Element optionsElement = cfg.getChild("options");
      if (optionsElement != null) {
        for (Element option : optionsElement.getChildren()) {
          res.put(option.getName(), option.getText());
        }
      }
    }
    return res;
  }

  public @Nullable List<@NlsSafe String> getDeclaredAnnotationProcessors() {
    Element compilerConfig = getCompilerConfig();
    if (compilerConfig == null) {
      return null;
    }

    List<String> result = new ArrayList<>();
    if (getProcMode() != MavenProject.ProcMode.NONE) {
      Element processors = compilerConfig.getChild("annotationProcessors");
      if (processors != null) {
        for (Element element : processors.getChildren("annotationProcessor")) {
          String processorClassName = element.getTextTrim();
          if (!processorClassName.isEmpty()) {
            result.add(processorClassName);
          }
        }
      }
    }
    else {
      MavenPlugin bscMavenPlugin = findPlugin("org.bsc.maven", "maven-processor-plugin");
      if (bscMavenPlugin != null) {
        Element bscCfg = bscMavenPlugin.getGoalConfiguration("process");
        if (bscCfg == null) {
          bscCfg = bscMavenPlugin.getConfigurationElement();
        }

        if (bscCfg != null) {
          Element bscProcessors = bscCfg.getChild("processors");
          if (bscProcessors != null) {
            for (Element element : bscProcessors.getChildren("processor")) {
              String processorClassName = element.getTextTrim();
              if (!processorClassName.isEmpty()) {
                result.add(processorClassName);
              }
            }
          }
        }
      }
    }

    return result;
  }

  public @NotNull @NlsSafe String getOutputDirectory() {
    return myState.getOutputDirectory();
  }

  public @NotNull @NlsSafe String getTestOutputDirectory() {
    return myState.getTestOutputDirectory();
  }

  public @NotNull List<@NlsSafe String> getSources() {
    return myState.getSources();
  }

  public @NotNull List<@NlsSafe String> getTestSources() {
    return myState.getTestSources();
  }

  public @NotNull List<MavenResource> getResources() {
    return myState.getResources();
  }

  public @NotNull List<MavenResource> getTestResources() {
    return myState.getTestResources();
  }

  public @NotNull List<@NlsSafe String> getFilters() {
    return myState.getFilters();
  }

  public List<@NlsSafe String> getFilterPropertiesFiles() {
    List<String> res = getCachedValue(FILTERS_CACHE_KEY);
    if (res == null) {
      Element propCfg = getPluginGoalConfiguration("org.codehaus.mojo", "properties-maven-plugin", "read-project-properties");
      if (propCfg != null) {
        Element files = propCfg.getChild("files");
        if (files != null) {
          res = new ArrayList<>();

          for (Element file : files.getChildren("file")) {
            File f = new File(file.getValue());
            if (!f.isAbsolute()) {
              f = new File(getDirectory(), file.getValue());
            }

            res.add(f.getAbsolutePath());
          }
        }
      }

      if (res == null) {
        res = getFilters();
      }
      else {
        res.addAll(getFilters());
      }

      res = putCachedValue(FILTERS_CACHE_KEY, res);
    }

    return res;
  }

  public void setConfigFileError(@Nullable String message) {
    if (message != null) {
      String mavenConfigPath = myFile.getPath() + "/" + MavenConstants.MAVEN_CONFIG_RELATIVE_PATH;
      myState.getReadingProblems().add(new MavenProjectProblem(mavenConfigPath, message, SYNTAX, true));
    }
  }

  public @Nullable String getConfigFileError() {
    return myState.getReadingProblems().stream().filter(p -> p.getPath().endsWith(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH))
      .map(p -> p.getDescription())
      .findFirst()
      .orElse(null);
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

  public @NotNull List<MavenProjectProblem> getProblems() {
    var problems = myState.getProblemsCache();
    if (null != problems) return problems;

    return collectProblems(null);
  }

  @ApiStatus.Internal
  public @NotNull List<MavenProjectProblem> collectProblems(Predicate<File> fileExistsPredicate) {
    return myState.collectProblems(myFile, fileExistsPredicate);
  }

  public @NotNull List<MavenProjectProblem> getCacheProblems() {
    List<MavenProjectProblem> problemsCache = myState.getProblemsCache();
    return problemsCache == null ? Collections.emptyList() : problemsCache;
  }

  public @NotNull List<VirtualFile> getExistingModuleFiles() {
    LocalFileSystem fs = LocalFileSystem.getInstance();

    List<VirtualFile> result = new ArrayList<>();
    Set<String> pathsInStack = getModulePaths();
    for (String each : pathsInStack) {
      VirtualFile f = fs.findFileByPath(each);
      if (f != null) result.add(f);
    }
    return result;
  }

  public @NotNull Set<String> getModulePaths() {
    return getModulesPathsAndNames().keySet();
  }

  public @NotNull Map<String, String> getModulesPathsAndNames() {
    return myState.getModulesPathsAndNames();
  }

  public @NotNull Collection<String> getProfilesIds() {
    return myState.getProfilesIds();
  }

  public @NotNull MavenExplicitProfiles getActivatedProfilesIds() {
    return myState.getActivatedProfilesIds();
  }

  public @Nullable String getDependencyHash() {
    return myState.getDependencyHash();
  }

  public @NotNull List<MavenArtifact> getDependencies() {
    return myState.getDependencies();
  }

  public @NotNull List<MavenArtifact> getExternalAnnotationProcessors() {
    return myState.getAnnotationProcessors();
  }

  public @NotNull List<MavenArtifactNode> getDependencyTree() {
    return myState.getDependencyTree();
  }

  @SuppressWarnings("SpellCheckingInspection")
  public @NotNull Set<String> getSupportedPackagings() {
    Set<String> result = ContainerUtil.newHashSet(
      MavenConstants.TYPE_POM, MavenConstants.TYPE_JAR, "ejb", "ejb-client", "war", "ear", "bundle", "maven-plugin");
    for (MavenImporter each : MavenImporter.getSuitableImporters(this)) {
      each.getSupportedPackagings(result);
    }
    return result;
  }

  public Set<String> getDependencyTypesFromImporters(@NotNull SupportedRequestType type) {
    Set<String> res = new HashSet<>();

    for (MavenImporter each : MavenImporter.getSuitableImporters(this)) {
      each.getSupportedDependencyTypes(res, type);
    }

    return res;
  }

  public @NotNull Set<String> getSupportedDependencyScopes() {
    Set<String> result = ContainerUtil.newHashSet(MavenConstants.SCOPE_COMPILE,
                                                  MavenConstants.SCOPE_PROVIDED,
                                                  MavenConstants.SCOPE_RUNTIME,
                                                  MavenConstants.SCOPE_TEST,
                                                  MavenConstants.SCOPE_SYSTEM);
    for (MavenImporter each : MavenImporter.getSuitableImporters(this)) {
      each.getSupportedDependencyScopes(result);
    }
    return result;
  }

  public void addDependency(@NotNull MavenArtifact dependency) {
    addDependencies(List.of(dependency));
  }

  public void addDependencies(@NotNull Collection<MavenArtifact> dependencies) {
    updateState(newState -> newState.getDependencies().addAll(dependencies));
  }

  public void addAnnotationProcessors(@NotNull Collection<MavenArtifact> annotationProcessors) {
    updateState(newState -> newState.getAnnotationProcessors().addAll(annotationProcessors));
  }

  public @NotNull List<MavenArtifact> findDependencies(@NotNull MavenProject depProject) {
    return findDependencies(depProject.getMavenId());
  }

  public List<MavenArtifact> findDependencies(@NotNull MavenId id) {
    return getDependencyArtifactIndex().findArtifacts(id);
  }

  public @NotNull List<MavenArtifact> findDependencies(@NonNls @Nullable String groupId, @NonNls @Nullable String artifactId) {
    return getDependencyArtifactIndex().findArtifacts(groupId, artifactId);
  }

  public boolean hasDependency(@NonNls @Nullable String groupId, @NonNls @Nullable String artifactId) {
    return getDependencyArtifactIndex().hasArtifact(groupId, artifactId);
  }

  public boolean hasUnresolvedArtifacts() {
    return myState.hasUnresolvedArtifacts();
  }

  public boolean hasUnresolvedPlugins() {
    return !myState.getUnresolvedPlugins().isEmpty();
  }

  public @NotNull List<MavenPlugin> getPlugins() {
    return myState.getPlugins();
  }

  public @NotNull List<MavenPlugin> getDeclaredPlugins() {
    return myState.getDeclaredPlugins();
  }

  public @Nullable Element getPluginConfiguration(@Nullable String groupId, @Nullable String artifactId) {
    return getPluginGoalConfiguration(groupId, artifactId, null);
  }

  public @Nullable Element getPluginGoalConfiguration(@Nullable String groupId, @Nullable String artifactId, @Nullable String goal) {
    return getPluginGoalConfiguration(findPlugin(groupId, artifactId), goal);
  }

  public @Nullable Element getPluginGoalConfiguration(@Nullable MavenPlugin plugin, @Nullable String goal) {
    if (plugin == null) return null;
    return goal == null ? plugin.getConfigurationElement() : plugin.getGoalConfiguration(goal);
  }

  private Element getPluginExecutionConfiguration(@Nullable String groupId, @Nullable String artifactId, @NotNull String executionId) {
    MavenPlugin plugin = findPlugin(groupId, artifactId);
    if (plugin == null) return null;
    return plugin.getExecutionConfiguration(executionId);
  }

  @NotNull
  private List<Element> getCompileExecutionConfigurations() {
    MavenPlugin plugin = findPlugin("org.apache.maven.plugins", "maven-compiler-plugin");
    if (plugin == null) return Collections.emptyList();
    return plugin.getCompileExecutionConfigurations();
  }

  public @Nullable MavenPlugin findPlugin(@Nullable String groupId, @Nullable String artifactId) {
    return findPlugin(groupId, artifactId, false);
  }

  public @Nullable MavenPlugin findPlugin(@Nullable String groupId, @Nullable String artifactId, final boolean explicitlyDeclaredOnly) {
    final List<MavenPlugin> plugins = explicitlyDeclaredOnly ? getDeclaredPlugins() : getPlugins();
    for (MavenPlugin each : plugins) {
      if (each.getMavenId().equals(groupId, artifactId)) return each;
    }
    return null;
  }

  public @Nullable String getSourceEncoding() {
    return myState.getProperties().getProperty("project.build.sourceEncoding");
  }

  public @Nullable String getResourceEncoding(Project project) {
    Element pluginConfiguration = getPluginConfiguration("org.apache.maven.plugins", "maven-resources-plugin");
    if (pluginConfiguration != null) {

      String encoding = pluginConfiguration.getChildTextTrim("encoding");
      if (encoding == null) {
        return null;
      }

      if (encoding.startsWith("$")) {
        MavenDomProjectModel domModel = MavenDomUtil.getMavenDomProjectModel(project, this.getFile());
        if (domModel == null) {
          MavenLog.LOG.warn("cannot get MavenDomProjectModel to find encoding");
          return getSourceEncoding();
        }
        else {
          MavenPropertyResolver.resolve(encoding, domModel);
        }
      }
      else {
        return encoding;
      }
    }
    return getSourceEncoding();
  }

  public @Nullable String getSourceLevel() {
    return getCompilerLevel("source");
  }

  public @Nullable String getTargetLevel() {
    return getCompilerLevel("target");
  }

  public @Nullable String getReleaseLevel() {
    return getCompilerLevel("release");
  }

  public @Nullable String getTestSourceLevel() {
    return getCompilerLevel("testSource");
  }

  public @Nullable String getTestTargetLevel() {
    return getCompilerLevel("testTarget");
  }

  public @Nullable String getTestReleaseLevel() {
    return getCompilerLevel("testRelease");
  }

  private @Nullable String getCompilerLevel(String level) {
    List<Element> configs = getCompilerConfigs();
    if (configs.size() == 1) return getCompilerLevel(level, configs.get(0));

    return configs.stream()
      .map(element -> MavenJDOMUtil.findChildValueByPath(element, level))
      .filter(Objects::nonNull)
      .map(propertyValue -> LanguageLevel.parse(propertyValue))
      .map(languageLevel -> languageLevel == null ? LanguageLevel.HIGHEST : languageLevel)
      .max(Comparator.naturalOrder())
      .map(l -> l.toJavaVersion().toFeatureString())
      .orElseGet(() -> myState.getProperties().getProperty("maven.compiler." + level));
  }

  private String getCompilerLevel(String level, Element config) {
    String result = MavenJDOMUtil.findChildValueByPath(config, level);
    if (result == null) {
      result = myState.getProperties().getProperty("maven.compiler." + level);
    }
    return result;
  }

  private @Nullable Element getCompilerConfig() {
    Element executionConfiguration =
      getPluginExecutionConfiguration("org.apache.maven.plugins", "maven-compiler-plugin", "default-compile");
    if (executionConfiguration != null) return executionConfiguration;
    return getPluginConfiguration("org.apache.maven.plugins", "maven-compiler-plugin");
  }

  private @NotNull List<Element> getCompilerConfigs() {
    List<Element> configurations = getCompileExecutionConfigurations();
    if (!configurations.isEmpty()) return configurations;
    Element configuration = getPluginConfiguration("org.apache.maven.plugins", "maven-compiler-plugin");
    return ContainerUtil.createMaybeSingletonList(configuration);
  }

  public @NotNull Properties getProperties() {
    return myState.getProperties();
  }

  public @NotNull Map<String, String> getMavenConfig() {
    return getPropertiesFromConfig(ConfigFileKind.MAVEN_CONFIG);
  }

  private @NotNull Map<String, String> getPropertiesFromConfig(ConfigFileKind kind) {
    Map<String, String> mavenConfig = getCachedValue(kind.CACHE_KEY);
    if (mavenConfig == null) {
      mavenConfig = readConfigFile(MavenUtil.getBaseDir(getDirectoryFile()).toFile(), kind);
      putCachedValue(kind.CACHE_KEY, mavenConfig);
    }

    return mavenConfig;
  }

  public @NotNull Map<String, String> getJvmConfig() {
    return getPropertiesFromConfig(ConfigFileKind.JVM_CONFIG);
  }

  public static @NotNull Map<String, String> readConfigFile(final File baseDir, ConfigFileKind kind) {
    File configFile = new File(baseDir, FileUtil.toSystemDependentName(kind.myRelativeFilePath));

    ParametersList parametersList = new ParametersList();
    if (configFile.isFile()) {
      try {
        parametersList.addParametersString(FileUtil.loadFile(configFile, CharsetToolkit.UTF8));
      }
      catch (IOException ignore) {
      }
    }
    Map<String, String> config = parametersList.getProperties(kind.myValueIfMissing);
    return config.isEmpty() ? Collections.emptyMap() : config;
  }

  public @NotNull File getLocalRepository() {
    return myState.getLocalRepository();
  }

  public @NotNull List<MavenRemoteRepository> getRemoteRepositories() {
    return myState.getRemoteRepositories();
  }

  public @NotNull Pair<String, String> getClassifierAndExtension(@NotNull MavenArtifact artifact, @NotNull MavenExtraArtifactType type) {
    for (MavenImporter each : MavenImporter.getSuitableImporters(this)) {
      Pair<String, String> result = each.getExtraArtifactClassifierAndExtension(artifact, type);
      if (result != null) return result;
    }
    return Pair.create(type.getDefaultClassifier(), type.getDefaultExtension());
  }

  public MavenArtifactIndex getDependencyArtifactIndex() {
    MavenArtifactIndex res = getCachedValue(DEPENDENCIES_CACHE_KEY);
    if (res == null) {
      res = MavenArtifactIndex.build(getDependencies());
      res = putCachedValue(DEPENDENCIES_CACHE_KEY, res);
    }

    return res;
  }

  public @Nullable <V> V getCachedValue(Key<V> key) {
    @SuppressWarnings("unchecked") V v = (V)myState.getCache().get(key);
    return v;
  }

  public @NotNull <V> V putCachedValue(Key<V> key, @NotNull V value) {
    Object oldValue = myState.getCache().putIfAbsent(key, value);
    if (oldValue != null) {
      @SuppressWarnings("unchecked") V v = (V)oldValue;
      return v;
    }
    return value;
  }

  @Override
  public String toString() {
    return null == myState.getMavenId() ? myFile.getPath() : getMavenId().toString();
  }
}
