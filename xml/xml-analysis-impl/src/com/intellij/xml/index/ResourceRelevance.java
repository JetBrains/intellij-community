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
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public enum ResourceRelevance {

  STANDARD,
  LIBRARY,
  SOURCE,
  MAPPED;

  public static ResourceRelevance getRelevance(VirtualFile resource,
                                               @Nullable Module module,
                                               ProjectFileIndex fileIndex,
                                               @Nullable GlobalSearchScope additionalScope) {
    boolean inTest = fileIndex.isInTestSourceContent(resource);
    if (module != null) {
      GlobalSearchScope scope = module.getModuleRuntimeScope(inTest);
      Module resourceModule = fileIndex.getModuleForFile(resource);
      if (resourceModule != null &&
          (resourceModule == module || scope.isSearchInModuleContent(resourceModule)) ||
        scope.contains(resource) || (additionalScope != null && additionalScope.contains(resource))) {
        return inTest || fileIndex.isInSource(resource) ? SOURCE : LIBRARY;
      }
    }
    else if (inTest ||  fileIndex.isInSource(resource)) {
      return SOURCE;
    }
    else if (fileIndex.isInLibraryClasses(resource)) {
      return LIBRARY;
    }
    ExternalResourceManagerEx resourceManager = (ExternalResourceManagerEx)ExternalResourceManager.getInstance();
    return resourceManager.isUserResource(resource) ? MAPPED : STANDARD;
  }
}
