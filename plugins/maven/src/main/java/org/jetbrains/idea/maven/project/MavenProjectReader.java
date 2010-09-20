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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.facade.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.facade.MavenFacadeManager;
import org.jetbrains.idea.maven.facade.MavenWrapperExecutionResult;
import org.jetbrains.idea.maven.facade.ProfileApplicationResult;
import org.jetbrains.idea.maven.model.*;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;

public class MavenProjectReader {
  private static final String UNKNOWN = MavenId.UNKNOWN_VALUE;

  private final Map<VirtualFile, RawModelReadResult> myRawModelsCache = new THashMap<VirtualFile, RawModelReadResult>();
  private SettingsProfilesCache mySettingsProfilesCache;

  public MavenProjectReaderResult readProject(MavenGeneralSettings generalSettings,
                                              VirtualFile file,
                                              Collection<String> explicitProfiles,
                                              MavenProjectReaderProjectLocator locator) {
    Pair<RawModelReadResult, Collection<String>> readResult =
      doReadProjectModel(generalSettings, file, explicitProfiles, new THashSet<VirtualFile>(), locator);

    File basedir = getBaseDir(file);
    MavenModel model = MavenFacadeManager.getInstance().interpolateAndAlignModel(readResult.first.model, basedir);

    return new MavenProjectReaderResult(model,
                                        Collections.<String, String>emptyMap(),
                                        readResult.second,
                                        null,
                                        readResult.first.problems,
                                        new THashSet<MavenId>());
  }

  private File getBaseDir(VirtualFile file) {
    return new File(file.getParent().getPath());
  }

  private Pair<RawModelReadResult, Collection<String>> doReadProjectModel(MavenGeneralSettings generalSettings,
                                                                          VirtualFile file,
                                                                          Collection<String> explicitProfiles,
                                                                          Set<VirtualFile> recursionGuard,
                                                                          MavenProjectReaderProjectLocator locator) {
    RawModelReadResult cachedModel = myRawModelsCache.get(file);
    if (cachedModel == null) {
      cachedModel = doReadProjectModel(file, false);
      myRawModelsCache.put(file, cachedModel);
    }

    // todo modifying cached model and problems here??????
    MavenModel model = cachedModel.model;
    Set<String> alwaysOnProfiles = cachedModel.alwaysOnProfiles;
    Collection<MavenProjectProblem> problems = cachedModel.problems;

    model = resolveInheritance(generalSettings, model, file, explicitProfiles, recursionGuard, locator, problems);
    addSettingsProfiles(generalSettings, model, alwaysOnProfiles, problems);

    ProfileApplicationResult applied = applyProfiles(model, getBaseDir(file), explicitProfiles, alwaysOnProfiles);
    model = applied.getModel();

    repairModelBody(model);

    return new Pair<RawModelReadResult, Collection<String>>(new RawModelReadResult(model, problems, alwaysOnProfiles),
                                                            applied.getActivatedProfiles());
  }

  private RawModelReadResult doReadProjectModel(VirtualFile file, boolean headerOnly) {
    MavenModel result = new MavenModel();
    Collection<MavenProjectProblem> problems = MavenProjectProblem.createProblemsList();
    Set<String> alwaysOnProfiles = new THashSet<String>();

    Element xmlProject = readXml(file, problems, MavenProjectProblem.ProblemType.SYNTAX);
    if (xmlProject == null || !"project".equals(xmlProject.getName())) {
      result.setMavenId(new MavenId(UNKNOWN, UNKNOWN, UNKNOWN));
      result.setPackaging(MavenConstants.TYPE_JAR);

      return new RawModelReadResult(result, problems, alwaysOnProfiles);
    }

    MavenParent parent = new MavenParent(new MavenId(UNKNOWN, UNKNOWN, UNKNOWN), "../pom.xml");
    if (MavenJDOMUtil.hasChildByPath(xmlProject, "parent")) {
      parent = new MavenParent(new MavenId(MavenJDOMUtil.findChildValueByPath(xmlProject, "parent.groupId", UNKNOWN),
                                           MavenJDOMUtil.findChildValueByPath(xmlProject, "parent.artifactId", UNKNOWN),
                                           MavenJDOMUtil.findChildValueByPath(xmlProject, "parent.version", UNKNOWN)),
                               MavenJDOMUtil.findChildValueByPath(xmlProject, "parent.relativePath", "../pom.xml"));
      result.setParent(parent);
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

  private void readModelBody(MavenModelBase mavenModelBase, MavenBuildBase mavenBuildBase, Element xmlModel) {
    mavenModelBase.setModules(MavenJDOMUtil.findChildrenValuesByPath(xmlModel, "modules", "module"));
    collectProperties(MavenJDOMUtil.findChildByPath(xmlModel, "properties"), mavenModelBase);

    Element xmlBuild = MavenJDOMUtil.findChildByPath(xmlModel, "build");

    mavenBuildBase.setFinalName(MavenJDOMUtil.findChildValueByPath(xmlBuild, "finalName"));
    mavenBuildBase.setDefaultGoal(MavenJDOMUtil.findChildValueByPath(xmlBuild, "defaultGoal"));
    mavenBuildBase.setDirectory(MavenJDOMUtil.findChildValueByPath(xmlBuild, "directory"));
    mavenBuildBase.setResources(collectResources(MavenJDOMUtil.findChildrenByPath(xmlBuild, "resources", "resource")));
    mavenBuildBase.setTestResources(collectResources(MavenJDOMUtil.findChildrenByPath(xmlBuild, "testResources", "testResource")));

    if (mavenBuildBase instanceof MavenBuild) {
      MavenBuild mavenBuild = (MavenBuild)mavenBuildBase;

      String source = MavenJDOMUtil.findChildValueByPath(xmlBuild, "sourceDirectory");
      if (!isEmptyOrSpaces(source)) mavenBuild.addSource(source);
      String testSource = MavenJDOMUtil.findChildValueByPath(xmlBuild, "testSourceDirectory");
      if (!isEmptyOrSpaces(testSource)) mavenBuild.addTestSource(testSource);

      mavenBuild.setOutputDirectory(MavenJDOMUtil.findChildValueByPath(xmlBuild, "outputDirectory"));
      mavenBuild.setTestOutputDirectory(MavenJDOMUtil.findChildValueByPath(xmlBuild, "testOutputDirectory"));
    }
  }

  private List<MavenResource> collectResources(List<Element> xmlResources) {
    List<MavenResource> result = new ArrayList<MavenResource>();
    for (Element each : xmlResources) {
      result.add(new MavenResource(MavenJDOMUtil.findChildValueByPath(each, "directory"),
                                   "true".equals(MavenJDOMUtil.findChildValueByPath(each, "filtering")),
                                   MavenJDOMUtil.findChildValueByPath(each, "targetPath"),
                                   MavenJDOMUtil.findChildrenValuesByPath(each, "includes", "include"),
                                   MavenJDOMUtil.findChildrenValuesByPath(each, "excludes", "exclude")));
    }
    return result;
  }

  private void repairModelBody(MavenModel model) {
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

  private List<MavenResource> repairResources(List<MavenResource> resources, String defaultDir) {
    List<MavenResource> result = new ArrayList<MavenResource>();
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

  private MavenResource createResource(String directory) {
    return new MavenResource(directory, false, null, Collections.<String>emptyList(), Collections.<String>emptyList());
  }

  private List<MavenProfile> collectProfiles(VirtualFile projectFile,
                                             Element xmlProject,
                                             Collection<MavenProjectProblem> problems,
                                             Set<String> alwaysOnProfiles) {
    List<MavenProfile> result = new ArrayList<MavenProfile>();
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

      List<MavenProfile> settingsProfiles = new ArrayList<MavenProfile>();
      Collection<MavenProjectProblem> settingsProblems = MavenProjectProblem.createProblemsList();
      Set<String> settingsAlwaysOnProfiles = new THashSet<String>();

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

    List<MavenProfile> modelProfiles = new ArrayList<MavenProfile>(model.getProfiles());
    for (MavenProfile each : mySettingsProfilesCache.profiles) {
      addProfileIfDoesNotExist(each, modelProfiles);
    }
    model.setProfiles(modelProfiles);
    
    problems.addAll(mySettingsProfilesCache.problems);
    alwaysOnProfiles.addAll(mySettingsProfilesCache.alwaysOnProfiles);
  }

  private void collectProfilesFromSettingsXmlOrProfilesXml(VirtualFile profilesFile,
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

  private void collectProfiles(List<Element> xmlProfiles, List<MavenProfile> result, String source) {
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

  private boolean addProfileIfDoesNotExist(MavenProfile profile, List<MavenProfile> result) {
    for (MavenProfile each : result) {
      if (Comparing.equal(each.getId(), profile.getId())) return false;
    }
    result.add(profile);
    return true;
  }

  private void collectProperties(Element xmlProperties, MavenModelBase mavenModelBase) {
    if (xmlProperties == null) return;

    Properties props = mavenModelBase.getProperties();

    for (Element each : (Iterable<? extends Element>)xmlProperties.getChildren()) {
      String name = each.getName();
      String value = each.getTextTrim();
      if (!props.containsKey(name) && !StringUtil.isEmptyOrSpaces(value)) {
        props.setProperty(name, value);
      }
    }
  }

  private ProfileApplicationResult applyProfiles(MavenModel model,
                                                 File basedir,
                                                 Collection<String> explicitProfiles,
                                                 Collection<String> alwaysOnProfiles) {
    return MavenFacadeManager.getInstance().applyProfiles(model, basedir, explicitProfiles, alwaysOnProfiles);
  }

  private MavenModel resolveInheritance(final MavenGeneralSettings generalSettings,
                                        MavenModel model,
                                        final VirtualFile file,
                                        final Collection<String> explicitProfiles,
                                        final Set<VirtualFile> recursionGuard,
                                        final MavenProjectReaderProjectLocator locator,
                                        Collection<MavenProjectProblem> problems) {
    if (recursionGuard.contains(file)) {
      problems.add(MavenProjectProblem.createProblem(file.getPath(), ProjectBundle.message("maven.project.problem.recursiveInheritance"),
                                                     MavenProjectProblem.ProblemType.PARENT));
      return model;
    }
    recursionGuard.add(file);

    try {
      final MavenParentDesc[] parentDesc = new MavenParentDesc[1];
      MavenParent parent = model.getParent();
      if (parent != null) {
        if (model.getMavenId().equals(parent.getMavenId())) {
          problems.add(MavenProjectProblem.createProblem(file.getPath(), ProjectBundle.message("maven.project.problem.selfInheritance"),
                                                         MavenProjectProblem.ProblemType.PARENT));
          return model;
        }
        parentDesc[0] = new MavenParentDesc(parent.getMavenId(), parent.getRelativePath());
      }

      Pair<VirtualFile, RawModelReadResult> parentModelWithProblems =
        new MavenParentProjectFileProcessor<Pair<VirtualFile, RawModelReadResult>>() {
          @Nullable
          protected VirtualFile findManagedFile(@NotNull MavenId id) {
            return locator.findProjectFile(id);
          }

          @Override
          @Nullable
          protected Pair<VirtualFile, RawModelReadResult> processRelativeParent(VirtualFile parentFile) {
            MavenModel parentModel = doReadProjectModel(parentFile, true).model;
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
            RawModelReadResult result = doReadProjectModel(generalSettings, parentFile, explicitProfiles, recursionGuard, locator).first;
            return Pair.create(parentFile, result);
          }
        }.process(generalSettings, file, parentDesc[0]);

      if (parentModelWithProblems == null) return model; // no parent or parent not found;

      MavenModel parentModel = parentModelWithProblems.second.model;
      if (!parentModelWithProblems.second.problems.isEmpty()) {
        problems.add(MavenProjectProblem.createProblem(parentModelWithProblems.first.getPath(),
                                                       ProjectBundle.message("maven.project.problem.parentHasProblems",
                                                                             parentModel.getMavenId()),
                                                       MavenProjectProblem.ProblemType.PARENT));
      }

      model = MavenFacadeManager.getInstance().assembleInheritance(model, parentModel);
      List<MavenProfile> profiles = model.getProfiles();
      for (MavenProfile each : parentModel.getProfiles()) {
        addProfileIfDoesNotExist(each, profiles);
      }
      return model;
    }
    finally {
      recursionGuard.remove(file);
    }
  }

  public MavenProjectReaderResult resolveProject(MavenGeneralSettings generalSettings,
                                                 MavenEmbedderWrapper embedder,
                                                 VirtualFile file,
                                                 Collection<String> explicitProfiles,
                                                 MavenProjectReaderProjectLocator locator) throws MavenProcessCanceledException {
    try {
      MavenWrapperExecutionResult result = embedder.resolveProject(file, explicitProfiles);
      if (result.projectData == null) {
        MavenProjectReaderResult temp = readProject(generalSettings, file, explicitProfiles, locator);
        temp.readingProblems.addAll(result.problems);
        temp.unresolvedArtifactIds.addAll(result.unresolvedArtifacts);
        return temp;
      }

      return new MavenProjectReaderResult(result.projectData.mavenModel,
                                          result.projectData.mavenModelMap,
                                          result.projectData.activatedProfiles,
                                          result.projectData.nativeMavenProject,
                                          result.problems,
                                          result.unresolvedArtifacts);
    }
    catch (MavenProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      MavenLog.LOG.info(e);
      MavenLog.printInTests(e); // print exception since we need to know if something wrong with our logic

      MavenProjectReaderResult result = readProject(generalSettings, file, explicitProfiles, locator);
      String message = e.getMessage();
      if (message != null) {
        result.readingProblems.add(MavenProjectProblem.createStructureProblem(file.getPath(), message));
      }
      else {
        result.readingProblems.add(MavenProjectProblem.createSyntaxProblem(file.getPath(), MavenProjectProblem.ProblemType.SYNTAX));
      }
      return result;
    }
  }

  public MavenProjectReaderResult generateSources(MavenEmbedderWrapper embedder,
                                                  MavenImportingSettings importingSettings,
                                                  VirtualFile file,
                                                  Collection<String> profiles,
                                                  MavenConsole console) throws MavenProcessCanceledException {
    try {
      List<String> goals = Arrays.asList(importingSettings.getUpdateFoldersOnImportPhase());
      MavenWrapperExecutionResult result = embedder.execute(file, profiles, goals);
      if (result.projectData == null) return null;

      return new MavenProjectReaderResult(result.projectData.mavenModel,
                                          result.projectData.mavenModelMap,
                                          result.projectData.activatedProfiles,
                                          result.projectData.nativeMavenProject,
                                          result.problems,
                                          result.unresolvedArtifacts);
    }
    catch (Throwable e) {
      console.printException(e);
      MavenLog.LOG.warn(e);
      return null;
    }
  }

  private Element readXml(final VirtualFile file,
                          final Collection<MavenProjectProblem> problems,
                          final MavenProjectProblem.ProblemType type) {
    return MavenJDOMUtil.read(file, new MavenJDOMUtil.ErrorHandler() {
      public void onReadError(IOException e) {
        MavenLog.LOG.warn("Cannot read the pom file: " + e);
        problems.add(MavenProjectProblem.createProblem(file.getPath(), e.getMessage(), type));
      }

      public void onSyntaxError() {
        problems.add(MavenProjectProblem.createSyntaxProblem(file.getPath(), type));
      }
    });
  }

  private static class SettingsProfilesCache {
    final List<MavenProfile> profiles;
    final Set<String> alwaysOnProfiles;
    final Collection<MavenProjectProblem> problems;

    private SettingsProfilesCache(List<MavenProfile> profiles, Set<String> alwaysOnProfiles, Collection<MavenProjectProblem> problems) {
      this.profiles = profiles;
      this.alwaysOnProfiles = alwaysOnProfiles;
      this.problems = problems;
    }
  }

  private static class RawModelReadResult {
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
