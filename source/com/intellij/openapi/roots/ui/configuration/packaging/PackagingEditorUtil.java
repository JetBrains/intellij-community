package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ModuleLibraryTable;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.ui.util.OrderEntryCellAppearanceUtils;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author nik
 */
public class PackagingEditorUtil {
  private PackagingEditorUtil() {
  }

  private static String getLibraryTableDisplayName(final Library library) {
    LibraryTable table = library.getTable();
    LibraryTablePresentation presentation = table != null ? table.getPresentation() : ModuleLibraryTable.MODULE_LIBRARY_TABLE_PRESENTATION;
    return presentation.getDisplayName(false);
  }

  public static void renderLibraryNode(final ColoredTreeCellRenderer renderer, final Library library,
                                       final SimpleTextAttributes mainAttributes, final SimpleTextAttributes commentAttributes) {
    String name = library.getName();
    if (name != null) {
      renderer.setIcon(Icons.LIBRARY_ICON);
      renderer.append(name, mainAttributes);
      renderer.append(getLibraryTableComment(library), commentAttributes);
    }
    else {
      VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
      if (files.length > 0) {
        VirtualFile file = files[0];
        renderer.setIcon(file.getIcon());
        renderer.append(file.getName(), mainAttributes);
        renderer.append(getLibraryTableComment(library), commentAttributes);
      }
      else {
        OrderEntryCellAppearanceUtils.forLibrary(library).customize(renderer);
      }
    }
  }

  private static String getLibraryTableComment(final Library library) {
    LibraryTable libraryTable = library.getTable();
    String displayName;
    if (libraryTable != null) {
      displayName = libraryTable.getPresentation().getDisplayName(false);
    }
    else {
      Module module = ((LibraryImpl)library).getModule();
      String tableName = getLibraryTableDisplayName(library);
      displayName = module != null ? "'" + module.getName() + "' " + tableName : tableName;
    }
    return " (" + displayName + ")";
  }

  public static void renderLibraryFile(final ColoredTreeCellRenderer renderer, final Library library, final VirtualFile file,
                                       final SimpleTextAttributes mainAttributes, final SimpleTextAttributes commentAttributes) {
    renderer.setIcon(file.getIcon());
    renderer.append(file.getName(), mainAttributes);
    String name = library.getName();
    LibraryTable table = library.getTable();
    if (name != null) {
      StringBuilder comment = new StringBuilder();
      comment.append(" ('").append(name).append("' ");
      comment.append(getLibraryTableDisplayName(library));
      comment.append(")");
      renderer.append(comment.toString(), commentAttributes);
    }
    else if (table == null) {
      Module module = ((LibraryImpl)library).getModule();
      String comment;
      if (module == null) {
        comment = " (" + getLibraryTableDisplayName(library) + ")";
      }
      else {
        comment = " " + ProjectBundle.message("node.text.library.of.module", module.getName());
      }
      renderer.append(comment, commentAttributes);
    }
  }

  public static String getLibraryDescription(final @NotNull Library library) {
    String name = library.getName();
    VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
    if (name != null) {
      return "'" + name + "' " + getLibraryTableDisplayName(library);
    }
    else if (files.length > 0) {
      Module module = ((LibraryImpl)library).getModule();
      final String description;
      if (module == null) {
        description = "(" + getLibraryTableDisplayName(library) + ")";
      }
      else {
        description = ProjectBundle.message("node.text.library.of.module", module.getName());
      }
      return files[0].getName() + " " + description;
    }
    else {
      return ProjectBundle.message("library.empty.item");
    }
  }

  public static String getLibraryItemText(final @NotNull Library library) {
    String name = library.getName();
    VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
    if (name != null) {
      return name + getLibraryTableComment(library);
    }
    else if (files.length > 0) {
      return files[0].getName() + getLibraryTableComment(library);
    }
    else {
      return ProjectBundle.message("library.empty.item");
    }
  }

  public static List<Module> getModulesFromDependentOrderEntries(final @NotNull ModuleRootModel rootModel) {
    List<Module> moduleList = new ArrayList<Module>();
    Collection<OrderEntry> orderEntries = OrderEntryUtil.getDependentOrderEntries(rootModel);
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof ModuleOrderEntry) {
        Module module = ((ModuleOrderEntry)orderEntry).getModule();
        if (module != null) {
          moduleList.add(module);
        }
      }
    }

    return moduleList;
  }

  public static List<Library> getLibrariesFromDependentOrderEntries(final @NotNull ModuleRootModel rootModel) {
    List<Library> libraries = new ArrayList<Library>();
    for (OrderEntry orderEntry : OrderEntryUtil.getDependentOrderEntries(rootModel)) {
      if (orderEntry instanceof LibraryOrderEntry) {
        Library library = ((LibraryOrderEntry)orderEntry).getLibrary();
        if (library != null) {
          libraries.add(library);
        }
      }
    }
    return libraries;
  }
}
