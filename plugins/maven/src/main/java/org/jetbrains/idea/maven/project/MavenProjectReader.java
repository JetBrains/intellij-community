// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.buildtool.MavenLogEventHandler;
import org.jetbrains.idea.maven.dom.converters.MavenConsumerPomUtil;
import org.jetbrains.idea.maven.internal.ReadStatisticsCollector;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.server.ProfileApplicationResult;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import static org.jetbrains.idea.maven.utils.MavenUtil.getBaseDir;

public final class MavenProjectReader {
  private static final String UNKNOWN = MavenId.UNKNOWN_VALUE;

  private MavenReadProjectCache myCache = new MavenReadProjectCache();
  private final Project myProject;
  private final MavenProjectModelReadHelper myReadHelper;
  private SettingsProfilesCache mySettingsProfilesCache;

  public MavenProjectReader(@NotNull Project project) {
    myProject = project;
    myReadHelper = MavenUtil.createModelReadHelper(project);
  }

  @NotNull
  public MavenProjectReaderResult readProject(MavenGeneralSettings generalSettings,
                                              VirtualFile file,
                                              MavenExplicitProfiles explicitProfiles,
                                              MavenProjectReaderProjectLocator locator) {
    Path basedir = getBaseDir(file);

    Pair<RawModelReadResult, MavenExplicitProfiles> readResult =
      doReadProjectModel(generalSettings, basedir, file, explicitProfiles, new HashSet<>(), locator);

    MavenModel model = myReadHelper.interpolate(basedir, file, readResult.first.model);

    Map<String, String> modelMap = new HashMap<>();
    modelMap.put("groupId", model.getMavenId().getGroupId());
    modelMap.put("artifactId", model.getMavenId().getArtifactId());
    modelMap.put("version", model.getMavenId().getVersion());
    modelMap.put("build.outputDirectory", model.getBuild().getOutputDirectory());
    modelMap.put("build.testOutputDirectory", model.getBuild().getTestOutputDirectory());
    modelMap.put("build.finalName", model.getBuild().getFinalName());
    modelMap.put("build.directory", model.getBuild().getDirectory());

    return new MavenProjectReaderResult(model,
                                        modelMap,
                                        readResult.second,
                                        null,
                                        readResult.first.problems,
                                        new HashSet<>());
  }

  private Pair<RawModelReadResult, MavenExplicitProfiles> doReadProjectModel(MavenGeneralSettings generalSettings,
                                                                             Path projectPomDir,
                                                                             VirtualFile file,
                                                                             MavenExplicitProfiles explicitProfiles,
                                                                             Set<VirtualFile> recursionGuard,
                                                                             MavenProjectReaderProjectLocator locator) {
    RawModelReadResult cachedModel = myCache.get(file);
    if (cachedModel == null) {
      cachedModel = doReadProjectModel(myProject, file, false);
      myCache.put(file, cachedModel);
    }

    // todo modifying cached model and problems here??????
    MavenModel model = cachedModel.model;
    Set<String> alwaysOnProfiles = cachedModel.alwaysOnProfiles;
    Collection<MavenProjectProblem> problems = cachedModel.problems;

    model = resolveInheritance(generalSettings, model, projectPomDir, file, explicitProfiles, recursionGuard, locator, problems);
    addSettingsProfiles(generalSettings, model, alwaysOnProfiles, problems);

    ProfileApplicationResult applied = applyProfiles(model, projectPomDir, getBaseDir(file), explicitProfiles, alwaysOnProfiles);
    model = applied.getModel();

    repairModelBody(model);

    return Pair.create(new RawModelReadResult(model, problems, alwaysOnProfiles),
                       applied.getActivatedProfiles());
  }

  private static RawModelReadResult doReadProjectModel(Project project, VirtualFile file, boolean headerOnly) {
    Collection<MavenProjectProblem> problems = MavenProjectProblem.createProblemsList();
    Set<String> alwaysOnProfiles = new HashSet<>();

    String fileExtension = file.getExtension();
    if (!"pom".equalsIgnoreCase(fileExtension) && !"xml".equalsIgnoreCase(fileExtension)) {
      return readProjectModelUsingMavenServer(project, file, problems, alwaysOnProfiles);
    }

    return readMavenProjectModel(file, headerOnly, problems, alwaysOnProfiles, MavenConsumerPomUtil.isAutomaticVersionFeatureEnabled(file, project));
  }

  @NotNull
  private static RawModelReadResult readProjectModelUsingMavenServer(Project project,
                                                                     VirtualFile file,
                                                                     Collection<MavenProjectProblem> problems,
                                                                     Set<String> alwaysOnProfiles) {
    MavenModel result = null;
    String basedir = getBaseDir(file).toString();
    MavenEmbeddersManager manager = MavenProjectsManager.getInstance(project).getEmbeddersManager();
    MavenEmbedderWrapper embedder = manager.getEmbedder(MavenEmbeddersManager.FOR_MODEL_READ, basedir);
    try {
      result = embedder.readModel(VfsUtilCore.virtualToIoFile(file));
    }
    catch (MavenProcessCanceledException ignore) {
    }
    finally {
      manager.release(embedder);
    }

    if (result == null) {
      result = new MavenModel();
      result.setPackaging(MavenConstants.TYPE_JAR);
    }
    return new RawModelReadResult(result, problems, alwaysOnProfiles);
  }

  @NotNull
  private static RawModelReadResult readMavenProjectModel(VirtualFile file,
                                                          boolean headerOnly,
                                                          Collection<MavenProjectProblem> problems,
                                                          Set<String> alwaysOnProfiles,
                                                          boolean isAutomaticVersionFeatureEnabled) {
    MavenModel result;
    result = new MavenModel();
    Element xmlProject = readXml(file, problems, MavenProjectProblem.ProblemType.SYNTAX);
    if (xmlProject == null || !"project".equals(xmlProject.getName())) {
      result.setPackaging(MavenConstants.TYPE_JAR);
      return new RawModelReadResult(result, problems, alwaysOnProfiles);
    }

    MavenParent parent;
    if (MavenJDOMUtil.hasChildByPath(xmlProject, "parent")) {
      parent = new MavenParent(new MavenId(MavenJDOMUtil.findChildValueByPath(xmlProject, "parent.groupId", UNKNOWN),
                                           MavenJDOMUtil.findChildValueByPath(xmlProject, "parent.artifactId", UNKNOWN),
                                           calculateParentVersion(xmlProject, problems, file, isAutomaticVersionFeatureEnabled)),
                               MavenJDOMUtil.findChildValueByPath(xmlProject, "parent.relativePath", "../pom.xml"));
      result.setParent(parent);
    }
    else {
      parent = new MavenParent(new MavenId(UNKNOWN, UNKNOWN, UNKNOWN), "../pom.xml");
    }

    result.setMavenId(new MavenId(MavenJDOMUtil.findChildValueByPath(xmlProject, "groupId", parent.getMavenId().getGroupId()),
                                  MavenJDOMUtil.findChildValueByPath(xmlProject, "artifactId", UNKNOWN),
                                  MavenJDOMUtil.findChildValueByPath(xmlProject, "version", parent.getMavenId().getVersion())));

    if (headerOnly) return new RawModelReadResult(result, problems, alwaysOnProfiles);

    result.setPackaging(MavenJDOMUtil.findChildValueByPath(xmlProject, "packaging", MavenConstants.TYPE_JAR));
    result.setName(MavenJDOMUtil.findChildValueByPath(xmlProject, "name"));

    readModelBody(result, result.getBuild(), xmlProject);

    result.setProfiles(collectProfiles(file, xmlProject, problems, alwaysOnProfiles));
    return new RawModelReadResult(result, problems, alwaysOnProfiles);
  }

  @NotNull
  private static String calculateParentVersion(
    Element xmlProject,
                                        Collection<MavenProjectProblem> problems,
    VirtualFile file,
    boolean isAutomaticVersionFeatureEnabled) {
    String version = MavenJDOMUtil.findChildValueByPath(xmlProject, "parent.version");
    if (version != null || !isAutomaticVersionFeatureEnabled) {
      return StringUtil.notNullize(version, UNKNOWN);
    }
    String parentGroupId = MavenJDOMUtil.findChildValueByPath(xmlProject, "parent.groupId");
    String parentArtifactId = MavenJDOMUtil.findChildValueByPath(xmlProject, "parent.artifactId");
    if (parentGroupId == null || parentArtifactId == null) {
      problems.add(new MavenProjectProblem(file.getPath(), MavenProjectBundle.message("consumer.pom.cannot.determine.parent.version"),
                                           MavenProjectProblem.ProblemType.STRUCTURE,
                                           false));
      return UNKNOWN;
    }
    String relativePath = MavenJDOMUtil.findChildValueByPath(xmlProject, "parent.relativePath", "../pom.xml");
    VirtualFile parentFile = file.getParent().findFileByRelativePath(relativePath);
    if (parentFile == null) {
      problems.add(new MavenProjectProblem(file.getPath(), MavenProjectBundle.message("consumer.pom.cannot.determine.parent.version"),
                                           MavenProjectProblem.ProblemType.STRUCTURE,
                                           false));
      return UNKNOWN;
    }

    Element parentXmlProject = readXml(parentFile, problems, MavenProjectProblem.ProblemType.SYNTAX);
    version = MavenJDOMUtil.findChildValueByPath(parentXmlProject, "version");
    if (version != null) {
      return version;
    }
    return calculateParentVersion(parentXmlProject, problems, parentFile, isAutomaticVersionFeatureEnabled);
  }

  private static void repairModelBody(MavenModel model) {
    MavenBuild build = model.getBuild();

    if (isEmptyOrSpaces(build.getFinalName())) {
      build.setFinalName("${project.artifactId}-${project.version}");
    }

    if (build.getSources().isEmpty()) build.addSource("src/main/java");
    if (build.getTestSources().isEmpty()) build.addTestSource("src/test/java");

    build.setResources(repairResources(build.getResources(), "src/main/resources"));
    build.setTestResources(repairResources(build.getTestResources(), "src/test/resources"));

    build.setDirectory(isEmptyOrSpaces(build.getDirectory()) ? "target" : build.getDirectory());
    build.setOutputDirectory(isEmptyOrSpaces(build.getOutputDirectory())
                             ? "${project.build.directory}/classes" : build.getOutputDirectory());
    build.setTestOutputDirectory(isEmptyOrSpaces(build.getTestOutputDirectory())
                                 ? "${project.build.directory}/test-classes" : build.getTestOutputDirectory());
  }

  private static List<MavenResource> repairResources(List<MavenResource> resources, @NotNull String defaultDir) {
    List<MavenResource> result = new ArrayList<>();
    if (resources.isEmpty()) {
      result.add(createResource(defaultDir));
      return result;
    }

    for (MavenResource each : resources) {
      if (isEmptyOrSpaces(each.getDirectory())) continue;
      result.add(each);
    }
    return result;
  }

  private static MavenResource createResource(@NotNull String directory) {
    return new MavenResource(directory, false, null, Collections.emptyList(), Collections.emptyList());
  }

  private static List<MavenProfile> collectProfiles(VirtualFile projectFile,
                                             Element xmlProject,
                                             Collection<MavenProjectProblem> problems,
                                             Set<String> alwaysOnProfiles) {
    List<MavenProfile> result = new ArrayList<>();
    collectProfiles(MavenJDOMUtil.findChildrenByPath(xmlProject, "profiles", "profile"), result, MavenConstants.PROFILE_FROM_POM);

    VirtualFile profilesFile = MavenUtil.findProfilesXmlFile(projectFile);
    if (profilesFile != null) {
      collectProfilesFromSettingsXmlOrProfilesXml(profilesFile,
                                                  "profilesXml",
                                                  true,
                                                  MavenConstants.PROFILE_FROM_PROFILES_XML,
                                                  result,
                                                  alwaysOnProfiles,
                                                  problems);
    }

    return result;
  }

  private void addSettingsProfiles(MavenGeneralSettings generalSettings,
                                   MavenModel model,
                                   Set<String> alwaysOnProfiles,
                                   Collection<MavenProjectProblem> problems) {
    if (mySettingsProfilesCache == null) {

      List<MavenProfile> settingsProfiles = new ArrayList<>();
      Collection<MavenProjectProblem> settingsProblems = MavenProjectProblem.createProblemsList();
      Set<String> settingsAlwaysOnProfiles = new HashSet<>();

      for (VirtualFile each : generalSettings.getEffectiveSettingsFiles()) {
        collectProfilesFromSettingsXmlOrProfilesXml(each,
                                                    "settings",
                                                    false,
                                                    MavenConstants.PROFILE_FROM_SETTINGS_XML,
                                                    settingsProfiles,
                                                    settingsAlwaysOnProfiles,
                                                    settingsProblems);
      }
      mySettingsProfilesCache = new SettingsProfilesCache(settingsProfiles, settingsAlwaysOnProfiles, settingsProblems);
    }

    List<MavenProfile> modelProfiles = new ArrayList<>(model.getProfiles());
    for (MavenProfile each : mySettingsProfilesCache.profiles) {
      addProfileIfDoesNotExist(each, modelProfiles);
    }
    model.setProfiles(modelProfiles);

    problems.addAll(mySettingsProfilesCache.problems);
    alwaysOnProfiles.addAll(mySettingsProfilesCache.alwaysOnProfiles);
  }

  private static void collectProfilesFromSettingsXmlOrProfilesXml(VirtualFile profilesFile,
                                                           String rootElementName,
                                                           boolean wrapRootIfNecessary,
                                                           String profilesSource,
                                                           List<MavenProfile> result,
                                                           Set<String> alwaysOnProfiles,
                                                           Collection<MavenProjectProblem> problems) {
    Element rootElement = readXml(profilesFile, problems, MavenProjectProblem.ProblemType.SETTINGS_OR_PROFILES);
    if (rootElement == null) return;

    if (wrapRootIfNecessary && !rootElementName.equals(rootElement.getName())) {
      Element wrapper = new Element(rootElementName);
      wrapper.addContent(rootElement);
      rootElement = wrapper;
    }

    List<Element> xmlProfiles = MavenJDOMUtil.findChildrenByPath(rootElement, "profiles", "profile");
    collectProfiles(xmlProfiles, result, profilesSource);

    alwaysOnProfiles.addAll(MavenJDOMUtil.findChildrenValuesByPath(rootElement, "activeProfiles", "activeProfile"));
  }

  private static void collectProfiles(List<Element> xmlProfiles, List<MavenProfile> result, String source) {
    for (Element each : xmlProfiles) {
      String id = MavenJDOMUtil.findChildValueByPath(each, "id");
      if (isEmptyOrSpaces(id)) continue;

      MavenProfile profile = new MavenProfile(id, source);
      if (!addProfileIfDoesNotExist(profile, result)) continue;

      Element xmlActivation = MavenJDOMUtil.findChildByPath(each, "activation");
      if (xmlActivation != null) {
        MavenProfileActivation activation = new MavenProfileActivation();
        activation.setActiveByDefault("true".equals(MavenJDOMUtil.findChildValueByPath(xmlActivation, "activeByDefault")));

        Element xmlOS = MavenJDOMUtil.findChildByPath(xmlActivation, "os");
        if (xmlOS != null) {
          activation.setOs(new MavenProfileActivationOS(
            MavenJDOMUtil.findChildValueByPath(xmlOS, "name"),
            MavenJDOMUtil.findChildValueByPath(xmlOS, "family"),
            MavenJDOMUtil.findChildValueByPath(xmlOS, "arch"),
            MavenJDOMUtil.findChildValueByPath(xmlOS, "version")));
        }

        activation.setJdk(MavenJDOMUtil.findChildValueByPath(xmlActivation, "jdk"));

        Element xmlProperty = MavenJDOMUtil.findChildByPath(xmlActivation, "property");
        if (xmlProperty != null) {
          activation.setProperty(new MavenProfileActivationProperty(
            MavenJDOMUtil.findChildValueByPath(xmlProperty, "name"),
            MavenJDOMUtil.findChildValueByPath(xmlProperty, "value")));
        }

        Element xmlFile = MavenJDOMUtil.findChildByPath(xmlActivation, "file");
        if (xmlFile != null) {
          activation.setFile(new MavenProfileActivationFile(
            MavenJDOMUtil.findChildValueByPath(xmlFile, "exists"),
            MavenJDOMUtil.findChildValueByPath(xmlFile, "missing")));
        }

        profile.setActivation(activation);
      }

      readModelBody(profile, profile.getBuild(), each);
    }
  }

  private static boolean addProfileIfDoesNotExist(MavenProfile profile, List<MavenProfile> result) {
    for (MavenProfile each : result) {
      if (Objects.equals(each.getId(), profile.getId())) return false;
    }
    result.add(profile);
    return true;
  }

  private ProfileApplicationResult applyProfiles(MavenModel model,
                                                 Path projectPomDir,
                                                 Path basedir,
                                                 MavenExplicitProfiles explicitProfiles,
                                                 Collection<String> alwaysOnProfiles) {

    return MavenServerManager.getInstance().getConnector(myProject, projectPomDir.toAbsolutePath().toString())
      .applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles);
  }

  private MavenModel resolveInheritance(final MavenGeneralSettings generalSettings,
                                        MavenModel model,
                                        final Path projectPomDir,
                                        final VirtualFile file,
                                        final MavenExplicitProfiles explicitProfiles,
                                        final Set<VirtualFile> recursionGuard,
                                        final MavenProjectReaderProjectLocator locator,
                                        Collection<MavenProjectProblem> problems) {
    if (recursionGuard.contains(file)) {
      problems
        .add(MavenProjectProblem.createProblem(file.getPath(), MavenProjectBundle.message("maven.project.problem.recursiveInheritance"),
                                               MavenProjectProblem.ProblemType.PARENT, false));
      return model;
    }
    recursionGuard.add(file);

    try {
      final MavenParentDesc[] parentDesc = new MavenParentDesc[1];
      MavenParent parent = model.getParent();
      if (parent != null) {
        if (model.getMavenId().equals(parent.getMavenId())) {
          problems
            .add(MavenProjectProblem.createProblem(file.getPath(), MavenProjectBundle.message("maven.project.problem.selfInheritance"),
                                                   MavenProjectProblem.ProblemType.PARENT, false));
          return model;
        }
        parentDesc[0] = new MavenParentDesc(parent.getMavenId(), parent.getRelativePath());
      }

      Pair<VirtualFile, RawModelReadResult> parentModelWithProblems =
        new MavenParentProjectFileProcessor<Pair<VirtualFile, RawModelReadResult>>(myProject) {
          @Override
          @Nullable
          protected VirtualFile findManagedFile(@NotNull MavenId id) {
            return locator.findProjectFile(id);
          }

          @Override
          @Nullable
          protected Pair<VirtualFile, RawModelReadResult> processRelativeParent(VirtualFile parentFile) {
            MavenModel parentModel = doReadProjectModel(myProject, parentFile, true).model;
            MavenId parentId = parentDesc[0].getParentId();
            if (!parentId.equals(parentModel.getMavenId())) return null;

            return super.processRelativeParent(parentFile);
          }

          @Override
          protected Pair<VirtualFile, RawModelReadResult> processSuperParent(VirtualFile parentFile) {
            return null; // do not process superPom
          }

          @Override
          protected Pair<VirtualFile, RawModelReadResult> doProcessParent(VirtualFile parentFile) {
            RawModelReadResult result =
              doReadProjectModel(generalSettings, projectPomDir, parentFile, explicitProfiles, recursionGuard, locator).first;
            return Pair.create(parentFile, result);
          }
        }.process(generalSettings, file, parentDesc[0]);

      if (parentModelWithProblems == null) return model; // no parent or parent not found;

      MavenModel parentModel = parentModelWithProblems.second.model;
      if (!parentModelWithProblems.second.problems.isEmpty()) {
        problems.add(MavenProjectProblem.createProblem(parentModelWithProblems.first.getPath(),
                                                       MavenProjectBundle.message("maven.project.problem.parentHasProblems",
                                                                                  parentModel.getMavenId()),
                                                       MavenProjectProblem.ProblemType.PARENT, false));
      }

      model = myReadHelper.assembleInheritance(projectPomDir, parentModel, model);

      // todo: it is a quick-hack here - we add inherited dummy profiles to correctly collect activated profiles in 'applyProfiles'.
      List<MavenProfile> profiles = model.getProfiles();
      for (MavenProfile each : parentModel.getProfiles()) {
        MavenProfile copyProfile = new MavenProfile(each.getId(), each.getSource());
        if (each.getActivation() != null) {
          copyProfile.setActivation(each.getActivation().clone());
        }

        addProfileIfDoesNotExist(copyProfile, profiles);
      }
      return model;
    }
    finally {
      recursionGuard.remove(file);
    }
  }

  /**
   * @deprecated use {@link MavenProjectResolver}
   */
  // used in third-party plugins
  @Deprecated(forRemoval = true)
  public Collection<MavenProjectReaderResult> resolveProject(MavenGeneralSettings generalSettings,
                                                             MavenEmbedderWrapper embedder,
                                                             Collection<VirtualFile> files,
                                                             MavenExplicitProfiles explicitProfiles,
                                                             MavenProjectReaderProjectLocator locator)
    throws MavenProcessCanceledException {
    return MavenProjectResolutionUtil.resolveProjectSync(
      this,
      generalSettings,
      embedder,
      files,
      explicitProfiles,
      locator,
      null,
      MavenLogEventHandler.INSTANCE,
      null,
      false);
  }

  private static void readModelBody(MavenModelBase mavenModelBase, MavenBuildBase mavenBuildBase, Element xmlModel) {
    mavenModelBase.setModules(MavenJDOMUtil.findChildrenValuesByPath(xmlModel, "modules", "module"));
    collectProperties(MavenJDOMUtil.findChildByPath(xmlModel, "properties"), mavenModelBase);

    Element xmlBuild = MavenJDOMUtil.findChildByPath(xmlModel, "build");

    mavenBuildBase.setFinalName(MavenJDOMUtil.findChildValueByPath(xmlBuild, "finalName"));
    mavenBuildBase.setDefaultGoal(MavenJDOMUtil.findChildValueByPath(xmlBuild, "defaultGoal"));
    mavenBuildBase.setDirectory(MavenJDOMUtil.findChildValueByPath(xmlBuild, "directory"));
    mavenBuildBase.setResources(collectResources(MavenJDOMUtil.findChildrenByPath(xmlBuild, "resources", "resource")));
    mavenBuildBase.setTestResources(collectResources(MavenJDOMUtil.findChildrenByPath(xmlBuild, "testResources", "testResource")));
    mavenBuildBase.setFilters(MavenJDOMUtil.findChildrenValuesByPath(xmlBuild, "filters", "filter"));

    if (mavenBuildBase instanceof MavenBuild mavenBuild) {

      String source = MavenJDOMUtil.findChildValueByPath(xmlBuild, "sourceDirectory");
      if (!isEmptyOrSpaces(source)) mavenBuild.addSource(source);
      String testSource = MavenJDOMUtil.findChildValueByPath(xmlBuild, "testSourceDirectory");
      if (!isEmptyOrSpaces(testSource)) mavenBuild.addTestSource(testSource);

      mavenBuild.setOutputDirectory(MavenJDOMUtil.findChildValueByPath(xmlBuild, "outputDirectory"));
      mavenBuild.setTestOutputDirectory(MavenJDOMUtil.findChildValueByPath(xmlBuild, "testOutputDirectory"));
    }
  }

  private static List<MavenResource> collectResources(List<Element> xmlResources) {
    List<MavenResource> result = new ArrayList<>();
    for (Element each : xmlResources) {
      String directory = MavenJDOMUtil.findChildValueByPath(each, "directory");
      boolean filtered = "true".equals(MavenJDOMUtil.findChildValueByPath(each, "filtering"));
      String targetPath = MavenJDOMUtil.findChildValueByPath(each, "targetPath");
      List<String> includes = MavenJDOMUtil.findChildrenValuesByPath(each, "includes", "include");
      List<String> excludes = MavenJDOMUtil.findChildrenValuesByPath(each, "excludes", "exclude");

      if (null == directory) continue;

      result.add(new MavenResource(directory, filtered, targetPath, includes, excludes));
    }
    return result;
  }

  private static void collectProperties(Element xmlProperties, MavenModelBase mavenModelBase) {
    if (xmlProperties == null) return;

    Properties props = mavenModelBase.getProperties();

    for (Element each : xmlProperties.getChildren()) {
      String name = each.getName();
      String value = each.getTextTrim();
      if (!props.containsKey(name) && !isEmptyOrSpaces(name)) {
        props.setProperty(name, value);
      }
    }
  }

  private static Element readXml(final VirtualFile file,
                                 final Collection<MavenProjectProblem> problems,
                                 final MavenProjectProblem.ProblemType type) {

    ReadStatisticsCollector.getInstance().fileRead(file);

    return MavenJDOMUtil.read(file, new MavenJDOMUtil.ErrorHandler() {
      @Override
      public void onReadError(IOException e) {
        MavenLog.LOG.warn("Cannot read the pom file: " + e);
        problems.add(MavenProjectProblem.createProblem(file.getPath(), e.getMessage(), type, false));
      }

      @Override
      public void onSyntaxError() {
        problems.add(MavenProjectProblem.createSyntaxProblem(file.getPath(), type));
      }
    });
  }

  private static final class SettingsProfilesCache {
    final List<MavenProfile> profiles;
    final Set<String> alwaysOnProfiles;
    final Collection<MavenProjectProblem> problems;

    private SettingsProfilesCache(List<MavenProfile> profiles, Set<String> alwaysOnProfiles, Collection<MavenProjectProblem> problems) {
      this.profiles = profiles;
      this.alwaysOnProfiles = alwaysOnProfiles;
      this.problems = problems;
    }
  }

  public static final class RawModelReadResult {
    public MavenModel model;
    public Collection<MavenProjectProblem> problems;
    public Set<String> alwaysOnProfiles;

    private RawModelReadResult(MavenModel model, Collection<MavenProjectProblem> problems, Set<String> alwaysOnProfiles) {
      this.model = model;
      this.problems = problems;
      this.alwaysOnProfiles = alwaysOnProfiles;
    }
  }
}
