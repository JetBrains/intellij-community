/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AbstractFix implements IntentionAction {

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  protected static void moveTo(Editor editor, XmlTag xmlTag) {
    editor.getCaretModel().moveToOffset(xmlTag.getTextRange().getStartOffset());
  }

  protected static TemplateBuilderImpl createTemplateBuilder(XmlTag xmlTag) {
    final PsiFile psiFile = PsiFileFactory.getInstance(xmlTag.getProject())
      .createFileFromText("dummy.xml", XmlFileType.INSTANCE, xmlTag.getText(), LocalTimeCounter.currentTime(), true, false);
    return new TemplateBuilderImpl(psiFile);
  }

  @Override
  public final boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (requiresEditor() && editor == null) return false;

    return isAvailableImpl(project, editor, psiFile);
  }

  protected abstract boolean isAvailableImpl(@NotNull Project project, @Nullable Editor editor, PsiFile file);

  protected abstract boolean requiresEditor();

  public @Nullable LocalQuickFix createQuickFix(boolean isOnTheFly) {
    final boolean requiresEditor = requiresEditor();
    if (requiresEditor && !isOnTheFly) return null;

    return new LocalQuickFix() {
      @Override
      public @NotNull String getName() {
        return AbstractFix.this.getText();
      }

      @Override
      public @NotNull String getFamilyName() {
        return AbstractFix.this.getFamilyName();
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        Editor editor;
        if (requiresEditor) {
          final DataContext dataContext = DataManager.getInstance().getDataContext();

          editor = CommonDataKeys.EDITOR.getData(dataContext);
          if (editor == null) {
            if ((editor = FileEditorManager.getInstance(project).getSelectedTextEditor()) == null) {
              return;
            }
          }
        }
        else {
          editor = null;
        }

        final PsiFile psiFile = descriptor.getPsiElement().getContainingFile();
        if (!isAvailable(project, editor, psiFile)) {
          return;
        }
        invoke(project, editor, psiFile);
      }
    };
  }

  public static @NotNull LocalQuickFix @NotNull [] createFixes(@Nullable LocalQuickFix @NotNull ... fixes) {
    List<LocalQuickFix> result = ContainerUtil.findAll(fixes, localQuickFix -> localQuickFix != null);
    return result.toArray(LocalQuickFix.EMPTY_ARRAY);
  }
}