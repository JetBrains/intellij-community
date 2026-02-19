// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.auto.reload;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependencies;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.List;
import java.util.function.Consumer;

@ApiStatus.Internal
public class MavenRenameModuleWatcher implements ModuleListener {

  @Override
  public void modulesRenamed(@NotNull Project project,
                             @NotNull List<? extends Module> modules,
                             @NotNull Function<? super Module, String> oldNameProvider) {
    for (var module : modules) {
      new MavenRenameModuleHandler(project, module, oldNameProvider.fun(module)).handleModuleRename();
    }
  }

  private static class MavenRenameModuleHandler {
    private final Project myProject;
    private final Module myModule;
    private final String myOldName;
    private final String myNewName;
    private String myGroupId;
    private final MavenProjectsManager myProjectsManager;

    private MavenRenameModuleHandler(@NotNull Project project,
                                     @NotNull Module module,
                                     @NotNull String oldName) {
      myProject = project;
      myModule = module;
      myOldName = oldName;

      // handle module groups: group.subgroup.module
      var myNewNameHierarchy = module.getName().split("\\.");
      myNewName = myNewNameHierarchy[myNewNameHierarchy.length - 1];

      myProjectsManager = MavenProjectsManager.getInstance(project);
    }

    private void replaceArtifactId(@Nullable XmlTag parentTag) {
      if (null == parentTag) return;
      var artifactIdTag = parentTag.findFirstSubTag("artifactId");
      if (null == artifactIdTag) return;
      var groupIdTag = parentTag.findFirstSubTag("groupId");
      if (null == groupIdTag) return;
      if (myGroupId.equals(groupIdTag.getValue().getText()) && myOldName.equals(artifactIdTag.getValue().getText())) {
        artifactIdTag.getValue().setText(myNewName);
      }
    }

    private void replaceModuleArtifactId(MavenDomProjectModel mavenModel) {
      var artifactIdTag = mavenModel.getArtifactId().getXmlTag();
      if (null != artifactIdTag) {
        if (myOldName.equals(artifactIdTag.getValue().getText())) {
          artifactIdTag.getValue().setText(myNewName);
        }
      }
    }

    private void replaceArtifactIdReferences(@NotNull MavenDomDependencies dependencies) {
      for (var dependency : dependencies.getDependencies()) {
        replaceArtifactId(dependency.getXmlTag());
        for (var exclusion : dependency.getExclusions().getExclusions()) {
          replaceArtifactId(exclusion.getXmlTag());
        }
      }
    }

    private void replaceArtifactIdReferences(MavenDomProjectModel mavenModel) {
      if (null != mavenModel.getXmlTag()) {
        // parent artifactId
        replaceArtifactId(mavenModel.getXmlTag().findFirstSubTag("parent"));
      }

      // dependencies and exclusions
      replaceArtifactIdReferences(mavenModel.getDependencies());

      // dependency management
      replaceArtifactIdReferences(mavenModel.getDependencyManagement().getDependencies());
    }

    private void processModule(Module module, Consumer<MavenDomProjectModel> artifactIdReplacer) {
      if (!myProjectsManager.isMavenizedModule(module)) return;
      var mavenProject = myProjectsManager.findProject(module);
      if (null == mavenProject) return;
      var mavenModel = MavenDomUtil.getMavenDomProjectModel(myProject, mavenProject.getFile());
      if (null == mavenModel) return;
      var psiFile = DomUtil.getFile(mavenModel);

      WriteCommandAction.writeCommandAction(myProject, psiFile).run(() -> {
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
        Document document = documentManager.getDocument(psiFile);
        if (document != null) {
          documentManager.commitDocument(document);
        }

        artifactIdReplacer.accept(mavenModel);

        if (document != null) {
          FileDocumentManager.getInstance().saveDocument(document);
        }
      });
    }

    public void handleModuleRename() {
      if (!myProjectsManager.isMavenizedModule(myModule)) return;
      var mavenProject = myProjectsManager.findProject(myModule);
      if (null == mavenProject) return;
      myGroupId = mavenProject.getMavenId().getGroupId();

      var modules = ModuleManager.getInstance(myProject).getModules();
      for (var module : modules) {
        if (module == myModule) {
          processModule(module, this::replaceModuleArtifactId);
        }
        else {
          processModule(module, this::replaceArtifactIdReferences);
        }
      }
    }
  }
}
