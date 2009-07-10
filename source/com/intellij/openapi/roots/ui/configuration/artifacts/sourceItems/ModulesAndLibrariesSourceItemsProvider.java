package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Comparing;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.elements.LibraryElementType;
import com.intellij.packaging.impl.elements.ModuleOutputElementType;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.PackagingSourceItemsProvider;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public class ModulesAndLibrariesSourceItemsProvider extends PackagingSourceItemsProvider {

  @NotNull
  public Collection<? extends PackagingSourceItem> getSourceItems(@NotNull PackagingEditorContext editorContext, @NotNull Artifact artifact,
                                                                  PackagingSourceItem parent) {
    if (parent == null) {
      return createModuleItems(editorContext, artifact, ArrayUtil.EMPTY_STRING_ARRAY);
    }
    else if (parent instanceof ModuleGroupItem) {
      return createModuleItems(editorContext, artifact, ((ModuleGroupItem)parent).getPath());
    }
    else if (parent instanceof ModuleSourceItemGroup) {
      return createClasspathItems(editorContext, artifact, ((ModuleSourceItemGroup)parent).getModule());
    }
    return Collections.emptyList();
  }

  private static Collection<? extends PackagingSourceItem> createClasspathItems(PackagingEditorContext editorContext,
                                                                                Artifact artifact, @NotNull Module module) {
    final List<PackagingSourceItem> items = new ArrayList<PackagingSourceItem>();
    final ModuleRootModel rootModel = editorContext.getModulesProvider().getRootModel(module);
    List<Library> libraries = new ArrayList<Library>();
    for (OrderEntry orderEntry : rootModel.getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry) {
        final Library library = ((LibraryOrderEntry)orderEntry).getLibrary();
        if (library != null) {
          libraries.add(library);
        }
      }
    }

    for (Module toAdd : ModuleOutputElementType.getNotAddedModules(editorContext, artifact, module)) {
      items.add(new ModuleOutputSourceItem(toAdd));
    }

    for (Library library : LibraryElementType.getNotAddedLibraries(editorContext, artifact, libraries)) {
      items.add(new LibrarySourceItem(library));
    }
    return items;
  }

  private static Collection<? extends PackagingSourceItem> createModuleItems(PackagingEditorContext editorContext, Artifact artifact, @NotNull String[] groupPath) {
    final Module[] modules = editorContext.getModulesProvider().getModules();
    final List<PackagingSourceItem> items = new ArrayList<PackagingSourceItem>();
    Set<String> groups = new HashSet<String>();
    for (Module module : modules) {
      String[] path = ModuleManager.getInstance(editorContext.getProject()).getModuleGroupPath(module);
      if (path == null) {
        path = ArrayUtil.EMPTY_STRING_ARRAY;
      }

      if (Comparing.equal(path, groupPath)) {
        items.add(new ModuleSourceItemGroup(module));
      }
      else if (ArrayUtil.startsWith(path, groupPath)) {
        groups.add(path[groupPath.length]);
      }
    }
    for (String group : groups) {
      items.add(0, new ModuleGroupItem(ArrayUtil.append(groupPath, group)));
    }
    return items;
  }
}
