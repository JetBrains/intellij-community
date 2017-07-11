/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ModuleOrderEntryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.Path;
import org.jetbrains.idea.maven.utils.Url;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.Arrays;
import java.util.Set;

public class MavenRootModelAdapter {

  private final MavenProject myMavenProject;
  private final ModifiableModuleModel myModuleModel;
  private final ModifiableRootModel myRootModel;

  private final MavenSourceFoldersModuleExtension myRootModelModuleExtension;

  private final Set<String> myOrderEntriesBeforeJdk = new THashSet<>();

  public MavenRootModelAdapter(@NotNull MavenProject p, @NotNull Module module, final IdeModifiableModelsProvider rootModelsProvider) {
    myMavenProject = p;
    myModuleModel = rootModelsProvider.getModifiableModuleModel();
    myRootModel = rootModelsProvider.getModifiableRootModel(module);

    myRootModelModuleExtension = myRootModel.getModuleExtension(MavenSourceFoldersModuleExtension.class);
    myRootModelModuleExtension.init(module, myRootModel);
  }

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
        if (!isMavenLibrary(((LibraryOrderEntry)e).getLibrary())) continue;
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

  public ModifiableRootModel getRootModel() {
    return myRootModel;
  }

  @NotNull
  public String[] getSourceRootUrls(boolean includingTests) {
    return myRootModelModuleExtension.getSourceRootUrls(includingTests);
  }

  public Module getModule() {
    return myRootModel.getModule();
  }

  public void clearSourceFolders() {
    myRootModelModuleExtension.clearSourceFolders();
  }

  public <P extends JpsElement> void addSourceFolder(String path, final JpsModuleSourceRootType<P> rootType) {
    addSourceFolder(path, rootType, false, rootType.createDefaultProperties());
  }

  public void addGeneratedJavaSourceFolder(String path, JavaSourceRootType rootType) {
    addSourceFolder(path, rootType, true, JpsJavaExtensionService.getInstance().createSourceRootProperties("", true));
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

  public boolean hasRegisteredSourceSubfolder(@NotNull File f) {
    String url = toUrl(f.getPath()).getUrl();
    return myRootModelModuleExtension.hasRegisteredSourceSubfolder(url);
  }

  @Nullable
  public SourceFolder getSourceFolder(File folder) {
    String url = toUrl(folder.getPath()).getUrl();
    return myRootModelModuleExtension.getSourceFolder(url);
  }

  public boolean isAlreadyExcluded(File f) {
    String url = toUrl(f.getPath()).getUrl();
    return VfsUtilCore.isUnder(url, Arrays.asList(myRootModel.getExcludeRootUrls()));
  }

  private boolean exists(String path) {
    return new File(toPath(path).getPath()).exists();
  }

  public void addExcludedFolder(String path) {
    unregisterAll(path, true, false);
    Url url = toUrl(path);
    ContentEntry e = getContentRootFor(url);
    if (e == null) return;
    if (e.getUrl().equals(url.getUrl())) return;
    e.addExcludeFolder(url.getUrl());
    if (!Registry.is("ide.hide.excluded.files")) {
      Project project = myRootModel.getProject();
      ChangeListManager.getInstance(project).addDirectoryToIgnoreImplicitly(toPath(path).getPath());
    }
  }

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

  public void useModuleOutput(String production, String test) {
    getCompilerExtension().inheritCompilerOutputPath(false);
    getCompilerExtension().setCompilerOutputPath(toUrl(production).getUrl());
    getCompilerExtension().setCompilerOutputPathForTests(toUrl(test).getUrl());
  }

  private CompilerModuleExtension getCompilerExtension() {
    return myRootModel.getModuleExtension(CompilerModuleExtension.class);
  }

  private Url toUrl(String path) {
    return toPath(path).toUrl();
  }

  public Path toPath(String path) {
    if (!FileUtil.isAbsolute(path)) {
      path = new File(myMavenProject.getDirectory(), path).getPath();
    }
    return new Path(path);
  }

  public void addModuleDependency(@NotNull String moduleName,
                                  @NotNull DependencyScope scope,
                                  boolean testJar) {
    Module m = findModuleByName(moduleName);

    ModuleOrderEntry e;
    if (m != null) {
      e = myRootModel.addModuleOrderEntry(m);
    }
    else {
      AccessToken accessToken = ReadAction.start();
      try {
        e = myRootModel.addInvalidModuleEntry(moduleName);
      }
      finally {
        accessToken.finish();
      }
    }

    e.setScope(scope);
    if (testJar) {
      ((ModuleOrderEntryImpl)e).setProductionOnTestDependency(true);
    }

    if (myOrderEntriesBeforeJdk.contains(moduleName)) {
      moveLastOrderEntryBeforeJdk();
    }
  }

  @Nullable
  public Module findModuleByName(String moduleName) {
    return myModuleModel.findModuleByName(moduleName);
  }

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

  public LibraryOrderEntry addLibraryDependency(MavenArtifact artifact,
                                   DependencyScope scope,
                                   IdeModifiableModelsProvider provider,
                                   MavenProject project) {
    assert !MavenConstants.SCOPE_SYSTEM.equals(artifact.getScope()); // System dependencies must be added ad module library, not as project wide library.

    String libraryName = artifact.getLibraryName();

    Library library = provider.getLibraryByName(libraryName);
    if (library == null) {
      library = provider.createLibrary(libraryName, getMavenExternalSource());
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

  public static boolean isChangedByUser(Library library) {
    String[] classRoots = library.getUrls(OrderRootType.CLASSES);
    if (classRoots.length != 1) return true;

    String classes = classRoots[0];

    if (!classes.endsWith("!/")) return true;

    int dotPos = classes.lastIndexOf("/", classes.length() - 2 /* trim ending !/ */);
    if (dotPos == -1) return true;
    String pathToJar = classes.substring(0, dotPos);

    if (hasUserPaths(OrderRootType.SOURCES, library, pathToJar)) return true;
    if (hasUserPaths(JavadocOrderRootType.getInstance(), library, pathToJar)) return true;

    return false;
  }

  private static boolean hasUserPaths(OrderRootType rootType, Library library, String pathToJar) {
    String[] sources = library.getUrls(rootType);
    for (String each : sources) {
      if (!FileUtil.startsWith(each, pathToJar)) return true;
    }
    return false;
  }

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

  @Deprecated // Use artifact.getLibraryName();
  public static String makeLibraryName(@NotNull MavenArtifact artifact) {
    return artifact.getLibraryName();
  }

  public static boolean isMavenLibrary(@Nullable Library library) {
    return library != null && MavenArtifact.isMavenLibrary(library.getName());
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

  public void setLanguageLevel(LanguageLevel level) {
    try {
      myRootModel.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(level);
    }
    catch (IllegalArgumentException e) {
      //bad value was stored
    }
  }
}
