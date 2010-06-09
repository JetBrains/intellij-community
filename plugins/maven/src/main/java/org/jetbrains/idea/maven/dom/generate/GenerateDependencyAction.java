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

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.xml.ui.actions.generate.GenerateDomElementAction;
import org.jetbrains.idea.maven.dom.MavenDomBundle;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchDialog;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenIcons;

public class GenerateDependencyAction extends GenerateDomElementAction {
  public GenerateDependencyAction() {
    super(new MavenGenerateProvider<MavenDomDependency>(MavenDomBundle.message("generate.dependency"), MavenDomDependency.class) {
      @Override
      protected MavenDomDependency doGenerate(MavenDomProjectModel mavenModel, Editor editor) {
        MavenId id = MavenArtifactSearchDialog.searchForArtifact(editor.getProject());
        if (id == null) return null;

        PsiDocumentManager.getInstance(mavenModel.getManager().getProject()).commitAllDocuments();

        MavenProjectsManager manager = MavenProjectsManager.getInstance(editor.getProject());
        return manager.addDependency(manager.findProject(mavenModel.getModule()), id);
      }
    }, MavenIcons.DEPENDENCY_ICON);
  }

  @Override
  protected boolean startInWriteAction() {
    return false;
  }
}
