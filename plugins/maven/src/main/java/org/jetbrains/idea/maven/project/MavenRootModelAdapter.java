package org.jetbrains.idea.maven.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.utils.Path;
import org.jetbrains.idea.maven.utils.Url;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;

public class MavenRootModelAdapter {
  private static final String MAVEN_LIB_PREFIX = "Maven: ";
  private final ModifiableRootModel myRootModel;

  public MavenRootModelAdapter(Module module, final ModulesProvider modulesProvider) {
    if (modulesProvider != null) {
      final ModuleRootModel rootModel = modulesProvider.getRootModel(module);
      if (rootModel instanceof ModifiableRootModel) {
        myRootModel = (ModifiableRootModel)rootModel;
      }
      else {
        myRootModel = ModuleRootManager.getInstance(module).getModifiableModel();
      }
    }
    else {
      myRootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    }
  }

  public void init(MavenProjectModel p, boolean isNewlyCreatedModule) {
    setupInitialValues(isNewlyCreatedModule);
    initContentRoots(p);
    initOrderEntries();
  }

  private void setupInitialValues(boolean newlyCreatedModule) {
    if (newlyCreatedModule || myRootModel.getSdk() == null) {
      myRootModel.inheritSdk();
    }
    if (newlyCreatedModule) {
      myRootModel.getModule().setSavePathsRelative(true);
      getCompilerExtension().setExcludeOutput(true);
    }
  }

  private void initContentRoots(MavenProjectModel p) {
    findOrCreateContentRoot(toUrl(p.getFile().getParent().getPath()));
  }

  private void initOrderEntries() {
    for (OrderEntry e : myRootModel.getOrderEntries()) {
      if (e instanceof ModuleSourceOrderEntry || e instanceof JdkOrderEntry) continue;
      if (e instanceof LibraryOrderEntry) {
        if (!isMavenLibrary(((LibraryOrderEntry)e).getLibraryName())) continue;
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
    unregisterAll(path, false, true);
    findOrCreateContentRoot(url).addSourceFolder(url.getUrl(), testSource);
  }

  public boolean hasRegisteredSourceSubfolder(File f) {
    String url = toUrl(f.getPath()).getUrl();
    for (ContentEntry eachEntry : myRootModel.getContentEntries()) {
      for (SourceFolder eachFolder : eachEntry.getSourceFolders()) {
        if (isEqualOrAncestor(url, eachFolder.getUrl())) return true;
      }
    }
    return false;
  }

  public boolean isAlreadyExcluded(File f) {
    String url = toUrl(f.getPath()).getUrl();
    for (ContentEntry eachEntry : myRootModel.getContentEntries()) {
      for (ExcludeFolder eachFolder : eachEntry.getExcludeFolders()) {
        if (isEqualOrAncestor(eachFolder.getUrl(), url)) return true;
      }
    }
    return false;
  }

  private boolean isEqualOrAncestor(String ancestor, String child) {
    return ancestor.equals(child) || child.startsWith(ancestor + "/");
  }

  private boolean exists(String path) {
    return new File(new Path(path).getPath()).exists();
  }

  public void addExcludedFolder(String path) {
    unregisterAll(path, true, false);
    Url url = toUrl(path);
    findOrCreateContentRoot(url).addExcludeFolder(url.getUrl());
  }

  public void unregisterAll(String path, boolean under, boolean unregisterSources) {
    Url url = toUrl(path);

    for (ContentEntry eachEntry : myRootModel.getContentEntries()) {
      if (unregisterSources) {
        for (SourceFolder eachFolder : eachEntry.getSourceFolders()) {
          String ancestor = under ? url.getUrl() : eachFolder.getUrl();
          String child = under ? eachFolder.getUrl() : url.getUrl();
          if (isEqualOrAncestor(ancestor, child)) {
            eachEntry.removeSourceFolder(eachFolder);
          }
        }
      }

      for (ExcludeFolder eachFolder : eachEntry.getExcludeFolders()) {
        String ancestor = under ? url.getUrl() : eachFolder.getUrl();
        String child = under ? eachFolder.getUrl() : url.getUrl();

        if (isEqualOrAncestor(ancestor, child)) {
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

  public void addLibraryDependency(MavenArtifact artifact, boolean isExportable, ProjectLibrariesProvider provider) {
    String libraryName = makeLibraryName(artifact);

    Library library = provider.getLibraryByName(libraryName);
    if (library == null) {
      library = provider.createLibrary(libraryName);
      Library.ModifiableModel libraryModel = provider.getModifiableModel(library);

      String artifactPath = artifact.getFile().getPath();

      setUrl(libraryModel, makeUrl(artifactPath, null), OrderRootType.CLASSES);
      setUrl(libraryModel, makeUrl(artifactPath, MavenConstants.SOURCES_CLASSIFIER), OrderRootType.SOURCES);
      setUrl(libraryModel, makeUrl(artifactPath, MavenConstants.JAVADOC_CLASSIFIER), JavadocOrderRootType.getInstance());

      provider.commit(libraryModel);
    }

    provider.markLibraryAsUsed(library);
    myRootModel.addLibraryEntry(library).setExported(isExportable);

    removeOldLibraryDependency(artifact);
  }

  @Nullable
  private String makeUrl(String artifactPath, String classifier) {
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

  public static boolean isChangedByUser(Library library) {
    String[] classRoots = library.getUrls(OrderRootType.CLASSES);
    if (classRoots.length != 1) return true;

    String classes = classRoots[0];

    int dotPos = classes.lastIndexOf(".");
    if (dotPos == -1) return true;
    String path = classes.substring(0, dotPos);

    String jarSuffix = ".jar" + JarFileSystem.JAR_SEPARATOR;
    String sourcesPath = path + "-" + MavenConstants.SOURCES_CLASSIFIER + jarSuffix;
    String javadocPath = path + "-" + MavenConstants.JAVADOC_CLASSIFIER + jarSuffix;

    for (String each : library.getUrls(OrderRootType.SOURCES)) {
      if (!FileUtil.pathsEqual(each, sourcesPath)) return true;
    }
    for (String each : library.getUrls(JavadocOrderRootType.getInstance())) {
      if (!FileUtil.pathsEqual(each, javadocPath)) return true;
    }
    return false;
  }

  private void removeOldLibraryDependency(MavenArtifact artifact) {
    Library lib = findLibrary(artifact, false);
    if (lib == null) return;
    LibraryOrderEntry entry = myRootModel.findLibraryOrderEntry(lib);
    if (entry == null) return;

    myRootModel.removeOrderEntry(entry);
  }

  private void setUrl(Library.ModifiableModel libraryModel, @Nullable String newUrl, OrderRootType type) {
    for (String url : libraryModel.getUrls(type)) {
      libraryModel.removeRoot(url, type);
    }
    if (newUrl != null) {
      libraryModel.addRoot(newUrl, type);
    }
  }

  public Library findLibrary(MavenArtifact artifact) {
    return findLibrary(artifact, true);
  }

  private Library findLibrary(final MavenArtifact artifact, final boolean newType) {
    return myRootModel.processOrder(new RootPolicy<Library>() {
      @Override
      public Library visitLibraryOrderEntry(LibraryOrderEntry e, Library result) {
        String name = newType ? makeLibraryName(artifact) : artifact.getMavenId().toString();
        return name.equals(e.getLibraryName()) ? e.getLibrary() : result;
      }
    }, null);
  }

  private String makeLibraryName(MavenArtifact artifact) {
    return MAVEN_LIB_PREFIX + artifact.getMavenId().toString();
  }

  public static boolean isMavenLibrary(Library library) {
    String name = library.getName();
    return isMavenLibrary(name);
  }

  private static boolean isMavenLibrary(String name) {
    return name != null && name.startsWith(MAVEN_LIB_PREFIX);
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
