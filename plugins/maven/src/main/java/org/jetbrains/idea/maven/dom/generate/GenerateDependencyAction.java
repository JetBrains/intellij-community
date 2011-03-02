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
package org.jetbrains.idea.maven.dom.generate;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchDialog;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenIcons;

import java.util.List;

public class GenerateDependencyAction extends GenerateDomElementAction {
  public GenerateDependencyAction() {
    super(new MavenGenerateProvider<MavenDomDependency>(MavenDomBundle.message("generate.dependency"), MavenDomDependency.class) {
        @Nullable
        @Override
        protected MavenDomDependency doGenerate(@NotNull final MavenDomProjectModel mavenModel, final Editor editor) {
          MavenProjectsManager manager = MavenProjectsManager.getInstance(editor.getProject());
          MavenProject project = manager.findProject(mavenModel.getModule());
          if (project == null) return null;

          final List<MavenId> ids = MavenArtifactSearchDialog.searchForArtifact(editor.getProject());
          if (ids.isEmpty()) return null;

          PsiDocumentManager.getInstance(mavenModel.getManager().getProject()).commitAllDocuments();

          XmlFile psiFile = DomUtil.getFile(mavenModel);
          return new WriteCommandAction<MavenDomDependency>(psiFile.getProject(), "Generate Dependency", psiFile) {
            @Override
            protected void run(Result<MavenDomDependency> result) throws Throwable {
              for (MavenId each : ids) {
                result.setResult(MavenDomUtil.createDomDependency(mavenModel, editor, each));
              }
            }
          }.execute().getResultObject();
        }
      }, MavenIcons.DEPENDENCY_ICON);
  }

  @Override
  protected boolean startInWriteAction() {
    return false;
  }
}
