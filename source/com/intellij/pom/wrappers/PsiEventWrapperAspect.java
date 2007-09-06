package com.intellij.pom.wrappers;

import com.intellij.lang.ASTNode;
import com.intellij.pom.PomModel;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.tree.TreeAspect;
import com.intellij.pom.tree.events.ChangeInfo;
import com.intellij.pom.tree.events.ReplaceChangeInfo;
import com.intellij.pom.tree.events.TreeChange;
import com.intellij.pom.tree.events.TreeChangeEvent;
import com.intellij.pom.tree.events.impl.ChangeInfoImpl;
import com.intellij.pom.tree.events.impl.TreeChangeImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.LeafElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class PsiEventWrapperAspect implements PomModelAspect{
  private final TreeAspect myTreeAspect;

  public PsiEventWrapperAspect(PomModel model, TreeAspect aspect) {
    myTreeAspect = aspect;
    model.registerAspect(PsiEventWrapperAspect.class, this, Collections.singleton((PomModelAspect)aspect));
  }

  public void projectOpened() {}
  public void projectClosed() {}

  @NotNull
  public String getComponentName() {
    return "PSI event wrapper aspect for POM";
  }

  public void initComponent() {}
  public void disposeComponent() {}
  public void update(PomModelEvent event) {
    final TreeChangeEvent changeSet = (TreeChangeEvent)event.getChangeSet(myTreeAspect);
    if(changeSet == null) return;
    ASTNode rootElement = changeSet.getRootElement();
    final PsiFile file = (PsiFile)SourceTreeToPsiMap.treeElementToPsi(rootElement);
    final PsiManagerImpl manager = (PsiManagerImpl)file.getManager();
    if(manager == null) return;

    if(file.isPhysical()){
      final ASTNode[] changedElements = changeSet.getChangedElements();
      for (ASTNode changedElement : changedElements) {
        TreeChange changesByElement = changeSet.getChangesByElement(changedElement);
        PsiElement psiParent = null;

        while (changedElement != null &&
               ((psiParent = changedElement.getPsi()) == null || !checkPsiForChildren(changesByElement.getAffectedChildren()))) {
          final ASTNode parent = changedElement.getTreeParent();
          final ChangeInfoImpl changeInfo = ChangeInfoImpl.create(ChangeInfo.CONTENTS_CHANGED, changedElement);
          changeInfo.compactChange(changedElement, changesByElement);
          changesByElement = new TreeChangeImpl(parent);
          changesByElement.addChange(changedElement, changeInfo);
          changedElement = parent;
        }
        if (changedElement == null) continue;

        final ASTNode[] affectedChildren = changesByElement.getAffectedChildren();

        for (final ASTNode treeElement : affectedChildren) {
          PsiTreeChangeEventImpl psiEvent = new PsiTreeChangeEventImpl(manager);
          psiEvent.setParent(psiParent);
          psiEvent.setFile(file);

          final PsiElement psiChild = treeElement.getPsi();
          psiEvent.setChild(psiChild);

          final ChangeInfo changeByChild = changesByElement.getChangeByChild(treeElement);
          switch (changeByChild.getChangeType()) {
            case ChangeInfo.ADD:
              psiEvent.setOffset(treeElement.getStartOffset());
              psiEvent.setOldLength(0);
              manager.childAdded(psiEvent);
              break;
            case ChangeInfo.REPLACE:
              final ReplaceChangeInfo change = (ReplaceChangeInfo)changeByChild;
              psiEvent.setOffset(treeElement.getStartOffset());
              final ASTNode replaced = change.getReplaced();
              psiEvent.setOldChild(replaced.getPsi());
              psiEvent.setNewChild(psiChild);
              psiEvent.setOldLength(replaced.getTextLength());
              manager.childReplaced(psiEvent);
              break;
            case ChangeInfo.CONTENTS_CHANGED:
              psiEvent.setOffset(treeElement.getStartOffset());
              psiEvent.setOldChild(psiChild);
              psiEvent.setNewChild(psiChild);
              psiEvent.setOldLength(changeByChild.getOldLength());
              manager.childReplaced(psiEvent);
              break;
            case ChangeInfo.REMOVED:
              psiEvent.setOffset(changesByElement.getChildOffsetInNewTree(treeElement));
              psiEvent.setOldParent(psiParent);
              psiEvent.setOldChild(psiChild);
              psiEvent.setOldLength(changeByChild.getOldLength());
              manager.childRemoved(psiEvent);
              break;
          }
        }
      }
    }
    else{
      manager.nonPhysicalChange();
    }
  }

  private static boolean checkPsiForChildren(final ASTNode[] affectedChildren) {
    for (final ASTNode astNode : affectedChildren) {
      if (astNode instanceof LeafElement && ((LeafElement)astNode).isChameleon()) return false;
      if (astNode.getPsi() == null) return false;
    }
    return true;
  }
}
