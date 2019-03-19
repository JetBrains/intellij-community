// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.jetbrains.python.codeInsight.completion.PyPathCompletionContributor.getStringLiteral;

public class PathLookupElement extends PythonLookupElement {
    private static final Icon textIcon = IconLoader.getIcon("/fileTypes/text.png");
    private final String file;
    private final Boolean isDirectory;

    PathLookupElement(String path, Boolean isDirectory) {
        super(path, false, isDirectory ? PlatformIcons.FOLDER_ICON: textIcon);
        this.isDirectory = isDirectory;

        int pos = path.lastIndexOf('/');
        file = pos < 0 ? path : path.substring(pos + 1);
    }

    @Nullable
    @Override
    public PsiElement getPsiElement() {
        return getStringLiteral(super.getPsiElement());
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
        presentation.setItemText(file);
        presentation.setIcon(isDirectory ? PlatformIcons.FOLDER_ICON: textIcon);
    }

    @Override
    public boolean isCaseSensitive() {
        return true;
    }
}