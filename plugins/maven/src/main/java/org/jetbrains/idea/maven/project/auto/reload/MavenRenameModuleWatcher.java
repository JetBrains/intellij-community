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
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.importing.MavenCustomModuleNameMapper;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

@ApiStatus.Internal
public class MavenRenameModuleWatcher implements ModuleListener {

  @Override
  public void modulesRenamed(@NotNull Project project,
                             @NotNull List<? extends Module> modules,
                             @NotNull Function<? super Module, String> oldNameProvider) {
    MavenCustomModuleNameMapper customNameMapper = project.getService(MavenCustomModuleNameMapper.class);
    for (var module : modules) {
      customNameMapper.onModuleRename(module);
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
    private final List<MavenProject> myUpdatedMavenProjects = new ArrayList<>();

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

    private boolean replaceArtifactId(@Nullable XmlTag parentTag) {
      if (null == parentTag) return false;
      var artifactIdTag = parentTag.findFirstSubTag("artifactId");
      if (null == artifactIdTag) return false;
      var groupIdTag = parentTag.findFirstSubTag("groupId");
      if (null == groupIdTag) return false;
      if (myGroupId.equals(groupIdTag.getValue().getText()) && myOldName.equals(artifactIdTag.getValue().getText())) {
        artifactIdTag.getValue().setText(myNewName);
        return true;
      }
      return false;
    }

    private boolean replaceModuleArtifactId(MavenDomProjectModel mavenModel) {
      var artifactIdTag = mavenModel.getArtifactId().getXmlTag();
      if (null != artifactIdTag) {
        if (myOldName.equals(artifactIdTag.getValue().getText())) {
          artifactIdTag.getValue().setText(myNewName);
          return true;
        }
      }
      return false;
    }

    private boolean replaceArtifactIdReferences(MavenDomProjectModel mavenModel) {
      var replaced = false;
      if (null != mavenModel.getXmlTag()) {
        // parent artifactId
        replaced = replaceArtifactId(mavenModel.getXmlTag().findFirstSubTag("parent"));
      }

      // dependencies and exclusions
      var dependencies = mavenModel.getDependencies();
      for (var dependency : dependencies.getDependencies()) {
        replaced |= replaceArtifactId(dependency.getXmlTag());
        for (var exclusion : dependency.getExclusions().getExclusions()) {
          replaced |= replaceArtifactId(exclusion.getXmlTag());
        }
      }
      return replaced;
    }

    private void processModule(Module module, Predicate<MavenDomProjectModel> artifactIdReplacer) {
      if (!myProjectsManager.isMavenizedModule(module)) return;
      var mavenProject = myProjectsManager.findProject(module);
      if (null == mavenProject) return;
      var mavenModel = MavenDomUtil.getMavenDomProjectModel(myProject, mavenProject.getFile());
      if (null == mavenModel) return;
      var psiFile = DomUtil.getFile(mavenModel);
      var mavenProjectUpdated = new AtomicBoolean(false);

      WriteCommandAction.writeCommandAction(myProject, psiFile).run(() -> {
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
        Document document = documentManager.getDocument(psiFile);
        if (document != null) {
          documentManager.commitDocument(document);
        }

        mavenProjectUpdated.set(artifactIdReplacer.test(mavenModel));

        if (document != null) {
          FileDocumentManager.getInstance().saveDocument(document);
        }
      });
      if (mavenProjectUpdated.get()) {
        myUpdatedMavenProjects.add(mavenProject);
      }
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

      if (!myUpdatedMavenProjects.isEmpty()) {
        myProjectsManager.forceUpdateProjects(myUpdatedMavenProjects);
      }
    }
  }
}
