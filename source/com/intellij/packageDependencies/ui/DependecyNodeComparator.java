package com.intellij.packageDependencies.ui;

import java.util.Comparator;

public class DependecyNodeComparator implements Comparator<PackageDependenciesNode>{
  public int compare(PackageDependenciesNode p1, PackageDependenciesNode p2) {
    if (p1.getWeight() != p2.getWeight()) return p1.getWeight() - p2.getWeight();
    return p1.toString().compareTo(p2.toString());
  }
}