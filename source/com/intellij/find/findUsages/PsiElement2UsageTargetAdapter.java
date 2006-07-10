package com.intellij.find.findUsages;

import com.intellij.find.FindManager;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.meta.PsiMetaBaseOwner;
import com.intellij.psi.meta.PsiMetaDataBase;
import com.intellij.psi.meta.PsiPresentableMetaData;
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
  private MyItemPresentation myPresentation;

  public PsiElement2UsageTargetAdapter(final PsiElement element) {
    myPointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);

    if (!(element instanceof NavigationItem)) {
      throw new IllegalArgumentException("Element is not a navigation item: " + element);
    }

    myPresentation = new MyItemPresentation();
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

  public boolean canNavigateToSource() {
    return isValid() && getNavigationItem().canNavigateToSource();
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
    if (!isValid()) return null;

    final PsiFile psiFile = myPointer.getElement().getContainingFile();
    if (psiFile == null) return null;

    final VirtualFile virtualFile = psiFile.getVirtualFile();
    return virtualFile == null ? null : new VirtualFile[]{virtualFile};
  }

  public void update() {
    myPresentation.update();
  }

  public static PsiElement2UsageTargetAdapter[] convert(PsiElement[] psiElements) {
    PsiElement2UsageTargetAdapter[] targets = new PsiElement2UsageTargetAdapter[psiElements.length];
    for (int i = 0; i < targets.length; i++) {
      targets[i] = new PsiElement2UsageTargetAdapter(psiElements[i]);
    }

    return targets;
  }

  private class MyItemPresentation implements ItemPresentation {
    private String myPresentableText;
    private Icon myIconOpen;
    private Icon myIconClosed;

    public MyItemPresentation() {
      update();
    }

    public void update() {
      final PsiElement element = getElement();
      if (element != null) {
        final ItemPresentation presentation = ((NavigationItem)element).getPresentation();
        myIconOpen = presentation != null ? presentation.getIcon(true) : null;
        myIconClosed = presentation != null ? presentation.getIcon(false) : null;
        myPresentableText = UsageViewUtil.createNodeText(element, true);
        if (myIconOpen == null || myIconClosed == null) {
          if (element instanceof PsiMetaBaseOwner) {
            final PsiMetaBaseOwner psiMetaOwner = (PsiMetaBaseOwner)element;
            final PsiMetaDataBase metaData = psiMetaOwner.getMetaData();
            if (metaData instanceof PsiPresentableMetaData) {
              final PsiPresentableMetaData psiPresentableMetaData = (PsiPresentableMetaData)metaData;
              if (myIconOpen == null) myIconOpen = psiPresentableMetaData.getIcon();
              if (myIconClosed == null) myIconClosed = psiPresentableMetaData.getIcon();
            }
          }
        }
      }
    }

    public String getPresentableText() {
      return myPresentableText;
    }
    
    private String createPresentableText(final PsiElement element) {
      return UsageViewUtil.createNodeText(element, true);
    }

    public String getLocationString() {
      return null;
    }

    public TextAttributesKey getTextAttributesKey() {
      return null;
    }

    public Icon getIcon(boolean open) {
      return open ? myIconOpen : myIconClosed;
    }
  }
}
