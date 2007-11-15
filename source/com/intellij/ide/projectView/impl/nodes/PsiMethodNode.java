package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;

import java.util.Collection;

public class PsiMethodNode extends BasePsiNode<PsiMethod>{
  public PsiMethodNode(Project project, PsiMethod value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public Collection<AbstractTreeNode> getChildrenImpl() {
    return null;
  }

  public void updateImpl(PresentationData data) {
    String name = PsiFormatUtil.formatMethod(
      getValue(),
        PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_PARAMETERS,
      PsiFormatUtil.SHOW_TYPE
    );
    int c = name.indexOf('\n');
    if (c > -1) {
      name = name.substring(0, c - 1);
    }
    data.setPresentableText(name);
  }

  public boolean isConstructor() {
    final PsiMethod psiMethod = getValue();
    return psiMethod != null && psiMethod.isConstructor();
  }

  public int getWeight() {
    return isConstructor() ? 40 : 50;
  }
}
