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
package org.jetbrains.idea.maven.wizards;

import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlElement;
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
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class MavenModuleBuilderHelper {
  private final MavenId myProjectId;

  private final MavenProject myAggregatorProject;
  private final MavenProject myParentProject;

  private final boolean myInheritGroupId;
  private final boolean myInheritVersion;

  private final MavenArchetype myArchetype;

  private final String myCommandName;

  public MavenModuleBuilderHelper(MavenId projectId,
                                  MavenProject aggregatorProject,
                                  MavenProject parentProject,
                                  boolean inheritGroupId,
                                  boolean inheritVersion,
                                  MavenArchetype archetype,
                                  String commaneName) {
    myProjectId = projectId;
    myAggregatorProject = aggregatorProject;
    myParentProject = parentProject;
    myInheritGroupId = inheritGroupId;
    myInheritVersion = inheritVersion;
    myArchetype = archetype;
    myCommandName = commaneName;
  }

  public void configure(final Project project, final VirtualFile root, final boolean isInteractive) {
    PsiFile[] psiFiles = myAggregatorProject != null
                         ? new PsiFile[]{getPsiFile(project, myAggregatorProject.getFile())}
                         : PsiFile.EMPTY_ARRAY;
    final VirtualFile pom = new WriteCommandAction<VirtualFile>(project, myCommandName, psiFiles) {
      @Override
      protected void run(Result<VirtualFile> result) throws Throwable {
        VirtualFile file;
        try {
          file = root.createChildData(this, MavenConstants.POM_XML);
          MavenUtil.runOrApplyMavenProjectFileTemplate(project, file, myProjectId, null, isInteractive);
          result.setResult(file);
        }
        catch (IOException e) {
          showError(project, e);
          return;
        }

        updateProjectPom(project, file);

        if (myAggregatorProject != null) {
          MavenDomProjectModel model = MavenDomUtil.getMavenDomProjectModel(project, myAggregatorProject.getFile());
          model.getPackaging().setStringValue("pom");
          MavenDomModule module = model.getModules().addModule();
          module.setValue(getPsiFile(project, file));
        }
      }
    }.execute().getResultObject();

    if (pom == null) return;

    if (myAggregatorProject == null) {
      MavenProjectsManager manager = MavenProjectsManager.getInstance(project);
      manager.addManagedFiles(Collections.singletonList(pom));
    }

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
    MavenUtil.invokeLater(project, ModalityState.NON_MODAL, new Runnable() {
      public void run() {
        EditorHelper.openInEditor(getPsiFile(project, pom));
        if (myArchetype != null) generateFromArchetype(project, pom);
      }
    });
  }

  private void updateProjectPom(final Project project, final VirtualFile pom) {
    if (myParentProject == null) return;

    new WriteCommandAction.Simple(project, myCommandName) {
      protected void run() throws Throwable {
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
      showError(project, e);
      return;
    }

    MavenRunnerParameters params = new MavenRunnerParameters(
      false, workingDir.getPath(), Collections.singletonList("org.apache.maven.plugins:maven-archetype-plugin:RELEASE:generate"), null);

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
        copyGeneratedFiles(workingDir, pom, project);
      }
    });
  }

  private void copyGeneratedFiles(File workingDir, VirtualFile pom, Project project) {
    try {
      FileUtil.copyDir(new File(workingDir, myProjectId.getArtifactId()), new File(pom.getParent().getPath()));
    }
    catch (IOException e) {
      showError(project, e);
      return;
    }

    FileUtil.delete(workingDir);

    pom.refresh(false, false);
    updateProjectPom(project, pom);

    LocalFileSystem.getInstance().refreshWithoutFileWatcher(true);
  }

  private void showError(Project project, Throwable e) {
    MavenUtil.showError(project, "Failed to create a Maven project", e);
  }
}
