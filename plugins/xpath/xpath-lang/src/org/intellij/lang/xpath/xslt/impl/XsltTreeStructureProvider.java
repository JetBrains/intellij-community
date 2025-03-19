// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.xpath.xslt.impl;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.ui.LayeredIcon;
import icons.XpathIcons;
import org.intellij.lang.xpath.xslt.XsltConfig;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.associations.FileAssociationsManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

final class XsltTreeStructureProvider implements TreeStructureProvider {
  private final Project myProject;

  XsltTreeStructureProvider(Project project) {
    myProject = project;
  }

  @Override
  @SuppressWarnings({"RawUseOfParameterizedType"})
  public @NotNull Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> parent, @NotNull Collection<AbstractTreeNode<?>> children, ViewSettings settings) {
    Collection<AbstractTreeNode<?>> l = children;
    int i = 0;
    for (AbstractTreeNode<?> o : children) {
      if (o instanceof ProjectViewNode) {
        ProjectViewNode<?> node = (ProjectViewNode)o;
        Object element = node.getValue();
        if (element instanceof PsiFile) {
          ProgressManager.checkCanceled();
          if (XsltSupport.isXsltFile((PsiFile)element)) {
            if (l == children && l.getClass() != ArrayList.class) {
              l = new ArrayList<>(children);
            }
            final XsltFileNode fileNode = new XsltFileNode(myProject, (PsiFile)element, settings);
            ((List<AbstractTreeNode<?>>)l).set(i, fileNode);
          }
        }
      }
      i++;
    }
    return l;
  }

  private static class XsltFileNode extends PsiFileNode {
    private final FileAssociationsManager myInstance;
    private final XsltConfig myConfig;

    XsltFileNode(Project project, @NotNull PsiFile psiFile, ViewSettings viewSettings) {
      super(project, psiFile, viewSettings);
      myInstance = FileAssociationsManager.getInstance(myProject);
      myConfig = XsltConfig.getInstance();
    }

    @Override
    public void updateImpl(@NotNull PresentationData presentationData) {
      super.updateImpl(presentationData);
      final PsiFile[] psiFiles = myInstance.getAssociationsFor(getValue());

      Icon icon = XsltSupport.createXsltIcon(presentationData.getIcon(false));
      if (psiFiles.length > 0) {
        icon = LayeredIcon.create(icon, XpathIcons.Association_small);
      }
      presentationData.setIcon(icon);
    }

    @Override
    public boolean expandOnDoubleClick() {
      return false;
    }

    @Override
    @SuppressWarnings({"RawUseOfParameterizedType"})
    public Collection<AbstractTreeNode<?>> getChildrenImpl() {
      if (myConfig.isShowLinkedFiles()) {
        final PsiFile[] psiFiles = myInstance.getAssociationsFor(getValue());
        if (psiFiles.length > 0) {
          return ProjectViewNode.wrap(Arrays.asList(psiFiles), myProject, PsiFileNode.class, getSettings());
        }
      }
      return super.getChildrenImpl();
    }
  }
}
