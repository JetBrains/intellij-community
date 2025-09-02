// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.generate;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.DependencyConflictId;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomDependencyManagement;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchDialog;
import org.jetbrains.idea.maven.model.MavenCoordinate;
import org.jetbrains.idea.maven.model.MavenId;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.intellij.ide.plugins.PluginManagerCore.getPlugin;
import static com.intellij.openapi.extensions.PluginId.getId;
import static java.util.Optional.ofNullable;

public class GenerateDependencyAction extends GenerateDomElementAction {
  public GenerateDependencyAction() {
    super(new MavenGenerateProvider<>(MavenDomBundle.message("generate.dependency.title"), MavenDomDependency.class) {
      @Override
      protected @Nullable MavenDomDependency doGenerate(final @NotNull MavenDomProjectModel mavenModel, final Editor editor) {
        Project project = mavenModel.getManager().getProject();

        final Map<DependencyConflictId, MavenDomDependency> managedDependencies =
          GenerateManagedDependencyAction.collectManagingDependencies(mavenModel);

        final List<MavenId> ids = MavenArtifactSearchDialog.searchForArtifact(project, managedDependencies.values());
        if (ids.isEmpty()) return null;

        PsiDocumentManager.getInstance(project).commitAllDocuments();

        XmlFile psiFile = DomUtil.getFile(mavenModel);
        return createDependencyInWriteAction(mavenModel, editor, managedDependencies, ids, psiFile);
      }
    }, AllIcons.Nodes.PpLib);
  }

  public static MavenDomDependency createDependencyInWriteAction(@NotNull MavenDomProjectModel mavenModel,
                                                                 @NotNull Editor editor,
                                                                 @NotNull Map<DependencyConflictId, MavenDomDependency> managedDependencies,
                                                                 @NotNull List<? extends MavenCoordinate> ids,
                                                                 @NotNull XmlFile psiFile) {
    return WriteCommandAction.writeCommandAction(psiFile.getProject(), psiFile).withName(MavenDomBundle.message("generate.dependency"))
      .compute(() -> createDependency(mavenModel, editor, managedDependencies, ids));
  }

  public static @Nullable MavenDomDependency createDependency(@NotNull MavenDomProjectModel mavenModel,
                                                              @NotNull Editor editor,
                                                              @NotNull Map<DependencyConflictId, MavenDomDependency> managedDependencies,
                                                              @NotNull List<? extends MavenCoordinate> ids) {
    boolean isInsideManagedDependencies;

    MavenDomDependencyManagement dependencyManagement = mavenModel.getDependencyManagement();
    XmlElement managedDependencyXml = dependencyManagement.getXmlElement();
    if (managedDependencyXml != null && managedDependencyXml.getTextRange().contains(editor.getCaretModel().getOffset())) {
      isInsideManagedDependencies = true;
    }
    else {
      isInsideManagedDependencies = false;
    }

    for (MavenCoordinate each : ids) {
      MavenDomDependency res;
      if (isInsideManagedDependencies) {
        res = MavenDomUtil.createDomDependency(dependencyManagement.getDependencies(), editor, each);
      }
      else {
        DependencyConflictId conflictId = new DependencyConflictId(each.getGroupId(), each.getArtifactId(), null, null);
        MavenDomDependency managedDependenciesDom = managedDependencies.get(conflictId);

        if (managedDependenciesDom != null
            && Objects.equals(each.getVersion(), managedDependenciesDom.getVersion().getStringValue())) {
          // Generate dependency without <version> tag
          res = MavenDomUtil.createDomDependency(mavenModel.getDependencies(), editor);

          res.getGroupId().setStringValue(conflictId.getGroupId());
          res.getArtifactId().setStringValue(conflictId.getArtifactId());
        }
        else {
          res = MavenDomUtil.createDomDependency(mavenModel.getDependencies(), editor, each);
        }
      }
      return (res);
    }
    return null;
  }

  @Override
  protected boolean startInWriteAction() {
    return false;
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    return super.isValidForFile(project, editor, psiFile)
           && ofNullable(getPlugin(getId("com.jetbrains.packagesearch.intellij-plugin"))).map(p -> !p.isEnabled()).orElse(true);
  }
}
