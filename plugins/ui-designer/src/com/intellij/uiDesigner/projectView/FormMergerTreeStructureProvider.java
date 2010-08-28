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

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.BasePsiNode;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.actions.MoveAction;
import com.intellij.uiDesigner.binding.FormClassIndex;
import com.intellij.util.containers.ContainerUtil;

import java.util.*;

public class FormMergerTreeStructureProvider implements TreeStructureProvider {
  private final Project myProject;

  public FormMergerTreeStructureProvider(Project project) {
    myProject = project;
  }

  public Collection<AbstractTreeNode> modify(AbstractTreeNode parent, Collection<AbstractTreeNode> children, ViewSettings settings) {
    if (parent.getValue() instanceof Form) return children;

    // Optimization. Check if there are any forms at all.
    boolean formsFound = false;
    for (AbstractTreeNode node : children) {
      if (node.getValue() instanceof PsiFile) {
        PsiFile file = (PsiFile)node.getValue();
        if (file.getFileType() == StdFileTypes.GUI_DESIGNER_FORM) {
          formsFound = true;
          break;
        }
      }
    }

    if (!formsFound) return children;

    Collection<AbstractTreeNode> result = new LinkedHashSet<AbstractTreeNode>(children);
    ProjectViewNode[] copy = children.toArray(new ProjectViewNode[children.size()]);
    for (ProjectViewNode element : copy) {
      if (element.getValue() instanceof PsiClass) {
        PsiClass aClass = (PsiClass)element.getValue();
        final String qName = aClass.getQualifiedName();
        if (qName == null) continue;
        List<PsiFile> forms;
        try {
          forms = FormClassIndex.findFormsBoundToClass(myProject, qName);
        }
        catch (ProcessCanceledException e) {
          continue;
        }
        Collection<BasePsiNode<? extends PsiElement>> formNodes = findFormsIn(children, forms);
        if (!formNodes.isEmpty()) {
          Collection<PsiFile> formFiles = convertToFiles(formNodes);
          Collection<BasePsiNode<? extends PsiElement>> subNodes = new ArrayList<BasePsiNode<? extends PsiElement>>();
          //noinspection unchecked
          subNodes.add((BasePsiNode<? extends PsiElement>) element);
          subNodes.addAll(formNodes);
          result.add(new FormNode(myProject, new Form(aClass, formFiles), settings, subNodes));
          result.remove(element);
          result.removeAll(formNodes);
        }
      }
    }
    return result;
  }

  public Object getData(Collection<AbstractTreeNode> selected, String dataId) {
    if (selected != null) {
      if (Form.DATA_KEY.is(dataId)) {
        List<Form> result = new ArrayList<Form>();
        for(AbstractTreeNode node: selected) {
          if (node.getValue() instanceof Form) {
            result.add((Form) node.getValue());
          }
        }
        if (!result.isEmpty()) {
          return result.toArray(new Form[result.size()]);
        }
      }
      else if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
        for(AbstractTreeNode node: selected) {
          if (node.getValue() instanceof Form) {
            return new MyDeleteProvider(selected);
          }
        }
      }
    }
    return null;
  }

  private static Collection<PsiFile> convertToFiles(Collection<BasePsiNode<? extends PsiElement>> formNodes) {
    ArrayList<PsiFile> psiFiles = new ArrayList<PsiFile>();
    for (AbstractTreeNode treeNode : formNodes) {
      psiFiles.add((PsiFile)treeNode.getValue());
    }
    return psiFiles;
  }

  private static Collection<BasePsiNode<? extends PsiElement>> findFormsIn(Collection<AbstractTreeNode> children, List<PsiFile> forms) {
    if (children.isEmpty() || forms.isEmpty()) return Collections.emptyList();
    ArrayList<BasePsiNode<? extends PsiElement>> result = new ArrayList<BasePsiNode<? extends PsiElement>>();
    HashSet<PsiFile> psiFiles = new HashSet<PsiFile>(forms);
    for (final AbstractTreeNode child : children) {
      if (child instanceof BasePsiNode) {
        //noinspection unchecked
        BasePsiNode<? extends PsiElement> treeNode = (BasePsiNode<? extends PsiElement>)child;
        //noinspection SuspiciousMethodCalls
        if (psiFiles.contains(treeNode.getValue())) result.add(treeNode);
      }
    }
    return result;
  }

  private static class MyDeleteProvider implements DeleteProvider {
    private final PsiElement[] myElements;

    public MyDeleteProvider(final Collection<AbstractTreeNode> selected) {
      myElements = collectFormPsiElements(selected);
    }

    public void deleteElement(DataContext dataContext) {
      Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      DeleteHandler.deletePsiElement(myElements, project);
    }

    public boolean canDeleteElement(DataContext dataContext) {
      return DeleteHandler.shouldEnableDeleteAction(myElements);
    }

    private static PsiElement[] collectFormPsiElements(Collection<AbstractTreeNode> selected) {
      Set<PsiElement> result = new HashSet<PsiElement>();
      for(AbstractTreeNode node: selected) {
        if (node.getValue() instanceof Form) {
          Form form = (Form)node.getValue();
          result.add(form.getClassToBind());
          ContainerUtil.addAll(result, form.getFormFiles());
        }
        else if (node.getValue() instanceof PsiElement) {
          result.add((PsiElement) node.getValue());
        }
      }
      return result.toArray(new PsiElement[result.size()]);
    }
  }
}
