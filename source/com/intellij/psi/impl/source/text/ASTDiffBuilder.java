/*
 * @author max
 */
package com.intellij.psi.impl.source.text;

import com.intellij.lang.ASTNode;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.impl.ChangeInfoImpl;
import com.intellij.pom.tree.events.impl.ReplaceChangeInfoImpl;
import com.intellij.pom.tree.events.impl.TreeChangeEventImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.parsing.ChameleonTransforming;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.util.diff.DiffTreeChangeBuilder;

public class ASTDiffBuilder implements DiffTreeChangeBuilder<ASTNode, ASTNode> {
  private final RepositoryManager myRepositoryManager;
  private final TreeChangeEventImpl myEvent;
  private final PsiFileImpl myFile;
  private final PsiManagerEx myPsiManager;
  private final boolean myIsPhysicalScope;


  public ASTDiffBuilder(final PsiFileImpl fileImpl) {
    myFile = fileImpl;
    myIsPhysicalScope = fileImpl.isPhysical();
    myPsiManager = (PsiManagerEx)fileImpl.getManager();
    myRepositoryManager = myPsiManager.getRepositoryManager();
    myEvent = new TreeChangeEventImpl(fileImpl.getProject().getModel().getModelAspect(TreeAspect.class), fileImpl.getTreeElement());
  }

  public void nodeReplaced(ASTNode oldNode, final ASTNode newNode) {
    if (oldNode instanceof FileElement && newNode instanceof FileElement) {
      BlockSupportImpl.replaceFileElement(myFile, (FileElement)oldNode, (FileElement)newNode, myPsiManager);
    }
    else {
      myRepositoryManager.beforeChildAddedOrRemoved(myFile, oldNode);

      TreeUtil.remove((TreeElement)newNode);
      TreeUtil.replaceWithList((TreeElement)oldNode, (TreeElement)newNode);

      final ReplaceChangeInfoImpl change = (ReplaceChangeInfoImpl)ChangeInfoImpl.create(ChangeInfo.REPLACE, newNode);

      if (oldNode instanceof ChameleonElement) {
        oldNode = ChameleonTransforming.transform((ChameleonElement)oldNode);
      }

      change.setReplaced(oldNode);
      myEvent.addElementaryChange(newNode, change);
      ((TreeElement)newNode).clearCaches();
      if (!(newNode instanceof FileElement)) {
        ((CompositeElement)newNode.getTreeParent()).subtreeChanged();
      }
      myRepositoryManager.beforeChildAddedOrRemoved(myFile, newNode);

      //System.out.println("REPLACED: " + oldNode + " to " + newNode);
    }
  }

  public void nodeDeleted(ASTNode parent, final ASTNode child) {
    myRepositoryManager.beforeChildAddedOrRemoved(myFile, parent);

    PsiElement psiParent = parent.getPsi();
    PsiElement psiChild = myIsPhysicalScope && !(child instanceof ChameleonElement) ? child.getPsi() : null;

    PsiTreeChangeEventImpl event = null;
    if (psiParent != null && psiChild != null) {
      event = new PsiTreeChangeEventImpl(myPsiManager);
      event.setParent(psiParent);
      event.setChild(psiChild);
      myPsiManager.beforeChildRemoval(event);
    }

    myEvent.addElementaryChange(child, ChangeInfoImpl.create(ChangeInfo.REMOVED, child));
    TreeUtil.remove((TreeElement)child);
    ((CompositeElement)parent).subtreeChanged();

    /*if (event != null) {
      myPsiManager.childRemoved(event);
    }*/

    //System.out.println("DELETED from " + parent + ": " + child);
  }

  public void nodeInserted(final ASTNode oldParent, final ASTNode node, final int pos) {
    myRepositoryManager.beforeChildAddedOrRemoved(myFile, oldParent);

    ASTNode anchor = null;
    for (int i = 0; i < pos; i++) {
      if (anchor == null) {
        anchor = oldParent.getFirstChildNode();
      }
      else {
        anchor = anchor.getTreeNext();
      }
    }

    TreeUtil.remove((TreeElement)node);
    if (anchor != null) {
      TreeUtil.insertAfter((TreeElement)anchor, (TreeElement)node);
    }
    else {
      if (oldParent.getFirstChildNode() != null) {
        TreeUtil.insertBefore((TreeElement)oldParent.getFirstChildNode(), (TreeElement)node);
      }
      else {
        TreeUtil.addChildren((CompositeElement)oldParent, (TreeElement)node);
      }
    }

    myEvent.addElementaryChange(node, ChangeInfoImpl.create(ChangeInfo.ADD, node));
    ((TreeElement)node).clearCaches();
    ((CompositeElement)oldParent).subtreeChanged();

    myRepositoryManager.beforeChildAddedOrRemoved(myFile, oldParent);
    //System.out.println("INSERTED to " + oldParent + ": " + node + " at " + pos);
  }

  public TreeChangeEventImpl getEvent() {
    return myEvent;
  }
}