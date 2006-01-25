package com.intellij.packageDependencies.ui;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiFile;

import javax.swing.*;
import java.util.Set;

/**
 * User: anna
 * Date: 24-Jan-2006
 */
public class ModuleGroupNode extends PackageDependenciesNode {
  public static final Icon CLOSED_ICON = IconLoader.getIcon("/nodes/moduleGroupClosed.png");
  public static final Icon OPENED_ICON = IconLoader.getIcon("/nodes/moduleGroupOpen.png");
  private ModuleGroup myModule;

  public ModuleGroupNode(ModuleGroup module) {
    myModule = module;
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
    return OPENED_ICON;
  }

  public Icon getClosedIcon() {
    return CLOSED_ICON;
  }

  public String toString() {
    return myModule == null ? AnalysisScopeBundle.message("unknown.node.text") : myModule.toString();
  }

  public String getModuleGroupName() {
    return myModule.presentableText();
  }

  public int getWeight() {
    return 1;
  }

  public boolean equals(Object o) {
    if (isEquals()){
      return super.equals(o);
    }
    if (this == o) return true;
    if (!(o instanceof ModuleGroupNode)) return false;

    final ModuleGroupNode moduleNode = (ModuleGroupNode)o;

    return Comparing.equal(myModule, moduleNode.myModule);
  }

  public int hashCode() {
    return myModule == null ? 0 : myModule.hashCode();
  }
}