package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiUtil;

import javax.swing.*;
import java.awt.*;

public class SmartElementDescriptor extends NodeDescriptor{
  protected PsiElement myElement;
  private final SmartPsiElementPointer mySmartPointer;

  public SmartElementDescriptor(Project project, NodeDescriptor parentDescriptor, PsiElement element) {
    super(project, parentDescriptor);
    myElement = element;
    mySmartPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(element);
  }

  public Object getElement() {
    return myElement;
  }

  protected boolean isMarkReadOnly() {
    return getParentDescriptor() instanceof PsiDirectoryNode;
  }

  protected boolean isMarkModified() {
    return getParentDescriptor() instanceof PsiDirectoryNode;
  }

  // Should be called in atomic action
  public boolean update() {
    myElement = mySmartPointer.getElement();
    if (myElement == null) return true;
    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (isMarkReadOnly()){
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }
    Icon icon = myElement.getIcon(flags);
    Color color = null;

    if (isMarkModified() ){
      VirtualFile virtualFile = PsiUtil.getVirtualFile(myElement);
      if (virtualFile != null) {
        color = FileStatusManager.getInstance(myProject).getStatus(virtualFile).getColor();
      }
    }
    if (CopyPasteManager.getInstance().isCutElement(myElement)) {
      color = CopyPasteManager.CUT_COLOR;
    }

    boolean changes = !Comparing.equal(icon, myOpenIcon) || !Comparing.equal(color, myColor);
    myOpenIcon = icon;
    myClosedIcon = myOpenIcon;
    myColor = color;
    return changes;
  }
}