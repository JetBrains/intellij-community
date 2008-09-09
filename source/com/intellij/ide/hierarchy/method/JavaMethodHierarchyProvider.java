package com.intellij.ide.hierarchy.method;

import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class JavaMethodHierarchyProvider implements HierarchyProvider {
  public PsiElement getTarget(final DataContext dataContext) {
    final PsiMethod method = getMethodImpl(dataContext);
    if (
      method != null &&
      method.getContainingClass() != null &&
      !method.hasModifierProperty(PsiModifier.PRIVATE) &&
      !method.hasModifierProperty(PsiModifier.STATIC)
    ){
      return method;
    }
    else {
      return null;
    }
  }

  @Nullable
  private static PsiMethod getMethodImpl(final DataContext dataContext){
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;

    PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);

    if (method != null) {
      return method;
    }

    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return null;
    }

    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) {
      return null;
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final int offset = editor.getCaretModel().getOffset();
    if (offset < 1) {
      return null;
    }

    element = psiFile.findElementAt(offset);
    if (!(element instanceof PsiWhiteSpace)) {
      return null;
    }

    element = psiFile.findElementAt(offset - 1);
    if (!(element instanceof PsiJavaToken) || ((PsiJavaToken)element).getTokenType() != JavaTokenType.SEMICOLON) {
      return null;
    }

    return PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
  }

  public HierarchyBrowser createHierarchyBrowser(final PsiElement target) {
    return new MethodHierarchyBrowser(target.getProject(), (PsiMethod) target);
  }

  public void browserActivated(final HierarchyBrowser hierarchyBrowser) {
    ((MethodHierarchyBrowser) hierarchyBrowser).changeView(MethodHierarchyTreeStructure.TYPE);
  }
}
