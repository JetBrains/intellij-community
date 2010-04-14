/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom.refactorings.extract;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;

import java.util.Collections;
import java.util.Set;

public class ExtractDependenciesAction extends BaseRefactoringAction {

  public ExtractDependenciesAction() {
    setInjectedContext(true);
  }

  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  protected boolean isEnabledOnElements(PsiElement[] elements) {
    return false;
  }

  @Override
  protected boolean isAvailableForLanguage(Language language) {
    return true;
  }

  protected RefactoringActionHandler getHandler(DataContext dataContext) {
    return new MyRefactoringActionHandler();

  }

  @Override
  protected boolean isAvailableForFile(PsiFile file) {
    return MavenDomUtil.isMavenFile(file);
  }

  private static class MyRefactoringActionHandler implements RefactoringActionHandler {
    public void invoke(@NotNull final Project project, final Editor editor, PsiFile file, DataContext dataContext) {
      MavenDomDependency dependency =
        DomUtil.findDomElement(file.findElementAt(editor.getCaretModel().getOffset()), MavenDomDependency.class);

      if (dependency != null && !isManagedDependency(dependency)) {
        Set<MavenDomProjectModel> models = getParentProjects(project, file);
        if (models.size() == 0) return;

        SelectMavenProjectDialog dialog = new SelectMavenProjectDialog(project, models);
        dialog.show();

        if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
          final MavenDomProjectModel selectedProject = dialog.getSelectedProject();

          new WriteCommandAction(project) {
            @Override
            protected void run(Result result) throws Throwable {
            }
          }.execute();
        }
      }
    }

    @NotNull
    private Set<MavenDomProjectModel> getParentProjects(@NotNull Project project, @NotNull PsiFile file) {
      final MavenDomProjectModel model = MavenDomUtil.getMavenDomModel(file, MavenDomProjectModel.class);

      if (model == null) return Collections.emptySet();
      return MavenDomProjectProcessorUtils.collectParentProjects(model, project);
    }

    private boolean isManagedDependency(@NotNull MavenDomDependency dependency) {
      return false; //todo
    }

    public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    }

  }
}