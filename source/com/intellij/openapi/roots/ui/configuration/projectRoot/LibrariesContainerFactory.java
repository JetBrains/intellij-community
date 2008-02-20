package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class LibrariesContainerFactory {
  private LibrariesContainerFactory() {
  }

  @NotNull
  public static LibrariesContainer createContainer(@Nullable Project project) {
    return new LibraryContainerImpl(project, null, null);
  }

  @NotNull
  public static LibrariesContainer createContainer(@NotNull Module module) {
    return new LibraryContainerImpl(module.getProject(), module, null);
  }

  @NotNull
  public static LibrariesContainer createContainer(@NotNull ModifiableRootModel rootModel) {
    Module module = rootModel.getModule();
    return new LibraryContainerImpl(module.getProject(), module, rootModel);
  }

  @NotNull
  public static LibrariesContainer createContainer() {
    return new LibraryContainerImpl(null, null, null);
  }

  public static LibrariesContainer createContainer(@NotNull Project project, StructureConfigurableContext context) {
    return new StructureConfigurableLibrariesContainer(project, context);
  }

  @NotNull
  public static Library createLibraryInTable(final @NonNls String name, final VirtualFile[] roots, final VirtualFile[] sources, final LibraryTable table) {
    LibraryTable.ModifiableModel modifiableModel = table.getModifiableModel();
    Library library = modifiableModel.createLibrary(getUniqueLibraryName(name, modifiableModel));
    modifiableModel.commit();
    final Library.ModifiableModel model = library.getModifiableModel();
    for (VirtualFile root : roots) {
      model.addRoot(root, OrderRootType.CLASSES);
    }
    for (VirtualFile root : sources) {
      model.addRoot(root, OrderRootType.SOURCES);
    }
    model.commit();
    return library;
  }

  private static String getUniqueLibraryName(final String baseName, final LibraryTable.ModifiableModel model) {
    String name = baseName;
    int count = 2;
    while (model.getLibraryByName(name) != null) {
      name = baseName + " (" + count++ + ")";
    }
    return name;
  }

  private static class LibraryContainerImpl implements LibrariesContainer {
    private @Nullable Project myProject;
    @Nullable private final Module myModule;
    @Nullable private final ModifiableRootModel myRootModel;

    private LibraryContainerImpl(final @Nullable Project project, final @Nullable Module module, final @Nullable ModifiableRootModel rootModel) {
      myProject = project;
      myModule = module;
      myRootModel = rootModel;
    }

    @NotNull
    public Library[] getAllLibraries() {
      LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();
      Library[] globalLibraries = registrar.getLibraryTable().getLibraries();
      if (myProject == null) {
        return globalLibraries;
      }
      Library[] libraries = ArrayUtil.mergeArrays(globalLibraries, registrar.getLibraryTable(myProject).getLibraries(), Library.class);
      if (myModule == null) {
        return libraries;
      }
      return ArrayUtil.mergeArrays(libraries, getModuleLibraries(), Library.class);
    }

    private Library[] getModuleLibraries() {
      if (myRootModel != null) {
        return myRootModel.getModuleLibraryTable().getLibraries();
      }
      //todo[nik] is there better way to get module libraries?
      ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
      LibraryTable table = model.getModuleLibraryTable();
      model.dispose();
      return table.getLibraries();
    }

    @NotNull
    public VirtualFile[] getLibraryFiles(@NotNull final Library library, @NotNull final OrderRootType rootType) {
      return library.getFiles(rootType);
    }

    public boolean canCreateLibrary(@NotNull final LibraryLevel level) {
      if (level == LibraryLevel.MODULE) {
        return myRootModel != null;
      }
      return level == LibraryLevel.GLOBAL || myProject != null;
    }

    public Library createLibrary(@NotNull @NonNls final String name, @NotNull final LibraryLevel level,
                                 @NotNull final VirtualFile[] classRoots, @NotNull final VirtualFile[] sourceRoots) {
      if (level == LibraryLevel.MODULE && myRootModel != null) {
        return createLibraryInTable(name, classRoots, sourceRoots, myRootModel.getModuleLibraryTable());
      }

      LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();
      LibraryTable table;
      if (level == LibraryLevel.GLOBAL) {
        table = registrar.getLibraryTable();
      }
      else if (level == LibraryLevel.PROJECT && myProject != null) {
        table = registrar.getLibraryTable(myProject);
      }
      else {
        return null;
      }
      return createLibraryInTable(name, classRoots, sourceRoots, table);
    }
  }

  private static class StructureConfigurableLibrariesContainer implements LibrariesContainer {
    private final Project myProject;
    private final StructureConfigurableContext myContext;

    public StructureConfigurableLibrariesContainer(final Project project, final StructureConfigurableContext context) {
      myProject = project;
      myContext = context;
    }

    public Library createLibrary(@NotNull @NonNls final String name, @NotNull final LibraryLevel level,
                                 @NotNull final VirtualFile[] classRoots, @NotNull final VirtualFile[] sourceRoots) {
      LibraryTableModifiableModelProvider provider = myContext.getProjectLibrariesProvider(false);
      LibraryTable.ModifiableModel model = provider.getModifiableModel();
      Library library = model.createLibrary(getUniqueLibraryName(name, model));
      LibraryEditor libraryEditor = ((LibrariesModifiableModel)model).getLibraryEditor(library);
      for (VirtualFile root : classRoots) {
        libraryEditor.addRoot(root, OrderRootType.CLASSES);
      }
      for (VirtualFile source : sourceRoots) {
        libraryEditor.addRoot(source, OrderRootType.SOURCES);
      }
      return library;
    }

    @NotNull
    public Library[] getAllLibraries() {
      LibraryTablesRegistrar registrar = LibraryTablesRegistrar.getInstance();
      return ArrayUtil.mergeArrays(registrar.getLibraryTable().getLibraries(), registrar.getLibraryTable(myProject).getLibraries(),
                                   Library.class);
    }

    public boolean canCreateLibrary(@NotNull final LibraryLevel level) {
      return level == LibraryLevel.GLOBAL || level == LibraryLevel.PROJECT;
    }

    @NotNull
    public VirtualFile[] getLibraryFiles(@NotNull final Library library, @NotNull final OrderRootType rootType) {
      LibrariesModifiableModel projectLibrariesModel = (LibrariesModifiableModel)myContext.getProjectLibrariesProvider(false).getModifiableModel();
      if (projectLibrariesModel.hasLibraryEditor(library)) {
        LibraryEditor libraryEditor = projectLibrariesModel.getLibraryEditor(library);
        return libraryEditor.getFiles(rootType);
      }
      LibrariesModifiableModel globalLibraries = (LibrariesModifiableModel)myContext.getGlobalLibrariesProvider(false).getModifiableModel();
      if (globalLibraries.hasLibraryEditor(library)) {
        LibraryEditor libraryEditor = globalLibraries.getLibraryEditor(library);
        return libraryEditor.getFiles(rootType);
      }
      return library.getFiles(rootType);
    }
  }
}
