package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.IconUtilEx;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

import java.util.ArrayList;
import java.util.Collection;

public class PsiFileNode extends BasePsiNode<PsiFile>{

  public PsiFileNode(Project project, PsiFile value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public PsiFileNode(Project project, Object value, ViewSettings viewSettings) {
    this(project, (PsiFile)value, viewSettings);
  }

  public Collection<AbstractTreeNode> getChildrenImpl() {
    if (getSettings().isStructureView() && getValue() instanceof PsiJavaFile){
      PsiClass[] classes = ((PsiJavaFile)getValue()).getClasses();
      ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
      for (int i = 0; i < classes.length; i++) {
        PsiClass aClass = classes[i];
        result.add(new ClassTreeNode(getProject(), aClass, getSettings()));
      }
      return result;
    } else {
      return null;
    }
  }

  public void updateImpl(PresentationData data) {
    data.setPresentableText(getValue().getName());
    data.setIcons(IconUtilEx.getIcon(getValue(), Iconable.ICON_FLAG_READ_STATUS, getProject()));
  }
}
