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
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;

import java.util.Collections;

public class PsiEventWrapperAspect implements PomModelAspect{
  private final PomModel myModel;
  private final TreeAspect myTreeAspect;

  public PsiEventWrapperAspect(PomModel model, TreeAspect aspect) {
    myModel = model;
    myTreeAspect = aspect;
    myModel.registerAspect(this, Collections.singleton((PomModelAspect)aspect));
  }

  public void projectOpened() {}
  public void projectClosed() {}

  public String getComponentName() {
    return "PSI event wrapper aspect for POM";
  }

  public void initComponent() {}
  public void disposeComponent() {}
  public void update(PomModelEvent event) {
    final TreeChangeEvent changeSet = (TreeChangeEvent)event.getChangeSet(myTreeAspect);
    if(changeSet == null) return;
    FileElement rootElement = (FileElement)changeSet.getRootElement();
    final PsiFile file = (PsiFile)SourceTreeToPsiMap.treeElementToPsi(rootElement);
    final PsiManagerImpl manager = (PsiManagerImpl)file.getManager();
    if(manager == null) return;

    if(file.isPhysical()){
      final ASTNode[] changedElements = changeSet.getChangedElements();
      for (int i = 0; i < changedElements.length; i++) {
        ASTNode changedElement = changedElements[i];
        TreeChange changesByElement = changeSet.getChangesByElement(changedElement);
        PsiElement psiParent = null;
        while(changedElement != null && (psiParent = SourceTreeToPsiMap.treeElementToPsi(changedElement)) == null){
          final ASTNode parent = changedElement.getTreeParent();
          final ChangeInfoImpl changeInfo = ChangeInfoImpl.create(ChangeInfo.CONTENTS_CHANGED, changedElement, rootElement.getCharTable());
          changeInfo.compactChange(changedElement, changesByElement);
          changesByElement = new TreeChangeImpl(parent);
          changesByElement.addChange(changedElement, changeInfo);
          changedElement = (TreeElement)parent;
        }
        if(changedElement == null) continue;

        final ASTNode[] affectedChildren = changesByElement.getAffectedChildren();
        boolean contentsChange = false;
        for (int j = 0; j < affectedChildren.length; j++) {
          if(changesByElement.getChangeByChild(affectedChildren[j]).getChangeType() == ChangeInfo.REMOVED){
            contentsChange = true;
            break;
          }
        }

        if(contentsChange){
          final ChangeInfoImpl changeInfo = ChangeInfoImpl.create(ChangeInfo.CONTENTS_CHANGED, changedElement, rootElement.getCharTable());
          changeInfo.compactChange(changedElement, changesByElement);
          PsiTreeChangeEventImpl psiEvent = new PsiTreeChangeEventImpl(manager);
          psiEvent.setParent(psiParent.getParent());
          psiEvent.setFile(file);
          psiEvent.setChild(psiParent);
          psiEvent.setOffset(changedElement.getStartOffset());
          psiEvent.setOldChild(psiParent);
          psiEvent.setNewChild(psiParent);
          psiEvent.setOldLength(changeInfo.getOldLength());
          //manager.beforeChildReplacement(psiEvent);
          manager.childReplaced(psiEvent);
          continue;
        }

        for (int j = 0; j < affectedChildren.length; j++) {
          final ASTNode treeElement = affectedChildren[j];
          PsiTreeChangeEventImpl psiEvent = new PsiTreeChangeEventImpl(manager);
          psiEvent.setParent(psiParent);
          psiEvent.setFile(file);
          psiEvent.setChild(SourceTreeToPsiMap.treeElementToPsi(treeElement));

          switch(changesByElement.getChangeByChild(treeElement).getChangeType()){
            case ChangeInfo.ADD:
              psiEvent.setOffset(treeElement.getStartOffset());
              psiEvent.setOldLength(0);
              //manager.beforeChildAddition(psiEvent);
              manager.childAdded(psiEvent);
              break;
            case ChangeInfo.REPLACE:
              final ReplaceChangeInfo changeByChild = (ReplaceChangeInfo)changesByElement.getChangeByChild(treeElement);
              psiEvent.setOffset(treeElement.getStartOffset());
              final ASTNode replaced = changeByChild.getReplaced();
              psiEvent.setOldChild(SourceTreeToPsiMap.treeElementToPsi(replaced));
              psiEvent.setNewChild(SourceTreeToPsiMap.treeElementToPsi(treeElement));
              psiEvent.setOldLength(replaced.getTextLength());
              //manager.beforeChildReplacement(psiEvent);
              manager.childReplaced(psiEvent);
              break;
            case ChangeInfo.CONTENTS_CHANGED:
              final ChangeInfo contentsChangeInfo = changesByElement.getChangeByChild(treeElement);
              psiEvent.setOffset(treeElement.getStartOffset());
              psiEvent.setOldChild(SourceTreeToPsiMap.treeElementToPsi(treeElement));
              psiEvent.setNewChild(SourceTreeToPsiMap.treeElementToPsi(treeElement));
              psiEvent.setOldLength(contentsChangeInfo.getOldLength());
              //manager.beforeChildReplacement(psiEvent);
              manager.childReplaced(psiEvent);
              break;
          }
        }
      }
    }
    else{
      manager.nonPhysicalChange();
    }
  }
}
