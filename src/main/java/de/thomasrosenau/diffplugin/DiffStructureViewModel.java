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
import com.intellij.psi.PsiFile;
import de.thomasrosenau.diffplugin.psi.DiffFile;
import org.jetbrains.annotations.NotNull;

public class DiffStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {
    public DiffStructureViewModel(PsiFile psiFile) {
        super(psiFile, new DiffStructureViewElement(psiFile));
    }

    @NotNull
    public Sorter[] getSorters() {
        return Sorter.EMPTY_ARRAY;
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

