package com.intellij.find.findUsages;

import com.intellij.find.FindManager;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
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
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author max
 */
public class PsiElement2UsageTargetAdapter implements UsageTarget, TypeSafeDataProvider {
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
    return (NavigationItem)getElement();
  }

  public FileStatus getFileStatus() {
    return isValid() ? getNavigationItem().getFileStatus() : FileStatus.NOT_CHANGED;
  }

  public String toString() {
    return myPresentation.getPresentableText();
  }

  public void findUsages() {
    PsiElement element = getElement();
    FindManager.getInstance(element.getProject()).findUsages(element);
  }

  public PsiElement getElement() {
    return myPointer.getElement();
  }

  public void findUsagesInEditor(@NotNull FileEditor editor) {
    PsiElement element = getElement();
    FindManager.getInstance(element.getProject()).findUsagesInEditor(element, editor);
  }

  public boolean isValid() {
    return getElement() != null;
  }

  public boolean isReadOnly() {
    return isValid() && !getElement().isWritable();
  }

  public VirtualFile[] getFiles() {
    if (!isValid()) return null;

    final PsiFile psiFile = getElement().getContainingFile();
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

  public void calcData(final DataKey key, final DataSink sink) {
    if (key == UsageView.USAGE_INFO_KEY) {
      PsiElement element = getElement();
      if (element != null && element.getTextRange() != null) {
        sink.put(UsageView.USAGE_INFO_KEY, new UsageInfo(element));
      }
    }
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
          } else if (element instanceof PsiFile) {
            final PsiFile psiFile = (PsiFile)element;
            final VirtualFile virtualFile = psiFile.getVirtualFile();
            if (virtualFile != null) {
              myIconOpen = virtualFile.getIcon();
              myIconClosed = virtualFile.getIcon();
            }
          }
        }
      }
    }

    public String getPresentableText() {
      return myPresentableText;
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
