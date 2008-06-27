package com.intellij.ide.impl;

import com.intellij.facet.*;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author nik
 */
public class ProjectSettingsSelectInTarget implements SelectInTarget {
  public boolean canSelect(final SelectInContext context) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(context.getProject()).getFileIndex();
    VirtualFile file = context.getVirtualFile();
    return fileIndex.isInContent(file) || fileIndex.isInLibraryClasses(file) || fileIndex.isInLibrarySource(file);
  }

  public void selectIn(final SelectInContext context, final boolean requestFocus) {
    final Project project = context.getProject();
    VirtualFile file = context.getVirtualFile();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module module = fileIndex.getModuleForFile(file);
    if (module != null) {
      final Facet facet = findFacet(project, file, fileIndex);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (facet != null) {
            ModulesConfigurator.showFacetSettingsDialog(facet, null);
          }
          else {
            ModulesConfigurator.showDialog(project, module.getName(), null, false);
          }
        }
      });
      return;
    }

    final LibraryOrderEntry libraryOrderEntry = findLibrary(file, fileIndex);
    if (libraryOrderEntry != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          ModulesConfigurator.showLibrarySettings(project, libraryOrderEntry);
        }
      });
      return;
    }

    final Sdk jdk = findJdk(file, fileIndex);
    if (jdk != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          ModulesConfigurator.showSdkSettings(project, jdk);
        }
      });
    }
  }

  @Nullable
  private static LibraryOrderEntry findLibrary(final VirtualFile file, final ProjectFileIndex fileIndex) {
    List<OrderEntry> entries = fileIndex.getOrderEntriesForFile(file);
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry) {
        return (LibraryOrderEntry)entry;
      }
    }
    return null;
  }

  @Nullable
  private static Sdk findJdk(final VirtualFile file, final ProjectFileIndex fileIndex) {
    List<OrderEntry> entries = fileIndex.getOrderEntriesForFile(file);
    for (OrderEntry entry : entries) {
      if (entry instanceof JdkOrderEntry) {
        return ((JdkOrderEntry)entry).getJdk();
      }
    }
    return null;
  }

  @Nullable
  private static Facet findFacet(final @NotNull Project project, final @NotNull VirtualFile file, final @NotNull ProjectFileIndex fileIndex) {
    if (!fileIndex.isInSourceContent(file)) {
      for (FacetTypeId id : FacetTypeRegistry.getInstance().getFacetTypeIds()) {
        if (hasFacetWithRoots(project, id)) {
          Facet facet = FacetFinder.getInstance(project).findFacet(file, id);
          if (facet != null) {
            return facet;
          }
        }
      }
    }
    return null;
  }

  private static <F extends Facet> boolean hasFacetWithRoots(final @NotNull Project project, final @NotNull FacetTypeId<F> id) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      Collection<? extends Facet> facets = FacetManager.getInstance(module).getFacetsByType(id);
      Iterator<? extends Facet> iterator = facets.iterator();
      if (iterator.hasNext()) {
        return iterator.next() instanceof FacetRootsProvider;
      }
    }
    return false;
  }

  public String getToolWindowId() {
    return null;
  }

  public String getMinorViewId() {
    return null;
  }

  public String toString() {
    return IdeBundle.message("select.in.project.settings");
  }

  public float getWeight() {
    return StandardTargetWeights.PROJECT_SETTINGS_WEIGHT;
  }
}
