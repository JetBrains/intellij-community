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
package com.intellij.xml.index;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public enum ResourceRelevance {

  NONE,
  STANDARD,
  LIBRARY,
  SOURCE,
  MAPPED;

  public static ResourceRelevance getRelevance(VirtualFile file, @Nullable Module module, ProjectFileIndex fileIndex) {
    if (module != null) {
      Module moduleForFile = fileIndex.getModuleForFile(file);
      if (moduleForFile != null) { // in module content
        return module.equals(moduleForFile) || ModuleManager.getInstance(module.getProject()).isModuleDependent(module, moduleForFile) ? SOURCE : NONE;
      }
    }
    if (fileIndex.isInLibraryClasses(file)) {
      List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(file);
      if (orderEntries.isEmpty()) {
        return NONE;
      }
      if (module != null) {
        for (OrderEntry orderEntry : orderEntries) {
          Module ownerModule = orderEntry.getOwnerModule();
          if (ownerModule.equals(module)) {
            return LIBRARY;
          }
        }
      }
    }
    ExternalResourceManagerImpl resourceManager = (ExternalResourceManagerImpl)ExternalResourceManager.getInstance();
    if (resourceManager.isUserResource(file)) {
      return MAPPED;
    }
    if (ExternalResourceManagerImpl.isStandardResource(file)) {
      return STANDARD;
    }
    return NONE;
  }
}
