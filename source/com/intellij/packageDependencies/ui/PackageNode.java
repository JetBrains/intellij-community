package com.intellij.packageDependencies.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;

import javax.swing.*;
import java.util.Set;

public class PackageNode extends PackageDependenciesNode {
  private static final Icon PACKAGE_OPEN_ICON = IconLoader.getIcon("/nodes/packageOpen.png");
  private static final Icon PACKAGE_CLOSED_ICON = IconLoader.getIcon("/nodes/packageClosed.png");

  private String myPackageName;
  private String myPackageQName;
  private final PsiPackage myPackage;

  public PackageNode(PsiPackage aPackage, boolean showFQName) {
    myPackage = aPackage;
    myPackageName = showFQName ? aPackage.getQualifiedName() : aPackage.getName();
    if (myPackageName == null || myPackageName.length() == 0) {
      myPackageName = "<default package>";
    }
    myPackageQName = aPackage.getQualifiedName();
    if (myPackageQName != null && myPackageQName.length() == 0) {
      myPackageQName = null;
    }
  }

  public void fillFiles(Set<PsiFile> set, boolean recursively) {
    super.fillFiles(set, recursively);
    int count = getChildCount();
    for (int i = 0; i < count; i++) {
      PackageDependenciesNode child = (PackageDependenciesNode)getChildAt(i);
      if (child instanceof FileNode || recursively) {
        child.fillFiles(set, true);
      }
    }
  }

  public String toString() {
    return myPackageName;
  }

  public String getPackageQName() {
    return myPackageQName;
  }

  public PsiElement getPsiElement() {
    return myPackage;
  }

  public int getWeight() {
    return 3;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PackageNode)) return false;

    final PackageNode packageNode = (PackageNode)o;

    if (!myPackageName.equals(packageNode.myPackageName)) return false;
    if (myPackageQName != null ? !myPackageQName.equals(packageNode.myPackageQName) : packageNode.myPackageQName != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myPackageName.hashCode();
    result = 29 * result + (myPackageQName != null ? myPackageQName.hashCode() : 0);
    return result;
  }

  public Icon getOpenIcon() {
    return PACKAGE_OPEN_ICON;
  }

  public Icon getClosedIcon() {
    return PACKAGE_CLOSED_ICON;
  }
}