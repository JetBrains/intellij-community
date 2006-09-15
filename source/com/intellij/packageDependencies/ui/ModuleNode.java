package com.intellij.packageDependencies.ui;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.ide.IconUtilEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;

import javax.swing.*;
import java.util.Set;

public class ModuleNode extends PackageDependenciesNode {
  private Module myModule;

  public ModuleNode(Module module) {
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
    return myModule == null ? null : IconUtilEx.getIcon(myModule, Iconable.ICON_FLAG_OPEN);
  }

  public Icon getClosedIcon() {
    return myModule == null ? null : IconUtilEx.getIcon(myModule, 0);
  }

  public String toString() {
    return myModule == null ? AnalysisScopeBundle.message("unknown.node.text") : myModule.getName();
  }

  public String getModuleName() {
    return myModule.getName();
  }

  public Module getModule() {
    return myModule;
  }

  public int getWeight() {
    return 1;
  }

  public boolean equals(Object o) {
    if (isEquals()){
      return super.equals(o);
    }
    if (this == o) return true;
    if (!(o instanceof ModuleNode)) return false;

    final ModuleNode moduleNode = (ModuleNode)o;

    return Comparing.equal(myModule, moduleNode.myModule);
  }

  public int hashCode() {
    return myModule == null ? 0 : myModule.hashCode();
  }
}