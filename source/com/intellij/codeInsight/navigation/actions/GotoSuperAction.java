package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.highlighting.HighlightDefUseHandler;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.ListPopup;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

/**
 *
 */
public class GotoSuperAction extends BaseCodeInsightAction implements CodeInsightActionHandler {

  protected CodeInsightActionHandler getHandler() {
    return this;
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return file.canContainJavaCode();
  }

  public void invoke(final Project project, Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    if (new HighlightDefUseHandler ().invoke(true, project, editor, file))
      return;

    int offset = editor.getCaretModel().getOffset();
    PsiElement[] superElements = findSuperElements(file, offset);
    if (superElements == null || superElements.length == 0) return;
    if (superElements.length == 1) {
      PsiElement superElement = superElements[0].getNavigationElement();
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, superElement.getContainingFile().getVirtualFile(), superElement.getTextOffset());
      FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    } else {
      String title = " Choose super " + (superElements[0] instanceof PsiMethod ? "method " : "class or interface ");
      ListPopup listPopup = NavigationUtil.getPsiElementPopup(superElements, title, project);
      LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();
      Point caretLocation = editor.logicalPositionToXY(caretPosition);
      int x = caretLocation.x;
      int y = caretLocation.y;
      Point location = editor.getContentComponent().getLocationOnScreen();
      x += location.x;
      y += location.y;
      listPopup.show(x, y);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }

  private static PsiElement[] findSuperElements(PsiFile file, int offset) {
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;

    PsiElement e = PsiTreeUtil.getParentOfType(element, new Class[]{PsiMethod.class, PsiClass.class});
    if (e instanceof PsiClass) {
      PsiClass aClass = (PsiClass) e;
      java.util.List<PsiClass> allSupers = new ArrayList<PsiClass>(Arrays.asList(aClass.getSupers()));
      for (Iterator<PsiClass> iterator = allSupers.iterator(); iterator.hasNext();) {
        PsiClass superClass = iterator.next();
        if ("java.lang.Object".equals(superClass.getQualifiedName())) iterator.remove();
      }
      return allSupers.toArray(new PsiClass[allSupers.size()]);
    } else if (e instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) e;
      if (method.isConstructor()) {
        PsiMethod constructorInSuper = PsiSuperMethodUtil.findConstructorInSuper(method);
        if (constructorInSuper != null) {
          return new PsiElement[]{constructorInSuper};
        }
      } else {
        return PsiSuperMethodUtil.findSuperMethods(method, false);
      }
    }
    return null;
  }
}
