package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.pom.java.LanguageLevel;
import org.apache.maven.artifact.Artifact;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.core.util.Path;
import org.jetbrains.idea.maven.core.util.Url;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

public class RootModelAdapter {
  private static final String MAVEN_LIB_PREFIX = "Maven: ";
  private final ModifiableRootModel myRootModel;

  public RootModelAdapter(Module module) {
    myRootModel = ModuleRootManager.getInstance(module).getModifiableModel();
  }

  public void init(MavenProjectModel p, boolean isNewlyCreatedModule) {
    if (isNewlyCreatedModule) setupInitialValues();
    initContentRoots(p);
    initOrderEntries();
  }

  private void setupInitialValues() {
    myRootModel.inheritSdk();
    myRootModel.getModule().setSavePathsRelative(true);
    getCompilerExtension().setExcludeOutput(true);
  }

  private void initContentRoots(MavenProjectModel p) {
    findOrCreateContentRoot(toUrl(p.getFile().getParent().getPath()));
  }

  private void initOrderEntries() {
    for (OrderEntry e : myRootModel.getOrderEntries()) {
      if (e instanceof ModuleSourceOrderEntry || e instanceof JdkOrderEntry) continue;
      if (e instanceof LibraryOrderEntry) {
        String name = ((LibraryOrderEntry)e).getLibraryName();
        if (name == null || !name.startsWith(MAVEN_LIB_PREFIX)) continue;
      }
      if (e instanceof ModuleOrderEntry) {
        Module m = ((ModuleOrderEntry)e).getModule();
        if (m == null) continue;
        if (!MavenProjectsManager.getInstance(myRootModel.getProject()).isMavenizedModule(m)) continue;
      }
      myRootModel.removeOrderEntry(e);
    }
  }

  public ModifiableRootModel getRootModel() {
    return myRootModel;
  }

  public Module getModule() {
    return myRootModel.getModule();
  }

  public void addSourceFolder(String path, boolean testSource) {
    if (!exists(path)) return;

    Url url = toUrl(path);
    removeRegisteredAncestorFolders(url.getUrl());
    findOrCreateContentRoot(url).addSourceFolder(url.getUrl(), testSource);
  }

  private void removeRegisteredAncestorFolders(String url) {
    for (ContentEntry eachEntry : myRootModel.getContentEntries()) {
      for (SourceFolder eachFolder : eachEntry.getSourceFolders()) {
        if (isAncestor(eachFolder.getUrl(), url)) {
          eachEntry.removeSourceFolder(eachFolder);
        }
      }
      for (ExcludeFolder eachFolder : eachEntry.getExcludeFolders()) {
        if (isAncestor(eachFolder.getUrl(), url)) {
          if (eachFolder.isSynthetic()) {
            getCompilerExtension().setExcludeOutput(false);
          }
          else {
            eachEntry.removeExcludeFolder(eachFolder);
          }
        }
      }
    }
  }

  public boolean hasRegisteredSourceSubfolder(File f) {
    String url = toUrl(f.getPath()).getUrl();
    for (ContentEntry eachEntry : myRootModel.getContentEntries()) {
      for (SourceFolder eachFolder : eachEntry.getSourceFolders()) {
        if (isAncestor(url, eachFolder.getUrl())) return true;
      }
    }
    return false;
  }

  public boolean isAlreadyExcluded(File f) {
    String url = toUrl(f.getPath()).getUrl();
    for (ContentEntry eachEntry : myRootModel.getContentEntries()) {
      for (ExcludeFolder eachFolder : eachEntry.getExcludeFolders()) {
        if (isAncestor(eachFolder.getUrl(), url)) return true;
      }
    }
    return false;
  }

  private boolean isAncestor(String ancestor, String child) {
    return ancestor.equals(child) || child.startsWith(ancestor + "/");
  }

  private boolean exists(String path) {
    return new File(new Path(path).getPath()).exists();
  }

  public void addExcludedFolder(String path) {
    unexcludeAllUnder(path);
    Url url = toUrl(path);
    findOrCreateContentRoot(url).addExcludeFolder(url.getUrl());
  }

  public void unexcludeAllUnder(String path) {
    Url url = toUrl(path);
    for (ContentEntry eachEntry : myRootModel.getContentEntries()) {
      for (ExcludeFolder eachFolder : eachEntry.getExcludeFolders()) {
        if (isAncestor(url.getUrl(), eachFolder.getUrl())) {
          if (eachFolder.isSynthetic()) {
            getCompilerExtension().setExcludeOutput(false);
          }
          else {
            eachEntry.removeExcludeFolder(eachFolder);
          }
        }
      }
    }
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

  public void addModuleDependency(String moduleName, boolean isExportable) {
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
    return ModuleManager.getInstance(myRootModel.getProject()).findModuleByName(moduleName);
  }

  public void addLibraryDependency(Artifact artifact, boolean isExportable) {
    String libraryName = makeLibraryName(artifact);

    Library library = getLibraryTable().getLibraryByName(libraryName);
    if (library == null) {
      library = getLibraryTable().createLibrary(libraryName);
      Library.ModifiableModel libraryModel = library.getModifiableModel();

      String artifactPath = artifact.getFile().getPath();

      setUrl(libraryModel, getUrl(artifactPath, null), OrderRootType.CLASSES);
      setUrl(libraryModel, getUrl(artifactPath, MavenConstants.SOURCES_CLASSIFIER), OrderRootType.SOURCES);
      setUrl(libraryModel, getUrl(artifactPath, MavenConstants.JAVADOC_CLASSIFIER), JavadocOrderRootType.getInstance());

      libraryModel.commit();
    }

    myRootModel.addLibraryEntry(library).setExported(isExportable);

    removeOldLibraryDependency(artifact);
  }

  @Nullable
  private String getUrl(String artifactPath, String classifier) {
    String path = artifactPath;

    if (classifier != null) {
      int dotPos = path.lastIndexOf(".");
      if (dotPos == -1) return null; // somethimes path doesn't contain '.'; but i can't make up any reason.

      String withoutExtension = path.substring(0, dotPos);
      path = MessageFormat.format("{0}-{1}.jar", withoutExtension, classifier);
    }

    String normalizedPath = FileUtil.toSystemIndependentName(path);
    return VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, normalizedPath) + JarFileSystem.JAR_SEPARATOR;
  }

  private void removeOldLibraryDependency(Artifact artifact) {
    Library lib = findLibrary(artifact, false);
    if (lib == null) return;
    LibraryOrderEntry entry = myRootModel.findLibraryOrderEntry(lib);
    if (entry == null) return;

    myRootModel.removeOrderEntry(entry);
  }

  private LibraryTable getLibraryTable() {
    return ProjectLibraryTable.getInstance(myRootModel.getProject());
  }

  private void setUrl(Library.ModifiableModel libraryModel, @Nullable String newUrl, OrderRootType type) {
    for (String url : libraryModel.getUrls(type)) {
      libraryModel.removeRoot(url, type);
    }
    if (newUrl != null) {
      libraryModel.addRoot(newUrl, type);
    }
  }

  public Library findLibrary(Artifact artifact) {
    return findLibrary(artifact, true);
  }

  private Library findLibrary(final Artifact artifact, final boolean newType) {
    return myRootModel.processOrder(new RootPolicy<Library>() {
      @Override
      public Library visitLibraryOrderEntry(LibraryOrderEntry e, Library result) {
        String name = newType ? makeLibraryName(artifact) : new MavenId(artifact).toString();
        return name.equals(e.getLibraryName()) ? e.getLibrary() : result;
      }
    }, null);
  }

  private String makeLibraryName(Artifact artifact) {
    return MAVEN_LIB_PREFIX + new MavenId(artifact).toString();
  }

  public void setLanguageLevel(LanguageLevel level) {
    try {
      myRootModel.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(level);
    }
    catch (IllegalArgumentException e) {
      //bad value was stored
    }
  }
}
