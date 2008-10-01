package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.EditorHelper;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.SourcePathsBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.idea.maven.dom.model.MavenModel;
import org.jetbrains.idea.maven.dom.model.MavenParent;
import org.jetbrains.idea.maven.dom.model.Module;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.runner.MavenRunner;
import org.jetbrains.idea.maven.runner.MavenRunnerParameters;
import org.jetbrains.idea.maven.runner.MavenRunnerSettings;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenId;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MavenModuleBuilder extends ModuleBuilder implements SourcePathsBuilder {
  private String myContentRootPath;

  private MavenProjectModel myAggregatorProject;
  private MavenProjectModel myParentProject;

  private boolean myInheritGroupId;
  private boolean myInheritVersion;

  private MavenId myProjectId;
  private MavenId myArchetypeId;

  public void setupRootModel(ModifiableRootModel rootModel) throws ConfigurationException {
    VirtualFile root = findContentEntry();
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
          MavenModel model = MavenUtil.getMavenModel(project, myAggregatorProject.getFile());
          Module module = model.getModules().addModule();
          module.setValue(getPsiFile(project, pom));
        }
      }.execute();
    }

    updateProjectPom(project, pom);

    StartupManager.getInstance(project).runWhenProjectIsInitialized(new Runnable() {
      public void run() {
        MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
        manager.addManagedFile(pom);
        reimportMavenProjects(project);

        EditorHelper.openInEditor(getPsiFile(project, pom));

        if (myArchetypeId != null) generateFromArchetype(project, pom);
      }
    });
  }

  private void updateProjectPom(final Project project, final VirtualFile pom) {
    new WriteCommandAction.Simple(project,
                                  "Create new Maven module",
                                  getPsiFile(project, pom)) {
      protected void run() throws Throwable {
        if (myParentProject != null) {
          MavenModel model = MavenUtil.getMavenModel(project, pom);
          MavenParent parent = model.getMavenParent();
          MavenId parentId = myParentProject.getMavenId();
          parent.getGroupId().setStringValue(parentId.groupId);
          parent.getArtifactId().setStringValue(parentId.artifactId);
          parent.getVersion().setStringValue(parentId.version);
          if (myInheritGroupId) {
            XmlElement el = model.getGroupId().getXmlElement();
            if (el != null) el.delete();
          }
          if (myInheritVersion) {
            XmlElement el = model.getVersion().getXmlElement();
            if (el != null) el.delete();
          }


          if (pom.getParent().getParent() != myParentProject.getDirectoryFile()) {
            parent.getRelativePath().setValue(getPsiFile(project, myParentProject.getFile()));
          }
        }

        CodeStyleManager.getInstance(project).reformat(getPsiFile(project, pom));
      }
    }.execute();
  }

  private PsiFile getPsiFile(Project project, VirtualFile pom) {
    return PsiManager.getInstance(project).findFile(pom);
  }

  private void generateFromArchetype(final Project project, final VirtualFile pom) {
    final File workingDir = MavenUtil.getPluginSystemDir("Archetypes/" + project.getLocationHash());
    workingDir.mkdirs();

    MavenRunnerParameters params = new MavenRunnerParameters(
        false, workingDir.getPath(), Collections.singletonList("archetype:create"), null);

    MavenRunner runner = MavenRunner.getInstance(project);
    MavenRunnerSettings settings = runner.getState().clone();
    Map<String, String> props = settings.getMavenProperties();

    props.put("archetypeGroupId", myArchetypeId.groupId);
    props.put("archetypeArtifactId", myArchetypeId.artifactId);
    props.put("archetypeVersion", myArchetypeId.version);
    props.put("groupId", myProjectId.groupId);
    props.put("artifactId", myProjectId.artifactId);
    props.put("version", myProjectId.version);

    runner.run(params, settings, new Runnable() {
      public void run() {
        try {
          FileUtil.copyDir(new File(workingDir, myProjectId.artifactId), new File(myContentRootPath));
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }

        pom.refresh(false, false);
        updateProjectPom(project, pom);

        LocalFileSystem.getInstance().refreshWithoutFileWatcher(true);
        MavenUtil.invokeLater(project, new Runnable() {
          public void run() {
            reimportMavenProjects(project);
          }
        });
      }
    });
  }

  private void reimportMavenProjects(Project project) {
    // under UnitTest mode invokeLater runs the Runnable immediatly and clashes
    // with ModuleBuilder logic that doesn't expect setupRootModel to commit the models.
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      MavenProjectsManager.getInstance(project).reimport();
    }
  }

  public ModuleType getModuleType() {
    return StdModuleTypes.JAVA;
  }

  public VirtualFile findContentEntry() {
    new File(myContentRootPath).mkdirs();
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(myContentRootPath.replace('\\', '/'));
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

  public void setAggregatorProject(MavenProjectModel project) {
    myAggregatorProject = project;
  }

  public void setParentProject(MavenProjectModel project) {
    myParentProject = project;
  }

  public void setInheritedOptions(boolean groupId, boolean version) {
    myInheritGroupId = groupId;
    myInheritVersion = version;
  }

  public void setProjectId(MavenId id) {
    myProjectId = id;
  }

  public void setArchetypeId(MavenId id) {
    myArchetypeId = id;
  }
}
