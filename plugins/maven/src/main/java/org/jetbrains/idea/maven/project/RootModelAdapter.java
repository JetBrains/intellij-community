package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.Path;
import org.jetbrains.idea.maven.core.util.Url;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class RootModelAdapter {
  private final ModifiableRootModel myRootModel;
  private LibraryTable.ModifiableModel myLibraryTable;

  public RootModelAdapter(Module module) {
    myRootModel = ModuleRootManager.getInstance(module).getModifiableModel();
  }

  public void init(MavenProjectModel.Node p) {
    initContentRoots(p);
    initOrderEntries();
    configure();
  }

  public void initContentRoots(MavenProjectModel.Node p) {
    for (ContentEntry e : myRootModel.getContentEntries()) {
      myRootModel.removeContentEntry(e);
    }

    findOrCreateContentRoot(toUrl(p.getFile().getParent().getPath()));
  }

  private void initOrderEntries() {
    for (OrderEntry e : myRootModel.getOrderEntries()) {
      if (e instanceof ModuleSourceOrderEntry || e instanceof JdkOrderEntry) continue;
      myRootModel.removeOrderEntry(e);
    }
  }

  private void configure() {
    myRootModel.inheritSdk();
    myRootModel.getModule().setSavePathsRelative(true);
    getCompilerExtension().setExcludeOutput(true);
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

  public ModifiableRootModel getRootModel() {
    return myRootModel;
  }

  private LibraryTable.ModifiableModel getLibraryModel() {
    if (myLibraryTable == null) {
      myLibraryTable = myRootModel.getModuleLibraryTable().getModifiableModel();
    }
    return myLibraryTable;
  }

  public void addSourceFolder(String path, boolean testSource) {
    if (!exists(path)) return;

    Url url = toUrl(path);
    findOrCreateContentRoot(url).addSourceFolder(url.getUrl(), testSource);
  }

  private boolean exists(String path) {
    return new File(new Path(path).getPath()).exists();
  }

  public Set<Path> getSourceFolders() {
    Set<Path> result = new HashSet<Path>();
    for (ContentEntry entry : myRootModel.getContentEntries()) {
      for (SourceFolder f : entry.getSourceFolders()) {
        result.add(fromUrl(f.getUrl()));
      }
    }
    return result;
  }

  public void addExcludedFolder(String path) {
    Url url = toUrl(path);
    findOrCreateContentRoot(url).addExcludeFolder(url.getUrl());
  }

  public Set<Path> getExcludedFolders() {
    Set<Path> result = new HashSet<Path>();
    for (ContentEntry entry : myRootModel.getContentEntries()) {
      for (ExcludeFolder f : entry.getExcludeFolders()) {
        result.add(fromUrl(f.getUrl()));
      }
    }
    String output = getCompilerExtension().getCompilerOutputUrl();
    String testOutput = getCompilerExtension().getCompilerOutputUrlForTests();
    if (output != null) result.add(fromUrl(output));
    if (testOutput != null) result.add(fromUrl(testOutput));
    return result;
  }

  private Path fromUrl(String output) {
    return new Path(VirtualFileManager.extractPath(output));
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

  public void createModuleDependency(String moduleName, boolean isExportable) {
    Module m = findModuleByName(moduleName);

    ModuleOrderEntry e;
    if (m != null) {
      e = myRootModel.addModuleOrderEntry(m);
    }
    else {
      e = myRootModel.addInvalidModuleEntry(moduleName);
    }

    e.setExported(isExportable);
  }

  @Nullable
  private Module findModuleByName(String moduleName) {
    ModuleManager mm = ModuleManager.getInstance(myRootModel.getModule().getProject());
    return mm.findModuleByName(moduleName);
  }

  public void createModuleLibrary(String libraryName,
                                  String urlClasses,
                                  String urlSources,
                                  String urlJavadoc,
                                  boolean isExportable) {
    final Library library = getLibraryModel().createLibrary(libraryName);
    final Library.ModifiableModel libraryModel = library.getModifiableModel();
    setUrl(libraryModel, urlClasses, OrderRootType.CLASSES);
    setUrl(libraryModel, urlSources, OrderRootType.SOURCES);
    setUrl(libraryModel, urlJavadoc, JavadocOrderRootType.getInstance());

    LibraryOrderEntry e = findLibraryEntry(myRootModel, library);
    e.setExported(isExportable);

    libraryModel.commit();
  }

  private LibraryOrderEntry findLibraryEntry(ModuleRootModel m, final Library library) {
    return m.processOrder(new RootPolicy<LibraryOrderEntry>() {
      @Override
      public LibraryOrderEntry visitLibraryOrderEntry(LibraryOrderEntry e, LibraryOrderEntry result) {
        return library.equals(e.getLibrary()) ? e : null;
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

  public void setLanguageLevel(final LanguageLevel languageLevel) {
    try {
      myRootModel.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(languageLevel);
    }
    catch (IllegalArgumentException e) {
      //bad value was stored
    }
  }
}
