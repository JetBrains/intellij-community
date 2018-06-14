// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.inspections;

import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLBundle;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.psi.YAMLAnchor;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

import java.util.Collection;

public class YAMLUnusedAnchorInspection extends LocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new YamlPsiElementVisitor() {
      @Override
      public void visitAnchor(@NotNull YAMLAnchor anchor) {
        Collection<PsiReference> references =
          ReferencesSearch.search(anchor, GlobalSearchScope.fileScope(anchor.getContainingFile())).findAll();
        if (references.isEmpty()) {
          holder.registerProblem(
            anchor,
            YAMLBundle.message("inspections.unused.anchor.message", anchor.getName()),
            ProblemHighlightType.LIKE_UNUSED_SYMBOL,
            new RemoveAnchorQuickFix(anchor)
          );
        }
      }
    };
  }

  private static class RemoveAnchorQuickFix implements LocalQuickFix {
    private final SmartPsiElementPointer<YAMLAnchor> myAnchorHolder;

    public RemoveAnchorQuickFix(@NotNull final YAMLAnchor anchor) {
      myAnchorHolder = SmartPointerManager.getInstance(anchor.getProject()).createSmartPsiElementPointer(anchor);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return YAMLBundle.message("inspections.unused.anchor.quickfix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      YAMLAnchor anchor = myAnchorHolder.getElement();
      if (anchor == null) {
        return;
      }
      PostprocessReformattingAspect.getInstance(project).disablePostprocessFormattingInside(() -> {
        ASTNode node = TreeUtil.prevLeaf(anchor.getNode());
        while (YAMLElementTypes.SPACE_ELEMENTS.contains(PsiUtilCore.getElementType(node))) {
          assert(node != null);
          ASTNode prev = TreeUtil.prevLeaf(node);
          ASTNode parent = node.getTreeParent();
          if (parent != null) {
            CodeEditUtil.removeChild(parent, node);
          }
          node = prev;
        }
        anchor.delete();
      });
    }
  }
}
