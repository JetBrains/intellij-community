/**
 * @author Alexey
 */
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.codeInsight.highlighting.HighlightManager;

public class CopyReferenceAction extends AnAction {
  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    boolean enabled = editor != null;
    e.getPresentation().setEnabled(enabled);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());

    PsiMember member = PsiTreeUtil.getParentOfType(element, PsiMember.class);
    String fqn;
    fqn = elementToFqn(member);
    PropertiesComponent.getInstance(project).setValue("REFERENCE_FQN",fqn);

    final StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(project);
    statusBar.setInfo("Reference to '"+fqn+"' has been copied.");

    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager manager = EditorColorsManager.getInstance();
    TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    PsiIdentifier nameIdentifier = member instanceof PsiClass ? ((PsiClass)member).getNameIdentifier() :
            member instanceof PsiVariable ? ((PsiVariable)member).getNameIdentifier() :
            ((PsiMethod)member).getNameIdentifier();
    highlightManager.addOccurrenceHighlights(editor, new PsiElement[]{nameIdentifier}, attributes, true, null);
  }

  public static String elementToFqn(final PsiElement element) {
    final String fqn;
    if (element instanceof PsiClass) {
      fqn = ((PsiClass)element).getQualifiedName();
    }
    else if (element instanceof PsiMember) {
      final PsiMember member = (PsiMember)element;
      fqn = member.getContainingClass().getQualifiedName() + "#" + member.getName();
    }
    else if (element instanceof PsiFile) {
      final PsiFile file = (PsiFile)element;
      final VirtualFile virtualFile = file.getVirtualFile();
      fqn = virtualFile == null ? file.getName() : FileUtil.toSystemDependentName(virtualFile.getPath());
    }
    else {
      fqn = element.getClass().getName();
    }
    return fqn;
  }

  static PsiMember fqnToMember(final Project project, final String fqn) {
    PsiClass aClass = PsiManager.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
    PsiMember member;
    if (aClass != null) {
      member = aClass;
    }
    else {
      String className = fqn.substring(0, fqn.indexOf("#"));
      aClass = PsiManager.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
      String memberName = fqn.substring(fqn.indexOf("#") + 1);
      member = aClass.findFieldByName(memberName, false);
      if (member == null) {
        member = aClass.findMethodsByName(memberName, false)[0];
      }
    }
    return member;
  }
}
