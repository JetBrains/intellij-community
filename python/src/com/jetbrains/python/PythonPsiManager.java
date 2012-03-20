package com.jetbrains.python;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessor;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonPsiManager extends AbstractProjectComponent implements PsiTreeChangePreprocessor {
  private final PsiManagerImpl myPsiManager;
  private PsiModificationTrackerImpl myModificationTracker;

  public PythonPsiManager(Project project, PsiManagerImpl psiManager) {
    super(project);
    myPsiManager = psiManager;
  }

  public void initComponent() {
    myModificationTracker = (PsiModificationTrackerImpl) myPsiManager.getModificationTracker();
    myPsiManager.addTreeChangePreprocessor(this);
  }

  public void treeChanged(@NotNull PsiTreeChangeEventImpl event) {
    if (!(event.getFile() instanceof PyFile)) return;
    boolean changedInsideCodeBlock = false;

    switch (event.getCode()) {
      case BEFORE_CHILDREN_CHANGE:
        if (event.getParent() instanceof PsiFile) {
          changedInsideCodeBlock = true;
          break; // May be caused by fake PSI event from PomTransaction. A real event will anyway follow.
        }

      case CHILDREN_CHANGED :
        if (event.isGenericChildrenChange()) return;
        changedInsideCodeBlock = isInsideCodeBlock(event.getParent());
      break;

      case BEFORE_CHILD_ADDITION:
      case BEFORE_CHILD_REMOVAL:
      case CHILD_ADDED :
      case CHILD_REMOVED :
        changedInsideCodeBlock = isInsideCodeBlock(event.getParent());
      break;

      case BEFORE_PROPERTY_CHANGE:
      case PROPERTY_CHANGED :
        changedInsideCodeBlock = false;
      break;

      case BEFORE_CHILD_REPLACEMENT:
      case CHILD_REPLACED :
        changedInsideCodeBlock = isInsideCodeBlock(event.getParent());
      break;

      case BEFORE_CHILD_MOVEMENT:
      case CHILD_MOVED :
        changedInsideCodeBlock = isInsideCodeBlock(event.getOldParent()) && isInsideCodeBlock(event.getNewParent());
      break;
    }

    if (!changedInsideCodeBlock) {
      myModificationTracker.incOutOfCodeBlockModificationCounter();
    }
  }

  private static boolean isInsideCodeBlock(PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      return false;
    }

    if (element == null || element.getParent() == null) return true;

    while(true) {
      if (element instanceof PyFile) {
        return false;
      }
      if (element instanceof PsiFile || element instanceof PsiDirectory || element == null) {
        return true;
      }
      PsiElement pparent = element.getParent();
      if (pparent instanceof PyFunction) {
        final PyFunction pyFunction = (PyFunction)pparent;
        return !(element == pyFunction.getParameterList() || element == pyFunction.getNameIdentifier());
      }
      element = pparent;
    }
  }
}
