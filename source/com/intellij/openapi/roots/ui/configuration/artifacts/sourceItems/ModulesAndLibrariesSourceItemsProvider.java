package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.elements.*;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingSourceItem;
import com.intellij.packaging.ui.PackagingSourceItemsProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
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

    for (Module toAdd : getNotAddedModules(editorContext, artifact, module)) {
      items.add(new ModuleOutputSourceItem(toAdd));
    }

    for (Library library : getNotAddedLibraries(editorContext, artifact, libraries)) {
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

  @NotNull
  private static List<? extends Module> getNotAddedModules(@NotNull final PackagingEditorContext context, @NotNull Artifact artifact,
                                                          final Module... allModules) {
    final Set<Module> modules = new HashSet<Module>(Arrays.asList(allModules));
    ArtifactUtil.processPackagingElements(artifact, ModuleOutputElementType.MODULE_OUTPUT_ELEMENT_TYPE, new Processor<ModuleOutputPackagingElement>() {
      public boolean process(ModuleOutputPackagingElement moduleOutputPackagingElement) {
        modules.remove(moduleOutputPackagingElement.findModule(context));
        return true;
      }
    }, context, true);
    return new ArrayList<Module>(modules);
  }

  private static List<? extends Library> getNotAddedLibraries(@NotNull final PackagingEditorContext context, @NotNull Artifact artifact,
                                                             List<Library> librariesList) {
    final Set<VirtualFile> roots = new HashSet<VirtualFile>();
    ArtifactUtil.processPackagingElements(artifact, PackagingElementFactoryImpl.FILE_COPY_ELEMENT_TYPE, new Processor<FileCopyPackagingElement>() {
      public boolean process(FileCopyPackagingElement fileCopyPackagingElement) {
        final VirtualFile root = fileCopyPackagingElement.getLibraryRoot();
        if (root != null) {
          roots.add(root);
        }
        return true;
      }
    }, context, true);
    final List<Library> result = new ArrayList<Library>();
    for (Library library : librariesList) {
      if (!roots.containsAll(Arrays.asList(library.getFiles(OrderRootType.CLASSES)))) {
        result.add(library);
      }
    }
    return result;
  }
}
