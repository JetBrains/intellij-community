package com.intellij.ide.util.importProject;

import com.intellij.ide.util.newProjectWizard.ProjectFromSourcesBuilder;
import com.intellij.util.Icons;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 18, 2007
 */
public class LibrariesLayoutPanel extends ProjectLayoutPanel<LibraryDescriptor>{

  public LibrariesLayoutPanel(final ProjectFromSourcesBuilder builder) {
    super(builder);
  }

  protected Icon getElementIcon(final Object element) {
    if (element instanceof LibraryDescriptor) {
      final LibraryDescriptor libDescr = (LibraryDescriptor)element;
      final Collection<File> jars = libDescr.getJars();
      if (jars.size() == 1) {
        return Icons.JAR_ICON;
      }
      return Icons.LIBRARY_ICON;
    }
    return super.getElementIcon(element);
  }

  protected String getElementText(final Object element) {
    if (element instanceof LibraryDescriptor) {
      final LibraryDescriptor libDescr = (LibraryDescriptor)element;
      final Collection<File> jars = libDescr.getJars();
      if (jars.size() == 1) {
        return jars.iterator().next().getName();
      }
      return libDescr.getName();
    }
    if (element instanceof File) {
      return ((File)element).getName();
    }
    return "";
  }

  protected Collection<LibraryDescriptor> getEntries() {
    final ProjectLayout layout = getBuilder().getProjectLayout();
    return layout.getLibraries();
  }

  protected Collection getDependencies(final LibraryDescriptor entry) {
    return entry.getJars();
  }

  protected LibraryDescriptor merge(final List<LibraryDescriptor> entries) {
    final ProjectLayout layout = getBuilder().getProjectLayout();
    LibraryDescriptor mainLib = null;
    for (LibraryDescriptor entry : entries) {
      if (mainLib == null) {
        mainLib = entry;
      }
      else {
        final Collection<File> files = entry.getJars();
        for (File jar : files) {
          layout.moveJarToLibrary(jar, mainLib);
        }
      }
    }
    return mainLib;
  }
}
