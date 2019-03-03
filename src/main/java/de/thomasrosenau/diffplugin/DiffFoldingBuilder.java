/*
 Copyright 2019 Thomas Rosenau

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package de.thomasrosenau.diffplugin;

import java.util.ArrayList;
import java.util.Collection;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import de.thomasrosenau.diffplugin.psi.impl.DiffContextHunkImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffGitBinaryPatchImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffGitDiffImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffGitHeaderImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffMultiDiffPartImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffNormalHunkImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffUnifiedHunkImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class DiffFoldingBuilder extends FoldingBuilderEx implements PossiblyDumbAware {
    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        ArrayList<FoldingDescriptor> result = new ArrayList<>();
        buildFileFoldingRegions(root, result);
        buildHunkFoldingRegions(root, result);
        buildGitFoldingRegions(root, result);
        return result.toArray(FoldingDescriptor.EMPTY);
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return node.getPsi() instanceof DiffGitBinaryPatchImpl;
    }

    private void buildFileFoldingRegions(@NotNull PsiElement root, @NotNull ArrayList<FoldingDescriptor> result) {
        for (PsiElement element : PsiTreeUtil.findChildrenOfType(root, DiffMultiDiffPartImpl.class)) {
            addElement(result, element);
        }
    }

    private void buildHunkFoldingRegions(@NotNull PsiElement root, @NotNull ArrayList<FoldingDescriptor> result) {
        Collection<PsiElement> hunks = PsiTreeUtil.findChildrenOfType(root, DiffContextHunkImpl.class);
        hunks.addAll(PsiTreeUtil.findChildrenOfType(root, DiffUnifiedHunkImpl.class));
        hunks.addAll(PsiTreeUtil.findChildrenOfType(root, DiffNormalHunkImpl.class));
        hunks.addAll(PsiTreeUtil.findChildrenOfType(root, DiffGitBinaryPatchImpl.class));
        for (PsiElement element : hunks) {
            addElement(result, element);
        }
    }

    void addElement(@NotNull ArrayList<FoldingDescriptor> result, @NotNull PsiElement element) {
        TextRange range = element.getTextRange();
        if (element.getText().endsWith("\n")) {
            range = new TextRange(range.getStartOffset(), range.getEndOffset() - 1);
        }
        result.add(new FoldingDescriptor(element, range));
    }

    private void buildGitFoldingRegions(@NotNull PsiElement root, @NotNull ArrayList<FoldingDescriptor> result) {
        PsiElement gitHeader = PsiTreeUtil.findChildOfType(root, DiffGitHeaderImpl.class);
        if (gitHeader != null) {
            addElement(result, gitHeader);
            for (PsiElement element : PsiTreeUtil.findChildrenOfType(root, DiffGitDiffImpl.class)) {
                addElement(result, element);
            }
        }
    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull ASTNode node) {
        PsiElement psiNode = node.getPsi();
        if (psiNode instanceof DiffMultiDiffPartImpl) {
            PsiElement commandNode = ((DiffMultiDiffPartImpl) psiNode).getConsoleCommand();
            return commandNode.getText();
        } else if (psiNode instanceof DiffGitHeaderImpl) {
            return ((DiffGitHeaderImpl) psiNode).getPlaceholderText();
        } else if (psiNode instanceof DiffContextHunkImpl) {
            return ((DiffContextHunkImpl) psiNode).getPlaceholderText();
        } else {
            return psiNode.getFirstChild().getText();
        }
    }

    @Override
    public boolean isDumbAware() {
        return true;
    }
}
