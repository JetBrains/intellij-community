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
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.apache.maven.model.*;
import org.apache.maven.profiles.activation.*;
import org.apache.maven.project.InvalidProjectModelException;
import org.apache.maven.project.JBMavenProjectHelper;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.inheritance.DefaultModelInheritanceAssembler;
import org.apache.maven.project.injection.DefaultProfileInjector;
import org.apache.maven.project.validation.ModelValidationResult;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.context.DefaultContext;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.embedder.*;
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

  private static final String PROFILE_FROM_POM = "pom";
  private static final String PROFILE_FROM_PROFILES_XML = "profiles.xml";
  private static final String PROFILE_FROM_SETTINGS_XML = "settings.xml";

  private final Map<VirtualFile, RawModelReadResult> myRawModelsCache = new THashMap<VirtualFile, RawModelReadResult>();
  private Pair<List<Profile>, Collection<MavenProjectProblem>> mySettingsProfilesWithProblemsCache;

  public MavenProjectReaderResult readProject(MavenGeneralSettings generalSettings,
                                              VirtualFile file,
                                              Collection<String> explicitProfiles,
                                              MavenProjectReaderProjectLocator locator) {
    Pair<RawModelReadResult, Collection<String>> readResult =
      doReadProjectModel(generalSettings, file, explicitProfiles, new THashSet<VirtualFile>(), locator);

    File basedir = getBaseDir(file);
    Model model = expandProperties(readResult.first.model, basedir);
    alignModel(model, basedir);

    MavenProject mavenProject = new MavenProject(model);
    mavenProject.setFile(new File(file.getPath()));
    JBMavenProjectHelper.setSourceRoots(mavenProject, Collections.singletonList(model.getBuild().getSourceDirectory()),
                                        Collections.singletonList(model.getBuild().getTestSourceDirectory()),
                                        Collections.singletonList(model.getBuild().getScriptSourceDirectory()));

    return new MavenProjectReaderResult(generalSettings, readResult.first.problems, Collections.EMPTY_SET,
                                        generalSettings.getEffectiveLocalRepository(), mavenProject, readResult.second);
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
    Model model = cachedModel.model;
    Collection<String> alwaysOnProfiles = cachedModel.alwaysOnProfiles;
    Collection<MavenProjectProblem> problems = cachedModel.problems;

    repairModelHeader(model);
    resolveInheritance(generalSettings, model, file, explicitProfiles, recursionGuard, locator, problems);
    addSettingsProfiles(generalSettings, model, alwaysOnProfiles, problems);

    Collection<String> activatedProfiles = applyProfiles(model, getBaseDir(file), explicitProfiles, alwaysOnProfiles);

    repairModelBody(model);

    return Pair.create(cachedModel, activatedProfiles);
  }

  private RawModelReadResult doReadProjectModel(VirtualFile file, boolean headerOnly) {
    Model result = new Model();
    LinkedHashSet<MavenProjectProblem> problems = createProblemsList();
    Set<String> alwaysOnProfiles = new THashSet<String>();

    Element xmlProject = readXml(file, problems, MavenProjectProblem.ProblemType.SYNTAX);
    if (xmlProject == null || !"project".equals(xmlProject.getName())) {
      return new RawModelReadResult(result, problems, alwaysOnProfiles);
    }

    result.setModelVersion(MavenJDOMUtil.findChildValueByPath(xmlProject, "modelVersion"));
    result.setGroupId(MavenJDOMUtil.findChildValueByPath(xmlProject, "groupId"));
    result.setArtifactId(MavenJDOMUtil.findChildValueByPath(xmlProject, "artifactId"));
    result.setVersion(MavenJDOMUtil.findChildValueByPath(xmlProject, "version"));

    if (headerOnly) return new RawModelReadResult(result, problems, alwaysOnProfiles);

    result.setPackaging(MavenJDOMUtil.findChildValueByPath(xmlProject, "packaging"));
    result.setName(MavenJDOMUtil.findChildValueByPath(xmlProject, "name"));

    if (MavenJDOMUtil.hasChildByPath(xmlProject, "parent")) {
      Parent parent = new Parent();

      String groupId = MavenJDOMUtil.findChildValueByPath(xmlProject, "parent.groupId");
      String artifactId = MavenJDOMUtil.findChildValueByPath(xmlProject, "parent.artifactId");
      String version = MavenJDOMUtil.findChildValueByPath(xmlProject, "parent.version");

      parent.setGroupId(groupId);
      parent.setArtifactId(artifactId);
      parent.setVersion(version);
      parent.setRelativePath(MavenJDOMUtil.findChildValueByPath(xmlProject, "parent.relativePath"));

      result.setParent(parent);
    }
    result.setBuild(new Build());
    readModelAndBuild(result, result.getBuild(), xmlProject);

    result.setProfiles(collectProfiles(file, xmlProject, problems, alwaysOnProfiles));
    return new RawModelReadResult(result, problems, alwaysOnProfiles);
  }

  private void readModelAndBuild(ModelBase mavenModelBase, BuildBase mavenBuildBase, Element xmlModel) {
    mavenModelBase.setModules(MavenJDOMUtil.findChildrenValuesByPath(xmlModel, "modules", "module"));
    collectProperties(MavenJDOMUtil.findChildByPath(xmlModel, "properties"), mavenModelBase);

    Element xmlBuild = MavenJDOMUtil.findChildByPath(xmlModel, "build");
    if (xmlBuild == null) return;

    mavenBuildBase.setFinalName(MavenJDOMUtil.findChildValueByPath(xmlBuild, "finalName"));
    mavenBuildBase.setDefaultGoal(MavenJDOMUtil.findChildValueByPath(xmlBuild, "defaultGoal"));
    mavenBuildBase.setDirectory(MavenJDOMUtil.findChildValueByPath(xmlBuild, "directory"));
    mavenBuildBase.setResources(collectResources(MavenJDOMUtil.findChildrenByPath(xmlBuild, "resources", "resource")));
    mavenBuildBase.setTestResources(collectResources(MavenJDOMUtil.findChildrenByPath(xmlBuild, "testResources", "testResource")));

    if (mavenBuildBase instanceof Build) {
      Build mavenBuild = (Build)mavenBuildBase;

      mavenBuild.setSourceDirectory(MavenJDOMUtil.findChildValueByPath(xmlBuild, "sourceDirectory"));
      mavenBuild.setTestSourceDirectory(MavenJDOMUtil.findChildValueByPath(xmlBuild, "testSourceDirectory"));
      mavenBuild.setScriptSourceDirectory(MavenJDOMUtil.findChildValueByPath(xmlBuild, "scriptSourceDirectory"));
      mavenBuild.setOutputDirectory(MavenJDOMUtil.findChildValueByPath(xmlBuild, "outputDirectory"));
      mavenBuild.setTestOutputDirectory(MavenJDOMUtil.findChildValueByPath(xmlBuild, "testOutputDirectory"));
    }
  }

  private List<Resource> collectResources(List<Element> xmlResources) {
    List<Resource> result = new ArrayList<Resource>();
    for (Element each : xmlResources) {
      Resource r = new Resource();
      r.setDirectory(MavenJDOMUtil.findChildValueByPath(each, "directory"));
      r.setFiltering("true".equals(MavenJDOMUtil.findChildValueByPath(each, "filtering")));
      r.setTargetPath(MavenJDOMUtil.findChildValueByPath(each, "targetPath"));
      r.setIncludes(MavenJDOMUtil.findChildrenValuesByPath(each, "includes", "include"));
      r.setExcludes(MavenJDOMUtil.findChildrenValuesByPath(each, "excludes", "exclude"));
      result.add(r);
    }
    return result;
  }

  private List<Profile> collectProfiles(VirtualFile projectFile,
                                        Element xmlProject,
                                        Collection<MavenProjectProblem> problems,
                                        Collection<String> alwaysOnProfiles) {
    List<Profile> result = new ArrayList<Profile>();
    collectProfiles(MavenJDOMUtil.findChildrenByPath(xmlProject, "profiles", "profile"), result, PROFILE_FROM_POM);

    VirtualFile profilesFile = MavenUtil.findProfilesXmlFile(projectFile);
    if (profilesFile != null) {
      collectProfilesFromSettingsXmlOrProfilesXml(profilesFile, "profilesXml", true, PROFILE_FROM_PROFILES_XML, result, alwaysOnProfiles,
                                                  problems);
    }

    return result;
  }

  private void addSettingsProfiles(MavenGeneralSettings generalSettings,
                                   Model model,
                                   Collection<String> alwaysOnProfiles,
                                   Collection<MavenProjectProblem> problems) {
    if (mySettingsProfilesWithProblemsCache == null) {

      List<Profile> settingsProfiles = new ArrayList<Profile>();
      Collection<MavenProjectProblem> settingsProblems = createProblemsList();

      for (VirtualFile each : generalSettings.getEffectiveSettingsFiles()) {
        collectProfilesFromSettingsXmlOrProfilesXml(each, "settings", false, PROFILE_FROM_SETTINGS_XML, settingsProfiles, alwaysOnProfiles,
                                                    settingsProblems);
      }
      mySettingsProfilesWithProblemsCache = Pair.create(settingsProfiles, settingsProblems);
    }

    List<Profile> modelProfiles = model.getProfiles();
    for (Profile each : mySettingsProfilesWithProblemsCache.first) {
      addProfileIfDoesNotExist(each, modelProfiles);
    }
    problems.addAll(mySettingsProfilesWithProblemsCache.second);
  }

  private void collectProfilesFromSettingsXmlOrProfilesXml(VirtualFile profilesFile,
                                                           String rootElementName,
                                                           boolean wrapRootIfNecessary,
                                                           String profilesSource,
                                                           List<Profile> result,
                                                           Collection<String> alwaysOnProfiles,
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

  private void collectProfiles(List<Element> xmlProfiles, List<Profile> result, String source) {
    for (Element each : xmlProfiles) {
      String id = MavenJDOMUtil.findChildValueByPath(each, "id");
      if (isEmptyOrSpaces(id)) continue;

      Profile profile = new Profile();
      profile.setId(id);
      profile.setSource(source);
      if (!addProfileIfDoesNotExist(profile, result)) continue;

      Element xmlActivation = MavenJDOMUtil.findChildByPath(each, "activation");
      if (xmlActivation != null) {
        Activation activation = new Activation();
        activation.setActiveByDefault("true".equals(MavenJDOMUtil.findChildValueByPath(xmlActivation, "activeByDefault")));

        Element xmlOS = MavenJDOMUtil.findChildByPath(xmlActivation, "os");
        if (xmlOS != null) {
          ActivationOS activationOS = new ActivationOS();
          activationOS.setName(MavenJDOMUtil.findChildValueByPath(xmlOS, "name"));
          activationOS.setFamily(MavenJDOMUtil.findChildValueByPath(xmlOS, "family"));
          activationOS.setArch(MavenJDOMUtil.findChildValueByPath(xmlOS, "arch"));
          activationOS.setVersion(MavenJDOMUtil.findChildValueByPath(xmlOS, "version"));
          activation.setOs(activationOS);
        }

        activation.setJdk(MavenJDOMUtil.findChildValueByPath(xmlActivation, "jdk"));

        Element xmlProperty = MavenJDOMUtil.findChildByPath(xmlActivation, "property");
        if (xmlProperty != null) {
          ActivationProperty activationProperty = new ActivationProperty();
          activationProperty.setName(MavenJDOMUtil.findChildValueByPath(xmlProperty, "name"));
          activationProperty.setValue(MavenJDOMUtil.findChildValueByPath(xmlProperty, "value"));
          activation.setProperty(activationProperty);
        }

        Element xmlFile = MavenJDOMUtil.findChildByPath(xmlActivation, "file");
        if (xmlFile != null) {
          ActivationFile activationFile = new ActivationFile();
          activationFile.setExists(MavenJDOMUtil.findChildValueByPath(xmlFile, "exists"));
          activationFile.setMissing(MavenJDOMUtil.findChildValueByPath(xmlFile, "missing"));
          activation.setFile(activationFile);
        }

        profile.setActivation(activation);
      }

      profile.setBuild(new BuildBase());
      readModelAndBuild(profile, profile.getBuild(), each);
    }
  }

  private boolean addProfileIfDoesNotExist(Profile profile, List<Profile> result) {
    for (Profile each : result) {
      if (Comparing.equal(each.getId(), profile.getId())) return false;
    }
    result.add(profile);
    return true;
  }

  private void collectProperties(Element xmlProperties, ModelBase mavenModelBase) {
    if (xmlProperties == null) return;

    Properties props = mavenModelBase.getProperties();

    for (Element each : (Iterable<? extends Element>)xmlProperties.getChildren()) {
      String name = each.getName();
      String value = each.getText();
      if (!props.containsKey(name) && !StringUtil.isEmptyOrSpaces(value)) {
        props.setProperty(name, value);
      }
    }
  }

  private List<String> applyProfiles(Model model, File basedir, Collection<String> explicitProfiles, Collection<String> alwaysOnProfiles) {
    List<Profile> activatedPom = new ArrayList<Profile>();
    List<Profile> activatedExternal = new ArrayList<Profile>();
    List<Profile> activeByDefault = new ArrayList<Profile>();

    List<Profile> rawProfiles = model.getProfiles();
    List<Profile> expandedProfilesCache = null;

    for (int i = 0; i < rawProfiles.size(); i++) {
      Profile eachRawProfile = rawProfiles.get(i);

      boolean shouldAdd = explicitProfiles.contains(eachRawProfile.getId()) || alwaysOnProfiles.contains(eachRawProfile.getId());

      Activation activation = eachRawProfile.getActivation();
      if (activation != null) {
        if (activation.isActiveByDefault()) {
          activeByDefault.add(eachRawProfile);
        }

        // expand only if necessary
        if (expandedProfilesCache == null) expandedProfilesCache = expandProperties(model, basedir).getProfiles();
        Profile eachExpandedProfile = expandedProfilesCache.get(i);

        for (ProfileActivator eachActivator : getProfileActivators()) {
          try {
            if (eachActivator.canDetermineActivation(eachExpandedProfile) && eachActivator.isActive(eachExpandedProfile)) {
              shouldAdd = true;
              break;
            }
          }
          catch (ProfileActivationException e) {
            MavenLog.LOG.warn(e);
          }
        }
      }

      if (shouldAdd) {
        if (PROFILE_FROM_POM.equals(eachRawProfile.getSource())) {
          activatedPom.add(eachRawProfile);
        }
        else {
          activatedExternal.add(eachRawProfile);
        }
      }
    }

    List<Profile> activatedProfiles = new ArrayList<Profile>(activatedPom.isEmpty() ? activeByDefault : activatedPom);
    activatedProfiles.addAll(activatedExternal);

    for (Profile each : activatedProfiles) {
      new DefaultProfileInjector().inject(each, model);
    }

    return ContainerUtil.mapNotNull(activatedProfiles, new NullableFunction<Profile, String>() {
      public String fun(Profile profile) {
        return profile.getId();
      }
    });
  }

  private ProfileActivator[] getProfileActivators() {
    SystemPropertyProfileActivator sysPropertyActivator = new SystemPropertyProfileActivator();
    DefaultContext context = new DefaultContext();
    context.put("SystemProperties", MavenEmbedderFactory.collectSystemProperties());
    try {
      sysPropertyActivator.contextualize(context);
    }
    catch (ContextException e) {
      MavenLog.LOG.error(e);
      return new ProfileActivator[0];
    }

    return new ProfileActivator[]{new FileProfileActivator(), sysPropertyActivator, new JdkPrefixProfileActivator(),
      new OperatingSystemProfileActivator()};
  }

  private void repairModelHeader(Model model) {
    if (isEmptyOrSpaces(model.getModelVersion())) model.setModelVersion("4.0.0");

    Parent parent = model.getParent();
    if (parent != null) {
      if (isEmptyOrSpaces(parent.getGroupId())) parent.setGroupId(UNKNOWN);
      if (isEmptyOrSpaces(parent.getArtifactId())) parent.setArtifactId(UNKNOWN);
      if (isEmptyOrSpaces(parent.getVersion())) parent.setVersion(UNKNOWN);
      if (isEmptyOrSpaces(parent.getRelativePath())) parent.setRelativePath("../pom.xml");
    }

    if (isEmptyOrSpaces(model.getGroupId())) {
      if (parent != null) {
        model.setGroupId(parent.getGroupId());
      }
      else {
        model.setGroupId(UNKNOWN);
      }
    }
    if (isEmptyOrSpaces(model.getArtifactId())) model.setArtifactId(UNKNOWN);
    if (isEmptyOrSpaces(model.getVersion())) {
      if (parent != null) {
        model.setVersion(parent.getVersion());
      }
      else {
        model.setVersion(UNKNOWN);
      }
    }

    if (isEmptyOrSpaces(model.getPackaging())) model.setPackaging("jar");
  }

  private void repairModelBody(Model model) {
    if (model.getBuild() == null) {
      model.setBuild(new Build());
    }
    Build build = model.getBuild();

    if (isEmptyOrSpaces(build.getFinalName())) {
      build.setFinalName("${project.artifactId}-${project.version}");
    }

    build.setSourceDirectory(isEmptyOrSpaces(build.getSourceDirectory()) ? "src/main/java" : build.getSourceDirectory());
    build.setTestSourceDirectory(isEmptyOrSpaces(build.getTestSourceDirectory()) ? "src/test/java" : build.getTestSourceDirectory());
    build
      .setScriptSourceDirectory(isEmptyOrSpaces(build.getScriptSourceDirectory()) ? "src/main/scripts" : build.getScriptSourceDirectory());

    build.setResources(repairResources(build.getResources(), "src/main/resources"));
    build.setTestResources(repairResources(build.getTestResources(), "src/test/resources"));

    build.setDirectory(isEmptyOrSpaces(build.getDirectory()) ? "target" : build.getDirectory());
    build
      .setOutputDirectory(isEmptyOrSpaces(build.getOutputDirectory()) ? "${project.build.directory}/classes" : build.getOutputDirectory());
    build.setTestOutputDirectory(
      isEmptyOrSpaces(build.getTestOutputDirectory()) ? "${project.build.directory}/test-classes" : build.getTestOutputDirectory());
  }

  private List<Resource> repairResources(List<Resource> resources, String defaultDir) {
    List<Resource> result = new ArrayList<Resource>();
    if (resources.isEmpty()) {
      result.add(createResource(defaultDir));
      return result;
    }

    for (Resource each : resources) {
      if (isEmptyOrSpaces(each.getDirectory())) continue;
      each.setDirectory(each.getDirectory());
      result.add(each);
    }
    return result;
  }

  private Resource createResource(String directory) {
    Resource result = new Resource();
    result.setDirectory(directory);
    return result;
  }

  private void resolveInheritance(final MavenGeneralSettings generalSettings,
                                  final Model model,
                                  final VirtualFile file,
                                  final Collection<String> explicitProfiles,
                                  final Set<VirtualFile> recursionGuard,
                                  final MavenProjectReaderProjectLocator locator,
                                  Collection<MavenProjectProblem> problems) {
    if (recursionGuard.contains(file)) {
      problems.add(
        createProblem(file, ProjectBundle.message("maven.project.problem.recursiveInheritance"), MavenProjectProblem.ProblemType.PARENT));
      return;
    }
    recursionGuard.add(file);

    try {
      Parent parent = model.getParent();
      final MavenParentDesc[] parentDesc = new MavenParentDesc[1];
      if (parent != null) {
        MavenId parentId = new MavenId(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
        if (parentId.equals(model.getGroupId(), model.getArtifactId(), model.getVersion())) {
          problems.add(
            createProblem(file, ProjectBundle.message("maven.project.problem.selfInheritance"), MavenProjectProblem.ProblemType.PARENT));
          return;
        }
        parentDesc[0] = new MavenParentDesc(parentId, parent.getRelativePath());
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
            Model parentModel = doReadProjectModel(parentFile, true).model;
            MavenId parentId = parentDesc[0].getParentId();
            if (!parentId.equals(new MavenId(parentModel))) return null;

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

      if (parentModelWithProblems == null) return; // no parent or parent not found;

      Model parentModel = parentModelWithProblems.second.model;
      if (!parentModelWithProblems.second.problems.isEmpty()) {
        problems.add(createProblem(parentModelWithProblems.first,
                                   ProjectBundle.message("maven.project.problem.parentHasProblems", new MavenId(parentModel)),
                                   MavenProjectProblem.ProblemType.PARENT));
      }

      new DefaultModelInheritanceAssembler().assembleModelInheritance(model, parentModel);

      List<Profile> profiles = model.getProfiles();
      for (Profile each : parentModel.getProfiles()) {
        addProfileIfDoesNotExist(each, profiles);
      }
    }
    finally {
      recursionGuard.remove(file);
    }
  }

  private Model expandProperties(Model model, File basedir) {
    return MavenEmbedderWrapper.interpolate(model, basedir);
  }

  private void alignModel(Model model, File basedir) {
    MavenEmbedderWrapper.alignModel(model, basedir);
  }

  private MavenProjectProblem createStructureProblem(VirtualFile file, String description) {
    return createProblem(file, description, MavenProjectProblem.ProblemType.STRUCTURE);
  }

  private MavenProjectProblem createSyntaxProblem(VirtualFile file, MavenProjectProblem.ProblemType type) {
    return createProblem(file, ProjectBundle.message("maven.project.problem.syntaxError", file.getName()), type);
  }

  private MavenProjectProblem createProblem(VirtualFile file, String description, MavenProjectProblem.ProblemType type) {
    return new MavenProjectProblem(file, description, type);
  }

  private LinkedHashSet<MavenProjectProblem> createProblemsList() {
    return createProblemsList(Collections.<MavenProjectProblem>emptySet());
  }

  private LinkedHashSet<MavenProjectProblem> createProblemsList(Collection<MavenProjectProblem> copyThis) {
    return new LinkedHashSet<MavenProjectProblem>(copyThis);
  }

  public MavenProjectReaderResult resolveProject(MavenGeneralSettings generalSettings,
                                                 MavenEmbedderWrapper embedder,
                                                 VirtualFile file,
                                                 Collection<String> explicitProfiles,
                                                 MavenProjectReaderProjectLocator locator) throws MavenProcessCanceledException {
    MavenProject mavenProject = null;
    Collection<MavenProjectProblem> problems = createProblemsList();
    Set<MavenId> unresolvedArtifactsIds = new THashSet<MavenId>();

    try {
      Pair<MavenProject, Set<MavenId>> result = doResolveProject(embedder, file, explicitProfiles, problems);
      mavenProject = result.first;
      unresolvedArtifactsIds = result.second;
    }
    catch (MavenProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      String message = e.getMessage();

      if (e instanceof RuntimeException && e.getCause() != null) {
        message = e.getCause().getMessage();
      }

      if (message != null) {
        problems.add(createStructureProblem(file, message));
      }
      MavenLog.LOG.info(e);
      MavenLog.printInTests(e); // print exception since we need to know if something wrong with our logic
    }

    if (mavenProject == null) {
      if (problems.isEmpty()) {
        problems.add(createSyntaxProblem(file, MavenProjectProblem.ProblemType.SYNTAX));
      }
      mavenProject = readProject(generalSettings, file, explicitProfiles, locator).nativeMavenProject;
    }

    return new MavenProjectReaderResult(generalSettings,
                                        problems,
                                        unresolvedArtifactsIds,
                                        embedder.getLocalRepositoryFile(),
                                        mavenProject,
                                        collectActivatedProfiles(mavenProject));
  }

  private Pair<MavenProject, Set<MavenId>> doResolveProject(MavenEmbedderWrapper embedder,
                                                            VirtualFile file,
                                                            Collection<String> profiles,
                                                            Collection<MavenProjectProblem> problems) throws MavenProcessCanceledException {
    MavenExecutionResult result = embedder.resolveProject(file, profiles);
    validate(file, result, problems);
    return Pair.create(result.getMavenProject(), result.getUnresolvedArtifactIds());
  }

  private boolean validate(VirtualFile file, MavenExecutionResult r, Collection<MavenProjectProblem> problems) {
    for (Exception each : r.getExceptions()) {
      MavenLog.LOG.info(each);

      if (each instanceof InvalidProjectModelException) {
        ModelValidationResult modelValidationResult = ((InvalidProjectModelException)each).getValidationResult();
        if (modelValidationResult != null) {
          for (Object eachValidationProblem : modelValidationResult.getMessages()) {
            problems.add(createStructureProblem(file, (String)eachValidationProblem));
          }
        }
        else {
          problems.add(createStructureProblem(file, each.getCause().getMessage()));
        }
      }
      else if (each instanceof ProjectBuildingException) {
        String causeMessage = each.getCause() != null ? each.getCause().getMessage() : each.getMessage();
        problems.add(createStructureProblem(file, causeMessage));
      }
      else {
        problems.add(createStructureProblem(file, each.getMessage()));
      }
    }

    return problems.isEmpty();
  }

  public MavenProjectReaderResult generateSources(MavenEmbedderWrapper embedder,
                                                  MavenGeneralSettings generalSettings,
                                                  MavenImportingSettings importingSettings,
                                                  VirtualFile file,
                                                  Collection<String> profiles,
                                                  MavenConsole console) throws MavenProcessCanceledException {
    try {
      MavenExecutionResult result = embedder.execute(file, profiles, Arrays.asList(importingSettings.getUpdateFoldersOnImportPhase()));

      if (result.hasExceptions()) {
        MavenConsoleHelper.printExecutionExceptions(console, result);
      }

      Collection<MavenProjectProblem> problems = createProblemsList();
      if (!validate(file, result, problems)) return null;

      MavenProject project = result.getMavenProject();
      if (project == null) return null;

      return new MavenProjectReaderResult(generalSettings, problems, result.getUnresolvedArtifactIds(), embedder.getLocalRepositoryFile(),
                                          project, collectActivatedProfiles(project));
    }
    catch (MavenProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      MavenConsoleHelper.printException(console, e);
      MavenLog.LOG.warn(e);
      return null;
    }
  }

  private Collection<String> collectActivatedProfiles(MavenProject mavenProject) {
    // for some reason project's active profiles do not contain parent's profiles - only local and settings'.
    // parent's profiles do not contain settings' profiles.

    List<Profile> profiles = new ArrayList<Profile>();
    while (mavenProject != null) {
      if (profiles != null) {
        profiles.addAll(mavenProject.getActiveProfiles());
      }
      mavenProject = mavenProject.getParent();
    }
    return collectProfilesIds(profiles);
  }

  private static Collection<String> collectProfilesIds(List<Profile> profiles) {
    Collection<String> result = new THashSet<String>();
    for (Profile each : profiles) {
      if (each.getId() != null) {
        result.add(each.getId());
      }
    }
    return result;
  }

  private Element readXml(final VirtualFile file,
                          final Collection<MavenProjectProblem> problems,
                          final MavenProjectProblem.ProblemType type) {
    return MavenJDOMUtil.read(file, new MavenJDOMUtil.ErrorHandler() {
      public void onReadError(IOException e) {
        MavenLog.LOG.warn("Cannot read the pom file: " + e);
        problems.add(createProblem(file, e.getMessage(), type));
      }

      public void onSyntaxError() {
        problems.add(createSyntaxProblem(file, type));
      }
    });
  }

  private static class RawModelReadResult {
    public Model model;
    public Collection<MavenProjectProblem> problems;
    public Set<String> alwaysOnProfiles;

    private RawModelReadResult(Model model, Collection<MavenProjectProblem> problems, Set<String> alwaysOnProfiles) {
      this.model = model;
      this.problems = problems;
      this.alwaysOnProfiles = alwaysOnProfiles;
    }
  }
}
