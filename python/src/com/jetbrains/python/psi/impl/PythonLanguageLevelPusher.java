/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.util.FileContentUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.messages.MessageBus;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.facet.PythonFacetSettings;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PythonSdkPathCache;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * @author yole
 */
public class PythonLanguageLevelPusher implements FilePropertyPusher<LanguageLevel> {
  public static final Key<LanguageLevel> PYTHON_LANGUAGE_LEVEL = Key.create("PYTHON_LANGUAGE_LEVEL");

  private final Map<Module, Sdk> myModuleSdks = ContainerUtil.createWeakMap();

  public static void pushLanguageLevel(final Project project) {
    PushedFilePropertiesUpdater.getInstance(project).pushAll(new PythonLanguageLevelPusher());
  }

  public void initExtra(@NotNull Project project, @NotNull MessageBus bus, @NotNull Engine languageLevelUpdater) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    Set<Sdk> usedSdks = new HashSet<>();
    for (Module module : modules) {
      if (isPythonModule(module)) {
        final Sdk sdk = PythonSdkType.findPythonSdk(module);
        myModuleSdks.put(module, sdk);
        if (sdk != null && !usedSdks.contains(sdk)) {
          usedSdks.add(sdk);
          updateSdkLanguageLevel(project, sdk);
        }
      }
    }
    project.putUserData(PYTHON_LANGUAGE_LEVEL, PyUtil.guessLanguageLevel(project));
  }

  @NotNull
  public Key<LanguageLevel> getFileDataKey() {
    return LanguageLevel.KEY;
  }

  public boolean pushDirectoriesOnly() {
    return true;
  }

  @NotNull
  public LanguageLevel getDefaultValue() {
    return LanguageLevel.getDefault();
  }

  @Nullable
  public LanguageLevel getImmediateValue(@NotNull Project project, @Nullable VirtualFile file) {
    return getFileLanguageLevel(project, file);
  }

  @Nullable
  public static LanguageLevel getFileLanguageLevel(@NotNull Project project, @Nullable VirtualFile file) {
    if (ApplicationManager.getApplication().isUnitTestMode() && LanguageLevel.FORCE_LANGUAGE_LEVEL != null) {
      return LanguageLevel.FORCE_LANGUAGE_LEVEL;
    }
    if (file == null) return null;
    final Sdk sdk = getFileSdk(project, file);
    if (sdk != null) {
      return PythonSdkType.getLanguageLevelForSdk(sdk);
    }
    return PyUtil.guessLanguageLevelWithCaching(project);
  }

  @Nullable
  private static Sdk getFileSdk(@NotNull Project project, @NotNull VirtualFile file) {
    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module != null) {
      final Sdk sdk = PythonSdkType.findPythonSdk(module);
      if (sdk != null) {
        return sdk;
      }
      return null;
    } else {
      return findSdkForFileOutsideTheProject(project, file);
    }
  }

  @Nullable
  private static Sdk findSdkForFileOutsideTheProject(Project project, VirtualFile file) {
    if (file != null) {
      final List<OrderEntry> orderEntries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(file);
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof JdkOrderEntry) {
          return ((JdkOrderEntry)orderEntry).getJdk();
        }
      }
    }
    return null;
  }

  public LanguageLevel getImmediateValue(@NotNull Module module) {
    if (ApplicationManager.getApplication().isUnitTestMode() && LanguageLevel.FORCE_LANGUAGE_LEVEL != null) {
      return LanguageLevel.FORCE_LANGUAGE_LEVEL;
    }

    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    return PythonSdkType.getLanguageLevelForSdk(sdk);
  }

  public boolean acceptsFile(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public boolean acceptsDirectory(@NotNull VirtualFile file, @NotNull Project project) {
    return true;
  }

  private static final FileAttribute PERSISTENCE = new FileAttribute("python_language_level_persistence", 2, true);

  public void persistAttribute(@NotNull Project project, @NotNull VirtualFile fileOrDir, @NotNull LanguageLevel level) throws IOException {
    final DataInputStream iStream = PERSISTENCE.readAttribute(fileOrDir);
    if (iStream != null) {
      try {
        final int oldLevelOrdinal = DataInputOutputUtil.readINT(iStream);
        if (oldLevelOrdinal == level.ordinal()) return;
      }
      finally {
        iStream.close();
      }
    }

    final DataOutputStream oStream = PERSISTENCE.writeAttribute(fileOrDir);
    DataInputOutputUtil.writeINT(oStream, level.ordinal());
    oStream.close();

    PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(fileOrDir, PythonLanguageLevelPusher::isPythonFile);
    for (VirtualFile child : fileOrDir.getChildren()) {
      if (!child.isDirectory() && isPythonFile(child)) {
        clearSdkPathCache(child);
      }
    }
  }

  private static boolean isPythonFile(VirtualFile child) {
    return PythonFileType.INSTANCE.equals(FileTypeRegistry.getInstance().getFileTypeByFileName(child.getName()));
  }

  private static void clearSdkPathCache(@NotNull final VirtualFile child) {
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      final Sdk sdk = getFileSdk(project, child);
      if (sdk != null) {
        final PythonSdkPathCache pathCache = PythonSdkPathCache.getInstance(project, sdk);
        pathCache.clearCache();
      }
    }
  }

  public void afterRootsChanged(@NotNull final Project project) {
    Set<Sdk> updatedSdks = new HashSet<>();
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    boolean needReparseOpenFiles = false;
    for (Module module : modules) {
      if (isPythonModule(module)) {
        Sdk newSdk = PythonSdkType.findPythonSdk(module);
        if (myModuleSdks.containsKey(module)) {
          Sdk oldSdk = myModuleSdks.get(module);
          if ((newSdk != null || oldSdk != null) && newSdk != oldSdk) {
            needReparseOpenFiles = true;
          }
        }
        myModuleSdks.put(module, newSdk);
        if (newSdk != null && !updatedSdks.contains(newSdk)) {
          updatedSdks.add(newSdk);
          updateSdkLanguageLevel(project, newSdk);
        }
      }
    }
    if (needReparseOpenFiles) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (project.isDisposed()) {
          return;
        }
        FileContentUtil.reparseFiles(project, Collections.emptyList(), true);
      });
    }
  }

  private static boolean isPythonModule(@NotNull final Module module) {
    final ModuleType moduleType = ModuleType.get(module);
    if (moduleType instanceof PythonModuleTypeBase) return true;
    final Facet[] allFacets = FacetManager.getInstance(module).getAllFacets();
    for (Facet facet : allFacets) {
      if (facet.getConfiguration() instanceof PythonFacetSettings) {
        return true;
      }
    }
    return false;
  }

  private void updateSdkLanguageLevel(@NotNull final Project project, final Sdk sdk) {
    final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
    final VirtualFile[] files = sdk.getRootProvider().getFiles(OrderRootType.CLASSES);
    final Application application = ApplicationManager.getApplication();
    PyUtil.invalidateLanguageLevelCache(project);
    final Runnable markFiles = () -> application.runReadAction(() -> {
      if (project.isDisposed()) {
        return;
      }
      for (VirtualFile file : files) {
        if (file.isValid()) {
          VirtualFile parent = file.getParent();
          boolean suppressSizeLimit = false;
          if (parent != null && parent.getName().equals(PythonSdkType.SKELETON_DIR_NAME)) {
            suppressSizeLimit = true;
          }
          markRecursively(project, file, languageLevel, suppressSizeLimit);
        }
      }
    });
    if (application.isUnitTestMode()) {
      markFiles.run();
    }
    else {
      application.executeOnPooledThread(markFiles);
    }
  }

  private void markRecursively(final Project project,
                               @NotNull final VirtualFile file,
                               final LanguageLevel languageLevel,
                               final boolean suppressSizeLimit) {
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        if (fileTypeManager.isFileIgnored(file)) {
          return false;
        }
        if (file.isDirectory()) {
          PushedFilePropertiesUpdater.getInstance(project).findAndUpdateValue(file, PythonLanguageLevelPusher.this, languageLevel);
        }
        if (suppressSizeLimit) {
          SingleRootFileViewProvider.doNotCheckFileSizeLimit(file);
        }
        return true;
      }
    });
  }

  public static void setForcedLanguageLevel(final Project project, @Nullable LanguageLevel languageLevel) {
    LanguageLevel.FORCE_LANGUAGE_LEVEL = languageLevel;
    pushLanguageLevel(project);
  }

  public void flushLanguageLevelCache() {
    myModuleSdks.clear();
  }
}
