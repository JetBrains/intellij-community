package com.intellij.ide.hierarchy.call;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public final class CallHierarchyNodeDescriptor extends HierarchyNodeDescriptor implements Navigatable {
  private int myUsageCount = 1;
  private ArrayList<PsiReference> myReferences = new ArrayList<PsiReference>();
  private boolean myNavigateToReference;

  public CallHierarchyNodeDescriptor(
    final Project project,
    final HierarchyNodeDescriptor parentDescriptor,
    final PsiElement element,
    final boolean isBase,
    final boolean navigateToReference){
    super(project, parentDescriptor, element, isBase);
    myNavigateToReference = navigateToReference;
  }

  /**
   * @return PsiMethod or PsiClass or JspFile
   */
  public final PsiMember getEnclosingElement(){
    return myElement == null ? null : getEnclosingElement(myElement);
  }

  static PsiMember getEnclosingElement(final PsiElement element){
    return PsiTreeUtil.getNonStrictParentOfType(element, PsiMethod.class, PsiClass.class);
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
      final String invalidPrefix = IdeBundle.message("node.hierarchy.invalid");
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
      if (enclosingElement instanceof JspHolderMethod) {
        PsiFile file = enclosingElement.getContainingFile();
        myHighlightedText.getEnding().addText(file != null ? file.getName() : IdeBundle.message("node.call.hierarchy.unknown.jsp"), mainTextAttributes);
      }
      else {
        final PsiMethod method = (PsiMethod)enclosingElement;
        final StringBuilder buffer = new StringBuilder(128);
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
    }
    else if (PsiUtil.isInJspFile(enclosingElement) && enclosingElement instanceof PsiFile) {
      final JspFile file = PsiUtil.getJspFile(enclosingElement);
      myHighlightedText.getEnding().addText(file.getName(), mainTextAttributes);
    }
    else {
      myHighlightedText.getEnding().addText(ClassPresentationUtil.getNameForClass((PsiClass)enclosingElement, false), mainTextAttributes);
    }
    if (myUsageCount > 1) {
      myHighlightedText.getEnding().addText(IdeBundle.message("node.call.hierarchy.N.usages", myUsageCount), HierarchyNodeDescriptor.getUsageCountPrefixAttributes());
    }
    if (!(PsiUtil.isInJspFile(enclosingElement) && enclosingElement instanceof PsiFile)) {
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

  public void addReference(final PsiReference reference) {
    myReferences.add(reference);
  }

  public void navigate(boolean requestFocus) {
    if (!myNavigateToReference) {
      if (myElement instanceof Navigatable && ((Navigatable)myElement).canNavigate()) {
        ((Navigatable)myElement).navigate(requestFocus);
      }
      return;
    }

    final PsiReference firstReference = myReferences.get(0);
    final PsiElement element = firstReference.getElement();
    if (element == null) return;
    final PsiElement callElement = element.getParent();
    if (callElement instanceof Navigatable && ((Navigatable)callElement).canNavigate()) {
      ((Navigatable)callElement).navigate(requestFocus);
    } else {
      final PsiFile psiFile = callElement.getContainingFile();
      if (psiFile == null || psiFile.getVirtualFile() == null) return;
      FileEditorManager.getInstance(myProject).openFile(psiFile.getVirtualFile(), requestFocus);
    }

    Editor editor = getEditor(callElement);

    if (editor != null) {

      HighlightManager highlightManager = HighlightManager.getInstance(myProject);
      EditorColorsManager colorManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
      for (PsiReference psiReference : myReferences) {
        final PsiElement eachElement = psiReference.getElement();
        if (eachElement != null) {
          final PsiElement eachMethodCall = eachElement.getParent();
          if (eachMethodCall != null) {
            final TextRange textRange = eachMethodCall.getTextRange();
            highlightManager.addRangeHighlight(editor, textRange.getStartOffset(), textRange.getEndOffset(), attributes, false, highlighters);
          }
        }
      }
    }
  }

  @Nullable
  private Editor getEditor(final PsiElement callElement) {
    final FileEditor editor = FileEditorManager.getInstance(myProject).getSelectedEditor(callElement.getContainingFile().getVirtualFile());
    if (editor instanceof TextEditor) {
      return ((TextEditor)editor).getEditor();
    } else {
      return null;
    }
  }

  public boolean canNavigate() {
    if (!myNavigateToReference) {
      return myElement instanceof Navigatable && ((Navigatable) myElement).canNavigate();
    }
    if (myReferences.isEmpty()) return false;
    final PsiReference firstReference = myReferences.get(0);
    final PsiElement callElement = firstReference.getElement().getParent();
    if (!(callElement instanceof Navigatable) || !((Navigatable)callElement).canNavigate()) {
      final PsiFile psiFile = callElement.getContainingFile();
      if (psiFile == null) return false;
    }
    return true;
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }
}
