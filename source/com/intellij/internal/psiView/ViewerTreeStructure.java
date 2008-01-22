/**
 * class ViewerTreeStructure
 * created Aug 25, 2001
 * @author Jeka
 */
package com.intellij.internal.psiView;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ViewerTreeStructure extends AbstractTreeStructure {

  private boolean myShowWhiteSpaces = true;
  private boolean myShowTreeNodes = true;

  private Project myProject;
  private PsiElement myRootPsiElement = null;
  private Object myRootElement = new Object();

  public ViewerTreeStructure(Project project) {
    myProject = project;
  }

  public void setRootPsiElement(PsiElement rootPsiElement) {
    myRootPsiElement = rootPsiElement;
  }

  public PsiElement getRootPsiElement() {
    return myRootPsiElement;
  }

  public Object getRootElement() {
    return myRootElement;
  }

  public Object[] getChildElements(final Object element) {
    if (myRootElement == element) {
      if (myRootPsiElement == null) {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
      }
      return myRootPsiElement instanceof PsiFile ? ((PsiFile)myRootPsiElement).getPsiRoots() : new Object[]{myRootPsiElement};
    }
    final Object[][] children = new Object[1][];
    children[0] = ArrayUtil.EMPTY_OBJECT_ARRAY;
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final Object[] result;
        if (myShowTreeNodes) {
          final ArrayList<Object> list = new ArrayList<Object>();
          final ASTNode root = element instanceof PsiElement? SourceTreeToPsiMap.psiElementToTree((PsiElement)element) :
                               element instanceof ASTNode? (ASTNode)element : null;
          assert root != null;
          if (root instanceof CompositeElement) {
            ChameleonTransforming.transformChildren(root);
            ASTNode child = root.getFirstChildNode();
            while (child != null) {
              if (myShowWhiteSpaces || child.getElementType() != TokenType.WHITE_SPACE) {
                final PsiElement childElement = child.getPsi();
                list.add(childElement == null ? child : childElement);
              }
              child = child.getTreeNext();
            }
          }
          result = list.toArray(new Object[list.size()]);
        }
        else {
          final PsiElement[] elementChildren = ((PsiElement)element).getChildren();
          if (!myShowWhiteSpaces) {
            final List<PsiElement> childrenList = new ArrayList<PsiElement>(elementChildren.length);
            for (PsiElement psiElement : elementChildren) {
              if (!myShowWhiteSpaces && psiElement instanceof PsiWhiteSpace) {
                continue;
              }
              childrenList.add(psiElement);
            }
            result = childrenList.toArray(new PsiElement[childrenList.size()]);
          }
          else {
            result = elementChildren;
          }
        }
        children[0] = result;
      }
    });
    return children[0];
  }

  public Object getParentElement(Object element) {
    return null;
    //if (element == myRootElement) {
    //  return null;
    //}
    //if (element == myRootPsiElement) {
    //  return myRootElement;
    //}
    //PsiElement parent = ((PsiElement)element).getParent();
    //return parent;
  }

  public void commit() {
  }

  public boolean hasSomethingToCommit() {
    return false;
  }

  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    if (element == myRootElement) {
      return new NodeDescriptor(myProject, null) {
        public boolean update() {
          return false;
        }
        public Object getElement() {
          return myRootElement;
        }
      };
    }
    return new ViewerNodeDescriptor(myProject, element, parentDescriptor);
  }

  public boolean isShowWhiteSpaces() {
    return myShowWhiteSpaces;
  }

  public void setShowWhiteSpaces(boolean showWhiteSpaces) {
    myShowWhiteSpaces = showWhiteSpaces;
  }

  public boolean isShowTreeNodes() {
    return myShowTreeNodes;
  }

  public void setShowTreeNodes(final boolean showTreeNodes) {
    myShowTreeNodes = showTreeNodes;
  }
}
