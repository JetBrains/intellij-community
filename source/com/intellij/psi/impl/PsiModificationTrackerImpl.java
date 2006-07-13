/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 18, 2002
 * Time: 5:57:57 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiModificationTracker;

public class PsiModificationTrackerImpl implements PsiModificationTracker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiModificationTrackerImpl");

  private PsiManager myManager;
  private long myModificationCount = 0;
  private long myOutOfCodeBlockModificationCount = 0;

  public PsiModificationTrackerImpl(PsiManager manager) {
    myManager = manager;
  }

  public void incCounter(){
    myModificationCount++;
    myOutOfCodeBlockModificationCount++;
  }

  public void treeChanged(PsiTreeChangeEventImpl event) {
    myModificationCount++;

    boolean changedInsideCodeBlock = false;

    switch (event.getCode()) {
      case PsiManagerImpl.BEFORE_CHILDREN_CHANGE:
        if (event.getParent() instanceof PsiFile) {
          changedInsideCodeBlock = true;
          break; // May be caused by fake PSI event from PomTransaction. A real event will anyway follow.
        }

      case PsiManagerImpl.CHILDREN_CHANGED :
        changedInsideCodeBlock = isInsideCodeBlock(event.getParent());
      break;

      case PsiManagerImpl.BEFORE_CHILD_ADDITION:
      case PsiManagerImpl.BEFORE_CHILD_REMOVAL:
      case PsiManagerImpl.CHILD_ADDED :
      case PsiManagerImpl.CHILD_REMOVED :
        changedInsideCodeBlock = isInsideCodeBlock(event.getParent());
      break;

      case PsiManagerImpl.BEFORE_PROPERTY_CHANGE:
      case PsiManagerImpl.PROPERTY_CHANGED :
        changedInsideCodeBlock = false;
      break;

      case PsiManagerImpl.BEFORE_CHILD_REPLACEMENT:
      case PsiManagerImpl.CHILD_REPLACED :
        changedInsideCodeBlock = isInsideCodeBlock(event.getParent());
      break;

      case PsiManagerImpl.BEFORE_CHILD_MOVEMENT:
      case PsiManagerImpl.CHILD_MOVED :
        changedInsideCodeBlock = isInsideCodeBlock(event.getOldParent()) && isInsideCodeBlock(event.getNewParent());
      break;

      default:
        LOG.error("Unknown code:" + event.getCode());
    }

    if (!changedInsideCodeBlock) myOutOfCodeBlockModificationCount++;

    myManager.getCachedValuesManager().releaseOutdatedValues();
  }

  public boolean isInsideCodeBlock(PsiElement element) {
    if (element == null || element.getParent() == null) return true;
    while(true){
      if (element instanceof PsiFile || element instanceof PsiDirectory || element == null){
        return false;
      }
      PsiElement pparent = element.getParent();
      if (element instanceof PsiClass) return false; // anonymous or local class
      if (element instanceof PsiCodeBlock){
        if (pparent instanceof PsiMethod || pparent instanceof PsiClassInitializer){
          return true;
        }
      }
      element = pparent;
    }
  }

  public long getModificationCount() {
    return myModificationCount;
  }

  public long getOutOfCodeBlockModificationCount() {
    return myOutOfCodeBlockModificationCount;
  }
}
