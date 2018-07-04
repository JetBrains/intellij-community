/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ResourcesFavoriteNodeProvider extends FavoriteNodeProvider {

  @Override
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

  @Override
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

  @Override
  public int getElementWeight(final Object element, final boolean isSortByType) {
    return -1;
  }

  @Override
  public String getElementLocation(final Object element) {
    return null;
  }

  @Override
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

  @Override
  @NotNull
  public String getFavoriteTypeId() {
    return "resource_bundle";
  }

  @Override
  public String getElementUrl(final Object element) {
    if (element instanceof ResourceBundleImpl) {
      return ((ResourceBundleImpl)element).getUrl();
    }
    else if (element instanceof PsiFile[]) {
      PsiFile[] files = (PsiFile[])element;

      ResourceBundle bundle = null;
      for (PsiFile file : files) {
        PropertiesFile propertiesFile = PropertiesImplUtil.getPropertiesFile(file);
        if (propertiesFile == null) return null;
        ResourceBundle currentBundle = propertiesFile.getResourceBundle();
        if (bundle == null) {
          bundle = currentBundle;
        }
        else if (!PsiManager.getInstance(bundle.getProject()).areElementsEquivalent(bundle.getDefaultPropertiesFile().getContainingFile(),
                                                                                    currentBundle.getDefaultPropertiesFile().getContainingFile())) {

          return null;
        }
      }
      return getElementUrl(bundle);
    }
    return null;
  }

  @Override
  public String getElementModuleName(final Object element) {
    return null;
  }

  @Override
  public Object[] createPathFromUrl(final Project project, final String url, final String moduleName) {
    return new Object[]{PropertiesImplUtil.createByUrl(url, project)};
  }
}
