// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.projectView;

import com.intellij.ide.favoritesTreeView.FavoriteNodeProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.uiDesigner.GuiFormFileType;
import com.intellij.uiDesigner.compiler.Utils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class UIDesignerFavoriteNodeProvider extends FavoriteNodeProvider {
  @Override
  public @Nullable Collection<AbstractTreeNode<?>> getFavoriteNodes(DataContext context, final @NotNull ViewSettings viewSettings) {
    Project project = CommonDataKeys.PROJECT.getData(context);
    if (project == null) return null;
    Form[] forms = Form.DATA_KEY.getData(context);
    if (forms != null) {
      Collection<AbstractTreeNode<?>> result = new ArrayList<>();
      Set<PsiClass> bindClasses = new HashSet<>();
      for (Form form: forms) {
        final PsiClass classToBind = form.getClassToBind();
        if (bindClasses.contains(classToBind)) continue;
        bindClasses.add(classToBind);
        result.add(FormNode.constructFormNode(classToBind, project, viewSettings));
      }
      if (!result.isEmpty()) {
        return result;
      }
    }

    VirtualFile vFile = CommonDataKeys.VIRTUAL_FILE.getData(context);
    if (vFile != null) {
      final FileType fileType = vFile.getFileType();
      if (fileType.equals(GuiFormFileType.INSTANCE)) {
        final PsiFile formFile = PsiManager.getInstance(project).findFile(vFile);
        if (formFile == null) return null;
        String text = formFile.getText();
        String className;
        try {
          className = Utils.getBoundClassName(text);
        }
        catch (Exception e) {
          return null;
        }
        if (className == null) return null;
        final PsiClass classToBind = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
        if (classToBind != null) {
          Form form = new Form(classToBind);
          final AbstractTreeNode<?> node = new FormNode(project, form, viewSettings);
          return Collections.singletonList(node);
        }
      }
    }

    return null;
  }

  @Override
  public boolean elementContainsFile(final Object element, final VirtualFile vFile) {
    if (element instanceof Form form){
      return form.containsFile(vFile);
    }
    return false;
  }

  @Override
  public int getElementWeight(final Object element, final boolean isSortByType) {
    if (element instanceof Form) return 9;
    return -1;
  }

  @Override
  public @Nullable String getElementLocation(final Object element) {
    if (element instanceof Form) {
      final PsiFile[] psiFiles = ((Form)element).getFormFiles();
      VirtualFile vFile = null;
      if (psiFiles.length > 0) {
        vFile = psiFiles [0].getVirtualFile();
      }
      if (vFile != null) {
        return vFile.getPresentableUrl();
      }
    }
    return null;
  }

  @Override
  public boolean isInvalidElement(Object element) {
    if (element instanceof Form) {
      return !((Form) element).isValid();
    }
    return false;
  }

  @Override
  public @NotNull
  @NonNls String getFavoriteTypeId() {
    return "form";
  }

  @Override
  public @Nullable
  @NonNls String getElementUrl(Object element) {
    if (element instanceof Form form) {
      return form.getClassToBind().getQualifiedName();
    }
    return null;
  }

  @Override
  public String getElementModuleName(final Object element) {
    if (element instanceof Form form) {
      final Module module = ModuleUtil.findModuleForPsiElement(form.getClassToBind());
      return module != null ? module.getName() : null;
    }
    return null;
  }

  @Override
  public Object[] createPathFromUrl(final Project project, final String url, final String moduleName) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final PsiClass classToBind = JavaPsiFacade.getInstance(psiManager.getProject()).findClass(url, GlobalSearchScope.allScope(project));
    if (classToBind == null) return null;
    return new Object[] { new Form(classToBind) };
  }
}
