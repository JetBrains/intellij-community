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

package de.thomasrosenau.diffplugin.psi;

import javax.swing.*;

import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.PsiElement;
import de.thomasrosenau.diffplugin.DiffIcons;

public class DiffNavigationItemPresentation implements ItemPresentation {
    private PsiElement element;

    DiffNavigationItemPresentation(PsiElement element) {
        this.element = element;
    }

    @Override
    public String getPresentableText() {
        if (element instanceof DiffMultiDiffPart) {
            return ((DiffMultiDiffPart) element).getConsoleCommand().getText();
        }
        if (element instanceof DiffGitDiff) {
            return ((DiffGitDiff) element).getConsoleCommand().getText();
        }
        if (element instanceof DiffContextHunkBase) {
            return ((DiffContextHunkBase) element).getPlaceholderText();
        }
        if (element instanceof DiffGitHeaderBase) {
            return ((DiffGitHeaderBase) element).getPlaceholderText();
        }
        return element.getFirstChild().getText();
    }

    @Override
    public String getLocationString() {
        return null;
    }

    @Override
    public Icon getIcon(boolean unused) {
        if (element instanceof DiffFile) {
            return DiffIcons.FILE;
        }
        if (element instanceof DiffMultiDiffPart || element instanceof DiffGitDiff) {
            return DiffIcons.MULTI_DIFF_PART;
        }
        if (element instanceof DiffGitBinaryPatch) {
            return DiffIcons.BINARY_PATCH;
        }
        // TODO: git header
        return DiffIcons.HUNK;
    }
}
