package com.intellij.ide.util.importProject;

import com.intellij.ide.util.newProjectWizard.ProjectFromSourcesBuilder;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Icons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 16, 2007
 */
public class ModulesLayoutPanel extends ProjectLayoutPanel<ModuleDescriptor>{
  private static final Icon ICON_MODULE = IconLoader.getIcon("/nodes/ModuleClosed.png");

  public ModulesLayoutPanel(ProjectFromSourcesBuilder builder) {
    super(builder);
  }

  protected Icon getElementIcon(final Object element) {
    if (element instanceof ModuleDescriptor) {
      return ICON_MODULE;
    }
    if (element instanceof LibraryDescriptor) {
      final LibraryDescriptor libDescr = (LibraryDescriptor)element;
      final Collection<File> jars = libDescr.getJars();
      if (jars.size() == 1) {
        return Icons.JAR_ICON;
      }
      return Icons.LIBRARY_ICON;
    }
    if (element instanceof File) {
      return Icons.JAR_ICON;
    }
    return super.getElementIcon(element);
  }

  protected String getElementText(final Object element) {
    if (element instanceof ModuleDescriptor) {
      return element.toString();
    }
    if (element instanceof LibraryDescriptor) {
      final LibraryDescriptor libDescr = (LibraryDescriptor)element;
      final Collection<File> jars = libDescr.getJars();
      if (jars.size() == 1) {
        return jars.iterator().next().getName();
      }
      return libDescr.getName();
    }
    return "";
  }

  protected List<ModuleDescriptor> getEntries() {
    final ProjectLayout layout = getBuilder().getProjectLayout();
    return layout.getModules();
  }

  protected Collection getDependencies(final ModuleDescriptor entry) {
    final ProjectLayout layout = getBuilder().getProjectLayout();
    final List deps = new ArrayList();
    deps.addAll(entry.getDependencies());
    deps.add(layout.getLibraryDependencies(entry));
    return deps;
  }

  @Nullable
  protected ModuleDescriptor merge(final List<ModuleDescriptor> entries) {
    final ProjectLayout layout = getBuilder().getProjectLayout();
    ModuleDescriptor mainDescr = null;
    for (ModuleDescriptor entry : entries) {
      if (mainDescr == null) {
        mainDescr = entry;
      }
      else {
        layout.merge(mainDescr, entry);
      }
    }
    return mainDescr;
  }
  
}
