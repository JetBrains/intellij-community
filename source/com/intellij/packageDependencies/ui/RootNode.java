package com.intellij.packageDependencies.ui;

public class RootNode extends PackageDependenciesNode {
  public boolean equals(Object obj) {
    return obj instanceof RootNode;
  }

  public int hashCode() {
    return 0;
  }

  public String toString() {
    return "Root";
  }
}