// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.projectView;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.BasePsiNode;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.uiDesigner.GuiFormFileType;
import com.intellij.uiDesigner.binding.FormClassIndex;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class FormMergerTreeStructureProvider implements TreeStructureProvider {
  private final Project myProject;

  public FormMergerTreeStructureProvider(Project project) {
    myProject = project;
  }

  @Override
  public @NotNull Collection<AbstractTreeNode<?>> modify(@NotNull AbstractTreeNode<?> parent, @NotNull Collection<AbstractTreeNode<?>> children, ViewSettings settings) {
    if (parent.getValue() instanceof Form) {
      return children;
    }

    // Optimization. Check if there are any forms at all.
    boolean formsFound = false;
    for (AbstractTreeNode<?> node : children) {
      if (node.getValue() instanceof PsiFile file) {
        if (file.getFileType() == GuiFormFileType.INSTANCE) {
          formsFound = true;
          break;
        }
      }
    }

    if (!formsFound) {
      return children;
    }

    Collection<AbstractTreeNode<?>> result = new LinkedHashSet<>(children);
    ProjectViewNode<?>[] copy = children.toArray(new ProjectViewNode[0]);
    for (ProjectViewNode<?> element : copy) {
      PsiClass psiClass = null;
      if (element.getValue() instanceof PsiClass) {
        psiClass = (PsiClass)element.getValue();
      }
      else if (element.getValue() instanceof PsiClassOwner) {
        final PsiClass[] psiClasses = ((PsiClassOwner) element.getValue()).getClasses();
        if (psiClasses.length == 1) {
          psiClass = psiClasses[0];
        }
      }
      if (psiClass == null) continue;
      String qName = psiClass.getQualifiedName();
      if (qName == null) continue;
      List<PsiFile> forms = FormClassIndex.findFormsBoundToClass(myProject, qName);
      Collection<BasePsiNode<? extends PsiElement>> formNodes = findFormsIn(children, forms);
      if (!formNodes.isEmpty()) {
        Collection<PsiFile> formFiles = convertToFiles(formNodes);
        Collection<BasePsiNode<? extends PsiElement>> subNodes = new ArrayList<>();
        subNodes.add((BasePsiNode<? extends PsiElement>) element);
        subNodes.addAll(formNodes);
        result.add(new FormNode(myProject, new Form(psiClass, formFiles), settings, subNodes));
        result.remove(element);
        result.removeAll(formNodes);
      }
    }
    return result;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink,
                             @NotNull Collection<? extends AbstractTreeNode<?>> selection) {
    List<FormNode> nodes = ContainerUtil.filterIsInstance(selection, FormNode.class);
    if (nodes.isEmpty()) return;
    sink.lazy(Form.DATA_KEY, () -> {
      return ContainerUtil.map2Array(nodes, Form.class, o -> o.getValue());
    });
    sink.lazy(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, () -> {
      return new MyDeleteProvider(selection);
    });
  }

  private static Collection<PsiFile> convertToFiles(Collection<? extends BasePsiNode<? extends PsiElement>> formNodes) {
    List<PsiFile> psiFiles = new ArrayList<>();
    for (AbstractTreeNode<?> treeNode : formNodes) {
      psiFiles.add((PsiFile)treeNode.getValue());
    }
    return psiFiles;
  }

  private static Collection<BasePsiNode<? extends PsiElement>> findFormsIn(Collection<? extends AbstractTreeNode<?>> children, List<? extends PsiFile> forms) {
    if (children.isEmpty() || forms.isEmpty()) return Collections.emptyList();
    List<BasePsiNode<? extends PsiElement>> result = new ArrayList<>();
    Set<PsiFile> psiFiles = new HashSet<>(forms);
    for (final AbstractTreeNode<?> child : children) {
      if (child instanceof BasePsiNode<? extends PsiElement> treeNode) {
        if (psiFiles.contains(treeNode.getValue())) {
          result.add(treeNode);
        }
      }
    }
    return result;
  }

  private static final class MyDeleteProvider implements DeleteProvider {
    private final AbstractTreeNode<?>[] myNodes;

    MyDeleteProvider(Collection<? extends AbstractTreeNode<?>> nodes) {
      myNodes = nodes.toArray(AbstractTreeNode[]::new);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void deleteElement(@NotNull DataContext dataContext) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      PsiElement[] elements = collectFormPsiElements(myNodes);
      DeleteHandler.deletePsiElement(elements, project);
    }

    @Override
    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      PsiElement[] elements = collectFormPsiElements(myNodes);
      return DeleteHandler.shouldEnableDeleteAction(elements);
    }

    private static PsiElement[] collectFormPsiElements(AbstractTreeNode<?>[] selected) {
      Set<PsiElement> result = new HashSet<>();
      for(AbstractTreeNode<?> node: selected) {
        if (node.getValue() instanceof Form form) {
          result.add(form.getClassToBind());
          ContainerUtil.addAll(result, form.getFormFiles());
        }
        else if (node.getValue() instanceof PsiElement) {
          result.add((PsiElement) node.getValue());
        }
      }
      return PsiUtilCore.toPsiElementArray(result);
    }
  }
}
