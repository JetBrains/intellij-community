package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.util.CellAppearanceUtils;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NamedLibraryElementNode extends ProjectViewNode<NamedLibraryElement>{

  private static final Icon LIB_ICON_OPEN = IconLoader.getIcon("/nodes/ppLibOpen.png");
  private static final Icon LIB_ICON_CLOSED = IconLoader.getIcon("/nodes/ppLibClosed.png");

  public NamedLibraryElementNode(Project project, NamedLibraryElement value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public Collection<AbstractTreeNode> getChildren() {
    final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    LibraryGroupNode.addLibraryChildren(getValue().getOrderEntry(), children, getProject(), this);
    return children;
  }

  public String getTestPresentation() {
    return "Library: " + getValue().getName();
  }

  private static Icon getJdkIcon(JdkOrderEntry entry, boolean isExpanded) {
    final ProjectJdk jdk = entry.getJdk();
    if (jdk == null) {
      return CellAppearanceUtils.GENERIC_JDK_ICON;
    }
    return isExpanded? jdk.getSdkType().getIconForExpandedTreeNode() : jdk.getSdkType().getIcon();
  }

  public String getName() {
    return getValue().getName();
  }

  public boolean contains(VirtualFile file) {
    return orderEntryContainsFile(getValue().getOrderEntry(), file);

  }

  public static boolean orderEntryContainsFile(OrderEntry orderEntry, VirtualFile file) {
    VirtualFile[] files = orderEntry.getFiles(OrderRootType.CLASSES);
    for (int i = 0; i < files.length; i++) {
      VirtualFile virtualFile = files[i];
      boolean ancestor = VfsUtil.isAncestor(virtualFile, file, false);
      if (ancestor) return true;
    }

    return false;
  }

  public void update(PresentationData presentation) {
    presentation.setPresentableText(getValue().getName());
    final OrderEntry orderEntry = getValue().getOrderEntry();
    presentation.setOpenIcon((orderEntry instanceof JdkOrderEntry)? getJdkIcon(((JdkOrderEntry)orderEntry), true) : LIB_ICON_OPEN);
    presentation.setClosedIcon((orderEntry instanceof JdkOrderEntry)? getJdkIcon(((JdkOrderEntry)orderEntry), false) : LIB_ICON_CLOSED);

  }

}
