package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.Icons;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class LibraryGroupNode extends ProjectViewNode<LibraryGroupElement> {

  public LibraryGroupNode(Project project, LibraryGroupElement value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public Collection<AbstractTreeNode> getChildren() {
    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(getValue().getModule());
    final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
    for (int idx = 0; idx < orderEntries.length; idx++) {
      final OrderEntry orderEntry = orderEntries[idx];
      if (orderEntry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)orderEntry;
        final Library library = libraryOrderEntry.getLibrary();
        if (library == null) {
          continue;
        }
        final String libraryName = library.getName();
        if (libraryName == null || libraryName.length() == 0) {
          addLibraryChildren(orderEntry, children, getProject(), this);
        }
        else {
          children.add(new NamedLibraryElementNode(getProject(), new NamedLibraryElement(getValue(), orderEntry), getSettings()));
        }
      }
      else if (orderEntry instanceof JdkOrderEntry) {
        final ProjectJdk jdk = ((JdkOrderEntry)orderEntry).getJdk();
        if (jdk != null) {
          children.add(new NamedLibraryElementNode(getProject(), new NamedLibraryElement(getValue(), orderEntry), getSettings()));
        }
      }
    }
    return children;
  }

  public static void addLibraryChildren(final OrderEntry entry, final List<AbstractTreeNode> children, Project project, ProjectViewNode node) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final VirtualFile[] files = entry.getFiles(OrderRootType.CLASSES);
    for (int idx = 0; idx < files.length; idx++) {
      final VirtualFile file = files[idx];
      final PsiDirectory psiDir = psiManager.findDirectory(file);
      if (psiDir == null) {
        continue;
      }
      children.add(new PsiDirectoryNode(project, psiDir, node.getSettings()));
    }
  }


  public String getTestPresentation() {
    return "Libraries";
  }

  public boolean contains(VirtualFile file) {
    return someChildContainsFile(file);
  }

  public void update(PresentationData presentation) {
    presentation.setPresentableText("Libraries");
    presentation.setIcons(Icons.LIBRARY_ICON);
  }
}
