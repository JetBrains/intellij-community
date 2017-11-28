/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.MavenExtraArtifactType;
import org.jetbrains.idea.maven.importing.MavenImporter;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.plugins.api.MavenModelPropertiesPatcher;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MavenProject {

  private static final Key<MavenArtifactIndex> DEPENDENCIES_CACHE_KEY = Key.create("MavenProject.DEPENDENCIES_CACHE_KEY");
  private static final Key<List<String>> FILTERS_CACHE_KEY = Key.create("MavenProject.FILTERS_CACHE_KEY");

  @NotNull private final VirtualFile myFile;
  @NotNull private volatile State myState = new State();

  public enum ProcMode {BOTH, ONLY, NONE}

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
  MavenProjectChanges set(@NotNull MavenProjectReaderResult readerResult,
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

    MavenModelPropertiesPatcher.patch(newState.myProperties, newState.myPlugins);

    newState.myModulesPathsAndNames = collectModulePathsAndNames(model, getDirectory());
    Collection<String> newProfiles = collectProfilesIds(model.getProfiles());
    if (resetProfiles || newState.myProfilesIds == null) {
      newState.myProfilesIds = newProfiles;
    }
    else {
      Set<String> mergedProfiles = new THashSet<>(newState.myProfilesIds);
      mergedProfiles.addAll(newProfiles);
      newState.myProfilesIds = new ArrayList<>(mergedProfiles);
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

    Set<MavenId> newUnresolvedArtifacts = new THashSet<>();
    LinkedHashSet<MavenRemoteRepository> newRepositories = new LinkedHashSet<>();
    LinkedHashSet<MavenArtifact> newDependencies = new LinkedHashSet<>();
    LinkedHashSet<MavenArtifactNode> newDependencyTree = new LinkedHashSet<>();
    LinkedHashSet<MavenPlugin> newPlugins = new LinkedHashSet<>();
    LinkedHashSet<MavenArtifact> newExtensions = new LinkedHashSet<>();

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
    state.myRemoteRepositories = new ArrayList<>(newRepositories);
    state.myDependencies = new ArrayList<>(newDependencies);
    state.myDependencyTree = new ArrayList<>(newDependencyTree);
    state.myPlugins = new ArrayList<>(newPlugins);
    state.myExtensions = new ArrayList<>(newExtensions);
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

  private Map<String, String> collectModulePathsAndNames(MavenModel mavenModel, String baseDir) {
    String basePath = baseDir + "/";
    Map<String, String> result = new LinkedHashMap<>();
    for (Map.Entry<String, String> each : collectModulesRelativePathsAndNames(mavenModel, basePath).entrySet()) {
      result.put(new Path(basePath + each.getKey()).getPath(), each.getValue());
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
      if (!name.endsWith('.' + extension)) {
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

    Set<String> result = new THashSet<>(profiles.size());
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

  @Nullable
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
    if (StringUtil.isEmptyOrSpaces(state.myName)) {
      return StringUtil.notNullize(state.myMavenId.getArtifactId());
    }
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
    if (getProcMode() == ProcMode.NONE) {
      MavenPlugin bscMavenPlugin = findPlugin("org.bsc.maven", "maven-processor-plugin");
      Element cfg = getPluginGoalConfiguration(bscMavenPlugin, testSources ? "process-test" : "process");
      if (bscMavenPlugin != null && cfg == null) {
        return getBuildDirectory() + (testSources ?  "/generated-sources/apt-test" : "/generated-sources/apt");
      }
      if (cfg != null) {
        String out = MavenJDOMUtil.findChildValueByPath(cfg, "outputDirectory");
        if (out == null) {
          out = MavenJDOMUtil.findChildValueByPath(cfg, "defaultOutputDirectory");
          if (out == null) {
            return getBuildDirectory() + (testSources ?  "/generated-sources/apt-test" : "/generated-sources/apt");
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

  @NotNull
  public ProcMode getProcMode() {
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
    addAnnotationProcessorOptionFomrParametersString(compilerArgument, res);

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

  private static void addAnnotationProcessorOptionFomrParametersString(String compilerArguments, Map<String, String> res) {
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
      } else {
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
      addAnnotationProcessorOptionFomrParametersString(compilerArguments, res);

      final Element optionsElement = cfg.getChild("options");
      if (optionsElement != null) {
        for (Element option : optionsElement.getChildren()) {
          res.put(option.getName(), option.getText());
        }
      }
    }
    return res;
  }

  @Nullable
  public List<String> getDeclaredAnnotationProcessors() {
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

  public List<String> getFilterPropertiesFiles() {
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

  @NotNull
  public MavenProjectChanges read(@NotNull MavenGeneralSettings generalSettings,
                                  @NotNull MavenExplicitProfiles profiles,
                                  @NotNull MavenProjectReader reader,
                                  @NotNull MavenProjectReaderProjectLocator locator) {
    return set(reader.readProject(generalSettings, myFile, profiles, locator), generalSettings, true, false, true);
  }

  @NotNull
  public Pair<MavenProjectChanges, NativeMavenProjectHolder> resolve(@NotNull Project project,
                                                                     @NotNull MavenGeneralSettings generalSettings,
                                                                     @NotNull MavenEmbedderWrapper embedder,
                                                                     @NotNull MavenProjectReader reader,
                                                                     @NotNull MavenProjectReaderProjectLocator locator,
                                                                     @NotNull ResolveContext context)
    throws MavenProcessCanceledException {
    Collection<MavenProjectReaderResult> results = reader.resolveProject(generalSettings,
                                                            embedder,
                                                            Collections.singleton(getFile()),
                                                            getActivatedProfilesIds(),
                                                            locator);
    final MavenProjectReaderResult result = results.iterator().next();
    MavenProjectChanges changes = set(result, generalSettings, false, result.readingProblems.isEmpty(), false);

    if (result.nativeMavenProject != null) {
      for (MavenImporter eachImporter : getSuitableImporters()) {
        eachImporter.resolve(project, this, result.nativeMavenProject, embedder, context);
      }
    }
    return Pair.create(changes, result.nativeMavenProject);
  }

  @NotNull
  public Pair<Boolean, MavenProjectChanges> resolveFolders(@NotNull MavenEmbedderWrapper embedder,
                                                           @NotNull MavenImportingSettings importingSettings,
                                                           @NotNull MavenConsole console) throws MavenProcessCanceledException {
    MavenProjectReaderResult result = MavenProjectReader.generateSources(embedder,
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
    List<MavenProjectProblem> result = new ArrayList<>();

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
        List<MavenArtifact> result = new ArrayList<>();
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

  @NotNull
  public List<VirtualFile> getExistingModuleFiles() {
    LocalFileSystem fs = LocalFileSystem.getInstance();

    List<VirtualFile> result = new ArrayList<>();
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
  public MavenExplicitProfiles getActivatedProfilesIds() {
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

  @NotNull
  public Set<String> getSupportedPackagings() {
    Set<String> result = ContainerUtil.newHashSet(MavenConstants.TYPE_POM,
                                                  MavenConstants.TYPE_JAR,
                                                  "ejb", "ejb-client", "war", "ear", "bundle", "maven-plugin");
    for (MavenImporter each : getSuitableImporters()) {
      each.getSupportedPackagings(result);
    }
    return result;
  }

  public Set<String> getDependencyTypesFromImporters(@NotNull SupportedRequestType type) {
    THashSet<String> res = new THashSet<>();

    for (MavenImporter each : getSuitableImporters()) {
      each.getSupportedDependencyTypes(res, type);
    }

    return res;
  }

  @NotNull
  public Set<String> getSupportedDependencyScopes() {
    Set<String> result = new THashSet<>(Arrays.asList(MavenConstants.SCOPE_COMPILE,
                                                      MavenConstants.SCOPE_PROVIDED,
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
    List<MavenArtifact> dependenciesCopy = new ArrayList<>(state.myDependencies);
    dependenciesCopy.add(dependency);
    state.myDependencies = dependenciesCopy;

    state.myCache.clear();
  }

  @NotNull
  public List<MavenArtifact> findDependencies(@NotNull MavenProject depProject) {
    return findDependencies(depProject.getMavenId());
  }

  public List<MavenArtifact> findDependencies(@NotNull MavenId id) {
    return getDependencyArtifactIndex().findArtifacts(id);
  }

  @NotNull
  public List<MavenArtifact> findDependencies(@Nullable String groupId, @Nullable String artifactId) {
    return getDependencyArtifactIndex().findArtifacts(groupId, artifactId);
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
    return ContainerUtil.findAll(state.myPlugins, mavenPlugin -> !mavenPlugin.isDefault());
  }

  @Nullable
  public Element getPluginConfiguration(@Nullable String groupId, @Nullable String artifactId) {
    return getPluginGoalConfiguration(groupId, artifactId, null);
  }

  @Nullable
  public Element getPluginGoalConfiguration(@Nullable String groupId, @Nullable String artifactId, @Nullable String goal) {
    return getPluginGoalConfiguration(findPlugin(groupId, artifactId), goal);
  }

  @Nullable
  public Element getPluginGoalConfiguration(@Nullable MavenPlugin plugin, @Nullable String goal) {
    if (plugin == null) return null;
    return goal == null ? plugin.getConfigurationElement() : plugin.getGoalConfiguration(goal);
  }

  public Element getPluginExecutionConfiguration(@Nullable String groupId, @Nullable String artifactId, @NotNull String executionId) {
    MavenPlugin plugin = findPlugin(groupId, artifactId);
    if (plugin == null) return null;
    return plugin.getExecutionConfiguration(executionId);
  }

  @Nullable
  public MavenPlugin findPlugin(@Nullable String groupId, @Nullable String artifactId) {
    return findPlugin(groupId, artifactId, false);
  }

  @Nullable
  public MavenPlugin findPlugin(@Nullable String groupId, @Nullable String artifactId, final boolean explicitlyDeclaredOnly) {
    final List<MavenPlugin> plugins = explicitlyDeclaredOnly ? getDeclaredPlugins() : getPlugins();
    for (MavenPlugin each : plugins) {
      if (each.getMavenId().equals(groupId, artifactId)) return each;
    }
    return null;
  }

  @Nullable
  public String getEncoding() {
    String encoding = myState.myProperties.getProperty("project.build.sourceEncoding");
    if (encoding != null) return encoding;

    Element pluginConfiguration = getPluginConfiguration("org.apache.maven.plugins", "maven-resources-plugin");
    if (pluginConfiguration != null) {
      return pluginConfiguration.getChildTextTrim("encoding");
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

    if (result == null) {
      result = myState.myProperties.getProperty("maven.compiler." + level);
    }

    return result;
  }

  @Nullable
  private Element getCompilerConfig() {
    Element executionConfiguration = getPluginExecutionConfiguration("org.apache.maven.plugins", "maven-compiler-plugin", "default-compile");
    if(executionConfiguration != null) return executionConfiguration;
    return getPluginConfiguration("org.apache.maven.plugins", "maven-compiler-plugin");
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
  public ModuleType getModuleType() {
    final List<MavenImporter> importers = getSuitableImporters();
    // getSuitableImporters() guarantees that all returned importers require the same module type
    return importers.size() > 0 ? importers.get(0).getModuleType() : StdModuleTypes.JAVA;
  }

  @NotNull
  public Pair<String, String> getClassifierAndExtension(@NotNull MavenArtifact artifact, @NotNull MavenExtraArtifactType type) {
    for (MavenImporter each : getSuitableImporters()) {
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

  @Nullable
  public <V> V getCachedValue(Key<V> key) {
    //noinspection unchecked
    return (V)myState.myCache.get(key);
  }

  @NotNull
  public <V> V putCachedValue(Key<V> key, @NotNull V value) {
    ConcurrentHashMap<Key, Object> map = myState.myCache;
    Object oldValue = map.putIfAbsent(key, value);
    if (oldValue != null) {
      return (V)oldValue;
    }

    return value;
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
    MavenExplicitProfiles myActivatedProfilesIds;

    Collection<MavenProjectProblem> myReadingProblems;
    Set<MavenId> myUnresolvedArtifactIds;
    File myLocalRepository;

    volatile List<MavenProjectProblem> myProblemsCache;
    volatile List<MavenArtifact> myUnresolvedDependenciesCache;
    volatile List<MavenPlugin> myUnresolvedPluginsCache;
    volatile List<MavenArtifact> myUnresolvedExtensionsCache;

    transient ConcurrentHashMap<Key, Object> myCache = new ConcurrentHashMap<>();

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

      myCache.clear();
    }

    public MavenProjectChanges getChanges(State other) {
      if (myLastReadStamp == 0) return MavenProjectChanges.ALL;

      MavenProjectChanges result = new MavenProjectChanges();

      result.packaging = !Comparing.equal(myPackaging, other.myPackaging);

      result.output = !Comparing.equal(myFinalName, other.myFinalName)
                      || !Comparing.equal(myBuildDirectory, other.myBuildDirectory)
                      || !Comparing.equal(myOutputDirectory, other.myOutputDirectory)
                      || !Comparing.equal(myTestOutputDirectory, other.myTestOutputDirectory);

      result.sources = !Comparing.equal(mySources, other.mySources)
                       || !Comparing.equal(myTestSources, other.myTestSources)
                       || !Comparing.equal(myResources, other.myResources)
                       || !Comparing.equal(myTestResources, other.myTestResources);

      boolean repositoryChanged = !Comparing.equal(myLocalRepository, other.myLocalRepository);

      result.dependencies = repositoryChanged || !Comparing.equal(myDependencies, other.myDependencies);

      result.plugins = repositoryChanged || !Comparing.equal(myPlugins, other.myPlugins);

      return result;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();
      myCache = new ConcurrentHashMap<>();
    }
  }
}