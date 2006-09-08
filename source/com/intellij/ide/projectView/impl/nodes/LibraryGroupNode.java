package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class LibraryGroupNode extends ProjectViewNode<LibraryGroupElement> {

  public LibraryGroupNode(Project project, LibraryGroupElement value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  public LibraryGroupNode(final Project project, final Object value, final ViewSettings viewSettings) {
    this(project, (LibraryGroupElement)value, viewSettings);
  }

  @NotNull
  public Collection<AbstractTreeNode> getChildren() {
    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(getValue().getModule());
    final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    final OrderEntry[] orderEntries = moduleRootManager.getOrderEntries();
    for (final OrderEntry orderEntry : orderEntries) {
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
    for (final VirtualFile file : files) {
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

  public boolean contains(@NotNull VirtualFile file) {
    return someChildContainsFile(file);
  }

  public void update(PresentationData presentation) {
    presentation.setPresentableText(IdeBundle.message("node.projectview.libraries"));
    presentation.setIcons(Icons.LIBRARY_ICON);
  }

  public boolean canNavigate() {
    return true;
  }

  public boolean canNavigateToSource() {
    return false;
  }

  public void navigate(final boolean requestFocus) {
    Module module = getValue().getModule();
    ModulesConfigurator.showDialog(getProject(), module.getName(), ClasspathEditor.NAME, false);
  }
}
