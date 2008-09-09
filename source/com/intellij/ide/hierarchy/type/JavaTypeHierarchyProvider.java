package com.intellij.ide.hierarchy.type;

import com.intellij.ide.hierarchy.HierarchyProvider;
import com.intellij.ide.hierarchy.HierarchyBrowser;
import com.intellij.psi.*;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;

/**
 * @author yole
 */
public class JavaTypeHierarchyProvider implements HierarchyProvider {
  public PsiElement getTarget(final DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return null;

    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor != null) {
      final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file == null) return null;

      final int offset = editor.getCaretModel().getOffset();
      PsiElement element = file.findElementAt(offset);
      while (element != null) {
        if (element instanceof PsiFile) {
          if (!(element instanceof PsiJavaFile)) return null;
          final PsiClass[] classes = ((PsiJavaFile)element).getClasses();
          return classes.length == 1 ? classes[0] : null;
        }
        if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass)) {
          return element;
        }
        element = element.getParent();
      }

      return null;
    }
    else {
      final PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
      return element instanceof PsiClass ? (PsiClass)element : null;
    }
  }

  public HierarchyBrowser createHierarchyBrowser(final PsiElement target) {
    return new TypeHierarchyBrowser(target.getProject(), (PsiClass) target);
  }

  public void browserActivated(final HierarchyBrowser hierarchyBrowser) {
    final TypeHierarchyBrowser browser = (TypeHierarchyBrowser)hierarchyBrowser;
    final String typeName = browser.isInterface() ? SubtypesHierarchyTreeStructure.TYPE : TypeHierarchyTreeStructure.TYPE;
    browser.changeView(typeName);
  }
}
