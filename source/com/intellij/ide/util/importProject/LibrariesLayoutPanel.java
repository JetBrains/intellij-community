package com.intellij.ide.util.importProject;

import com.intellij.util.Icons;

import javax.swing.*;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 18, 2007
 */
public class LibrariesLayoutPanel extends ProjectLayoutPanel<LibraryDescriptor>{

  public LibrariesLayoutPanel(final ModuleInsight insight) {
    super(insight);
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
    if(element instanceof File) {
      return Icons.JAR_ICON;
    }
    return super.getElementIcon(element);
  }

  protected int getWeight(final Object element) {
    if (element instanceof LibraryDescriptor) {
      return ((LibraryDescriptor)element).getJars().size() > 1? 10 : 20; 
    }
    return super.getWeight(element);
  }

  protected String getElementText(final Object element) {
    if (element instanceof LibraryDescriptor) {
      final LibraryDescriptor libDescr = (LibraryDescriptor)element;
      final Collection<File> jars = libDescr.getJars();
      if (jars.size() == 1) {
        return getDisplayText(jars.iterator().next());
      }
      return libDescr.getName();
    }
    if (element instanceof File) {
      return getDisplayText((File)element);
    }
    return "";
  }

  protected List<LibraryDescriptor> getEntries() {
    final List<LibraryDescriptor> libs = getInsight().getSuggestedLibraries();
    return libs != null? libs : Collections.<LibraryDescriptor>emptyList();
  }

  protected Collection getDependencies(final LibraryDescriptor entry) {
    return entry.getJars();
  }

  protected LibraryDescriptor merge(final List<LibraryDescriptor> entries) {
    final ModuleInsight insight = getInsight();
    LibraryDescriptor mainLib = null;
    for (LibraryDescriptor entry : entries) {
      if (mainLib == null) {
        mainLib = entry;
      }
      else {
        final Collection<File> files = entry.getJars();
        insight.moveJarsToLibrary(entry, files, mainLib);
      }
    }
    return mainLib;
  }
}
