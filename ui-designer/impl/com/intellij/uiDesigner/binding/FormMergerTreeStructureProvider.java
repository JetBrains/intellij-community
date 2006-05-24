package com.intellij.uiDesigner.binding;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.Form;
import com.intellij.ide.projectView.impl.nodes.FormNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class FormMergerTreeStructureProvider implements TreeStructureProvider, ProjectComponent{
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

    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    ProjectViewNode[] copy = children.toArray(new ProjectViewNode[children.size()]);
    for (ProjectViewNode element : copy) {
      if (element.getValue() instanceof PsiClass) {
        PsiClass aClass = ((PsiClass)element.getValue());
        PsiFile[] forms = aClass.getManager().getSearchHelper().findFormsBoundToClass(aClass.getQualifiedName());
        Collection<AbstractTreeNode> formNodes = findFormsIn(children, forms);
        if (formNodes.size() > 0) {
          Collection<PsiFile> formFiles = convertToFiles(formNodes);
          Collection<AbstractTreeNode> subNodes = new ArrayList<AbstractTreeNode>(formNodes);
          subNodes.add(element);
          result.add(new FormNode(myProject, new Form(aClass, formFiles), settings, subNodes));
          children.remove(element);
          children.removeAll(formNodes);
        }
      }
    }
    result.addAll(children);
    return result;
  }

  public Object getData(Collection<AbstractTreeNode> selected, String dataId) {
    if (dataId.equals(DataConstantsEx.GUI_DESIGNER_FORM_ARRAY)) {
      List<Form> result = new ArrayList<Form>();
      for(AbstractTreeNode node: selected) {
        if (node.getValue() instanceof Form) {
          result.add((Form) node.getValue());
        }
      }
      return result.toArray(new Form[result.size()]);
    }
    return null;
  }

  private static Collection<PsiFile> convertToFiles(Collection<AbstractTreeNode> formNodes) {
    ArrayList<PsiFile> psiFiles = new ArrayList<PsiFile>();
    for (AbstractTreeNode treeNode : formNodes) {
      psiFiles.add((PsiFile)treeNode.getValue());
    }
    return psiFiles;
  }

  private static Collection<AbstractTreeNode> findFormsIn(Collection<AbstractTreeNode> children, PsiFile[] forms) {
    ArrayList<AbstractTreeNode> result = new ArrayList<AbstractTreeNode>();
    HashSet<PsiFile> psiFiles = new HashSet<PsiFile>(Arrays.asList(forms));
    for (final AbstractTreeNode aChildren : children) {
      ProjectViewNode treeNode = (ProjectViewNode)aChildren;
      //noinspection SuspiciousMethodCalls
      if (psiFiles.contains(treeNode.getValue())) result.add(treeNode);
    }
    return result;
  }

  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "FormNodesProvider";
  }

  public void initComponent() {
  }
}

