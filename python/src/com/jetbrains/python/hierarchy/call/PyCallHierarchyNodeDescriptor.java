/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.hierarchy.call;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.LayeredIcon;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class PyCallHierarchyNodeDescriptor extends HierarchyNodeDescriptor implements Navigatable{
  private int myUsageCount = 1;
  private final List<PsiReference> myReferences = new ArrayList<PsiReference>();
  private final boolean myNavigateToReference;

  protected PyCallHierarchyNodeDescriptor(@NotNull Project project, @Nullable NodeDescriptor parentDescriptor, @NotNull PsiElement element, boolean isBase, boolean navigateToReference) {
    super(project, parentDescriptor, element, isBase);
    myNavigateToReference = navigateToReference;
  }

  public final void incrementCount() {
    myUsageCount++;
  }

  public final PyFunction getPyFunction() {
    return (PyFunction)myElement;
  }

  public final PyElement getEnclosingElement(){
    return PsiTreeUtil.getNonStrictParentOfType(myElement, PyFunction.class, PyClass.class, PyFile.class);
  }

  public final PsiElement getTargetElement() {
    return myElement;
  }

  @Override
  public boolean isValid() {
    return myElement != null && myElement.isValid();
  }

  @Override
  public boolean update() {
    final CompositeAppearance oldText = myHighlightedText;
    final Icon oldIcon = getIcon();

    int flags = Iconable.ICON_FLAG_VISIBILITY;
    if (isMarkReadOnly()) {
      flags |= Iconable.ICON_FLAG_READ_STATUS;
    }

    final PsiElement element = getEnclosingElement();
    if (element == null) {
      final String invalidPrefix = IdeBundle.message("node.hierarchy.invalid");
      if(!myHighlightedText.getText().startsWith(invalidPrefix)) {
        myHighlightedText.getBeginning().addText(invalidPrefix, HierarchyNodeDescriptor.getInvalidPrefixAttributes());
      }
      return true;
    }

    boolean changes = super.update();

    Icon newIcon = element.getIcon(flags);
    if (changes && myIsBase) {
      final LayeredIcon icon = new LayeredIcon(2);
      icon.setIcon(newIcon, 0);
      icon.setIcon(AllIcons.Hierarchy.Base, 1, -AllIcons.Hierarchy.Base.getIconWidth() / 2, 0);
      newIcon = icon;
    }
    setIcon(newIcon);

    myHighlightedText = new CompositeAppearance();
    final TextAttributes mainTextAttributes = myColor != null ? null : new TextAttributes(myColor, null, null, null, Font.PLAIN);

    PyElementVisitor visitor = new PyElementVisitor() {
      @Override
      public void visitPyClass(PyClass pyClass) {
        myHighlightedText.getEnding().addText(pyClass.getName(), mainTextAttributes);
        myHighlightedText.getEnding().addText("(" + pyClass.getContainingFile().getName() + ")",
                                              HierarchyNodeDescriptor.getPackageNameAttributes());
      }

      @Override
      public void visitPyFunction(PyFunction function) {
        final StringBuilder buffer = new StringBuilder();
        final PyClass pyClass = function.getContainingClass();
        if (pyClass != null) {
          buffer.append(pyClass.getName());
          buffer.append('.');
        }
        buffer.append(function.getName());

        myHighlightedText.getEnding().addText(buffer.toString(), mainTextAttributes);
        myHighlightedText.getEnding().addText("(" + function.getContainingFile().getName() + ")",
                                              HierarchyNodeDescriptor.getPackageNameAttributes());
      }

      @Override
      public void visitPyLambdaExpression(PyLambdaExpression node) {
      }

      @Override
      public void visitPyFile(PyFile pyFile) {
        myHighlightedText.getEnding().addText(pyFile.getName(), mainTextAttributes);
      }
    };

    element.accept(visitor);

    if (myUsageCount > 1) {
      myHighlightedText.getEnding().addText(IdeBundle.message("node.call.hierarchy.N.usages", myUsageCount), HierarchyNodeDescriptor.getUsageCountPrefixAttributes());
    }

    myName = myHighlightedText.getText();
    if (!Comparing.equal(myHighlightedText, oldText)
      || !Comparing.equal(getIcon(), oldIcon)) {
      return true;
    }

    return changes;
  }

  public void addReference(final PsiReference reference) {
    myReferences.add(reference);
  }

  public boolean hasReference(final PsiReference reference) {
    return myReferences.contains(reference);
  }


  @Override
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
    }
    else {
      final PsiFile file = callElement.getContainingFile();
      if (file == null || file.getVirtualFile() == null) return;
      FileEditorManager.getInstance(myProject).openFile(file.getVirtualFile(), requestFocus);
    }

    Editor editor = PsiUtilBase.findEditor(callElement);
    if (editor != null) {
      HighlightManager highlightManager = HighlightManager.getInstance(myProject);
      EditorColorsManager colorsManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      List<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
      for (PsiReference reference : myReferences) {
        final PsiElement eachElement = reference.getElement();
        if (eachElement != null) {
          PsiElement eachCallElement = eachElement.getParent();
          if (eachCallElement != null) {
            final TextRange textRange = eachCallElement.getTextRange();
            highlightManager.addRangeHighlight(editor, textRange.getStartOffset(), textRange.getEndOffset(), attributes, false, highlighters);
          }
        }
      }
    }
  }

  @Override
  public boolean canNavigate() {
    if (!myNavigateToReference) {
      return myElement instanceof Navigatable && ((Navigatable)myElement).canNavigate();
    }
    if (myReferences.isEmpty()) return false;
    final PsiReference reference = myReferences.get(0);
    final PsiElement callElement = reference.getElement().getParent();
    if (callElement == null || !callElement.isValid()) return false;
    if (!(callElement instanceof Navigatable) || !((Navigatable)callElement).canNavigate()) {
      final PsiFile file = callElement.getContainingFile();
      if (file == null) return false;
    }
    return true;
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }
}
