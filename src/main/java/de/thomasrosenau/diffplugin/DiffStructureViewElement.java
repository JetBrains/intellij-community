/*!
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
import java.util.List;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import de.thomasrosenau.diffplugin.psi.DiffFile;
import de.thomasrosenau.diffplugin.psi.impl.DiffContextHunkImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffGitBinaryPatchImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffGitDiffImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffGitHeaderImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffMultiDiffPartImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffNormalHunkImpl;
import de.thomasrosenau.diffplugin.psi.impl.DiffUnifiedHunkImpl;
import org.jetbrains.annotations.NotNull;

public class DiffStructureViewElement implements StructureViewTreeElement, SortableTreeElement {
    private NavigatablePsiElement element;

    DiffStructureViewElement(NavigatablePsiElement element) {
        this.element = element;
    }

    @Override
    public Object getValue() {
        return element;
    }

    @Override
    public void navigate(boolean requestFocus) {
        element.navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
        return element.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        return element.canNavigateToSource();
    }

    @NotNull
    @Override
    public String getAlphaSortKey() {
        return "";
    }

    @NotNull
    @Override
    public ItemPresentation getPresentation() {
        ItemPresentation presentation = element.getPresentation();
        return presentation != null ? presentation : new PresentationData();
    }

    @NotNull
    @Override
    public TreeElement[] getChildren() {
        if (element instanceof DiffFile) {
            return getDiffFileChildren(element);
        } else {
            List<TreeElement> treeElements = new ArrayList<>();
            addAllHunks(treeElements, element);
            return treeElements.toArray(TreeElement.EMPTY_ARRAY);
        }
    }

    private TreeElement[] getDiffFileChildren(NavigatablePsiElement element) {
        List<TreeElement> treeElements = new ArrayList<>();
        boolean isMulti = false;
        for (DiffMultiDiffPartImpl hunk : PsiTreeUtil.findChildrenOfType(element, DiffMultiDiffPartImpl.class)) {
            treeElements.add(new DiffStructureViewElement(hunk));
            isMulti = true;
        }
        DiffGitHeaderImpl gitHeader = PsiTreeUtil.findChildOfType(element, DiffGitHeaderImpl.class);
        if (gitHeader != null) {
            treeElements.add(new DiffStructureViewElement(gitHeader));
            for (DiffGitDiffImpl hunk : PsiTreeUtil.findChildrenOfType(element, DiffGitDiffImpl.class)) {
                treeElements.add(new DiffStructureViewElement(hunk));
            }
        } else if (!isMulti) {
            addAllHunks(treeElements, element);
        }
        return treeElements.toArray(TreeElement.EMPTY_ARRAY);
    }

    private static void addAllHunks(List<TreeElement> list, NavigatablePsiElement element) {
        for (DiffContextHunkImpl hunk : PsiTreeUtil.findChildrenOfType(element, DiffContextHunkImpl.class)) {
            list.add(new DiffStructureViewElement(hunk));
        }
        for (DiffUnifiedHunkImpl hunk : PsiTreeUtil.findChildrenOfType(element, DiffUnifiedHunkImpl.class)) {
            list.add(new DiffStructureViewElement(hunk));
        }
        for (DiffNormalHunkImpl hunk : PsiTreeUtil.findChildrenOfType(element, DiffNormalHunkImpl.class)) {
            list.add(new DiffStructureViewElement(hunk));
        }
        for (DiffGitBinaryPatchImpl hunk : PsiTreeUtil.findChildrenOfType(element, DiffGitBinaryPatchImpl.class)) {
            list.add(new DiffStructureViewElement(hunk));
        }
    }
}
