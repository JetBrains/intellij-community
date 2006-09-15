package com.intellij.packageDependencies.ui;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFile;
import com.intellij.util.Icons;

import javax.swing.*;
import java.util.Set;

/**
 * User: anna
 * Date: 24-Jan-2006
 */
public class ModuleGroupNode extends PackageDependenciesNode {
  private ModuleGroup myModuleGroup;

  public ModuleGroupNode(ModuleGroup moduleGroup) {
    myModuleGroup = moduleGroup;    
  }

  public void fillFiles(Set<PsiFile> set, boolean recursively) {
    super.fillFiles(set, recursively);
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      PackageDependenciesNode child = (PackageDependenciesNode)getChildAt(i);
      child.fillFiles(set, true);
    }
  }

  public Icon getOpenIcon() {
    return Icons.OPENED_MODULE_GROUP_ICON;
  }

  public Icon getClosedIcon() {
    return Icons.CLOSED_MODULE_GROUP_ICON;
  }

  public String toString() {
    return myModuleGroup == null ? AnalysisScopeBundle.message("unknown.node.text") : myModuleGroup.toString();
  }

  public String getModuleGroupName() {
    return myModuleGroup.presentableText();
  }

  public ModuleGroup getModuleGroup() {
    return myModuleGroup;
  }

  public int getWeight() {
    return 0;
  }

  public boolean equals(Object o) {
    if (isEquals()){
      return super.equals(o);
    }
    if (this == o) return true;
    if (!(o instanceof ModuleGroupNode)) return false;

    final ModuleGroupNode moduleNode = (ModuleGroupNode)o;

    return Comparing.equal(myModuleGroup, moduleNode.myModuleGroup);
  }

  public int hashCode() {
    return myModuleGroup == null ? 0 : myModuleGroup.hashCode();
  }
}