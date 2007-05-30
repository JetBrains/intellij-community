package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Map;

class RootModelAdapter {

  private final ModifiableRootModel modifiableRootModel;

  private LibraryTable.ModifiableModel libraryTableModel;

  public RootModelAdapter(Module module) {
    modifiableRootModel = ModuleRootManager.getInstance(module).getModifiableModel();
  }

  public void resetRoots(String root) {
    for (ContentEntry contentEntry : modifiableRootModel.getContentEntries()) {
      modifiableRootModel.removeContentEntry(contentEntry);
    }
    modifiableRootModel.inheritJdk(); // TODO should be able to import
    modifiableRootModel.setExcludeOutput(true);
    for (OrderEntry entry : modifiableRootModel.getOrderEntries()) {
      if (!(entry instanceof ModuleSourceOrderEntry) && !(entry instanceof JdkOrderEntry)) {
        modifiableRootModel.removeOrderEntry(entry);
      }
    }

    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(root);
    if (virtualFile != null) {
      modifiableRootModel.addContentEntry(virtualFile);
    }
  }

  public void commit() {
    if (libraryTableModel != null) {
      libraryTableModel.commit();
    }
    modifiableRootModel.commit();
  }

  void createSrcDir(String path, boolean testSource) {
    final VirtualFile srcDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    if (srcDir != null) {
      findOrCreateContentRoot(srcDir).addSourceFolder(srcDir, testSource);
    }
  }

  public void excludeRoot(final String path) {
    final VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    if (dir != null) {
      findOrCreateContentRoot(dir).addExcludeFolder(dir);
    }
  }

  public void useProjectOutput() {
    modifiableRootModel.inheritCompilerOutputPath(true);
  }

  public void useModuleOutput(final String production, final String test) {
    modifiableRootModel.inheritCompilerOutputPath(false);
    modifiableRootModel.setCompilerOutputPath(VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(production)));
    modifiableRootModel.setCompilerOutputPathForTests(VfsUtil.pathToUrl(FileUtil.toSystemIndependentName(test)));
  }

  ContentEntry findOrCreateContentRoot(VirtualFile srcDir) {
    for (ContentEntry contentEntry : modifiableRootModel.getContentEntries()) {
      VirtualFile virtualFile = contentEntry.getFile();
      if (virtualFile != null && VfsUtil.isAncestor(virtualFile, srcDir, false)) {
        return contentEntry;
      }
    }
    return modifiableRootModel.addContentEntry(srcDir);
  }

  void createModuleDependency(String moduleName) {
    modifiableRootModel.addInvalidModuleEntry(moduleName).setExported(true);
  }

  void createModuleLibrary(String libraryName, String urlClasses, String urlSources, String urlJavadoc) {
    if (libraryTableModel == null) {
      libraryTableModel = modifiableRootModel.getModuleLibraryTable().getModifiableModel();
    }
    Library library = libraryTableModel.createLibrary(libraryName);
    final Library.ModifiableModel libraryModel = library.getModifiableModel();
    libraryModel.addRoot(urlClasses, OrderRootType.CLASSES);
    if (urlSources != null) {
      libraryModel.addRoot(urlSources, OrderRootType.SOURCES);
    }
    if (urlJavadoc != null) {
      libraryModel.addRoot(urlJavadoc, OrderRootType.JAVADOC);
    }
    setExported(modifiableRootModel, library);
    libraryModel.commit();
  }

  private static void setExported(ModuleRootModel moduleRootModel, final Library library) {
    for (OrderEntry orderEntry : moduleRootModel.getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)orderEntry;
        if (library.equals(libraryOrderEntry.getLibrary())) {
          libraryOrderEntry.setExported(true);
        }
      }
    }
  }

  void resolveModuleDependencies(Map<String, String> libraryNameToModule) {
    OrderEntry[] entries = modifiableRootModel.getOrderEntries();
    boolean dirty = false;
    for (int i = 0; i != entries.length; i++) {
      if (entries[i] instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entries[i];
        if (libraryOrderEntry.isModuleLevel()) {
          final String moduleName = libraryNameToModule.get(libraryOrderEntry.getLibraryName());
          if (moduleName != null) {
            dirty = true;
            modifiableRootModel.removeOrderEntry(libraryOrderEntry);
            entries[i] = modifiableRootModel.addInvalidModuleEntry(moduleName);
          }
        }
      }
    }
    if (dirty) {
      modifiableRootModel.rearrangeOrderEntries(entries);
    }
  }
}
