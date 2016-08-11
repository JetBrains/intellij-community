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
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class FormNode extends ProjectViewNode<Form>{
  private final Collection<BasePsiNode<? extends PsiElement>> myChildren;

  public FormNode(Project project, Object value, ViewSettings viewSettings) {
    this(project, (Form)value, viewSettings, getChildren(project, (Form) value, viewSettings));
  }

  public FormNode(Project project, Form value, ViewSettings viewSettings, Collection<BasePsiNode<? extends PsiElement>> children) {
    super(project, value, viewSettings);
    myChildren = children;
  }

  @NotNull
  public Collection<BasePsiNode<? extends PsiElement>> getChildren() {
    return myChildren;
  }

  public String getTestPresentation() {
    return "Form:" + getValue().getName();
  }

  public boolean contains(@NotNull VirtualFile file) {
    for (final AbstractTreeNode aMyChildren : myChildren) {
      ProjectViewNode treeNode = (ProjectViewNode)aMyChildren;
      if (treeNode.contains(file)) return true;
    }
    return false;
  }

  public void update(PresentationData presentation) {
    if (getValue() == null || !getValue().isValid()) {
      setValue(null);
    } else {
      presentation.setPresentableText(getValue().getName());
      presentation.setIcon(StdFileTypes.GUI_DESIGNER_FORM.getIcon());
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

  public void navigate(final boolean requestFocus) {
    getValue().navigate(requestFocus);
  }

  public boolean canNavigate() {
    final Form value = getValue();
    return value != null && value.canNavigate();
  }

  public boolean canNavigateToSource() {
    final Form value = getValue();
    return value != null && value.canNavigateToSource();
  }

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
  public boolean canHaveChildrenMatching(final Condition<PsiFile> condition) {
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
