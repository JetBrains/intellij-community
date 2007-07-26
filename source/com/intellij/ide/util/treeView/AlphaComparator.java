package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.impl.nodes.*;
import com.intellij.ide.structureView.impl.java.PropertyGroup;

import java.util.Comparator;

public class AlphaComparator implements Comparator<NodeDescriptor>{
  public static final AlphaComparator INSTANCE = new AlphaComparator();

  protected AlphaComparator() {
  }

  public int compare(NodeDescriptor nodeDescriptor1, NodeDescriptor nodeDescriptor2) {
    int weight1 = getWeight(nodeDescriptor1);
    int weight2 = getWeight(nodeDescriptor2);
    if (weight1 != weight2) {
      return weight1 - weight2;
    }
    String s1 = nodeDescriptor1.toString();
    String s2 = nodeDescriptor2.toString();
    if (s1 == null) return s2 == null ? 0 : -1;
    if (s2 == null) return +1;
    return s1.compareToIgnoreCase(s2);
  }

  protected int getWeight(NodeDescriptor descriptor) {
    if (descriptor instanceof PsiDirectoryNode) {
      return ((PsiDirectoryNode)descriptor).isFQNameShown() ? 70 : 0;
    }

    if (descriptor instanceof PackageElementNode) {
      return 0;
    }

    if (descriptor instanceof PackageViewLibrariesNode) {
      return 60;
    }

    // show module groups before other modules
    if (descriptor instanceof ModuleGroupNode) {
      return 0;
    }

    if (descriptor instanceof ProjectViewModuleNode) {
      return 10;
    }

    if (descriptor instanceof PsiFileNode || descriptor instanceof ClassTreeNode) {
      return 20;
    }
    if (descriptor instanceof PsiMethodNode) {
      return ((PsiMethodNode)descriptor).isConstructor() ? 40 : 50;
    }
    if (descriptor.getElement() instanceof PropertyGroup) {
      return 60;
    }
    if (descriptor instanceof PsiFieldNode) {
      return 70;
    }
    return 30; //??
  }
}