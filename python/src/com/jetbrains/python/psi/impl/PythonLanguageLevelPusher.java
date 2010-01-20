/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.util.FileContentUtil;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.messages.MessageBus;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PythonLanguageLevelPusher implements FilePropertyPusher<LanguageLevel> {
  public static LanguageLevel FORCE_LANGUAGE_LEVEL = null;
  private final Map<Module, Sdk> myModuleSdks = new WeakHashMap<Module, Sdk>();

  public static void pushLanguageLevel(final Project project) {
    PushedFilePropertiesUpdater.getInstance(project).pushAll(new PythonLanguageLevelPusher());
  }

  public void initExtra(Project project, MessageBus bus, Engine languageLevelUpdater) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    for (Module module : modules) {
      myModuleSdks.put(module, PythonSdkType.findPythonSdk(module));
    }
  }

  @NotNull
  public Key<LanguageLevel> getFileDataKey() {
    return LanguageLevel.KEY;
  }

  public boolean pushDirectoriesOnly() {
    return true;
  }

  public LanguageLevel getDefaultValue() {
    return LanguageLevel.getDefault();
  }

  public LanguageLevel getImmediateValue(Project project, VirtualFile file) {
    if (ApplicationManager.getApplication().isUnitTestMode() && FORCE_LANGUAGE_LEVEL != null) {
      return FORCE_LANGUAGE_LEVEL;
    }

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

  public LanguageLevel getImmediateValue(Module module) {
    if (ApplicationManager.getApplication().isUnitTestMode() && FORCE_LANGUAGE_LEVEL != null) {
      return FORCE_LANGUAGE_LEVEL;
    }

    final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    return PythonSdkType.getLanguageLevelForSdk(sdk);
  }

  public boolean acceptsFile(VirtualFile file) {
    return false;
  }

  private static final FileAttribute PERSISTENCE = new FileAttribute("python_language_level_persistence", 1);

  public void persistAttribute(VirtualFile fileOrDir, LanguageLevel level) throws IOException {
    final DataInputStream iStream = PERSISTENCE.readAttribute(fileOrDir);
    if (iStream != null) {
      try {
        final int oldLevelOrdinal = iStream.readInt();
        if (oldLevelOrdinal == level.ordinal()) return;
      }
      finally {
        iStream.close();
      }
    }

    final DataOutputStream oStream = PERSISTENCE.writeAttribute(fileOrDir);
    oStream.writeInt(level.ordinal());
    oStream.close();

    for (VirtualFile child : fileOrDir.getChildren()) {
      if (!child.isDirectory() && PythonFileType.INSTANCE.equals(child.getFileType())) {
        FileBasedIndex.getInstance().requestReindex(child);
      }
    }
  }

  public void afterRootsChanged(Project project) {
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
    }
    if (needReparseOpenFiles) {
      FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
    }
  }
}