// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.xml.refactoring;

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
import com.intellij.xml.XmlBundle;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.impl.schema.AnyXmlElementDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlTagRenameHandler implements RenameHandler, TitledHandler {
  private static final Logger LOG = Logger.getInstance(XmlTagRenameHandler.class);


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
    return isDeclarationOutOfProjectOrAbsent(element.getProject(), dataContext);
  }

  @Override
  public String getActionTitle() {
    return XmlBundle.message("rename.xml.tag");
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
  public void invoke(@NotNull final Project project, final PsiElement @NotNull [] elements, @Nullable final DataContext dataContext) {
    PsiElement element = elements.length == 1 ? elements[0] : null;
    if (element == null) {
      element = getElement(dataContext);
    }

    LOG.assertTrue(element != null);
    invoke(getEditor(dataContext), element, dataContext);
  }
}
