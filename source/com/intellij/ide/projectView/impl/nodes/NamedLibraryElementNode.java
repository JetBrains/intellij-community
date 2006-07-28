package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.util.CellAppearanceUtils;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

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

  public NamedLibraryElementNode(final Project project, final Object value, final ViewSettings viewSettings) {
    this(project, (NamedLibraryElement)value, viewSettings);
  }

  @NotNull
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

  public boolean contains(@NotNull VirtualFile file) {
    return orderEntryContainsFile(getValue().getOrderEntry(), file);

  }

  public static boolean orderEntryContainsFile(OrderEntry orderEntry, VirtualFile file) {
    return containsFileInOrderType(orderEntry, OrderRootType.CLASSES, file) ||
           containsFileInOrderType(orderEntry, OrderRootType.SOURCES, file) ||
           containsFileInOrderType(orderEntry, OrderRootType.JAVADOC, file);
  }

  private static boolean containsFileInOrderType(final OrderEntry orderEntry, final OrderRootType orderType, final VirtualFile file) {
    if (!orderEntry.isValid()) return false;
    VirtualFile[] files = orderEntry.getFiles(orderType);
    for (VirtualFile virtualFile : files) {
      boolean ancestor = VfsUtil.isAncestor(virtualFile, file, false);
      if (ancestor) return true;
    }
    return false;
  }

  public void update(PresentationData presentation) {
    presentation.setPresentableText(getValue().getName());
    final OrderEntry orderEntry = getValue().getOrderEntry();
    presentation.setOpenIcon(orderEntry instanceof JdkOrderEntry ? getJdkIcon((JdkOrderEntry)orderEntry, true) : LIB_ICON_OPEN);
    presentation.setClosedIcon(orderEntry instanceof JdkOrderEntry ? getJdkIcon((JdkOrderEntry)orderEntry, false) : LIB_ICON_CLOSED);
    if (orderEntry instanceof JdkOrderEntry) {
      final JdkOrderEntry jdkOrderEntry = (JdkOrderEntry)orderEntry;
      final ProjectJdk projectJdk = jdkOrderEntry.getJdk();
      if (projectJdk != null) { //jdk not specified
        presentation.setLocationString(FileUtil.toSystemDependentName(projectJdk.getHomePath()));
      }
    }
  }

  protected String getToolTip() {
    OrderEntry orderEntry = getValue().getOrderEntry();
    return orderEntry instanceof JdkOrderEntry ? IdeBundle.message("node.projectview.jdk") : StringUtil.capitalize(IdeBundle.message("node.projectview.library", ((LibraryOrderEntry)orderEntry).getLibraryLevel()));
  }
}
