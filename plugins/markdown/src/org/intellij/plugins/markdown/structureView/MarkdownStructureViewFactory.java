package org.intellij.plugins.markdown.structureView;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import org.intellij.plugins.markdown.util.MarkdownPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.intellij.plugins.markdown.util.MarkdownPsiUtil.PRESENTABLE_TYPES;

public class MarkdownStructureViewFactory implements PsiStructureViewFactory {


  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder(@NotNull final PsiFile psiFile) {
    return new TreeBasedStructureViewBuilder() {
      @NotNull
      @Override
      public StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new MarkdownStructureViewModel(psiFile, editor);
      }

      @Override
      public boolean isRootNodeShown() {
        return false;
      }
    };
  }

  private static class MarkdownStructureViewModel extends StructureViewModelBase {
    MarkdownStructureViewModel(@NotNull PsiFile psiFile, @Nullable Editor editor) {
      super(psiFile, editor, new MarkdownStructureElement(psiFile));
    }

    @Nullable
    @Override
    protected Object findAcceptableElement(PsiElement element) {
      // walk up the psi-tree until we find an element from the structure view
      while (element != null && !PRESENTABLE_TYPES.contains(PsiUtilCore.getElementType(element))) {
        IElementType parentType = PsiUtilCore.getElementType(element.getParent());

        final PsiElement previous = element.getPrevSibling();
        if (previous == null || !MarkdownPsiUtil.TRANSPARENT_CONTAINERS.contains(parentType)) {
          element = element.getParent();
        }
        else {
          element = previous;
        }
      }

      return element;
    }
  }
}
