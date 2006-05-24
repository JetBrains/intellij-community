/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.binding;

import com.intellij.ide.favoritesTreeView.FavoriteNodeProvider;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.ide.projectView.impl.nodes.Form;
import com.intellij.ide.projectView.impl.nodes.FormNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

/**
 * @author yole
 */
public class UIDesignerFavoriteNodeProvider implements ApplicationComponent, FavoriteNodeProvider {
  @Nullable
  public Collection<AbstractTreeNode> getFavoriteNodes(DataContext context, final ViewSettings viewSettings) {
    Form[] forms = (Form[]) context.getData(DataConstantsEx.GUI_DESIGNER_FORM_ARRAY);
    if (forms != null) {
      Collection<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
      Set<PsiClass> bindClasses = new HashSet<PsiClass>();
      for (Form form: forms) {
        final PsiClass classToBind = form.getClassToBind();
        if (classToBind != null) {
          if (bindClasses.contains(classToBind)) continue;
          bindClasses.add(classToBind);
          result.add(FormNode.constructFormNode(classToBind, (Project) context.getData(DataConstants.PROJECT), viewSettings));
        }
      }
      if (!result.isEmpty()) {
        return result;
      }
    }
    return null;
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "UIDesignerFavoriteNodeProvider";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
