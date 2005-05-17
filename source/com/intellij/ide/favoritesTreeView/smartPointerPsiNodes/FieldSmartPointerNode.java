package com.intellij.ide.favoritesTreeView.smartPointerPsiNodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiFormatUtil;

import java.util.Collection;

public class FieldSmartPointerNode extends BaseSmartPointerPsiNode<SmartPsiElementPointer>{

  public FieldSmartPointerNode(Project project, PsiField value, ViewSettings viewSettings) {
    super(project, SmartPointerManager.getInstance(project).createLazyPointer(value), viewSettings);
  }

  public FieldSmartPointerNode(final Project project, final Object value, final ViewSettings viewSettings) {
    this(project, (PsiField)value, viewSettings);
  }

  public Collection<AbstractTreeNode> getChildrenImpl() {
    return null;
  }

  public void updateImpl(PresentationData data) {
    String name = PsiFormatUtil.formatVariable(
      (PsiField)getPsiElement(),
      PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_INITIALIZER,
        PsiSubstitutor.EMPTY);
    int c = name.indexOf('\n');
    if (c > -1) {
      name = name.substring(0, c - 1);
    }
    data.setPresentableText(name);
  }
}
