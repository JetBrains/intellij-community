/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.xml.refactoring;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.TitledHandler;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.actions.BaseRefactoringAction;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlTagRenameHandler implements RenameHandler, TitledHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xml.refactoring.XmlTagRenameHandler");


  @Override
  public boolean isAvailableOnDataContext(@NotNull final DataContext dataContext) {
    final PsiElement element = getElement(dataContext);
    if (element == null || PsiElementRenameHandler.isVetoed(element)) return false;
    PsiElement parent = element.getParent();
    if (!(parent instanceof XmlTag)) {
      return false;
    }
    XmlTag tag = (XmlTag)parent;
    String prefix = tag.getNamespacePrefix();
    if (StringUtil.isNotEmpty(prefix)) {
      Editor editor = getEditor(dataContext);
      assert editor != null;
      int offset = editor.getCaretModel().getOffset();
      if (offset <= element.getTextRange().getStartOffset() + prefix.length()) {
        return false;
      }
    }
    //noinspection ConstantConditions
    return isDeclarationOutOfProjectOrAbsent(element.getProject(), dataContext);
  }

  @Override
  public boolean isRenaming(@NotNull final DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public String getActionTitle() {
    return "Rename XML tag";
  }

  private static boolean isInplaceRenameAvailable(final Editor editor) {
    return editor.getSettings().isVariableInplaceRenameEnabled();
  }

  private static boolean isDeclarationOutOfProjectOrAbsent(@NotNull final Project project, final DataContext context) {
    final PsiElement[] elements = BaseRefactoringAction.getPsiElementArray(context);
    return elements.length == 0 || elements.length == 1 && shouldBeRenamedInplace(project, elements);
  }

  private static boolean shouldBeRenamedInplace(Project project, PsiElement[] elements) {
    boolean inProject = PsiManager.getInstance(project).isInProject(elements[0]);
    if (inProject && elements[0] instanceof XmlTag) {
      XmlElementDescriptor descriptor = ((XmlTag)elements[0]).getDescriptor();
      return descriptor instanceof AnyXmlElementDescriptor;
    }
    return !inProject;
  }

  @Nullable
  private static Editor getEditor(@Nullable DataContext context) {
    return CommonDataKeys.EDITOR.getData(context);
  }

  @Nullable
  private static PsiElement getElement(@Nullable final DataContext context) {
    if (context != null) {
      final Editor editor = getEditor(context);
      if (editor != null) {
        final int offset = editor.getCaretModel().getOffset();
        final PsiFile file = CommonDataKeys.PSI_FILE.getData(context);
        if (file instanceof XmlFile) {
          return file.getViewProvider().findElementAt(offset);
        }
        if (file != null) {
          final Language language = PsiUtilCore.getLanguageAtOffset(file, offset);
          if (language != file.getLanguage()) {
            final PsiFile psiAtOffset = file.getViewProvider().getPsi(language);
            if (psiAtOffset instanceof XmlFile) {
              return psiAtOffset.findElementAt(offset);
            }
          }
        }
      }
    }

    return null;
  }

  private void invoke(@Nullable final Editor editor, @NotNull final PsiElement element, @Nullable final DataContext context) {
    if (!isRenaming(context)) {
      return;
    }

    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.rename");

    if (isInplaceRenameAvailable(editor)) {
      XmlTagInplaceRenamer.rename(editor, (XmlTag)element.getParent());
    }
    else {
      XmlTagRenameDialog.renameXmlTag(editor, element, (XmlTag)element.getParent());
    }
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, @Nullable final DataContext dataContext) {
    if (!isRenaming(dataContext)) {
      return;
    }

    final PsiElement element = getElement(dataContext);
    assert element != null;

    invoke(editor, element, dataContext);
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, @Nullable final DataContext dataContext) {
    PsiElement element = elements.length == 1 ? elements[0] : null;
    if (element == null) {
      element = getElement(dataContext);
    }

    LOG.assertTrue(element != null);
    invoke(getEditor(dataContext), element, dataContext);
  }
}
