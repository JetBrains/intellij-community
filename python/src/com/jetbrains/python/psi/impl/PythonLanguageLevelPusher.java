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
package com.jetbrains.python.psi.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.util.FileContentUtil;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.messages.MessageBus;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.LanguageLevel;
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
  private final Map<Module, Sdk> myModuleSdks = new WeakHashMap<Module, Sdk>();

  public static void pushLanguageLevel(final Project project) {
    PushedFilePropertiesUpdater.getInstance(project).pushAll(new PythonLanguageLevelPusher());
  }

  public void initExtra(@NotNull Project project, @NotNull MessageBus bus, @NotNull Engine languageLevelUpdater) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    Set<Sdk> usedSdks = new HashSet<Sdk>();
    for (Module module : modules) {
      final Sdk sdk = PythonSdkType.findPythonSdk(module);
      myModuleSdks.put(module, sdk);
      if (sdk != null && !usedSdks.contains(sdk)) {
        usedSdks.add(sdk);
        updateSdkLanguageLevel(project, sdk);
      }
    }
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

  public LanguageLevel getImmediateValue(@NotNull Project project, @Nullable VirtualFile file) {
    if (ApplicationManager.getApplication().isUnitTestMode() && LanguageLevel.FORCE_LANGUAGE_LEVEL != null) {
      return LanguageLevel.FORCE_LANGUAGE_LEVEL;
    }
    if (file == null) return null;

    final Module module = ModuleUtil.findModuleForFile(file, project);
    if (module != null) {
      return getImmediateValue(module);
    }
    final Sdk sdk = findSdk(project, file);
    if (sdk != null) {
      return PythonSdkType.getLanguageLevelForSdk(sdk);
    }
    return null;
  }

  @Nullable
  private static Sdk findSdk(Project project, VirtualFile file) {
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

    final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
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

  public void persistAttribute(@NotNull VirtualFile fileOrDir, @NotNull LanguageLevel level) throws IOException {
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

    for (VirtualFile child : fileOrDir.getChildren()) {
      if (!child.isDirectory() && PythonFileType.INSTANCE.equals(child.getFileType())) {
        FileBasedIndex.getInstance().requestReindex(child);
      }
    }
  }

  public void afterRootsChanged(@NotNull Project project) {
    Set<Sdk> updatedSdks = new HashSet<Sdk>();
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    boolean needReparseOpenFiles = false;
    for (Module module : modules) {
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
    if (needReparseOpenFiles) {
      FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
    }
  }

  private void updateSdkLanguageLevel(Project project, Sdk sdk) {
    final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
    final VirtualFile[] files = sdk.getRootProvider().getFiles(OrderRootType.CLASSES);
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
  }

  private void markRecursively(final Project project,
                               @NotNull VirtualFile file,
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
          PushedFilePropertiesUpdater.findAndUpdateValue(project, file, PythonLanguageLevelPusher.this, languageLevel);
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
}
