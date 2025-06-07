// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.generate;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementAction;
import icons.MavenIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomParent;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.navigator.SelectMavenProjectDialog;
import org.jetbrains.idea.maven.project.MavenProject;

public class GenerateParentAction extends GenerateDomElementAction {
  public GenerateParentAction() {
    super(new MavenGenerateProvider<>(MavenDomBundle.message("generate.parent"), MavenDomParent.class) {
      @Override
      protected MavenDomParent doGenerate(final @NotNull MavenDomProjectModel mavenModel, Editor editor) {
        SelectMavenProjectDialog d = new SelectMavenProjectDialog(editor.getProject(), null);
        if (!d.showAndGet()) {
          return null;
        }
        final MavenProject parentProject = d.getResult();
        if (parentProject == null) return null;

        return WriteCommandAction.writeCommandAction(editor.getProject()).withName(getDescription())
          .compute(() -> MavenDomUtil.updateMavenParent(mavenModel, parentProject));
      }

      @Override
      protected boolean isAvailableForModel(MavenDomProjectModel mavenModel) {
        return !DomUtil.hasXml(mavenModel.getMavenParent());
      }
    }, MavenIcons.MavenProject);
  }

  @Override
  protected boolean startInWriteAction() {
    return false;
  }
}
