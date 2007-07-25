package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.HashMap;
import java.util.Map;

class RootModelAdapter {

  private final ModifiableRootModel modifiableRootModel;

  private LibraryTable.ModifiableModel libraryTableModel;

  public RootModelAdapter(Module module) {
    modifiableRootModel = ModuleRootManager.getInstance(module).getModifiableModel();
  }

  public void init(String root) {
    for (ContentEntry contentEntry : modifiableRootModel.getContentEntries()) {
      modifiableRootModel.removeContentEntry(contentEntry);
    }
    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(root);
    if (virtualFile != null) {
      modifiableRootModel.addContentEntry(virtualFile);
    }

    modifiableRootModel.inheritJdk(); // TODO should be able to import
    modifiableRootModel.setExcludeOutput(true);

    for (OrderEntry entry : modifiableRootModel.getOrderEntries()) {
      if (!(entry instanceof ModuleSourceOrderEntry) && !(entry instanceof JdkOrderEntry)) {
        modifiableRootModel.removeOrderEntry(entry);
      }
    }
  }

  public void resetRoots() {
    for (ContentEntry contentEntry : modifiableRootModel.getContentEntries()) {
      for (SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
        if (!sourceFolder.isSynthetic()) {
          contentEntry.removeSourceFolder(sourceFolder);
        }
      }
      for (ExcludeFolder excludeFolder : contentEntry.getExcludeFolders()) {
        if (!excludeFolder.isSynthetic()) {
          contentEntry.removeExcludeFolder(excludeFolder);
        }
      }
    }
  }

  public void commit() {
    if (libraryTableModel != null && libraryTableModel.isChanged()) {
      libraryTableModel.commit();
    }
    if (modifiableRootModel.isChanged()) {
      modifiableRootModel.commit();
    }
    else {
      modifiableRootModel.dispose();
    }
  }

  private LibraryTable.ModifiableModel getLibraryModel() {
    if (libraryTableModel == null) {
      libraryTableModel = modifiableRootModel.getModuleLibraryTable().getModifiableModel();
    }
    return libraryTableModel;
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
    final Library library = getLibraryModel().createLibrary(libraryName);
    final Library.ModifiableModel libraryModel = library.getModifiableModel();
    setUrl(libraryModel, urlClasses, OrderRootType.CLASSES);
    setUrl(libraryModel, urlSources, OrderRootType.SOURCES);
    setUrl(libraryModel, urlJavadoc, OrderRootType.JAVADOC);
    setExported(modifiableRootModel, library);
    libraryModel.commit();
  }

  Map<String, String> getModuleLibraries() {
    Map<String, String> libraries = new HashMap<String, String>();
    for (Library library : getLibraryModel().getLibraries()) {
      if (library.getTable() == null) {
        final String[] urls = library.getUrls(OrderRootType.CLASSES);
        if (urls.length == 1) {
          libraries.put(library.getName(), urls[0]);
        }
      }
    }
    return libraries;
  }

  void updateModuleLibrary(String libraryName, String urlSources, String urlJavadoc) {
    final Library library = getLibraryModel().getLibraryByName(libraryName);
    if (library != null) {
      final Library.ModifiableModel libraryModel = library.getModifiableModel();
      setUrl(libraryModel, urlSources, OrderRootType.SOURCES);
      setUrl(libraryModel, urlJavadoc, OrderRootType.JAVADOC);
      libraryModel.commit();
    }
  }

  private void setUrl(final Library.ModifiableModel libraryModel, final String newUrl, final OrderRootType type) {
    for (String url : libraryModel.getUrls(type)) {
      libraryModel.removeRoot(url, type);
    }
    if (newUrl != null) {
      libraryModel.addRoot(newUrl, type);
    }
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
