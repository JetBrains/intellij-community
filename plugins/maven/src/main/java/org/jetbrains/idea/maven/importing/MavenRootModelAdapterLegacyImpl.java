// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.Path;
import org.jetbrains.idea.maven.utils.Url;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MavenRootModelAdapterLegacyImpl implements MavenRootModelAdapterInterface {

  private final MavenProject myMavenProject;
  private final ModuleModelProxy myModuleModel;
  private final ModifiableRootModel myRootModel;

  private final MavenSourceFoldersModuleExtension myRootModelModuleExtension;

  private final Set<String> myOrderEntriesBeforeJdk = new HashSet<>();

  public MavenRootModelAdapterLegacyImpl(@NotNull MavenProject p, @NotNull Module module, final ModifiableModelsProviderProxy rootModelsProvider) {
    myMavenProject = p;
    myModuleModel = rootModelsProvider.getModuleModelProxy();
    myRootModel = rootModelsProvider.getModifiableRootModel(module);

    myRootModelModuleExtension = myRootModel.getModuleExtension(MavenSourceFoldersModuleExtension.class);
    myRootModelModuleExtension.init(module, myRootModel);
  }

  @Override
  public void init(boolean isNewlyCreatedModule) {
    setupInitialValues(isNewlyCreatedModule);
    initContentRoots();
    initOrderEntries();
  }

  private void setupInitialValues(boolean newlyCreatedModule) {
    if (newlyCreatedModule || myRootModel.getSdk() == null) {
      myRootModel.inheritSdk();
    }
    if (newlyCreatedModule) {
      getCompilerExtension().setExcludeOutput(true);
    }
  }

  private void initContentRoots() {
    Url url = toUrl(myMavenProject.getDirectory());
    if (getContentRootFor(url) != null) return;
    myRootModel.addContentEntry(url.getUrl());
  }

  private ContentEntry getContentRootFor(Url url) {
    for (ContentEntry e : myRootModel.getContentEntries()) {
      if (VfsUtilCore.isEqualOrAncestor(e.getUrl(), url.getUrl())) return e;
    }
    return null;
  }

  private void initOrderEntries() {
    boolean jdkProcessed = false;

    for (OrderEntry e : myRootModel.getOrderEntries()) {
      if (e instanceof ModuleSourceOrderEntry || e instanceof JdkOrderEntry) {
        jdkProcessed = true;
        continue;
      }

      if (e instanceof LibraryOrderEntry) {
        if (Registry.is("maven.always.remove.bad.entries")) {
          if (!isMavenLibrary((LibraryOrderEntry)e)) continue;
        }
        else {
          if (!isMavenLibrary(((LibraryOrderEntry)e).getLibrary())) continue;
        }
      }
      if (e instanceof ModuleOrderEntry) {
        Module m = ((ModuleOrderEntry)e).getModule();
        if (m != null &&
            !MavenProjectsManager.getInstance(myRootModel.getProject()).isMavenizedModule(m) &&
            ExternalSystemModulePropertyManager.getInstance(m).getExternalSystemId() == null) {
          continue;
        }
      }

      if (!jdkProcessed) {
        if (e instanceof ModuleOrderEntry) {
          myOrderEntriesBeforeJdk.add(((ModuleOrderEntry)e).getModuleName());
        }
        else if (e instanceof LibraryOrderEntry) {
          myOrderEntriesBeforeJdk.add(((LibraryOrderEntry)e).getLibraryName());
        }
      }

      myRootModel.removeOrderEntry(e);
    }
  }

  @Override
  public ModifiableRootModel getRootModel() {
    return myRootModel;
  }

  @Override
  public String @NotNull [] getSourceRootUrls(boolean includingTests) {
    return myRootModelModuleExtension.getSourceRootUrls(includingTests);
  }

  @Override
  public Module getModule() {
    return myRootModel.getModule();
  }

  @Override
  public void clearSourceFolders() {
    myRootModelModuleExtension.clearSourceFolders();
  }

  @Override
  public <P extends JpsElement> void addSourceFolder(String path, final JpsModuleSourceRootType<P> rootType) {
    addSourceFolder(path, rootType, false, rootType.createDefaultProperties());
  }

  @Override
  public void addGeneratedJavaSourceFolder(String path, JavaSourceRootType rootType, boolean ifNotEmpty) {
    addSourceFolder(path, rootType, ifNotEmpty, JpsJavaExtensionService.getInstance().createSourceRootProperties("", true));
  }

  @Override
  public void addGeneratedJavaSourceFolder(String path, JavaSourceRootType rootType) {
    addGeneratedJavaSourceFolder(path, rootType, true);
  }

  private  <P extends JpsElement> void addSourceFolder(@NotNull String path, final @NotNull JpsModuleSourceRootType<P> rootType, boolean ifNotEmpty,
                                                       final @NotNull P properties) {
    if (ifNotEmpty) {
      String[] childs = new File(toPath(path).getPath()).list();
      if (childs == null || childs.length == 0) return;
    }
    else {
      if (!exists(path)) return;
    }

    Url url = toUrl(path);
    myRootModelModuleExtension.addSourceFolder(url, rootType, properties);
  }

  @Override
  public boolean hasRegisteredSourceSubfolder(@NotNull File f) {
    String url = toUrl(f.getPath()).getUrl();
    return myRootModelModuleExtension.hasRegisteredSourceSubfolder(url);
  }

  @Override
  @Nullable
  public SourceFolder getSourceFolder(File folder) {
    String url = toUrl(folder.getPath()).getUrl();
    return myRootModelModuleExtension.getSourceFolder(url);
  }

  @Override
  public boolean isAlreadyExcluded(File f) {
    String url = toUrl(f.getPath()).getUrl();
    return VfsUtilCore.isUnder(url, Arrays.asList(myRootModel.getExcludeRootUrls()));
  }

  private boolean exists(String path) {
    return Files.exists(Paths.get(toPath(path).getPath()));
  }

  @Override
  public void addExcludedFolder(String path) {
    unregisterAll(path, true, false);
    Url url = toUrl(path);
    ContentEntry e = getContentRootFor(url);
    if (e == null) return;
    if (e.getUrl().equals(url.getUrl())) return;
    e.addExcludeFolder(url.getUrl());
  }

  @Override
  public void unregisterAll(String path, boolean under, boolean unregisterSources) {
    Url url = toUrl(path);

    for (ContentEntry eachEntry : myRootModel.getContentEntries()) {
      if (unregisterSources) {
        myRootModelModuleExtension.unregisterAll(url, under);
      }

      for (String excludedUrl : eachEntry.getExcludeFolderUrls()) {
        String ancestor = under ? url.getUrl() : excludedUrl;
        String child = under ? excludedUrl : url.getUrl();

        if (VfsUtilCore.isEqualOrAncestor(ancestor, child)) {
          eachEntry.removeExcludeFolder(excludedUrl);
        }
      }
      for (String outputUrl : getCompilerExtension().getOutputRootUrls(true)) {
        String ancestor = under ? url.getUrl() : outputUrl;
        String child = under ? outputUrl : url.getUrl();
        if (VfsUtilCore.isEqualOrAncestor(ancestor, child)) {
          getCompilerExtension().setExcludeOutput(false);
        }
      }
    }
  }

  @Override
  public boolean hasCollision(String sourceRootPath) {
    Url url = toUrl(sourceRootPath);

    for (ContentEntry eachEntry : myRootModel.getContentEntries()) {
      for (SourceFolder eachFolder : eachEntry.getSourceFolders()) {
        String ancestor = url.getUrl();
        String child = eachFolder.getUrl();
        if (VfsUtilCore.isEqualOrAncestor(ancestor, child) || VfsUtilCore.isEqualOrAncestor(child, ancestor)) {
          return true;
        }
      }

      for (String excludeUrl : eachEntry.getExcludeFolderUrls()) {
        String ancestor = url.getUrl();
        if (VfsUtilCore.isEqualOrAncestor(ancestor, excludeUrl) || VfsUtilCore.isEqualOrAncestor(excludeUrl, ancestor)) {
          return true;
        }
      }
    }

    return false;
  }

  @Override
  public void useModuleOutput(String production, String test) {
    getCompilerExtension().inheritCompilerOutputPath(false);
    if (StringUtils.isEmpty(production) && StringUtils.isEmpty(test)) {
      getCompilerExtension().inheritCompilerOutputPath(true);
    }
    else if (StringUtils.isEmpty(test)) {
      getCompilerExtension().setCompilerOutputPath(toUrl(production).getUrl());
      getCompilerExtension().setExcludeOutput(true);
    }
    else if (StringUtils.isEmpty(production)) {
      getCompilerExtension().setCompilerOutputPathForTests(toUrl(test).getUrl());
      getCompilerExtension().setExcludeOutput(true);
    }
    else {
      getCompilerExtension().setCompilerOutputPath(toUrl(production).getUrl());
      getCompilerExtension().setCompilerOutputPathForTests(toUrl(test).getUrl());
    }
  }

  private CompilerModuleExtension getCompilerExtension() {
    return myRootModel.getModuleExtension(CompilerModuleExtension.class);
  }

  private Url toUrl(String path) {
    return toPath(path).toUrl();
  }

  @Override
  public Path toPath(String path) {
    return MavenUtil.toPath(myMavenProject, path);
  }

  @Override
  public void addModuleDependency(@NotNull String moduleName,
                                  @NotNull DependencyScope scope,
                                  boolean testJar) {
    Module m = findModuleByName(moduleName);

    ModuleOrderEntry e;
    if (m != null) {
      e = myRootModel.addModuleOrderEntry(m);
    }
    else {
      e = ReadAction.compute(() -> myRootModel.addInvalidModuleEntry(moduleName));
    }

    e.setScope(scope);
    if (testJar) {
      e.setProductionOnTestDependency(true);
    }

    if (myOrderEntriesBeforeJdk.contains(moduleName)) {
      moveLastOrderEntryBeforeJdk();
    }
  }

  @Override
  @Nullable
  public Module findModuleByName(String moduleName) {
    return myModuleModel.findModuleByName(moduleName);
  }

  @Override
  public void addSystemDependency(MavenArtifact artifact, DependencyScope scope) {
    assert MavenConstants.SCOPE_SYSTEM.equals(artifact.getScope());

    String libraryName = artifact.getLibraryName();

    Library library = myRootModel.getModuleLibraryTable().getLibraryByName(libraryName);
    if (library == null) {
      library = myRootModel.getModuleLibraryTable().createLibrary(libraryName);
    }

    LibraryOrderEntry orderEntry = myRootModel.findLibraryOrderEntry(library);
    assert orderEntry != null;
    orderEntry.setScope(scope);

    Library.ModifiableModel modifiableModel = library.getModifiableModel();
    updateUrl(modifiableModel, OrderRootType.CLASSES, artifact, null, null, true);
    modifiableModel.commit();

    if (myOrderEntriesBeforeJdk.contains(libraryName)) {
      moveLastOrderEntryBeforeJdk();
    }
  }

  @Override
  public LibraryOrderEntry addLibraryDependency(MavenArtifact artifact,
                                                DependencyScope scope,
                                                ModifiableModelsProviderProxy provider,
                                                MavenProject project) {
    assert !MavenConstants.SCOPE_SYSTEM.equals(artifact.getScope()); // System dependencies must be added ad module library, not as project wide library.

    String libraryName = artifact.getLibraryName();

    Library library = provider.getLibraryByName(libraryName);
    if (library == null) {     library = provider.createLibrary(libraryName, getMavenExternalSource());
    }
    Library.ModifiableModel libraryModel = provider.getModifiableLibraryModel(library);

    updateUrl(libraryModel, OrderRootType.CLASSES, artifact, null, null, true);
    updateUrl(libraryModel, OrderRootType.SOURCES, artifact, MavenExtraArtifactType.SOURCES, project, false);
    updateUrl(libraryModel, JavadocOrderRootType.getInstance(), artifact, MavenExtraArtifactType.DOCS, project, false);

    LibraryOrderEntry e = myRootModel.addLibraryEntry(library);
    e.setScope(scope);

    if (myOrderEntriesBeforeJdk.contains(libraryName)) {
      moveLastOrderEntryBeforeJdk();
    }

    return e;
  }

  private void moveLastOrderEntryBeforeJdk() {
    OrderEntry[] entries = myRootModel.getOrderEntries().clone();

    int i = entries.length - 1;
    while (i > 0 && (entries[i - 1] instanceof ModuleSourceOrderEntry || entries[i - 1] instanceof JdkOrderEntry)) {
      OrderEntry e = entries[i - 1];
      entries[i - 1] = entries[i];
      entries[i] = e;
      i--;
    }

    if (i < entries.length) {
      myRootModel.rearrangeOrderEntries(entries);
    }
  }

  private static void updateUrl(Library.ModifiableModel library,
                                OrderRootType type,
                                MavenArtifact artifact,
                                MavenExtraArtifactType artifactType,
                                MavenProject project,
                                boolean clearAll) {
    String classifier = null;
    String extension = null;

    if (artifactType != null) {
      Pair<String, String> result = project.getClassifierAndExtension(artifact, artifactType);
      classifier = result.first;
      extension = result.second;
    }


    String newPath = artifact.getPathForExtraArtifact(classifier, extension);
    String newUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, newPath) + JarFileSystem.JAR_SEPARATOR;

    boolean urlExists = false;

    for (String url : library.getUrls(type)) {
      if (newUrl.equals(url)) {
        urlExists = true;
        continue;
      }
      if (clearAll || (isRepositoryUrl(artifact, url) && !url.startsWith(newUrl))) {
        library.removeRoot(url, type);
      }
    }

    if (!urlExists) {
      library.addRoot(newUrl, type);
    }
  }

  private static boolean isRepositoryUrl(MavenArtifact artifact, String url) {
    return url.contains(artifact.getGroupId().replace('.', '/') + '/' + artifact.getArtifactId() + '/' + artifact.getBaseVersion() + '/' + artifact.getArtifactId() + '-');
  }


  @Override
  public Library findLibrary(@NotNull final MavenArtifact artifact) {
    final String name = artifact.getLibraryName();
    final Ref<Library> result = Ref.create(null);
    myRootModel.orderEntries().forEachLibrary(library -> {
      if (name.equals(library.getName())) {
        result.set(library);
      }
      return true;
    });
    return result.get();
  }

  public static boolean isMavenLibrary(@Nullable Library library) {
    return library != null && MavenArtifact.isMavenLibrary(library.getName());
  }

  public static boolean isMavenLibrary(@Nullable LibraryOrderEntry entry) {
    return entry != null && MavenArtifact.isMavenLibrary(entry.getLibraryName());
  }

  public static ProjectModelExternalSource getMavenExternalSource() {
    return ExternalProjectSystemRegistry.getInstance().getSourceById(ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID);
  }

  @Nullable
  public static OrderEntry findLibraryEntry(@NotNull Module m, @NotNull MavenArtifact artifact) {
    String name = artifact.getLibraryName();
    for (OrderEntry each : ModuleRootManager.getInstance(m).getOrderEntries()) {
      if (each instanceof LibraryOrderEntry && name.equals(((LibraryOrderEntry)each).getLibraryName())) {
        return each;
      }
    }
    return null;
  }

  @Nullable
  public static MavenArtifact findArtifact(@NotNull MavenProject project, @Nullable Library library) {
    if (library == null) return null;

    String name = library.getName();

    if (!MavenArtifact.isMavenLibrary(name)) return null;

    for (MavenArtifact each : project.getDependencies()) {
      if (each.getLibraryName().equals(name)) return each;
    }
    return null;
  }

  @Override
  public void setLanguageLevel(LanguageLevel level) {
    try {
      myRootModel.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(level);
    }
    catch (IllegalArgumentException e) {
      //bad value was stored
    }
  }
}
