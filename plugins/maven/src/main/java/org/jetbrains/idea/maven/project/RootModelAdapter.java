package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.Path;
import org.jetbrains.idea.maven.core.util.Url;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class RootModelAdapter {
  private final ModifiableRootModel myRootModel;
  private LibraryTable.ModifiableModel myLibraryTable;

  public RootModelAdapter(Module module) {
    myRootModel = ModuleRootManager.getInstance(module).getModifiableModel();
  }

  public void init(String root) {
    for (ContentEntry contentEntry : myRootModel.getContentEntries()) {
      myRootModel.removeContentEntry(contentEntry);
    }
    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(root);
    if (virtualFile != null) {
      myRootModel.addContentEntry(virtualFile);
    }

    myRootModel.inheritSdk();
    getCompilerExtension().setExcludeOutput(true);

    for (OrderEntry entry : myRootModel.getOrderEntries()) {
      if (!(entry instanceof ModuleSourceOrderEntry) && !(entry instanceof JdkOrderEntry)) {
        myRootModel.removeOrderEntry(entry);
      }
    }
  }

  public void resetRoots() {
    for (ContentEntry contentEntry : myRootModel.getContentEntries()) {
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
    if (myLibraryTable != null && myLibraryTable.isChanged()) {
      myLibraryTable.commit();
    }
    if (myRootModel.isChanged()) {
      myRootModel.commit();
    }
    else {
      myRootModel.dispose();
    }
  }

  private LibraryTable.ModifiableModel getLibraryModel() {
    if (myLibraryTable == null) {
      myLibraryTable = myRootModel.getModuleLibraryTable().getModifiableModel();
    }
    return myLibraryTable;
  }

  public void addSourceDir(String path, boolean testSource) {
    Url url = toUrl(path);
    findOrCreateContentRoot(url).addSourceFolder(url.getUrl(), testSource);
  }

  public void excludeRoot(String path) {
    Url url = toUrl(path);
    findOrCreateContentRoot(url).addExcludeFolder(url.getUrl());
  }

  public void useProjectOutput() {
    getCompilerExtension().inheritCompilerOutputPath(true);
  }

  public void useModuleOutput(String production, String test) {
    getCompilerExtension().inheritCompilerOutputPath(false);
    getCompilerExtension().setCompilerOutputPath(toUrl(production).getUrl());
    getCompilerExtension().setCompilerOutputPathForTests(toUrl(test).getUrl());
  }

  private CompilerModuleExtension getCompilerExtension() {
    return myRootModel.getModuleExtension(CompilerModuleExtension.class);
  }

  private Url toUrl(String path) {
    return new Path(path).toUrl();
  }

  ContentEntry findOrCreateContentRoot(Url url) {
    try {
      for (ContentEntry e : myRootModel.getContentEntries()) {
        if (FileUtil.isAncestor(new File(e.getUrl()), new File(url.getUrl()), false)) return e;
      }
      return myRootModel.addContentEntry(url.getUrl());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  void createModuleDependency(String moduleName) {
    Module m = findModuleByName(moduleName);

    ModuleOrderEntry e;
    if (m != null) {
      e = myRootModel.addModuleOrderEntry(m);
    }
    else {
      e = myRootModel.addInvalidModuleEntry(moduleName);
    }

    e.setExported(true);
  }

  @Nullable
  private Module findModuleByName(String moduleName) {
    ModuleManager mm = ModuleManager.getInstance(myRootModel.getModule().getProject());
    return mm.findModuleByName(moduleName);
  }

  void createModuleLibrary(String libraryName,
                           String urlClasses,
                           String urlSources,
                           String urlJavadoc,
                           boolean exportable) {
    final Library library = getLibraryModel().createLibrary(libraryName);
    final Library.ModifiableModel libraryModel = library.getModifiableModel();
    setUrl(libraryModel, urlClasses, OrderRootType.CLASSES);
    setUrl(libraryModel, urlSources, OrderRootType.SOURCES);
    setUrl(libraryModel, urlJavadoc, JavadocOrderRootType.getInstance());

    if (exportable) setExported(myRootModel, library);
    libraryModel.commit();
  }

  private void setExported(ModuleRootModel m, final Library library) {
    m.processOrder(new RootPolicy<Object>() {
      @Override
      public Object visitLibraryOrderEntry(LibraryOrderEntry e, Object value) {
        if (!library.equals(e.getLibrary())) return null;

        e.setExported(true);
        return null;
      }
    }, null);
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
      setUrl(libraryModel, urlJavadoc, JavadocOrderRootType.getInstance());
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

  void resolveModuleDependencies(Map<String, String> libraryNameToModule) {
    OrderEntry[] entries = myRootModel.getOrderEntries();
    boolean dirty = false;
    for (int i = 0; i != entries.length; i++) {
      if (entries[i] instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entries[i];
        if (libraryOrderEntry.isModuleLevel()) {
          final String moduleName = libraryNameToModule.get(libraryOrderEntry.getLibraryName());
          if (moduleName != null) {
            dirty = true;
            myRootModel.removeOrderEntry(libraryOrderEntry);
            entries[i] = myRootModel.addInvalidModuleEntry(moduleName);
          }
        }
      }
    }
    if (dirty) {
      myRootModel.rearrangeOrderEntries(entries);
    }
  }

  public void setLanguageLevel(final LanguageLevel languageLevel) {
    try {
      myRootModel.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(languageLevel);
    }
    catch (IllegalArgumentException e) {
      //bad value was stored
    }
  }
}
