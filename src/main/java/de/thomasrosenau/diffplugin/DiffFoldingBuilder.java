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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import de.thomasrosenau.diffplugin.psi.impl.DiffConsoleCommandImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffContextHunkImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffGitBinaryPatchImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffGitFooterImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffGitHeaderImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffNormalHunkImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffUnifiedHunkImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO: add tests
class DiffFoldingBuilder extends FoldingBuilderEx {
    @NotNull
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
        ArrayList<FoldingDescriptor> result = new ArrayList<>();
        buildFileFoldingRegions(root, result);
        buildHunkFoldingRegions(root, result);
        buildGitFoldingRegions(root, result);
        return result.toArray(new FoldingDescriptor[] {});
    }

    // TODO: have parser detect the files rather than doing it here
    private void buildFileFoldingRegions(@NotNull PsiElement root, @NotNull ArrayList<FoldingDescriptor> result) {
        Collection<DiffConsoleCommandImpl> commands = PsiTreeUtil
                .findChildrenOfType(root, DiffConsoleCommandImpl.class);
        DiffConsoleCommandImpl[] commandsAsArray = commands.toArray(new DiffConsoleCommandImpl[] {});
        for (int i = 0; i < commandsAsArray.length; i++) {
            DiffConsoleCommandImpl command = commandsAsArray[i];
            int end;
            if (i < commandsAsArray.length - 1) {
                end = commandsAsArray[i + 1].getTextOffset() - 1;
            } else {
                PsiElement gitSeparator = PsiTreeUtil.getNextSiblingOfType(command, DiffGitFooterImpl.class);
                if (gitSeparator != null) {
                    end = gitSeparator.getTextOffset() - 1;
                } else {
                    end = root.getTextLength();
                }
            }
            result.add(new FoldingDescriptor(command, new TextRange(command.getTextOffset(), end)));
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

    private void buildGitFoldingRegions(@NotNull PsiElement root, @NotNull ArrayList<FoldingDescriptor> result) {
        PsiElement gitHeader = PsiTreeUtil.findChildOfType(root, DiffGitHeaderImpl.class);
        if (gitHeader != null) {
            addElement(result, gitHeader);
        }
    }

    void addElement(@NotNull ArrayList<FoldingDescriptor> result, @NotNull PsiElement element) {
        TextRange range = element.getTextRange();
        if (element.getText().endsWith("\n")) {
            range = new TextRange(range.getStartOffset(), range.getEndOffset() - 1);
        }
        result.add(new FoldingDescriptor(element, range));

    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return node.getPsi() instanceof DiffGitBinaryPatchImpl;
    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull ASTNode node) {
        PsiElement psiNode = node.getPsi();
        if (psiNode instanceof DiffContextHunkImpl) {
            PsiElement fromNode = ((DiffContextHunkImpl) psiNode).getContextHunkFrom().getFirstChild();
            String fromText = fromNode.getText();
            PsiElement toNode = ((DiffContextHunkImpl) psiNode).getContextHunkTo().getFirstChild();
            String toText = toNode.getText();
            return "@@ -" + fromText.substring(4, fromText.length() - 5) + " +" +
                    toText.substring(4, toText.length() - 5) + " @@";
        } else {
            return psiNode.getFirstChild().getText();
        }
    }
}
