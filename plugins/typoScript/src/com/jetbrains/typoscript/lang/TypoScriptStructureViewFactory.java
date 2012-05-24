/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.jetbrains.typoscript.lang;

import com.intellij.ide.structureView.*;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.navigation.ItemPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PlatformIcons;
import com.jetbrains.typoscript.TypoScriptIcons;
import com.jetbrains.typoscript.lang.psi.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;


public class TypoScriptStructureViewFactory implements PsiStructureViewFactory {
  private static final Class[] SUITABLE_CLASSES =
    new Class[]{Assignment.class, CodeBlock.class, ConditionElement.class, Copying.class, IncludeStatementElement.class,
      MultilineValueAssignment.class, Unsetting.class, ValueModification.class};

  @Override
  public StructureViewBuilder getStructureViewBuilder(final PsiFile psiFile) {

    return new TreeBasedStructureViewBuilder() {
      @NotNull
      public StructureViewModel createStructureViewModel() {
        return new Model(psiFile);
      }

      @Override
      public boolean isRootNodeShown() {
        return false;
      }
    };
  }

  private static class Model extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {

    public Model(@NotNull PsiFile psiFile) {
      super(psiFile, new Element(psiFile));
      withSuitableClasses(SUITABLE_CLASSES);
    }

    @Override
    public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
      return false;
    }

    @Override
    public boolean isAlwaysLeaf(StructureViewTreeElement element) {
      Object psi = element.getValue();
      return !(psi instanceof TypoScriptFile || psi instanceof CodeBlock);
    }
  }

  private static class Element implements StructureViewTreeElement, ItemPresentation {
    private final PsiElement myElement;

    public Element(PsiElement element) {
      this.myElement = element;
    }

    @Override
    public Object getValue() {
      return myElement;
    }

    @Override
    public void navigate(boolean requestFocus) {
      ((Navigatable)myElement).navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
      return ((Navigatable)myElement).canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
      return ((Navigatable)myElement).canNavigateToSource();
    }

    @Override
    public ItemPresentation getPresentation() {
      return this;
    }

    @Override
    public String getPresentableText() {
      if (myElement instanceof TypoScriptFile) {
        return myElement.getContainingFile().getName();
      }
      else if (myElement instanceof IncludeStatementElement) {
        return myElement.getText();
      }
      else if (myElement instanceof ConditionElement) {
        return myElement.getText();
      }
      ObjectPath objectPath = PsiTreeUtil.getChildOfType(myElement, ObjectPath.class);
      if (objectPath != null) {
        return objectPath.getText();
      }
      throw new AssertionError(myElement.getClass().getName() + " " + myElement.getText());
    }

    @Override
    public String getLocationString() {
      return null;
    }

    @Override
    public Icon getIcon(boolean open) {
      if (myElement instanceof TypoScriptFile) {
        return myElement.getIcon(0);
      }
      else if (myElement instanceof IncludeStatementElement) {
        return TypoScriptIcons.INCLUDE_ICON;
      }
      else if (myElement instanceof ConditionElement) {
        return TypoScriptIcons.CONDITION_ICON;
      }
      else if (myElement instanceof CodeBlock) {
        return PlatformIcons.FOLDER_ICON;
      }
      return TypoScriptIcons.PROPERTY_ICON;
    }

    @Override
    public TreeElement[] getChildren() {
      if (myElement instanceof TypoScriptFile || myElement instanceof CodeBlock) {
        final ArrayList<TreeElement> result = new ArrayList<TreeElement>();
        for (PsiElement child : myElement.getChildren()) {
          if (isSuitable(child)) {
            result.add(new Element(child));
          }
        }
        return result.toArray(new TreeElement[result.size()]);
      }
      return new TreeElement[0];
    }
  }


  private static boolean isSuitable(PsiElement element) {
    if (!(element instanceof TypoScriptCompositeElement)) {
      return false;
    }
    for (Class aClass : SUITABLE_CLASSES) {
      if (aClass.isInstance(element)) {
        return true;
      }
    }
    return false;
  }
}
