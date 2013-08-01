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
package org.jetbrains.idea.maven.vfs;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public class MavenGotoPropertyFileContributor implements ChooseByNameContributor {
  @NotNull
  public String[] getNames(Project project, boolean includeNonProjectItems) {
    return MavenPropertiesVirtualFileSystem.PROPERTIES_FILES;
  }

  @NotNull
  public NavigationItem[] getItemsByName(String name, String pattern, Project project, boolean includeNonProjectItems) {
    if (!includeNonProjectItems) return NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY;
    VirtualFile file = MavenPropertiesVirtualFileSystem.getInstance().findFileByPath(name);
    if (file != null) {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile != null) return new NavigationItem[]{psiFile};
    }
    return NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY;
  }
}
