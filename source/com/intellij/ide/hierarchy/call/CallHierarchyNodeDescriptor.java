package com.intellij.ide.hierarchy.call;

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.LayeredIcon;

import javax.swing.*;
import java.awt.*;

public final class CallHierarchyNodeDescriptor extends HierarchyNodeDescriptor {
  private int myUsageCount = 1;
  private final static Class[] ourEnclosingElementClasses = new Class[]{PsiMethod.class, PsiClass.class, JspFile.class};

  public CallHierarchyNodeDescriptor(
    final Project project,
    final HierarchyNodeDescriptor parentDescriptor,
    final PsiElement element,
    final boolean isBase
  ){
    super(project, parentDescriptor, element, isBase);
  }

  /**
   * @return PsiMethod or PsiClass or JspFile
   */
  public final PsiElement getEnclosingElement(){
    return getEnclosingElement(myElement);
  }

  static PsiElement getEnclosingElement(final PsiElement element){
    return PsiTreeUtil.getParentOfType(element, ourEnclosingElementClasses, false);
  }

  public final void incrementUsageCount(){
    myUsageCount++;
  }

  /**
   * Element for OpenFileDescriptor
   */
  public final PsiElement getTargetElement(){
    return myElement;
  }

  public final boolean isValid(){
    final PsiElement element = getEnclosingElement();
    return element != null && element.isValid();
  }

  public final boolean update(){
    final CompositeAppearance oldText = myHighlightedText;
    final Icon oldOpenIcon = myOpenIcon;

    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (isMarkReadOnly()) {
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }

    boolean changes = super.update();

    final PsiElement enclosingElement = getEnclosingElement();

    if (enclosingElement == null) {
      final String invalidPrefix = "[Invalid] ";
      if (!myHighlightedText.getText().startsWith(invalidPrefix)) {
        myHighlightedText.getBeginning().addText(invalidPrefix, HierarchyNodeDescriptor.getInvalidPrefixAttributes());
      }
      return true;
    }

    myOpenIcon = enclosingElement.getIcon(flags);
    if (changes && myIsBase) {
      final LayeredIcon icon = new LayeredIcon(2);
      icon.setIcon(myOpenIcon, 0);
      icon.setIcon(BASE_POINTER_ICON, 1, -BASE_POINTER_ICON.getIconWidth() / 2, 0);
      myOpenIcon = icon;
    }
    myClosedIcon = myOpenIcon;

    myHighlightedText = new CompositeAppearance();
    TextAttributes mainTextAttributes = null;
    if (myColor != null) {
      mainTextAttributes = new TextAttributes(myColor, null, null, null, Font.PLAIN);
    }
    if (enclosingElement instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)enclosingElement;
      final StringBuffer buffer = new StringBuffer(128);
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null) {
        buffer.append(ClassPresentationUtil.getNameForClass(containingClass, false));
        buffer.append('.');
      }
      final String methodText = PsiFormatUtil.formatMethod(
        method,
        PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
        PsiFormatUtil.SHOW_TYPE
      );
      buffer.append(methodText);

      myHighlightedText.getEnding().addText(buffer.toString(), mainTextAttributes);
    }
    else if (enclosingElement instanceof JspFile) {
      final JspFile file = (JspFile)enclosingElement;
      myHighlightedText.getEnding().addText(file.getName(), mainTextAttributes);
    }
    else {
      myHighlightedText.getEnding().addText(ClassPresentationUtil.getNameForClass((PsiClass)enclosingElement, false), mainTextAttributes);
    }
    if (myUsageCount > 1) {
      myHighlightedText.getEnding().addText("  (" + myUsageCount + " usages)", HierarchyNodeDescriptor.getUsageCountPrefixAttributes());
    }
    if (!(enclosingElement instanceof JspFile)) {
      final String packageName = getPackageName(enclosingElement instanceof PsiMethod ? ((PsiMethod)enclosingElement).getContainingClass() : (PsiClass)enclosingElement);
      myHighlightedText.getEnding().addText("  (" + packageName + ")", HierarchyNodeDescriptor.getPackageNameAttributes());
    }
    myName = myHighlightedText.getText();

    if (
      !Comparing.equal(myHighlightedText, oldText) ||
      !Comparing.equal(myOpenIcon, oldOpenIcon)
    ){
      changes = true;
    }
    return changes;
  }

}
