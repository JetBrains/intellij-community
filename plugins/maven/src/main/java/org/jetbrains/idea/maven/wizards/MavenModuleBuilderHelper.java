// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.GitSilentFileAdder;
import com.intellij.openapi.GitSilentFileAdderProvider;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomModule;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerParameters;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MavenModuleBuilderHelper {
  private final MavenId myProjectId;

  private final MavenProject myAggregatorProject;
  private final MavenProject myParentProject;

  private final boolean myInheritGroupId;
  private final boolean myInheritVersion;

  private final MavenArchetype myArchetype;
  private final Map<String, String> myPropertiesToCreateByArtifact;

  @NlsContexts.Command private final String myCommandName;

  public MavenModuleBuilderHelper(@NotNull MavenId projectId,
                                  MavenProject aggregatorProject,
                                  MavenProject parentProject,
                                  boolean inheritGroupId,
                                  boolean inheritVersion,
                                  MavenArchetype archetype,
                                  Map<String, String> propertiesToCreateByArtifact,
                                  @NlsContexts.Command String commandName) {
    myProjectId = projectId;
    myAggregatorProject = aggregatorProject;
    myParentProject = parentProject;
    myInheritGroupId = inheritGroupId;
    myInheritVersion = inheritVersion;
    myArchetype = archetype;
    myPropertiesToCreateByArtifact = propertiesToCreateByArtifact;
    myCommandName = commandName;
  }

  public void configure(final Project project, final VirtualFile root, final boolean isInteractive) {
    MavenActionsUsagesCollector.trigger(project, MavenActionsUsagesCollector.CREATE_MAVEN_PROJECT);

    PsiFile[] psiFiles = myAggregatorProject != null
                         ? new PsiFile[]{getPsiFile(project, myAggregatorProject.getFile())}
                         : PsiFile.EMPTY_ARRAY;
    final VirtualFile pom = WriteCommandAction.writeCommandAction(project, psiFiles).withName(myCommandName).compute(() -> {
      GitSilentFileAdder vcsFileAdder = GitSilentFileAdderProvider.create(project);
      VirtualFile file = null;
      try {
        try {
          file = root.findChild(MavenConstants.POM_XML);
          if (file != null) file.delete(this);
          file = root.createChildData(this, MavenConstants.POM_XML);
          vcsFileAdder.markFileForAdding(file);
          MavenUtil.runOrApplyMavenProjectFileTemplate(project, file, myProjectId, isInteractive);
        }
        catch (IOException e) {
          showError(project, e);
          return file;
        }

        updateProjectPom(project, file);
      }
      finally {
        vcsFileAdder.finish();
      }

      if (myAggregatorProject != null) {
        VirtualFile aggregatorProjectFile = myAggregatorProject.getFile();
        MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(project, aggregatorProjectFile);
        if (model != null) {
          model.getPackaging().setStringValue("pom");
          MavenDomModule module = model.getModules().addModule();
          module.setValue(getPsiFile(project, file));
          unblockAndSaveDocuments(project, aggregatorProjectFile);
        }
      }
      return file;
    });

    if (pom == null) return;

    if (myAggregatorProject == null) {
      MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
      manager.addManagedFilesOrUnignore(Collections.singletonList(pom));
    }

    if (myArchetype == null) {
      try {
        VfsUtil.createDirectories(root.getPath() + "/src/main/java");
        VfsUtil.createDirectories(root.getPath() + "/src/main/resources");
        VfsUtil.createDirectories(root.getPath() + "/src/test/java");
      }
      catch (IOException e) {
        MavenLog.LOG.info(e);
      }
    }

    MavenProjectsManager.getInstance(project).forceUpdateAllProjectsOrFindAllAvailablePomFiles();

    // execute when current dialog is closed (e.g. Project Structure)
    MavenUtil.invokeLater(project, ModalityState.NON_MODAL, () -> {
      if (!pom.isValid()) {
        showError(project, new RuntimeException("Project is not valid"));
        return;
      }

      EditorHelper.openInEditor(getPsiFile(project, pom));
      if (myArchetype != null) generateFromArchetype(project, pom);
    });
  }

  private void updateProjectPom(final Project project, final VirtualFile pom) {
    if (myParentProject == null) return;

    WriteCommandAction.writeCommandAction(project).withName(myCommandName).run(() -> {
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(project, pom);
      if (model == null) return;

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

      List<VirtualFile> pomFiles = new ArrayList<>(2);
      pomFiles.add(pom);

      if (!FileUtil.namesEqual(MavenConstants.POM_XML, myParentProject.getFile().getName())) {
        pomFiles.add(myParentProject.getFile());
        MavenProjectsManager.getInstance(project).forceUpdateProjects(Collections.singleton(myParentProject));
      }

      unblockAndSaveDocuments(project, pomFiles.toArray(VirtualFile.EMPTY_ARRAY));
    });
  }

  private static void unblockAndSaveDocuments(@NotNull Project project, VirtualFile @NotNull ... files) {
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    for (VirtualFile file : files) {
      Document document = fileDocumentManager.getDocument(file);
      if (document == null) continue;
      psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
      fileDocumentManager.saveDocument(document);
    }
  }

  private static PsiFile getPsiFile(Project project, VirtualFile pom) {
    return PsiManager.getInstance(project).findFile(pom);
  }

  private void generateFromArchetype(final Project project, final VirtualFile pom) {
    MavenActionsUsagesCollector.trigger(project, MavenActionsUsagesCollector.CREATE_MAVEN_PROJECT_FROM_ARCHETYPE);

    final File workingDir;
    try {
      workingDir = FileUtil.createTempDirectory("archetype", "tmp");
      workingDir.deleteOnExit();
    }
    catch (IOException e) {
      showError(project, e);
      return;
    }

    MavenRunnerParameters params = new MavenRunnerParameters(
      false, workingDir.getPath(), (String)null,
      Collections.singletonList("org.apache.maven.plugins:maven-archetype-plugin:RELEASE:generate"),
      Collections.emptyList());

    MavenRunner runner = MavenRunner.getInstance(project);
    MavenRunnerSettings settings = runner.getState().clone();

    Map<String, String> props = settings.getMavenProperties();
    props.put("interactiveMode", "false");
    props.putAll(myPropertiesToCreateByArtifact);

    runner.run(params, settings, () -> copyGeneratedFiles(workingDir, pom, project, props.get("artifactId")));
  }

  @VisibleForTesting
  void copyGeneratedFiles(File workingDir, VirtualFile pom, Project project, String artifactId) {
    GitSilentFileAdder vcsFileAdder = GitSilentFileAdderProvider.create(project);
    try {
      try {
        artifactId = artifactId != null ? artifactId : myProjectId.getArtifactId();
        if (artifactId != null) {
          File sourceDir = new File(workingDir, artifactId);
          File targetDir = new File(pom.getParent().getPath());
          vcsFileAdder.markFileForAdding(targetDir, true); // VFS is refreshed below
          FileUtil.copyDir(sourceDir, targetDir);
        }
        FileUtil.delete(workingDir);
      }
      catch (Exception e) {
        showError(project, e);
        return;
      }

      pom.getParent().refresh(false, false);
      pom.refresh(false, false);
      updateProjectPom(project, pom);

      LocalFileSystem.getInstance().refreshWithoutFileWatcher(true);
    }
    finally {
      vcsFileAdder.finish();
    }
  }

  private static void showError(Project project, Throwable e) {
    MavenUtil.showError(project, MavenProjectBundle.message("notification.title.failed.to.create.maven.project"), e);
  }
}
