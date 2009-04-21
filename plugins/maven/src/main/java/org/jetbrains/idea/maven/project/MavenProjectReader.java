package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import static com.intellij.openapi.util.text.StringUtil.isEmptyOrSpaces;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.GenericDomValue;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.extension.ExtensionScanningException;
import org.apache.maven.model.*;
import org.apache.maven.profiles.activation.*;
import org.apache.maven.profiles.injection.DefaultProfileInjector;
import org.apache.maven.project.InvalidProjectModelException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.inheritance.DefaultModelInheritanceAssembler;
import org.apache.maven.project.interpolation.ModelInterpolationException;
import org.apache.maven.project.interpolation.RegexBasedModelInterpolator;
import org.apache.maven.project.path.DefaultPathTranslator;
import org.apache.maven.project.validation.ModelValidationResult;
import org.apache.maven.reactor.MissingModuleException;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.dom.settings.MavenDomSettingsModel;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.embedder.MavenConsoleHelper;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenId;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

public class MavenProjectReader {
  private static final String UNKNOWN = "Unknown";
  private static final String UNNAMED = "Unnamed";

  public static MavenProjectReaderResult readProjectQuickly(Project project,
                                                            VirtualFile file,
                                                            List<String> activeProfiles,
                                                            MavenProjectReaderProjectLocator locator) {
    File localRepository = MavenProjectsManager.getInstance(project).getLocalRepository();

    Pair<Model, List<Profile>> readResult = doReadProjectModelQuickly(project, file, localRepository, activeProfiles, new HashSet<VirtualFile>(), locator);

    File basedir = new File(file.getParent().getPath());
    Model model = expandProperties(readResult.first, basedir);
    alignModel(model, basedir);

    MavenProject mavenProject = new MavenProject(model);
    mavenProject.setFile(new File(file.getPath()));
    mavenProject.setActiveProfiles(readResult.second);
    MavenProjectHelper.setSourceRoots(mavenProject,
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

  private static Pair<Model, List<Profile>> doReadProjectModelQuickly(Project project,
                                                                      VirtualFile file,
                                                                      File localRepository,
                                                                      List<String> activeProfiles,
                                                                      Set<VirtualFile> recursionGuard,
                                                                      MavenProjectReaderProjectLocator locator) {
    Model mavenModel = new Model();
    DomFileElement<MavenDomProjectModel> domFile = getDomFile(project, file, MavenDomProjectModel.class);

    if (domFile != null) {
      MavenDomProjectModel domProject = domFile.getRootElement();
      mavenModel.setModelVersion(domProject.getModelVersion().getStringValue());
      mavenModel.setGroupId(domProject.getGroupId().getStringValue());
      mavenModel.setArtifactId(domProject.getArtifactId().getStringValue());
      mavenModel.setVersion(domProject.getVersion().getStringValue());
      mavenModel.setPackaging(domProject.getPackaging().getStringValue());
      mavenModel.setName(domProject.getName().getStringValue());

      MavenDomParent domParent = domProject.getMavenParent();
      if (domElementExists(domParent)) {
        Parent parent = new Parent();

        String groupId = domParent.getGroupId().getStringValue();
        String artifactId = domParent.getArtifactId().getStringValue();
        String version = domParent.getVersion().getStringValue();

        parent.setGroupId(groupId);
        parent.setArtifactId(artifactId);
        parent.setVersion(version);
        parent.setRelativePath(domParent.getRelativePath().getStringValue());

        mavenModel.setParent(parent);
      }

      mavenModel.setBuild(new Build());
      readModelAndBuild(mavenModel, mavenModel.getBuild(), domProject);

      mavenModel.setProfiles(collectProfiles(project, file, domProject));
    }

    List<Profile> activatedProfiles = applyProfiles(mavenModel, activeProfiles);
    repairModelHeader(mavenModel);
    resolveInheritance(project, mavenModel, file, localRepository, activeProfiles, recursionGuard, locator);
    repairModelBody(mavenModel);

    return Pair.create(mavenModel, activatedProfiles);
  }

  private static void readModelAndBuild(ModelBase mavenModelBase,
                                        BuildBase mavenBuildBase,
                                        MavenDomProjectModelBase domModelBase) {
    mavenModelBase.setModules(readModules(domModelBase.getModules()));
    collectProperties(domModelBase.getProperties(), mavenModelBase);

    MavenDomBuildBase domBuildBase = domModelBase.getBuild();
    if (!domElementExists(domBuildBase)) return;

    mavenBuildBase.setFinalName(domBuildBase.getFinalName().getStringValue());
    mavenBuildBase.setDefaultGoal(domBuildBase.getDefaultGoal().getStringValue());
    mavenBuildBase.setDirectory(domBuildBase.getDirectory().getStringValue());
    mavenBuildBase.setResources(collectResources(domBuildBase.getResources().getResources()));
    mavenBuildBase.setTestResources(collectResources(domBuildBase.getTestResources().getTestResources()));

    if (mavenBuildBase instanceof Build) {
      Build mavenBuild = (Build)mavenBuildBase;
      MavenDomBuild domBuild = (MavenDomBuild)domBuildBase;

      mavenBuild.setSourceDirectory(domBuild.getSourceDirectory().getStringValue());
      mavenBuild.setTestSourceDirectory(domBuild.getTestSourceDirectory().getStringValue());
      mavenBuild.setOutputDirectory(domBuild.getOutputDirectory().getStringValue());
      mavenBuild.setTestOutputDirectory(domBuild.getTestOutputDirectory().getStringValue());
      mavenBuild.setScriptSourceDirectory(domBuild.getScriptSourceDirectory().getStringValue());
    }
  }

  private static List<Resource> collectResources(List<MavenDomResource> resources) {
    List<Resource> result = new ArrayList<Resource>();
    for (MavenDomResource each : resources) {
      Resource r = new Resource();
      r.setDirectory(each.getDirectory().getStringValue());
      r.setFiltering("true".equals(each.getFiltering().getStringValue()));
      r.setTargetPath(each.getTargetPath().getStringValue());
      r.setIncludes(collectIncludesOrExcludes(each.getIncludes().getIncludes()));
      r.setExcludes(collectIncludesOrExcludes(each.getExcludes().getExcludes()));
      result.add(r);
    }
    return result;
  }

  private static List<String> collectIncludesOrExcludes(List<GenericDomValue<String>> includesOrExcludes) {
    List<String> result = new ArrayList<String>();
    for (GenericDomValue<String> eachExclude : includesOrExcludes) {
      result.add(eachExclude.getStringValue());
    }
    return result;
  }

  private static List<Profile> collectProfiles(Project project, VirtualFile file, MavenDomProjectModel domProject) {
    MavenProjectsManager mavenManager = MavenProjectsManager.getInstance(project);

    List<Profile> result = new ArrayList<Profile>();
    collectProfiles(domProject.getProfiles(), result);

    VirtualFile profilesFile = file.getParent().findChild(MavenConstants.PROFILES_XML);
    if (profilesFile != null) {
      DomFileElement<MavenDomProfiles> domProfilesFile = getDomFile(project, profilesFile, MavenDomProfiles.class);
      if (domProfilesFile != null) {
        collectProfiles(domProfilesFile.getRootElement(), result);
      }
    }

    MavenGeneralSettings settings = mavenManager.getGeneralSettings();
    File userSettings = MavenEmbedderFactory.resolveUserSettingsFile(settings.getMavenSettingsFile());
    collectProfilesFromSettingsFile(project, userSettings, result);

    File globalSettings = MavenEmbedderFactory.resolveGlobalSettingsFile(settings.getMavenHome());
    collectProfilesFromSettingsFile(project, globalSettings, result);

    return result;
  }

  private static void collectProfilesFromSettingsFile(Project project, File settings, List<Profile> result) {
    if (settings == null) return;

    VirtualFile settingsFile = LocalFileSystem.getInstance().findFileByIoFile(settings);

    if (settingsFile == null) return;
    DomFileElement<MavenDomSettingsModel> domSettingsFile = getDomFile(project, settingsFile, MavenDomSettingsModel.class);

    if (domSettingsFile == null) return;
    collectProfiles(domSettingsFile.getRootElement().getProfiles(), result);
  }

  private static void collectProfiles(MavenDomProfiles domProfiles, List<Profile> result) {
    for (MavenDomProfile each : domProfiles.getProfiles()) {
      String id = each.getId().getStringValue();
      if (isEmptyOrSpaces(id)) continue;

      Profile profile = new Profile();
      profile.setId(id);

      MavenDomActivation domActivation = each.getActivation();
      if (domElementExists(domActivation)) {
        Activation activation = new Activation();
        activation.setActiveByDefault("true".equals(domActivation.getActiveByDefault().getStringValue()));
        profile.setActivation(activation);
      }

      profile.setBuild(new BuildBase());
      readModelAndBuild(profile, profile.getBuild(), each);

      result.add(profile);
    }
  }

  private static <T extends DomElement> DomFileElement<T> getDomFile(Project project, VirtualFile file, Class<T> domClass) {
    XmlFile xmlFile = (XmlFile)PsiManager.getInstance(project).findFile(file);
    if (xmlFile == null) return null;
    return DomManager.getDomManager(project).getFileElement(xmlFile, domClass);
  }

  private static ArrayList<String> readModules(MavenDomModules modules) {
    ArrayList<String> result = new ArrayList<String>();
    for (MavenDomModule each : modules.getModules()) {
      String path = each.getStringValue();
      result.add(path);
    }
    return result;
  }

  private static void collectProperties(DomElement domProperties, ModelBase mavenModelBase) {
    Properties props = mavenModelBase.getProperties();

    XmlTag propertiesTag = domProperties.getXmlTag();
    if (propertiesTag == null) return;

    for (XmlTag each : propertiesTag.getSubTags()) {
      String name = each.getName();
      if (!props.containsKey(name)) {
        props.setProperty(name, each.getValue().getText());
      }
    }
  }

  private static boolean domElementExists(DomElement domParent) {
    return domParent.getXmlElement() != null;
  }

  private static List<Profile> applyProfiles(Model model, List<String> profiles) {
    List<Profile> activated = new ArrayList<Profile>();
    List<Profile> activeByDefault = new ArrayList<Profile>();

    ProfileActivationContext context = new DefaultProfileActivationContext(System.getProperties(), true);

    for (Profile eachProfile : (Iterable<? extends Profile>)model.getProfiles()) {
      if (profiles.contains(eachProfile.getId())) {
        activated.add(eachProfile);
      }

      Activation activation = eachProfile.getActivation();
      if (activation == null) continue;

      if (activation.isActiveByDefault()) {
        activeByDefault.add(eachProfile);
      }

      ProfileActivator[] activators = new ProfileActivator[]{
        new FileProfileActivator(),
        new SystemPropertyProfileActivator(),
        new JdkPrefixProfileActivator(),
        new OperatingSystemProfileActivator()
      };

      for (ProfileActivator eachActivator : activators) {
        try {
          if (eachActivator.canDetermineActivation(eachProfile, context)
              && eachActivator.isActive(eachProfile, context)) {
            activated.add(eachProfile);
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

  private static void repairModelHeader(Model model) {
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

    if (isEmptyOrSpaces(model.getName())) model.setName(UNNAMED);
    if (isEmptyOrSpaces(model.getPackaging())) model.setPackaging("jar");
  }

  private static void repairModelBody(Model model) {
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

  private static List<Resource> repairResources(List<Resource> resources, String defaultDir) {
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

  private static Resource createResource(String directory) {
    Resource result = new Resource();
    result.setDirectory(directory);
    return result;
  }

  private static void resolveInheritance(Project project,
                                         Model model,
                                         VirtualFile file,
                                         File localRepository,
                                         List<String> activeProfiles,
                                         Set<VirtualFile> recursionGuard,
                                         MavenProjectReaderProjectLocator locator) {
    if (recursionGuard.contains(file)) return;
    recursionGuard.add(file);

    Parent parent = model.getParent();
    if (parent == null) return;

    Model parentModel = null;

    String parentGroupId = parent.getGroupId();
    String parentArtifactId = parent.getArtifactId();
    String parentVersion = parent.getVersion();

    VirtualFile parentFile = locator.findProjectFile(new MavenId(parentGroupId, parentArtifactId, parentVersion));
    if (parentFile != null) {
      parentModel = doReadProjectModelQuickly(project, parentFile, localRepository, activeProfiles, recursionGuard, locator).first;
    }

    if (parentModel == null) {
      parentFile = file.getParent().findFileByRelativePath(parent.getRelativePath());
      if (parentFile != null) {
        parentModel = doReadProjectModelQuickly(project, parentFile, localRepository, activeProfiles, recursionGuard, locator).first;
        if (!(parentGroupId.equals(parentModel.getGroupId())
              && parentArtifactId.equals(parentModel.getArtifactId())
              && parentVersion.equals(parentModel.getVersion()))) {
          parentModel = null;
        }
      }
    }

    if (parentModel == null) {
      File parentIoFile = MavenArtifactUtil.getArtifactFile(localRepository,
                                                            parent.getGroupId(),
                                                            parent.getArtifactId(),
                                                            parent.getVersion(),
                                                            "pom");
      parentFile = LocalFileSystem.getInstance().findFileByIoFile(parentIoFile);
      if (parentFile != null) {
        parentModel = doReadProjectModelQuickly(project, parentFile, localRepository, activeProfiles, recursionGuard, locator).first;
      }
    }

    if (parentModel != null) {
      new DefaultModelInheritanceAssembler().assembleModelInheritance(model, parentModel);
    }
  }

  private static Model expandProperties(Model model, File basedir) {
    RegexBasedModelInterpolator interpolator;
    try {
      interpolator = new RegexBasedModelInterpolator();
      Field f = RegexBasedModelInterpolator.class.getDeclaredField("pathTranslator");
      f.setAccessible(true);
      f.set(interpolator, createPathTranslator());
    }
    catch (Exception e) {
      MavenLog.LOG.error(e);
      return model;
    }

    Map context = new HashMap();
    Map overrideContext = new HashMap();

    try {
      model = interpolator.interpolate(model, context, overrideContext, basedir, false);
    }
    catch (ModelInterpolationException e) {
      MavenLog.LOG.warn(e);
    }

    return model;
  }

  private static void alignModel(Model model, File basedir) {
    DefaultPathTranslator pathTranslator = createPathTranslator();
    pathTranslator.alignToBaseDirectory(model, basedir);
    Build build = model.getBuild();
    build.setScriptSourceDirectory(pathTranslator.alignToBaseDirectory(build.getScriptSourceDirectory(), basedir));
  }

  private static DefaultPathTranslator createPathTranslator() {
    return new DefaultPathTranslator();
  }

  public static MavenProjectReaderResult readProject(Project project,
                                                     MavenEmbedderWrapper embedder,
                                                     VirtualFile f,
                                                     List<String> activeProfiles,
                                                     MavenProjectReaderProjectLocator locator,
                                                     MavenProcess process) throws MavenProcessCanceledException {
    MavenProject mavenProject = null;
    boolean isValid = true;
    List<MavenProjectProblem> problems = new ArrayList<MavenProjectProblem>();
    Set<MavenId> unresolvedArtifactsIds = new HashSet<MavenId>();

    String path = f.getPath();

    try {
      Pair<MavenProject, Set<MavenId>> result = doReadProject(embedder, path, activeProfiles, problems, process);
      mavenProject = result.first;
      unresolvedArtifactsIds = result.second;
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
    }

    if (mavenProject == null) {
      isValid = false;

      if (problems.isEmpty()) {
        problems.add(new MavenProjectProblem(ProjectBundle.message("maven.project.problem.syntaxError"), true));
      }
      mavenProject = readProjectQuickly(project, f, activeProfiles, locator).nativeMavenProject;
    }

    return new MavenProjectReaderResult(isValid,
                                        activeProfiles,
                                        problems,
                                        unresolvedArtifactsIds,
                                        new File(embedder.getLocalRepository()),
                                        mavenProject);
  }

  private static Pair<MavenProject, Set<MavenId>> doReadProject(MavenEmbedderWrapper embedder,
                                                                String path,
                                                                List<String> profiles,
                                                                List<MavenProjectProblem> problems,
                                                                MavenProcess p) throws MavenProcessCanceledException {
    MavenExecutionRequest request = createRequest(embedder, path, profiles);
    Pair<MavenExecutionResult, Set<MavenId>> result = embedder.readProject(request, p);
    if (!validate(result.first, problems)) return null;
    return Pair.create(result.first.getProject(), result.second);
  }

  private static boolean validate(MavenExecutionResult r, List<MavenProjectProblem> problems) {
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

  public static MavenProjectReaderResult generateSources(MavenEmbedderWrapper embedder,
                                                         MavenImportingSettings importingSettings,
                                                         VirtualFile f,
                                                         List<String> profiles,
                                                         MavenConsole console,
                                                         MavenProcess p)
    throws MavenProcessCanceledException {
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

  private static MavenExecutionRequest createRequest(MavenEmbedderWrapper embedder,
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
}
