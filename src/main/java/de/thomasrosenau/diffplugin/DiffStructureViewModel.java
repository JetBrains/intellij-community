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

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import de.thomasrosenau.diffplugin.psi.DiffContextHunk;
import de.thomasrosenau.diffplugin.psi.DiffFile;
import de.thomasrosenau.diffplugin.psi.DiffGitBinaryPatch;
import de.thomasrosenau.diffplugin.psi.DiffGitDiff;
import de.thomasrosenau.diffplugin.psi.DiffGitHeader;
import de.thomasrosenau.diffplugin.psi.DiffMultiDiffPart;
import de.thomasrosenau.diffplugin.psi.DiffNormalHunk;
import de.thomasrosenau.diffplugin.psi.DiffUnifiedHunk;
import org.jetbrains.annotations.NotNull;

public class DiffStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {
    private static Class[] CLASSES = new Class[] {DiffContextHunk.class, DiffNormalHunk.class, DiffUnifiedHunk.class,
            DiffGitHeader.class, DiffGitBinaryPatch.class, DiffGitDiff.class, DiffMultiDiffPart.class};

    public DiffStructureViewModel(PsiFile psiFile, Editor editor) {
        super(psiFile, editor, new DiffStructureViewElement(psiFile));
    }

    @NotNull
    public Sorter[] getSorters() {
        return Sorter.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    protected Class[] getSuitableClasses() {
        return CLASSES;
    }

    @Override
    public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
        return false;
    }

    @Override
    public boolean isAlwaysLeaf(StructureViewTreeElement element) {
        return element instanceof DiffFile;
    }
}
