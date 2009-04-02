package org.jetbrains.idea.maven.project;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.extension.ExtensionScanningException;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Profile;
import org.apache.maven.project.InvalidProjectModelException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.validation.ModelValidationResult;
import org.apache.maven.reactor.MissingModuleException;
import org.jetbrains.idea.maven.dom.model.*;
import org.jetbrains.idea.maven.dom.settings.MavenDomSettingsModel;
import org.jetbrains.idea.maven.embedder.MavenConsole;
import org.jetbrains.idea.maven.embedder.MavenConsoleHelper;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.embedder.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenId;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.io.File;
import java.util.*;

public class MavenProjectReader {
  private static final String UNKNOWN = "Unknown";

  public static MavenProjectReaderResult readProjectQuickly(Project project,
                                                            VirtualFile file,
                                                            List<String> activeProfiles) {
    Model mavenModel = new Model();

    DomFileElement<MavenDomProjectModel> domFile = getDomFile(project, file, MavenDomProjectModel.class);

    MavenProjectsManager mavenManager = MavenProjectsManager.getInstance(project);

    if (domFile != null) {
      MavenDomProjectModel domProject = domFile.getRootElement();
      mavenModel.setModelVersion(domProject.getModelVersion().getStringValue());
      mavenModel.setGroupId(domProject.getGroupId().getStringValue());
      mavenModel.setArtifactId(domProject.getArtifactId().getStringValue());
      mavenModel.setVersion(domProject.getVersion().getStringValue());
      mavenModel.setPackaging(domProject.getPackaging().getStringValue());
      mavenModel.setName(domProject.getName().getStringValue());

      mavenModel.setModules(readModules(domProject.getModules()));
      collectProperties(domProject.getProperties(), mavenModel);

      Build mavenBuild = new Build();
      mavenModel.setBuild(mavenBuild);
      MavenDomBuild domBuild = domProject.getBuild();
      if (domBuild.getXmlElement() != null) {
        mavenBuild.setFinalName(domBuild.getFinalName().getStringValue());
        mavenBuild.setDirectory(domBuild.getDirectory().getStringValue());
        mavenBuild.setSourceDirectory(domBuild.getSourceDirectory().getStringValue());
        mavenBuild.setTestSourceDirectory(domBuild.getTestSourceDirectory().getStringValue());
        mavenBuild.setOutputDirectory(domBuild.getOutputDirectory().getStringValue());
        mavenBuild.setTestOutputDirectory(domBuild.getTestOutputDirectory().getStringValue());
      }

      collectProfiles(mavenModel, domProject.getProfiles(), activeProfiles);

      VirtualFile profilesFile = file.getParent().findChild(MavenConstants.PROFILES_XML);
      if (profilesFile != null) {
        DomFileElement<MavenDomProfiles> domProfilesFile = getDomFile(project, profilesFile, MavenDomProfiles.class);
        if (domProfilesFile != null) {
          collectProfiles(mavenModel, domProfilesFile.getRootElement(), activeProfiles);
        }
      }

      MavenDomParent domParent = domProject.getMavenParent();
      if (domParent.getXmlElement() != null) {
        Parent parent = new Parent();

        String groupId = domParent.getGroupId().getStringValue();
        String artifactId = domParent.getArtifactId().getStringValue();
        String version = domParent.getVersion().getStringValue();

        parent.setGroupId(groupId);
        parent.setArtifactId(artifactId);
        parent.setVersion(version);
        parent.setRelativePath(domParent.getRelativePath().getStringValue());

        mavenModel.setParent(parent);

        if (groupId != null && artifactId != null && version != null) {
          MavenId parentId = new MavenId(groupId, artifactId, version);
          org.jetbrains.idea.maven.project.MavenProject mavenProject = mavenManager.findProject(parentId);
          if (mavenProject != null) {
            Properties parentProperties = mavenProject.getProperties();
            Properties props = mavenModel.getProperties();
            for (Map.Entry<Object, Object> each : parentProperties.entrySet()) {
              if (!props.containsKey(each.getKey())) {
                props.setProperty((String)each.getKey(), (String)each.getValue());
              }
            }
          }
        }
      }

      MavenGeneralSettings settings = mavenManager.getGeneralSettings();
      File userSettings = MavenEmbedderFactory.resolveUserSettingsFile(settings.getMavenSettingsFile());
      collectProfilesFromSettingsFile(project, userSettings, activeProfiles, mavenModel);

      File globalSettings = MavenEmbedderFactory.resolveGlobalSettingsFile(settings.getMavenHome());
      collectProfilesFromSettingsFile(project, globalSettings, activeProfiles, mavenModel);
    }

    MavenProject mavenProject = createMavenProject(file, mavenModel);
    return new MavenProjectReaderResult(true,
                                        activeProfiles,
                                        Collections.EMPTY_LIST,
                                        Collections.EMPTY_SET,
                                        mavenManager.getLocalRepository(),
                                        mavenProject);
  }

  private static void collectProfilesFromSettingsFile(Project project, File settings, List<String> activeProfiles, Model mavenModel) {
    if (settings == null) return;

    VirtualFile settingsFile = LocalFileSystem.getInstance().findFileByIoFile(settings);

    if (settingsFile == null) return;
    DomFileElement<MavenDomSettingsModel> domSettingsFile = getDomFile(project, settingsFile, MavenDomSettingsModel.class);

    if (domSettingsFile == null) return;
    collectProfiles(mavenModel, domSettingsFile.getRootElement().getProfiles(), activeProfiles);
  }

  private static void collectProfiles(Model mavenModel, MavenDomProfiles domProfiles, List<String> activeProfiles) {
    List<Profile> profiles = mavenModel.getProfiles();
    for (MavenDomProfile each : domProfiles.getProfiles()) {
      String id = each.getId().getStringValue();
      if (StringUtil.isEmptyOrSpaces(id)) continue;

      Profile profile = new Profile();
      profile.setId(id);
      profile.setModules(readModules(each.getModules()));
      profiles.add(profile);

      if (activeProfiles.contains(id)) {
        collectProperties(each.getProperties(), mavenModel);
      }
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

  private static void collectProperties(DomElement domProperties, Model mavenModel) {
    Properties props = mavenModel.getProperties();

    XmlTag propertiesTag = domProperties.getXmlTag();
    if (propertiesTag == null) return;

    for (XmlTag each : propertiesTag.getSubTags()) {
      String name = each.getName();
      if (!props.containsKey(name)) {
        props.setProperty(name, each.getValue().getText());
      }
    }
  }

  public static Pair<MavenProject, MavenProjectReaderResult> readProject(MavenEmbedderWrapper embedder,
                                                                         VirtualFile f,
                                                                         List<String> activeProfiles,
                                                                         MavenProcess process) throws MavenProcessCanceledException {
    MavenProject project = null;
    boolean isValid = true;
    List<MavenProjectProblem> problems = new ArrayList<MavenProjectProblem>();
    Set<MavenId> unresolvedArtifactsIds = new HashSet<MavenId>();

    String path = f.getPath();

    try {
      Pair<MavenProject, Set<MavenId>> result = doReadProject(embedder, path, activeProfiles, problems, process);
      project = result.first;
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

    if (project == null) {
      isValid = false;

      if (problems.isEmpty()) {
        problems.add(new MavenProjectProblem(ProjectBundle.message("maven.project.problem.syntaxError"), true));
      }
      Model model;

      try {
        model = embedder.readModel(path, process);
      }
      catch (Throwable e) {
        MavenLog.LOG.info(e);
        model = new Model();
      }
      project = createMavenProject(f, model);
    }

    return Pair.create(project, new MavenProjectReaderResult(isValid,
                                                             activeProfiles,
                                                             problems,
                                                             unresolvedArtifactsIds,
                                                             new File(embedder.getLocalRepository()),
                                                             project));
  }

  private static MavenProject createMavenProject(VirtualFile f, Model model) {
    repairModel(f, model);
    MavenProject result = new MavenProject(model);
    result.setFile(new File(f.getPath()));
    return result;
  }

  private static void repairModel(VirtualFile file, Model model) {
    if (model.getModelVersion() == null) model.setModelVersion("4.0.0");

    Parent parent = model.getParent();
    if (parent != null) {
      if (parent.getGroupId() == null) parent.setGroupId(UNKNOWN);
      if (parent.getArtifactId() == null) parent.setArtifactId(UNKNOWN);
      if (parent.getVersion() == null) parent.setVersion(UNKNOWN);
    }

    if (model.getGroupId() == null) {
      if (parent != null) {
        model.setGroupId(parent.getGroupId());
      }
      else {
        model.setGroupId(UNKNOWN);
      }
    }
    if (model.getArtifactId() == null) model.setArtifactId(UNKNOWN);
    if (model.getVersion() == null) {
      if (parent != null) {
        model.setVersion(parent.getVersion());
      }
      else {
        model.setVersion(UNKNOWN);
      }
    }

    if (model.getPackaging() == null) model.setPackaging("jar");

    if (model.getBuild() == null) {
      model.setBuild(new Build());
    }
    Build build = model.getBuild();
    if (build.getFinalName() == null) {
      build.setFinalName(model.getArtifactId() + "-" + model.getVersion() + "." + model.getPackaging());
    }

    String baseDir = file.getParent().getPath();
    if (build.getDirectory() == null) build.setDirectory(baseDir + "/target");
    if (build.getOutputDirectory() == null) build.setOutputDirectory(baseDir + "/classes");
    if (build.getTestOutputDirectory() == null) build.setTestOutputDirectory(baseDir + "/test-classes");
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
