package com.intellij.find.findUsages;

import com.intellij.find.FindManager;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.UsageTarget;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 17, 2004
 * Time: 4:46:38 PM
 * To change this template use File | Settings | File Templates.
 */
public class PsiElement2UsageTargetAdapter implements UsageTarget {
  private SmartPsiElementPointer myPointer;
  private ItemPresentation myPresentation;

  public PsiElement2UsageTargetAdapter(final PsiElement element) {
    myPointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);

    if (!(element instanceof NavigationItem)) {
      throw new IllegalArgumentException("Element is not a navigation item: " + element);
    }

    final NavigationItem navItem = (NavigationItem)element;
    final ItemPresentation presentation = navItem.getPresentation();

    final String nodeText = UsageViewUtil.createNodeText(element, true);
    final Icon iconOpen = presentation != null ? presentation.getIcon(true) : null;
    final Icon iconClosed = presentation != null ? presentation.getIcon(false) : null;
    myPresentation = new ItemPresentation() {
      public String getPresentableText() {
        return nodeText;
      }

      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        if (!isValid() || presentation == null) {
          return open ? iconOpen : iconClosed;
        }
        else {
          return presentation.getIcon(open);
        }
      }
    };
  }

  public String getName() {
    return getNavigationItem().getName();
  }

  public ItemPresentation getPresentation() {
    return myPresentation;
  }

  public void navigate(boolean requestFocus) {
    if (!canNavigate()) return;
    getNavigationItem().navigate(requestFocus);
  }

  public boolean canNavigate() {
    return isValid() && getNavigationItem().canNavigate();
  }

  private NavigationItem getNavigationItem() {
    return (NavigationItem)myPointer.getElement();
  }

  public FileStatus getFileStatus() {
    return isValid() ? getNavigationItem().getFileStatus() : FileStatus.NOT_CHANGED;
  }

  public String toString() {
    return myPresentation.getPresentableText();
  }

  public void findUsages() {
    PsiElement element = myPointer.getElement();
    FindManager.getInstance(element.getProject()).findUsages(element);
  }

  public PsiElement getElement() {
    return myPointer.getElement();
  }

  public void findUsagesInEditor(FileEditor editor) {
    PsiElement element = myPointer.getElement();
    FindManager.getInstance(element.getProject()).findUsagesInEditor(element, editor);
  }

  public boolean isValid() {
    return myPointer.getElement() != null;
  }

  public boolean isReadOnly() {
    return isValid() && !myPointer.getElement().isWritable();
  }

  public VirtualFile[] getFiles() {
    return isValid() ? new VirtualFile[] {myPointer.getElement().getContainingFile().getVirtualFile()} : null;
  }

  public static UsageTarget[] convert(PsiElement[] psiElements) {
    UsageTarget[] targets = new UsageTarget[psiElements.length];
    for (int i = 0; i < targets.length; i++) {
      targets[i] = new PsiElement2UsageTargetAdapter(psiElements[i]);
    }

    return targets;
  }
}
