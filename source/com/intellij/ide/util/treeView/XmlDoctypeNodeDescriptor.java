package com.intellij.ide.util.treeView;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

/**
 * @author Mike
 */
public class XmlDoctypeNodeDescriptor  extends SmartElementDescriptor {
  public XmlDoctypeNodeDescriptor(Project project, NodeDescriptor parentDescriptor, PsiElement element) {
    super(project, parentDescriptor, element);
    myName = "DOCTYPE";
  }
}
