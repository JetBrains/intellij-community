package com.intellij.codeInspection.ui;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author max
 */
public class InspectionPackageNode extends InspectionTreeNode {
  private static final Icon packageOpenIcon = IconLoader.getIcon("/nodes/packageOpen.png");
  private static final Icon packageClosedIcon = IconLoader.getIcon("/nodes/packageClosed.png");

  public InspectionPackageNode(String packageName) {
    super(packageName);
  }

  public String getPackageName() {
    return (String) getUserObject();
  }

  public Icon getIcon(boolean expanded) {
    return expanded ? packageOpenIcon : packageClosedIcon;
  }
}
