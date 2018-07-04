/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.generate;

import com.intellij.openapi.application.Result;
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
    super(new MavenGenerateProvider<MavenDomParent>(MavenDomBundle.message("generate.parent"), MavenDomParent.class) {
        protected MavenDomParent doGenerate(@NotNull final MavenDomProjectModel mavenModel, Editor editor) {
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
