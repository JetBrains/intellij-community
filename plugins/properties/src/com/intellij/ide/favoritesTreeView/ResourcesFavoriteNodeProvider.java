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

/*
 * User: anna
 * Date: 21-Jan-2008
 */
package com.intellij.ide.favoritesTreeView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.ResourceBundleImpl;
import com.intellij.lang.properties.projectView.ResourceBundleNode;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ResourcesFavoriteNodeProvider extends FavoriteNodeProvider {
  private final Project myProject;

  public ResourcesFavoriteNodeProvider(Project project) {
    myProject = project;
  }

  public Collection<AbstractTreeNode> getFavoriteNodes(final DataContext context, final ViewSettings viewSettings) {
    final Project project = CommonDataKeys.PROJECT.getData(context);
    if (project == null) {
      return null;
    }
    final ResourceBundle[] resourceBundles = ResourceBundle.ARRAY_DATA_KEY.getData(context);
    //on bundles nodes
    if (resourceBundles != null) {
      final Collection<AbstractTreeNode> result = new ArrayList<>();
      for (ResourceBundle bundle : resourceBundles) {
        result.add(new ResourceBundleNode(project, bundle, viewSettings));
      }
      return result;
    }
    return null;
  }

  public boolean elementContainsFile(final Object element, final VirtualFile vFile) {
    if (element instanceof ResourceBundle) {
      ResourceBundle bundle = (ResourceBundle)element;
      final List<PropertiesFile> propertiesFiles = bundle.getPropertiesFiles();
      for (PropertiesFile file : propertiesFiles) {
        final VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) continue;
        if (vFile.getPath().equals(virtualFile.getPath())) {
          return true;
        }
      }
    }
    return false;
  }

  public int getElementWeight(final Object element, final boolean isSortByType) {
    return -1;
  }

  public String getElementLocation(final Object element) {
    return null;
  }

  public boolean isInvalidElement(final Object element) {
    if (element instanceof ResourceBundle) {
      ResourceBundle resourceBundle = (ResourceBundle)element;
      List<PropertiesFile> propertiesFiles = resourceBundle.getPropertiesFiles();
      if (propertiesFiles.size() == 1) {
        //todo result.add(new PsiFileNode(myProject, propertiesFiles.iterator().next(), this));
        return true;
      }
    }
    return false;
  }

  @NotNull
  public String getFavoriteTypeId() {
    return "resource_bundle";
  }

  public String getElementUrl(final Object element) {
    if (element instanceof ResourceBundleImpl) {
      return ((ResourceBundleImpl)element).getUrl();
    }
    return null;
  }

  public String getElementModuleName(final Object element) {
    return null;
  }

  public Object[] createPathFromUrl(final Project project, final String url, final String moduleName) {
    return new Object[]{PropertiesImplUtil.createByUrl(url, project)};
  }
}
