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
import de.thomasrosenau.diffplugin.psi.DiffContextHunk;
import de.thomasrosenau.diffplugin.psi.DiffContextHunkBase;
import de.thomasrosenau.diffplugin.psi.DiffGitBinaryPatch;
import de.thomasrosenau.diffplugin.psi.DiffGitDiff;
import de.thomasrosenau.diffplugin.psi.DiffGitHeader;
import de.thomasrosenau.diffplugin.psi.DiffGitHeaderBase;
import de.thomasrosenau.diffplugin.psi.DiffMultiDiffPart;
import de.thomasrosenau.diffplugin.psi.DiffNormalHunk;
import de.thomasrosenau.diffplugin.psi.DiffUnifiedHunk;
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
        return node.getPsi() instanceof DiffGitBinaryPatch;
    }

    private void buildFileFoldingRegions(@NotNull PsiElement root, @NotNull ArrayList<FoldingDescriptor> result) {
        for (PsiElement element : PsiTreeUtil.findChildrenOfType(root, DiffMultiDiffPart.class)) {
            addElement(result, element);
        }
    }

    private void buildHunkFoldingRegions(@NotNull PsiElement root, @NotNull ArrayList<FoldingDescriptor> result) {
        Collection<PsiElement> hunks = PsiTreeUtil.findChildrenOfType(root, DiffContextHunk.class);
        hunks.addAll(PsiTreeUtil.findChildrenOfType(root, DiffUnifiedHunk.class));
        hunks.addAll(PsiTreeUtil.findChildrenOfType(root, DiffNormalHunk.class));
        hunks.addAll(PsiTreeUtil.findChildrenOfType(root, DiffGitBinaryPatch.class));
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
        PsiElement gitHeader = PsiTreeUtil.findChildOfType(root, DiffGitHeader.class);
        if (gitHeader != null) {
            addElement(result, gitHeader);
            for (PsiElement element : PsiTreeUtil.findChildrenOfType(root, DiffGitDiff.class)) {
                addElement(result, element);
            }
        }
    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull ASTNode node) {
        PsiElement psiNode = node.getPsi();
        if (psiNode instanceof DiffMultiDiffPart) {
            PsiElement commandNode = ((DiffMultiDiffPart) psiNode).getConsoleCommand();
            return commandNode.getText();
        } else if (psiNode instanceof DiffGitHeaderBase) {
            return ((DiffGitHeaderBase) psiNode).getPlaceholderText();
        } else if (psiNode instanceof DiffContextHunkBase) {
            return ((DiffContextHunkBase) psiNode).getPlaceholderText();
        } else {
            return psiNode.getFirstChild().getText();
        }
    }

    @Override
    public boolean isDumbAware() {
        return true;
    }
}
