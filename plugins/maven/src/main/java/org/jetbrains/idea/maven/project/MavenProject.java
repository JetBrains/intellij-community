// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.execution.configurations.ParametersList;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
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
import org.jetbrains.idea.maven.plugins.api.MavenModelPropertiesPatcher;
import org.jetbrains.idea.maven.server.MavenGoalExecutionResult;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
  private volatile @NotNull State myState = new State();

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
      result.myState = (State)os.readObject();
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
    State newState = myState.clone();

    newState.myLastReadStamp = myState.myLastReadStamp + 1;

    boolean keepPreviousPlugins = keepPreviousArtifacts;

    doUpdateState(newState,
                  readerResult.mavenModel,
                  readerResult.readingProblems,
                  readerResult.activatedProfiles,
                  Set.of(),
                  readerResult.nativeModelMap,
                  settings,
                  keepPreviousArtifacts,
                  false,
                  keepPreviousPlugins
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
    State newState = myState.clone();

    if (null != dependencyHash) {
      newState.myDependencyHash = dependencyHash;
    }

    doUpdateState(newState,
                  model,
                  readingProblems,
                  activatedProfiles,
                  unresolvedArtifactIds,
                  nativeModelMap,
                  settings,
                  keepPreviousArtifacts,
                  true,
                  keepPreviousPlugins
    );

    return setState(newState);
  }

  @NotNull
  @ApiStatus.Internal
  public MavenProjectChanges updateState(@NotNull Collection<@NotNull MavenProjectProblem> readingProblems) {
    State newState = myState.clone();

    newState.myReadingProblems = readingProblems;

    return setState(newState);
  }

  private void doUpdateState(State newState,
                             @NotNull MavenModel model,
                             @NotNull Collection<@NotNull MavenProjectProblem> readingProblems,
                             @NotNull MavenExplicitProfiles activatedProfiles,
                             @NotNull Set<MavenId> unresolvedArtifactIds,
                             @NotNull Map<@NotNull String, @Nullable String> nativeModelMap,
                             @NotNull MavenGeneralSettings settings,
                             boolean keepPreviousArtifacts,
                             boolean keepPreviousProfiles,
                             boolean keepPreviousPlugins) {
    newState.myReadingProblems = readingProblems;
    newState.myLocalRepository = settings.getEffectiveLocalRepository();
    newState.myActivatedProfilesIds = activatedProfiles;

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

    doSetFolders(newState, model.getBuild());

    newState.myFilters = model.getBuild().getFilters();
    newState.myProperties = model.getProperties();

    doSetResolvedAttributes(newState, model, unresolvedArtifactIds, keepPreviousArtifacts, keepPreviousPlugins);

    MavenModelPropertiesPatcher.patch(newState.myProperties, newState.myPlugins);

    newState.myModulesPathsAndNames = collectModulePathsAndNames(model, getDirectory());
    Collection<String> newProfiles = collectProfilesIds(model.getProfiles());
    if (keepPreviousProfiles && newState.myProfilesIds != null) {
      Set<String> mergedProfiles = new HashSet<>(newState.myProfilesIds);
      mergedProfiles.addAll(newProfiles);
      newState.myProfilesIds = new ArrayList<>(mergedProfiles);
    }
    else {
      newState.myProfilesIds = newProfiles;
    }

    newState.myModelMap = nativeModelMap;
  }

  private MavenProjectChanges setState(State newState) {
    MavenProjectChanges changes = myState.getChanges(newState);
    myState = newState;
    return changes;
  }

  private void updateState(Consumer<State> updater) {
    State newState = myState.clone();
    updater.consume(newState);
    myState = newState;
  }

  static class Snapshot {
    @NotNull private final State myState;

    private Snapshot(@NotNull State state) {
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

  private static void doSetResolvedAttributes(State state,
                                              MavenModel model,
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
      if (state.myUnresolvedArtifactIds != null) newUnresolvedArtifacts.addAll(state.myUnresolvedArtifactIds);
      if (state.myRemoteRepositories != null) newRepositories.addAll(state.myRemoteRepositories);
      if (state.myDependencies != null) newDependencies.addAll(state.myDependencies);
      if (state.myDependencyTree != null) newDependencyTree.addAll(state.myDependencyTree);
      if (state.myExtensions != null) newExtensions.addAll(state.myExtensions);
      if (state.myAnnotationProcessors != null) newAnnotationProcessors.addAll(state.myAnnotationProcessors);
    }

    if (keepPreviousPlugins) {
      if (state.myPlugins != null) newPlugins.addAll(state.myPlugins);
    }

    newUnresolvedArtifacts.addAll(unresolvedArtifactIds);
    newRepositories.addAll(model.getRemoteRepositories());
    newDependencyTree.addAll(model.getDependencyTree());
    newDependencies.addAll(model.getDependencies());
    newPlugins.addAll(model.getPlugins());
    newExtensions.addAll(model.getExtensions());

    state.myUnresolvedArtifactIds = newUnresolvedArtifacts;
    state.myRemoteRepositories = new ArrayList<>(newRepositories);
    state.myDependencies = new ArrayList<>(newDependencies);
    state.myDependencyTree = new ArrayList<>(newDependencyTree);
    state.myPlugins = new ArrayList<>(newPlugins);
    state.myExtensions = new ArrayList<>(newExtensions);
    state.myAnnotationProcessors = new ArrayList<>(newAnnotationProcessors);
  }

  @ApiStatus.Internal
  public MavenProjectChanges setFolders(MavenGoalExecutionResult.Folders folders) {
    State newState = myState.clone();
    doSetFolders(newState, folders.getSources(), folders.getTestSources(), folders.getResources(), folders.getTestResources());
    return setState(newState);
  }

  private static void doSetFolders(State newState, MavenBuild build) {
    doSetFolders(newState, build.getSources(), build.getTestSources(), build.getResources(), build.getTestResources());
  }

  private static void doSetFolders(State newState,
                                   List<String> sources,
                                   List<String> testSources,
                                   List<MavenResource> resources,
                                   List<MavenResource> testResources) {
    newState.mySources = sources;
    newState.myTestSources = testSources;

    newState.myResources = resources;
    newState.myTestResources = testResources;
  }

  private Map<String, String> collectModulePathsAndNames(MavenModel mavenModel, String baseDir) {
    String basePath = baseDir + "/";
    Map<String, String> result = new LinkedHashMap<>();
    for (Map.Entry<String, String> each : collectModulesRelativePathsAndNames(mavenModel, basePath).entrySet()) {
      result.put(new MavenPathWrapper(basePath + each.getKey()).getPath(), each.getValue());
    }
    return result;
  }

  private Map<String, String> collectModulesRelativePathsAndNames(MavenModel mavenModel, String basePath) {
    String extension = StringUtil.notNullize(myFile.getExtension());
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

  private static Collection<String> collectProfilesIds(Collection<MavenProfile> profiles) {
    if (profiles == null) return Collections.emptyList();

    Set<String> result = new HashSet<>(profiles.size());
    for (MavenProfile each : profiles) {
      result.add(each.getId());
    }
    return result;
  }

  public long getLastReadStamp() {
    return myState.myLastReadStamp;
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
    return !myState.myReadingProblems.isEmpty();
  }

  public @Nullable @NlsSafe String getName() {
    return myState.myName;
  }

  public @NotNull @NlsSafe String getDisplayName() {
    State state = myState;
    if (StringUtil.isEmptyOrSpaces(state.myName)) {
      return StringUtil.notNullize(state.myMavenId.getArtifactId());
    }
    return state.myName;
  }

  public @NotNull Map<String, String> getModelMap() {
    return myState.myModelMap;
  }

  public @NotNull MavenId getMavenId() {
    return myState.myMavenId;
  }

  boolean isNew() {
    return null == myState.myMavenId;
  }

  public @Nullable MavenId getParentId() {
    return myState.myParentId;
  }

  public @NotNull @NlsSafe String getPackaging() {
    return myState.myPackaging;
  }

  public @NotNull @NlsSafe String getFinalName() {
    return myState.myFinalName;
  }

  public @Nullable @NlsSafe String getDefaultGoal() {
    return myState.myDefaultGoal;
  }

  public @NotNull @NlsSafe String getBuildDirectory() {
    return myState.myBuildDirectory;
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
    return myState.myOutputDirectory;
  }

  public @NotNull @NlsSafe String getTestOutputDirectory() {
    return myState.myTestOutputDirectory;
  }

  public @NotNull List<@NlsSafe String> getSources() {
    return myState.mySources;
  }

  public @NotNull List<@NlsSafe String> getTestSources() {
    return myState.myTestSources;
  }

  public @NotNull List<MavenResource> getResources() {
    return myState.myResources;
  }

  public @NotNull List<MavenResource> getTestResources() {
    return myState.myTestResources;
  }

  public @NotNull List<@NlsSafe String> getFilters() {
    return myState.myFilters;
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
      myState.myReadingProblems.add(new MavenProjectProblem(mavenConfigPath, message, SYNTAX, true));
    }
  }

  public @Nullable String getConfigFileError() {
    return myState
      .myReadingProblems.stream().filter(p -> p.getPath().endsWith(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH))
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
    var problems = myState.myProblemsCache;
    if (null != problems) return problems;

    return collectProblems(null);
  }

  @ApiStatus.Internal
  public @NotNull List<MavenProjectProblem> collectProblems(Predicate<File> fileExistsPredicate) {
    State state = myState;
    synchronized (state) {
      if (state.myProblemsCache == null) {
        state.myProblemsCache = collectProblems(myFile, state, fileExistsPredicate);
      }
      return state.myProblemsCache;
    }
  }

  public @NotNull List<MavenProjectProblem> getCacheProblems() {
    List<MavenProjectProblem> problemsCache = myState.myProblemsCache;
    return problemsCache == null ? Collections.emptyList() : problemsCache;
  }

  private static List<MavenProjectProblem> collectProblems(VirtualFile file, State state, Predicate<File> fileExistsPredicate) {
    List<MavenProjectProblem> result = new ArrayList<>();

    validateParent(file, state, result);
    result.addAll(state.myReadingProblems);

    for (Map.Entry<String, String> each : state.myModulesPathsAndNames.entrySet()) {
      if (LocalFileSystem.getInstance().findFileByPath(each.getKey()) == null) {
        result.add(createDependencyProblem(file, MavenProjectBundle.message("maven.project.problem.moduleNotFound", each.getValue())));
      }
    }

    validateDependencies(file, state, result, fileExistsPredicate);
    validateExtensions(file, state, result);
    validatePlugins(file, state, result);

    return result;
  }

  private static void validateParent(VirtualFile file, State state, List<MavenProjectProblem> result) {
    if (!isParentResolved(state)) {
      result.add(createDependencyProblem(file, MavenProjectBundle.message("maven.project.problem.parentNotFound", state.myParentId)));
    }
  }

  private static void validateDependencies(VirtualFile file,
                                           State state,
                                           List<MavenProjectProblem> result,
                                           Predicate<File> fileExistsPredicate) {
    for (MavenArtifact each : getUnresolvedDependencies(state, fileExistsPredicate)) {
      result.add(createDependencyProblem(file, MavenProjectBundle.message("maven.project.problem.unresolvedDependency",
                                                                          each.getDisplayStringWithType())));
    }
  }

  private static void validateExtensions(VirtualFile file, State state, List<MavenProjectProblem> result) {
    for (MavenArtifact each : getUnresolvedExtensions(state)) {
      result.add(createDependencyProblem(file, MavenProjectBundle.message("maven.project.problem.unresolvedExtension",
                                                                          each.getDisplayStringSimple())));
    }
  }

  private static void validatePlugins(VirtualFile file, State state, List<MavenProjectProblem> result) {
    for (MavenPlugin each : getUnresolvedPlugins(state)) {
      result.add(createDependencyProblem(file, MavenProjectBundle.message("maven.project.problem.unresolvedPlugin", each)));
    }
  }

  private static MavenProjectProblem createDependencyProblem(VirtualFile file, String description) {
    return new MavenProjectProblem(file.getPath(), description, MavenProjectProblem.ProblemType.DEPENDENCY, false);
  }

  private static boolean isParentResolved(State state) {
    return !state.myUnresolvedArtifactIds.contains(state.myParentId);
  }

  private static List<MavenArtifact> getUnresolvedDependencies(State state, Predicate<File> fileExistsPredicate) {
    synchronized (state) {
      if (state.myUnresolvedDependenciesCache == null) {
        List<MavenArtifact> result = new ArrayList<>();
        for (MavenArtifact each : state.myDependencies) {
          boolean resolved = each.isResolved(fileExistsPredicate);
          each.setFileUnresolved(!resolved);
          if (!resolved) result.add(each);
        }
        state.myUnresolvedDependenciesCache = result;
      }
      return state.myUnresolvedDependenciesCache;
    }
  }

  private static List<MavenArtifact> getUnresolvedExtensions(State state) {
    synchronized (state) {
      if (state.myUnresolvedExtensionsCache == null) {
        List<MavenArtifact> result = new ArrayList<>();
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

  private static List<MavenArtifact> getUnresolvedAnnotationProcessors(State state) {
    synchronized (state) {
      if (state.myUnresolvedAnnotationProcessors == null) {
        List<MavenArtifact> result = new ArrayList<>();
        for (MavenArtifact each : state.myAnnotationProcessors) {
          if (!each.isResolved()) result.add(each);
        }
        state.myUnresolvedAnnotationProcessors = result;
      }
      return state.myUnresolvedAnnotationProcessors;
    }
  }

  private static boolean pomFileExists(File localRepository, MavenArtifact artifact) {
    return MavenArtifactUtil.hasArtifactFile(localRepository, artifact.getMavenId(), "pom");
  }

  private static List<MavenPlugin> getUnresolvedPlugins(State state) {
    synchronized (state) {
      if (state.myUnresolvedPluginsCache == null) {
        List<MavenPlugin> result = new ArrayList<>();
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
    return myState.myModulesPathsAndNames;
  }

  public @NotNull Collection<String> getProfilesIds() {
    return myState.myProfilesIds;
  }

  public @NotNull MavenExplicitProfiles getActivatedProfilesIds() {
    return myState.myActivatedProfilesIds;
  }

  public @Nullable String getDependencyHash() {
    return myState.myDependencyHash;
  }

  public @NotNull List<MavenArtifact> getDependencies() {
    return myState.myDependencies;
  }

  public @NotNull List<MavenArtifact> getExternalAnnotationProcessors() {
    return myState.myAnnotationProcessors;
  }

  public @NotNull List<MavenArtifactNode> getDependencyTree() {
    return myState.myDependencyTree;
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
    updateState(newState -> newState.myDependencies.addAll(dependencies));
  }

  public void addAnnotationProcessors(@NotNull Collection<MavenArtifact> annotationProcessors) {
    updateState(newState -> newState.myAnnotationProcessors.addAll(annotationProcessors));
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
    State state = myState;
    return !isParentResolved(state)
           || !getUnresolvedDependencies(state, null).isEmpty()
           || !getUnresolvedExtensions(state).isEmpty()
           || !getUnresolvedAnnotationProcessors(state).isEmpty();
  }

  public boolean hasUnresolvedPlugins() {
    return !getUnresolvedPlugins(myState).isEmpty();
  }

  public @NotNull List<MavenPlugin> getPlugins() {
    return myState.myPlugins;
  }

  public @NotNull List<MavenPlugin> getDeclaredPlugins() {
    return getDeclaredPlugins(myState);
  }

  private static List<MavenPlugin> getDeclaredPlugins(State state) {
    return ContainerUtil.findAll(state.myPlugins, mavenPlugin -> !mavenPlugin.isDefault());
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
    return myState.myProperties.getProperty("project.build.sourceEncoding");
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
      .orElseGet(() -> myState.myProperties.getProperty("maven.compiler." + level));
  }

  private String getCompilerLevel(String level, Element config) {
    String result = MavenJDOMUtil.findChildValueByPath(config, level);
    if (result == null) {
      result = myState.myProperties.getProperty("maven.compiler." + level);
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
    return myState.myProperties;
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
    return myState.myLocalRepository;
  }

  public @NotNull List<MavenRemoteRepository> getRemoteRepositories() {
    return myState.myRemoteRepositories;
  }

  /**
   * @deprecated this API was intended for internal use and will be removed after migration to WorkpsaceModel API
   */
  @ApiStatus.Internal
  @Deprecated(forRemoval = true)
  public @NotNull ModuleType<? extends ModuleBuilder> getModuleType() {
    final List<MavenImporter> importers = MavenImporter.getSuitableImporters(this);
    // getSuitableImporters() guarantees that all returned importers require the same module type
    return importers.size() > 0 ? importers.get(0).getModuleType() : StdModuleTypes.JAVA;
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
    @SuppressWarnings("unchecked") V v = (V)myState.myCache.get(key);
    return v;
  }

  public @NotNull <V> V putCachedValue(Key<V> key, @NotNull V value) {
    Object oldValue = myState.myCache.putIfAbsent(key, value);
    if (oldValue != null) {
      @SuppressWarnings("unchecked") V v = (V)oldValue;
      return v;
    }
    return value;
  }

  @Override
  public String toString() {
    return null == myState.myMavenId ? myFile.getPath() : getMavenId().toString();
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
    List<MavenArtifact> myAnnotationProcessors;

    Map<String, String> myModulesPathsAndNames;

    Map<String, String> myModelMap;

    Collection<String> myProfilesIds;
    MavenExplicitProfiles myActivatedProfilesIds;
    String myDependencyHash;

    Collection<MavenProjectProblem> myReadingProblems;
    Set<MavenId> myUnresolvedArtifactIds;
    File myLocalRepository;

    volatile List<MavenProjectProblem> myProblemsCache;
    volatile List<MavenArtifact> myUnresolvedDependenciesCache;
    volatile List<MavenPlugin> myUnresolvedPluginsCache;
    volatile List<MavenArtifact> myUnresolvedExtensionsCache;
    volatile List<MavenArtifact> myUnresolvedAnnotationProcessors;

    transient ConcurrentHashMap<Key<?>, Object> myCache = new ConcurrentHashMap<>();

    @Override
    public State clone() {
      try {
        State result = (State)super.clone();
        myCache = new ConcurrentHashMap<>();
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
      myUnresolvedAnnotationProcessors = null;

      myCache.clear();
    }

    public MavenProjectChanges getChanges(State newState) {
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

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      myCache = new ConcurrentHashMap<>();
    }
  }

  public class Updater {
    public Updater setDependencies(@NotNull List<MavenArtifact> dependencies) {
      myState.myDependencies = dependencies;
      return this;
    }

    public Updater setProperties(@NotNull Properties properties) {
      myState.myProperties = properties;
      return this;
    }

    public Updater setPlugins(@NotNull List<MavenPlugin> plugins) {
      myState.myPlugins.clear();
      myState.myPlugins.addAll(plugins);
      return this;
    }
  }

  public Updater updater() {
    return new Updater();
  }
}
