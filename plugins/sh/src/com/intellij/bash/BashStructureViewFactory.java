package com.intellij.bash;

import com.intellij.bash.psi.BashFile;
import com.intellij.bash.psi.BashFunctionDefinition;
import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.*;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class BashStructureViewFactory implements PsiStructureViewFactory {
  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile psiFile) {
    if (!(psiFile instanceof BashFile)) return null;
    return new TreeBasedStructureViewBuilder() {
      @NotNull
      @Override
      public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new Model(psiFile);
      }
    };
  }

  private static class Model extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {
    private Model(@NotNull PsiFile psiFile) {
      super(psiFile, new Element(psiFile));
      withSuitableClasses(BashFile.class, BashFunctionDefinition.class);
    }

    @Override
    public boolean isAlwaysShowsPlus(StructureViewTreeElement structureViewTreeElement) {
      return false;
    }

    @Override
    public boolean isAlwaysLeaf(StructureViewTreeElement structureViewTreeElement) {
      return false;
    }
  }

  private static class Element implements StructureViewTreeElement, ItemPresentation, NavigationItem {
    private final PsiElement myElement;

    private Element(PsiElement element) {
      myElement = element;
    }

    @Override
    public Object getValue() {
      return myElement;
    }

    @Override
    public void navigate(boolean requestFocus) {
      ((Navigatable) myElement).navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
      return ((Navigatable) myElement).canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
      return ((Navigatable) myElement).canNavigateToSource();
    }

    @Nullable
    @Override
    public String getName() {
      return myElement instanceof BashFunctionDefinition ? getFunctionName((BashFunctionDefinition) myElement) : null;
    }

    private static String getFunctionName(BashFunctionDefinition myElement) {
      PsiElement word = myElement.getWord();
      return word == null ? "<unnamed>" : word.getText();
    }

    @NotNull
    @Override
    public ItemPresentation getPresentation() {
      return this;
    }

    @NotNull
    @Override
    public TreeElement[] getChildren() {
      return SyntaxTraverser.psiTraverser(myElement)
          .children(myElement).flatMap(
              ch -> SyntaxTraverser.psiTraverser(ch).expand(psiElement -> !(psiElement instanceof BashFunctionDefinition))
          )
          .filter(BashFunctionDefinition.class)
          .map(Element::new)
          .toArray(new Element[]{});
    }

    @Override
    public String getPresentableText() {
      if (myElement instanceof BashFunctionDefinition) {
        return getFunctionName((BashFunctionDefinition) myElement);
      }
      else if (myElement instanceof BashFile) {
        return ((BashFile) myElement).getName();
      }
      else if (myElement instanceof PsiNamedElement) return ((PsiNamedElement) myElement).getName();

      throw new AssertionError(myElement.getClass().getName());
    }

    @Nullable
    @Override
    public String getLocationString() {
      return null;
    }

    @Override
    public Icon getIcon(boolean open) {
      if (!myElement.isValid()) return null;
      if (myElement instanceof BashFunctionDefinition) {
        return AllIcons.Nodes.Function;
      }
      return myElement.getIcon(0);
    }
  }
}
