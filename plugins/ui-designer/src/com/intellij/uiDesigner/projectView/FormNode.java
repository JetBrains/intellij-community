// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.projectView;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.BasePsiNode;
import com.intellij.ide.projectView.impl.nodes.ClassTreeNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.navigation.NavigationItemFileStatus;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.uiDesigner.GuiFormFileType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class FormNode extends ProjectViewNode<Form>{
  private final Collection<BasePsiNode<? extends PsiElement>> myChildren;

  public FormNode(Project project, @NotNull Object value, ViewSettings viewSettings) {
    this(project, (Form)value, viewSettings, getChildren(project, (Form) value, viewSettings));
  }

  public FormNode(Project project, @NotNull Form value, ViewSettings viewSettings, Collection<BasePsiNode<? extends PsiElement>> children) {
    super(project, value, viewSettings);
    myChildren = children;
  }

  @Override
  public @NotNull Collection<BasePsiNode<? extends PsiElement>> getChildren() {
    return myChildren;
  }

  @Override
  public String getTestPresentation() {
    return "Form:" + getValue().getName();
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    for (final AbstractTreeNode aMyChildren : myChildren) {
      ProjectViewNode treeNode = (ProjectViewNode)aMyChildren;
      if (treeNode.contains(file)) return true;
    }
    return false;
  }

  @Override
  public void update(@NotNull PresentationData presentation) {
    if (getValue() == null || !getValue().isValid()) {
      setValue(null);
    } else {
      presentation.setPresentableText(getValue().getName());
      presentation.setIcon(GuiFormFileType.INSTANCE.getIcon());
    }
  }

  @Override
  protected boolean shouldPostprocess() {
    return true;
  }

  @Override
  public boolean someChildContainsFile(VirtualFile file) {
    return contains(file);
  }

  @Override
  public void navigate(final boolean requestFocus) {
    getValue().navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    final Form value = getValue();
    return value != null && value.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    final Form value = getValue();
    return value != null && value.canNavigateToSource();
  }

  @Override
  public String getToolTip() {
    return IdeBundle.message("tooltip.ui.designer.form");
  }

  @Override
  public FileStatus getFileStatus() {
    for(BasePsiNode<? extends PsiElement> child: myChildren) {
      final PsiElement value = child.getValue();
      if (value == null || !value.isValid()) continue;
      final FileStatus fileStatus = NavigationItemFileStatus.get(child);
      if (fileStatus != FileStatus.NOT_CHANGED) {
        return fileStatus;
      }
    }
    return FileStatus.NOT_CHANGED;
  }

  @Override
  public boolean canHaveChildrenMatching(final Condition<? super PsiFile> condition) {
    for(BasePsiNode<? extends PsiElement> child: myChildren) {
      if (condition.value(child.getValue().getContainingFile())) {
        return true;
      }
    }
    return false;
  }

  public static AbstractTreeNode constructFormNode(final PsiClass classToBind, final Project project, final ViewSettings settings) {
    final Form form = new Form(classToBind);
    final Collection<BasePsiNode<? extends PsiElement>> children = getChildren(project, form, settings);
    return new FormNode(project, form, settings, children);
  }

  private static Collection<BasePsiNode<? extends PsiElement>> getChildren(final Project project, final Form form, final ViewSettings settings) {
    final Set<BasePsiNode<? extends PsiElement>> children = new LinkedHashSet<>();
    children.add(new ClassTreeNode(project, form.getClassToBind(), settings));
    for (PsiFile formBoundToClass : form.getFormFiles()) {
      children.add(new PsiFileNode(project, formBoundToClass, settings));
    }
    return children;
  }
}
