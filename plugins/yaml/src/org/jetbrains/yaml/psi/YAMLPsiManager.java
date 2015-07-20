package org.jetbrains.yaml.psi;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessor;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public class YAMLPsiManager implements ProjectComponent, PsiTreeChangePreprocessor {
  private PsiModificationTrackerImpl myModificationTracker;
  protected Project myProject;
  private final PsiManagerImpl myPsiManager;

  public YAMLPsiManager(@NotNull final Project project, @NotNull final PsiManagerImpl psiManager) {
    myProject = project;
    myPsiManager = psiManager;
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "YAMLPsiManager";

  }

  @Override
  public void initComponent() {
    myModificationTracker = (PsiModificationTrackerImpl) myPsiManager.getModificationTracker();
    myPsiManager.addTreeChangePreprocessor(this);
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  public void treeChanged(@NotNull final PsiTreeChangeEventImpl event) {
    if (!(event.getFile() instanceof YAMLFile)) return;
    boolean changedInsideCodeBlock = false;

    switch (event.getCode()) {
      case BEFORE_CHILDREN_CHANGE:
        if (event.getParent() instanceof PsiFile) {
          changedInsideCodeBlock = true;
          break; // May be caused by fake PSI event from PomTransaction. A real event will anyway follow.
        }

      case CHILDREN_CHANGED:
        if (event.isGenericChange()) return;
        changedInsideCodeBlock = isInsideCodeBlock(event.getParent());
        break;

      case BEFORE_CHILD_ADDITION:
      case BEFORE_CHILD_REMOVAL:
      case CHILD_ADDED:
      case CHILD_REMOVED:
        changedInsideCodeBlock = isInsideCodeBlock(event.getParent());
        break;

      case BEFORE_PROPERTY_CHANGE:
      case PROPERTY_CHANGED:
        changedInsideCodeBlock = false;
        break;

      case BEFORE_CHILD_REPLACEMENT:
      case CHILD_REPLACED:
        changedInsideCodeBlock = isInsideCodeBlock(event.getParent());
        break;

      case BEFORE_CHILD_MOVEMENT:
      case CHILD_MOVED:
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
      if (element instanceof YAMLFile) {
        return false;
      }
      if (element instanceof PsiFile || element instanceof PsiDirectory) {
        return true;
      }
      PsiElement parent = element.getParent();
      if (!(parent instanceof YAMLFile ||
            parent instanceof YAMLKeyValue ||
            parent instanceof YAMLCompoundValue ||
            parent instanceof YAMLDocument)) {
        return true;
      }
      element = parent;
    }
  }
}
