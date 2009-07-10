package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.source.parsing.xml.XmlBuilder;
import com.intellij.psi.impl.source.parsing.xml.XmlBuilderDriver;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.extension.ExtensionScanningException;
import org.apache.maven.model.*;
import org.apache.maven.profiles.activation.*;
import org.apache.maven.profiles.injection.DefaultProfileInjector;
import org.apache.maven.project.InvalidProjectModelException;
import org.apache.maven.project.JBMavenProjectHelper;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.inheritance.DefaultModelInheritanceAssembler;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.interpolation.ModelInterpolator;
import org.apache.maven.project.path.DefaultPathTranslator;
import org.apache.maven.project.path.PathTranslator;
import org.apache.maven.project.validation.ModelValidationResult;
import org.apache.maven.reactor.MissingModuleException;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.embedder.*;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MavenProjectReader {
  private static final String UNKNOWN = MavenId.UNKNOWN_VALUE;

  private final Map<VirtualFile, Model> myRawModelsCache = new THashMap<VirtualFile, Model>();

  public MavenProjectReaderResult readProject(MavenGeneralSettings generalSettings,
                                              VirtualFile file,
                                              List<String> activeProfiles,
                                              MavenProjectReaderProjectLocator locator) {
    File localRepository = generalSettings.getEffectiveLocalRepository();

    Pair<Model, List<Profile>> readResult = doReadProjectModel(generalSettings,
                                                               file,
                                                               localRepository,
                                                               activeProfiles,
                                                               new THashSet<VirtualFile>(),
                                                               locator);

    File basedir = getBaseDir(file);
    Model model = expandProperties(readResult.first, basedir);
    alignModel(model, basedir);

    MavenProject mavenProject = new MavenProject(model);
    mavenProject.setFile(new File(file.getPath()));
    mavenProject.setActiveProfiles(readResult.second);
    JBMavenProjectHelper.setSourceRoots(mavenProject,
                                        Collections.singletonList(model.getBuild().getSourceDirectory()),
                                        Collections.singletonList(model.getBuild().getTestSourceDirectory()),
                                        Collections.singletonList(model.getBuild().getScriptSourceDirectory()));

    return new MavenProjectReaderResult(true,
                                        activeProfiles,
                                        Collections.EMPTY_LIST,
                                        Collections.EMPTY_SET,
                                        localRepository,
                                        mavenProject);
  }

  private File getBaseDir(VirtualFile file) {
    return new File(file.getParent().getPath());
  }

  private Pair<Model, List<Profile>> doReadProjectModel(MavenGeneralSettings generalSettings,
                                                        VirtualFile file,
                                                        File localRepository,
                                                        List<String> activeProfiles,
                                                        Set<VirtualFile> recursionGuard,
                                                        MavenProjectReaderProjectLocator locator) {
    Model mavenModel = myRawModelsCache.get(file);
    if (mavenModel == null) {
      mavenModel = doReadProjectModel(file, generalSettings);
      myRawModelsCache.put(file, mavenModel);
    }

    List<Profile> activatedProfiles = applyProfiles(mavenModel, getBaseDir(file), activeProfiles);
    repairModelHeader(mavenModel);
    resolveInheritance(generalSettings, mavenModel, file, localRepository, activeProfiles, recursionGuard, locator);
    repairModelBody(mavenModel);

    return Pair.create(mavenModel, activatedProfiles);
  }

  private Model doReadProjectModel(VirtualFile file, MavenGeneralSettings generalSettings) {
    Model result = new Model();

    Element xmlProject = readXml(file).getChild("project");
    if (xmlProject == null) return result;

    result.setModelVersion(findChildValueByPath(xmlProject, "modelVersion"));
    result.setGroupId(findChildValueByPath(xmlProject, "groupId"));
    result.setArtifactId(findChildValueByPath(xmlProject, "artifactId"));
    result.setVersion(findChildValueByPath(xmlProject, "version"));
    result.setPackaging(findChildValueByPath(xmlProject, "packaging"));
    result.setName(findChildValueByPath(xmlProject, "name"));

    if (hasChildByPath(xmlProject, "parent")) {
      Parent parent = new Parent();

      String groupId = findChildValueByPath(xmlProject, "parent.groupId");
      String artifactId = findChildValueByPath(xmlProject, "parent.artifactId");
      String version = findChildValueByPath(xmlProject, "parent.version");

      parent.setGroupId(groupId);
      parent.setArtifactId(artifactId);
      parent.setVersion(version);
      parent.setRelativePath(findChildValueByPath(xmlProject, "parent.relativePath"));

      result.setParent(parent);
    }

    result.setBuild(new Build());
    readModelAndBuild(result, result.getBuild(), xmlProject);

    result.setProfiles(collectProfiles(generalSettings, file, xmlProject));
    return result;
  }

  private void readModelAndBuild(ModelBase mavenModelBase,
                                 BuildBase mavenBuildBase,
                                 Element xmlModel) {
    mavenModelBase.setModules(findChildrenValuesByPath(xmlModel, "modules", "module"));
    collectProperties(findChildByPath(xmlModel, "properties"), mavenModelBase);

    Element xmlBuild = findChildByPath(xmlModel, "build");
    if (xmlBuild == null) return;

    mavenBuildBase.setFinalName(findChildValueByPath(xmlBuild, "finalName"));
    mavenBuildBase.setDefaultGoal(findChildValueByPath(xmlBuild, "defaultGoal"));
    mavenBuildBase.setDirectory(findChildValueByPath(xmlBuild, "directory"));
    mavenBuildBase.setResources(collectResources(findChildrenByPath(xmlBuild, "resources", "resource")));
    mavenBuildBase.setTestResources(collectResources(findChildrenByPath(xmlBuild, "testResources", "testResource")));

    if (mavenBuildBase instanceof Build) {
      Build mavenBuild = (Build)mavenBuildBase;

      mavenBuild.setSourceDirectory(findChildValueByPath(xmlBuild, "sourceDirectory"));
      mavenBuild.setTestSourceDirectory(findChildValueByPath(xmlBuild, "testSourceDirectory"));
      mavenBuild.setScriptSourceDirectory(findChildValueByPath(xmlBuild, "scriptSourceDirectory"));
      mavenBuild.setOutputDirectory(findChildValueByPath(xmlBuild, "outputDirectory"));
      mavenBuild.setTestOutputDirectory(findChildValueByPath(xmlBuild, "testOutputDirectory"));
    }
  }

  private List<Resource> collectResources(List<Element> xmlResources) {
    List<Resource> result = new ArrayList<Resource>();
    for (Element each : xmlResources) {
      Resource r = new Resource();
      r.setDirectory(findChildValueByPath(each, "directory"));
      r.setFiltering("true".equals(findChildValueByPath(each, "filtering")));
      r.setTargetPath(findChildValueByPath(each, "targetPath"));
      r.setIncludes(findChildrenValuesByPath(each, "includes", "include"));
      r.setExcludes(findChildrenValuesByPath(each, "excludes", "exclude"));
      result.add(r);
    }
    return result;
  }

  private List<Profile> collectProfiles(MavenGeneralSettings generalSettings, VirtualFile file, Element xmlProject) {
    List<Profile> result = new ArrayList<Profile>();
    collectProfiles(findChildrenByPath(xmlProject, "profiles", "profile"), result);

    VirtualFile profilesFile = MavenUtil.findProfilesXmlFile(file);
    if (profilesFile != null) {
      Element profilesFileElement = readXml(profilesFile);
      Element rootElement = findChildByPath(profilesFileElement, "profiles");
      if (rootElement == null) rootElement = findChildByPath(profilesFileElement, "profilesXml.profiles");
      List<Element> xmlProfiles = collectChildren(rootElement, "profile");
      collectProfiles(xmlProfiles, result);
    }

    for (VirtualFile each : generalSettings.getEffectiveSettingsFiles()) {
      collectProfilesFromSettingsFile(each, result);
    }

    return result;
  }

  private void collectProfilesFromSettingsFile(VirtualFile settingsFile, List<Profile> result) {
    if (settingsFile == null) return;
    List<Element> xmlProfiles = findChildrenByPath(readXml(settingsFile), "settings.profiles", "profile");
    collectProfiles(xmlProfiles, result);
  }

  private void collectProfiles(List<Element> xmlProfiles, List<Profile> result) {
    for (Element each : xmlProfiles) {
      String id = findChildValueByPath(each, "id");
      if (isEmptyOrSpaces(id)) continue;

      Profile profile = new Profile();
      profile.setId(id);

      Element xmlActivation = findChildByPath(each, "activation");
      if (xmlActivation != null) {
        Activation activation = new Activation();
        activation.setActiveByDefault("true".equals(findChildValueByPath(xmlActivation, "activeByDefault")));

        Element xmlOS = findChildByPath(xmlActivation, "os");
        if (xmlOS != null) {
          ActivationOS activationOS = new ActivationOS();
          activationOS.setName(findChildValueByPath(xmlOS, "name"));
          activationOS.setFamily(findChildValueByPath(xmlOS, "family"));
          activationOS.setArch(findChildValueByPath(xmlOS, "arch"));
          activationOS.setVersion(findChildValueByPath(xmlOS, "version"));
          activation.setOs(activationOS);
        }

        activation.setJdk(findChildValueByPath(xmlActivation, "jdk"));

        Element xmlProperty = findChildByPath(xmlActivation, "property");
        if (xmlProperty != null) {
          ActivationProperty activationProperty = new ActivationProperty();
          activationProperty.setName(findChildValueByPath(xmlProperty, "name"));
          activationProperty.setValue(findChildValueByPath(xmlProperty, "value"));
          activation.setProperty(activationProperty);
        }

        Element xmlFile = findChildByPath(xmlActivation, "file");
        if (xmlFile != null) {
          ActivationFile activationFile = new ActivationFile();
          activationFile.setExists(findChildValueByPath(xmlFile, "exists"));
          activationFile.setMissing(findChildValueByPath(xmlFile, "missing"));
          activation.setFile(activationFile);
        }

        profile.setActivation(activation);
      }

      profile.setBuild(new BuildBase());
      readModelAndBuild(profile, profile.getBuild(), each);

      result.add(profile);
    }
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

  private List<Profile> applyProfiles(Model model, File basedir, List<String> profiles) {
    List<Profile> activated = new ArrayList<Profile>();
    List<Profile> activeByDefault = new ArrayList<Profile>();

    ProfileActivationContext context = new DefaultProfileActivationContext(MavenUtil.getSystemProperties(), true);

    List<Profile> rawProfiles = model.getProfiles();
    List<Profile> expandedProfiles = null;

    for (int i = 0; i < rawProfiles.size(); i++) {
      Profile eachRawProfile = rawProfiles.get(i);

      if (profiles.contains(eachRawProfile.getId())) {
        activated.add(eachRawProfile);
      }

      Activation activation = eachRawProfile.getActivation();
      if (activation == null) continue;

      if (activation.isActiveByDefault()) {
        activeByDefault.add(eachRawProfile);
      }

      ProfileActivator[] activators = new ProfileActivator[]{
        new FileProfileActivator(),
        new SystemPropertyProfileActivator(),
        new JdkPrefixProfileActivator(),
        new OperatingSystemProfileActivator()
      };

      // expand only if necessary
      if (expandedProfiles == null) expandedProfiles = expandProperties(model, basedir).getProfiles();
      Profile eachExpandedProfile = expandedProfiles.get(i);

      for (ProfileActivator eachActivator : activators) {
        try {
          if (eachActivator.canDetermineActivation(eachExpandedProfile, context)
              && eachActivator.isActive(eachExpandedProfile, context)) {
            activated.add(eachRawProfile);
          }
        }
        catch (ProfileActivationException e) {
          MavenLog.LOG.warn(e);
        }
      }
    }

    List<Profile> result = activated.isEmpty() ? activeByDefault : activated;

    for (Profile each : result) {
      new DefaultProfileInjector().inject(each, model);
    }

    return result;
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
      build.setFinalName(model.getArtifactId() + "-" + model.getVersion());
    }

    build.setSourceDirectory(isEmptyOrSpaces(build.getSourceDirectory())
                             ? "src/main/java"
                             : build.getSourceDirectory());
    build.setTestSourceDirectory(isEmptyOrSpaces(build.getTestSourceDirectory())
                                 ? "src/test/java"
                                 : build.getTestSourceDirectory());
    build.setScriptSourceDirectory(isEmptyOrSpaces(build.getScriptSourceDirectory())
                                   ? "src/main/scripts"
                                   : build.getScriptSourceDirectory());

    build.setResources(repairResources(build.getResources(), "src/main/resources"));
    build.setTestResources(repairResources(build.getTestResources(), "src/test/resources"));

    build.setDirectory(isEmptyOrSpaces(build.getDirectory()) ? "target" : build.getDirectory());
    build.setOutputDirectory(isEmptyOrSpaces(build.getOutputDirectory())
                             ? "target/classes"
                             : build.getOutputDirectory());
    build.setTestOutputDirectory(isEmptyOrSpaces(build.getTestOutputDirectory())
                                 ? "target/test-classes"
                                 : build.getTestOutputDirectory());
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
                                  Model model,
                                  VirtualFile file,
                                  final File localRepository,
                                  final List<String> activeProfiles,
                                  final Set<VirtualFile> recursionGuard,
                                  final MavenProjectReaderProjectLocator locator) {
    if (recursionGuard.contains(file)) return;
    recursionGuard.add(file);

    Parent parent = model.getParent();
    if (parent == null) return;

    final String parentGroupId = parent.getGroupId();
    final String parentArtifactId = parent.getArtifactId();
    final String parentVersion = parent.getVersion();

    Model parentModel = new MavenParentProjectFileProcessor<Model>() {
      protected VirtualFile findManagedFile(MavenId id) {
        return locator.findProjectFile(id);
      }

      @Override
      protected Model processRelativeParent(VirtualFile parentFile) {
        Model result = super.processRelativeParent(parentFile);
        if (!(parentGroupId.equals(result.getGroupId())
              && parentArtifactId.equals(result.getArtifactId())
              && parentVersion.equals(result.getVersion()))) {
          return null;
        }
        return result;
      }

      @Override
      protected Model doProcessParent(VirtualFile parentFile) {
        return doReadProjectModel(generalSettings,
                                  parentFile,
                                  localRepository,
                                  activeProfiles,
                                  recursionGuard,
                                  locator).first;
      }
    }.process(file,
              new MavenId(parentGroupId, parentArtifactId, parentVersion),
              parent.getRelativePath(),
              localRepository);

    if (parentModel != null) {
      new DefaultModelInheritanceAssembler().assembleModelInheritance(model, parentModel);
    }
  }

  private Model expandProperties(Model model, File basedir) {
    ModelInterpolator interpolator = new CustomModelInterpolator(new DefaultPathTranslator());

    Map context = MavenEmbedderFactory.collectSystemProperties();
    Map overrideContext = new THashMap();

    try {
      model = interpolator.interpolate(model, context, overrideContext, basedir, false);
    }
    catch (ModelInterpolationException e) {
      MavenLog.LOG.warn(e);
    }

    return model;
  }

  private void alignModel(Model model, File basedir) {
    PathTranslator pathTranslator = new DefaultPathTranslator();
    pathTranslator.alignToBaseDirectory(model, basedir);
    Build build = model.getBuild();
    build.setScriptSourceDirectory(pathTranslator.alignToBaseDirectory(build.getScriptSourceDirectory(), basedir));
  }

  public MavenProjectReaderResult resolveProject(MavenGeneralSettings generalSettings,
                                                 MavenEmbedderWrapper embedder,
                                                 VirtualFile f,
                                                 List<String> activeProfiles,
                                                 MavenProjectReaderProjectLocator locator,
                                                 MavenProgressIndicator process) throws MavenProcessCanceledException {
    MavenProject mavenProject = null;
    boolean isValid = true;
    List<MavenProjectProblem> problems = new ArrayList<MavenProjectProblem>();
    Set<MavenId> unresolvedArtifactsIds = new THashSet<MavenId>();

    String path = f.getPath();

    try {
      Pair<MavenProject, Set<MavenId>> result = doResolveProject(embedder, path, activeProfiles, problems, process);
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
        problems.add(new MavenProjectProblem(message, true));
      }
      MavenLog.LOG.info(e);
      MavenLog.printInTests(e); // print exception since we need to know if something wrong with our logic
    }

    if (mavenProject == null) {
      isValid = false;

      if (problems.isEmpty()) {
        problems.add(new MavenProjectProblem(ProjectBundle.message("maven.project.problem.syntaxError"), true));
      }
      mavenProject = readProject(generalSettings, f, activeProfiles, locator).nativeMavenProject;
    }

    return new MavenProjectReaderResult(isValid,
                                        activeProfiles,
                                        problems,
                                        unresolvedArtifactsIds,
                                        new File(embedder.getLocalRepository()),
                                        mavenProject);
  }

  private Pair<MavenProject, Set<MavenId>> doResolveProject(MavenEmbedderWrapper embedder,
                                                            String path,
                                                            List<String> profiles,
                                                            List<MavenProjectProblem> problems,
                                                            MavenProgressIndicator p) throws MavenProcessCanceledException {
    MavenExecutionRequest request = createRequest(embedder, path, profiles);
    Pair<MavenExecutionResult, Set<MavenId>> result = embedder.resolveProject(request, p);
    validate(result.first, problems);
    return Pair.create(result.first.getProject(), result.second);
  }

  private boolean validate(MavenExecutionResult r, List<MavenProjectProblem> problems) {
    for (Exception each : (List<Exception>)r.getExceptions()) {
      MavenLog.LOG.info(each);

      if (each instanceof InvalidProjectModelException) {
        ModelValidationResult modelValidationResult = ((InvalidProjectModelException)each).getValidationResult();
        if (modelValidationResult != null) {
          for (Object eachValidationProblem : modelValidationResult.getMessages()) {
            problems.add(new MavenProjectProblem((String)eachValidationProblem, true));
          }
        }
        else {
          problems.add(new MavenProjectProblem(each.getCause().getMessage(), true));
        }
      }
      else if (each instanceof MissingModuleException) {
        problems.add(new MavenProjectProblem(ProjectBundle.message("maven.project.problem.missingModule",
                                                                   ((MissingModuleException)each).getModuleName()), false));
      }
      else if (each instanceof ExtensionScanningException) {
        String causeMessage;

        if (each.getCause() instanceof ProjectBuildingException) {
          if (each.getCause().getCause() != null) {
            causeMessage = each.getCause().getCause().getMessage();
          }
          else {
            causeMessage = each.getCause().getMessage();
          }
        }
        else {
          causeMessage = each.getMessage();
        }

        problems.add(new MavenProjectProblem(causeMessage, true));
      }
      else if (each instanceof ProjectBuildingException) {
        String causeMessage = each.getCause() != null
                              ? each.getCause().getMessage()
                              : each.getMessage();
        problems.add(new MavenProjectProblem(causeMessage, true));
      }
      else {
        problems.add(new MavenProjectProblem(each.getMessage(), true));
      }
    }

    List<Exception> ee = new ArrayList<Exception>();
    ArtifactResolutionResult resolutionResult = r.getArtifactResolutionResult();
    if (resolutionResult != null) {
      ee.addAll(resolutionResult.getCircularDependencyExceptions());
      ee.addAll(resolutionResult.getMetadataResolutionExceptions());
      ee.addAll(resolutionResult.getVersionRangeViolations());
      ee.addAll(resolutionResult.getErrorArtifactExceptions());
    }

    for (Exception each : ee) {
      problems.add(new MavenProjectProblem(each.getMessage(), false));
      MavenLog.LOG.info(each);
    }

    return ee.isEmpty();
  }

  public MavenProjectReaderResult generateSources(MavenEmbedderWrapper embedder,
                                                  MavenImportingSettings importingSettings,
                                                  VirtualFile f,
                                                  List<String> profiles,
                                                  MavenConsole console,
                                                  MavenProgressIndicator p) throws MavenProcessCanceledException {
    try {
      MavenExecutionRequest request = createRequest(embedder, f.getPath(), profiles);
      request.setGoals(Arrays.asList(importingSettings.getUpdateFoldersOnImportPhase()));

      Pair<MavenExecutionResult, Set<MavenId>> result = embedder.execute(request, p);

      if (result.first.hasExceptions()) {
        MavenConsoleHelper.printExecutionExceptions(console, result.first);
      }

      List<MavenProjectProblem> problems = new ArrayList<MavenProjectProblem>();
      if (!validate(result.first, problems)) return null;

      MavenProject project = result.first.getProject();
      if (project == null) return null;

      return new MavenProjectReaderResult(true,
                                          profiles,
                                          problems,
                                          result.second,
                                          new File(embedder.getLocalRepository()),
                                          project);
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

  private MavenExecutionRequest createRequest(MavenEmbedderWrapper embedder,
                                              String path,
                                              List<String> profiles) {
    MavenExecutionRequest req = new DefaultMavenExecutionRequest();

    req.setPomFile(path);
    req.setRecursive(false);
    req.setSettings(embedder.getEmbedder().getSettings());
    req.setProxies(embedder.getEmbedder().getSettings().getProxies());

    if (profiles != null) req.addActiveProfiles(profiles);

    return req;
  }

  private Element readXml(VirtualFile file) {
    final LinkedList<Element> stack = new LinkedList<Element>();
    final Element root = new Element("root");

    String text;
    try {
      text = VfsUtil.loadText(file);
    }
    catch (IOException e) {
      MavenLog.LOG.warn("Cannot read the pom file: " + e);
      return root;
    }

    XmlBuilderDriver driver = new XmlBuilderDriver(text);
    XmlBuilder builder = new XmlBuilder() {
      public void doctype(@Nullable CharSequence publicId,
                          @Nullable CharSequence systemId,
                          int startOffset,
                          int endOffset) {
      }

      public ProcessingOrder startTag(CharSequence localName,
                                      String namespace,
                                      int startoffset,
                                      int endoffset,
                                      int headerEndOffset) {
        String name = localName.toString();
        if (StringUtil.isEmptyOrSpaces(name)) return ProcessingOrder.TAGS;

        Element newElement = new Element(name);

        Element parent = stack.isEmpty() ? root : stack.getLast();
        parent.addContent(newElement);
        stack.addLast(newElement);

        return ProcessingOrder.TAGS_AND_TEXTS;
      }

      public void endTag(CharSequence localName, String namespace, int startoffset, int endoffset) {
        String name = localName.toString();
        if (isEmptyOrSpaces(name)) return;

        int index = -1;
        for (int i = stack.size() - 1; i >= 0; i--) {
          if (stack.get(i).getName().equals(name)) {
            index = i;
            break;
          }
        }
        if (index == -1) return;
        while (stack.size() > index) {
          stack.removeLast();
        }
      }

      public void textElement(CharSequence text, CharSequence physical, int startoffset, int endoffset) {
        stack.getLast().addContent(text.toString());
      }

      public void attribute(CharSequence name, CharSequence value, int startoffset, int endoffset) {
      }

      public void entityRef(CharSequence ref, int startOffset, int endOffset) {
      }
    };

    driver.build(builder);
    return root;
  }

  private Element findChildByPath(Element element, String path) {
    List<String> parts = StringUtil.split(path, ".");
    Element current = element;
    for (String each : parts) {
      current = current.getChild(each);
      if (current == null) break;
    }
    return current;
  }

  private boolean hasChildByPath(Element element, String path) {
    return findChildValueByPath(element, path) != null;
  }

  private String findChildValueByPath(Element element, String path) {
    Element child = findChildByPath(element, path);
    return child == null ? null : child.getText();
  }

  private List<Element> findChildrenByPath(Element element, String path, String childrenName) {
    return collectChildren(findChildByPath(element, path), childrenName);
  }

  private List<Element> collectChildren(Element container, String childrenName) {
    if (container == null) return Collections.emptyList();

    List<Element> result = new ArrayList<Element>();
    for (Element each : (Iterable<? extends Element>)container.getChildren(childrenName)) {
      result.add(each);
    }
    return result;
  }

  private List<String> findChildrenValuesByPath(Element element, String path, String childrenName) {
    List<String> result = new ArrayList<String>();
    for (Element each : findChildrenByPath(element, path, childrenName)) {
      String value = each.getValue();
      if (!StringUtil.isEmptyOrSpaces(value)) {
        result.add(value);
      }
    }
    return result;
  }
}
