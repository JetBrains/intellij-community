package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.SourcePathsBuilder;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.ModalityState;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomModule;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.indices.ArchetypeInfo;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenId;
import org.jetbrains.idea.maven.runner.MavenRunner;
import org.jetbrains.idea.maven.runner.MavenRunnerParameters;
import org.jetbrains.idea.maven.runner.MavenRunnerSettings;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MavenModuleBuilder extends ModuleBuilder implements SourcePathsBuilder {
  private String myContentRootPath;

  private MavenProject myAggregatorProject;
  private MavenProject myParentProject;

  private boolean myInheritGroupId;
  private boolean myInheritVersion;

  private MavenId myProjectId;
  private ArchetypeInfo myArchetype;

  public void setupRootModel(ModifiableRootModel rootModel) throws ConfigurationException {
    final VirtualFile root = createAndGetContentEntry();
    rootModel.addContentEntry(root);

    final VirtualFile pom;
    try {
      pom = root.createChildData(this, MavenConstants.POM_XML);
      VfsUtil.saveText(pom, MavenUtil.makeFileContent(myProjectId));
    }
    catch (IOException e) {
      throw new ConfigurationException(e.getMessage());
    }

    final Project project = rootModel.getProject();

    if (myAggregatorProject != null) {
      new WriteCommandAction.Simple(project,
                                    "Create new Maven module",
                                    getPsiFile(project, myAggregatorProject.getFile())) {
        protected void run() throws Throwable {
          MavenDomProjectModel model = MavenUtil.getMavenModel(project, myAggregatorProject.getFile());
          model.getPackaging().setStringValue("pom");
          MavenDomModule module = model.getModules().addModule();
          module.setValue(getPsiFile(project, pom));
        }
      }.execute();
    }

    updateProjectPom(project, pom);

    MavenUtil.runWhenInitialized(project, new DumbAwareRunnable() {
      public void run() {
        final MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
        manager.addManagedFiles(Collections.singletonList(pom));

        if (myArchetype == null) {
          try {
            VfsUtil.createDirectories(root.getPath() + "/src/main/java");
            VfsUtil.createDirectories(root.getPath() + "/src/test/java");
          }
          catch (IOException e) {
            MavenLog.LOG.info(e);
          }
        }

        // execute when current dialog is closed (e.g. Project Structure)
        MavenUtil.invokeInDispatchThread(project, ModalityState.NON_MODAL, new Runnable() {
          public void run() {
            EditorHelper.openInEditor(getPsiFile(project, pom));
            if (myArchetype != null) generateFromArchetype(project, pom);
          }
        });
      }
    });
  }

  private void updateProjectPom(final Project project, final VirtualFile pom) {
    new WriteCommandAction.Simple(project, "Create new Maven module") {
      protected void run() throws Throwable {
        if (myParentProject != null) {
          MavenDomProjectModel model = MavenUtil.getMavenModel(project, pom);
          MavenDomUtil.updateMavenParent(model, myParentProject);

          if (myInheritGroupId) {
            XmlElement el = model.getGroupId().getXmlElement();
            if (el != null) el.delete();
          }
          if (myInheritVersion) {
            XmlElement el = model.getVersion().getXmlElement();
            if (el != null) el.delete();
          }

          CodeStyleManager.getInstance(project).reformat(getPsiFile(project, pom));
        }
      }
    }.execute();
  }

  private PsiFile getPsiFile(Project project, VirtualFile pom) {
    return PsiManager.getInstance(project).findFile(pom);
  }

  private void generateFromArchetype(final Project project, final VirtualFile pom) {
    final File workingDir;
    try {
      workingDir = FileUtil.createTempDirectory("archetype", "tmp");
      workingDir.deleteOnExit();
    }
    catch (IOException e) {
      MavenLog.LOG.warn("Cannot generate archetype", e);
      return;
    }

    MavenRunnerParameters params = new MavenRunnerParameters(
        false, workingDir.getPath(), Collections.singletonList("org.apache.maven.plugins:maven-archetype-plugin:generate"), null);

    MavenRunner runner = MavenRunner.getInstance(project);
    MavenRunnerSettings settings = runner.getState().clone();
    Map<String, String> props = settings.getMavenProperties();

    props.put("interactiveMode", "false");
    props.put("archetypeGroupId", myArchetype.groupId);
    props.put("archetypeArtifactId", myArchetype.artifactId);
    props.put("archetypeVersion", myArchetype.version);
    if (myArchetype.repository != null) props.put("archetypeRepository", myArchetype.repository);

    props.put("groupId", myProjectId.getGroupId());
    props.put("artifactId", myProjectId.getArtifactId());
    props.put("version", myProjectId.getVersion());

    runner.run(params, settings, new Runnable() {
      public void run() {
        try {
          FileUtil.copyDir(new File(workingDir, myProjectId.getArtifactId()), new File(myContentRootPath));
        }
        catch (IOException e) {
          MavenLog.LOG.warn("Cannot generate archetype", e);
          return;
        }

        FileUtil.delete(workingDir);

        pom.refresh(false, false);
        updateProjectPom(project, pom);

        LocalFileSystem.getInstance().refreshWithoutFileWatcher(true);
      }
    });
  }

  public ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  public MavenProject findPotentialParentProject(Project project) {
    if (!MavenProjectsManager.getInstance(project).isMavenizedProject()) return null;

    File parentDir = new File(myContentRootPath).getParentFile();
    if (parentDir == null) return null;
    VirtualFile parentPom = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(parentDir, "pom.xml"));
    if (parentPom == null) return null;

    return MavenProjectsManager.getInstance(project).findProject(parentPom);
  }

  private VirtualFile createAndGetContentEntry() {
    String path = FileUtil.toSystemIndependentName(myContentRootPath);
    new File(path).mkdirs();
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
  }

  public String getContentEntryPath() {
    return myContentRootPath;
  }

  public void setContentEntryPath(String path) {
    myContentRootPath = path;
  }

  public List<Pair<String, String>> getSourcePaths() {
    return Collections.emptyList();
  }

  public void setSourcePaths(List<Pair<String, String>> sourcePaths) {
  }

  public void addSourcePath(Pair<String, String> sourcePathInfo) {
  }

  public void setAggregatorProject(MavenProject project) {
    myAggregatorProject = project;
  }

  public MavenProject getAggregatorProject() {
    return myAggregatorProject;
  }

  public void setParentProject(MavenProject project) {
    myParentProject = project;
  }

  public MavenProject getParentProject() {
    return myParentProject;
  }

  public void setInheritedOptions(boolean groupId, boolean version) {
    myInheritGroupId = groupId;
    myInheritVersion = version;
  }

  public boolean isInheritGroupId() {
    return myInheritGroupId;
  }

  public boolean isInheritVersion() {
    return myInheritVersion;
  }

  public void setProjectId(MavenId id) {
    myProjectId = id;
  }

  public MavenId getProjectId() {
    return myProjectId;
  }

  public void setArchetype(ArchetypeInfo archetype) {
    myArchetype = archetype;
  }

  public ArchetypeInfo getArchetype() {
    return myArchetype;
  }
}
