package com.intellij.packageDependencies.ui;

import com.intellij.psi.PsiFile;

import java.util.Set;

public class RootNode extends PackageDependenciesNode {
  public boolean equals(Object obj) {
    return obj instanceof RootNode;
  }

  public int hashCode() {
    return 0;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "Root";
  }

  public void fillFiles(Set<PsiFile> set, boolean recursively) {
    super.fillFiles(set, recursively);
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      PackageDependenciesNode child = (PackageDependenciesNode)getChildAt(i);
      child.fillFiles(set, true);
    }
  }
}