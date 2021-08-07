// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.framework.ui;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class CreateLibraryDialog extends CreateLibraryDialogBase {

    public CreateLibraryDialog(@NotNull String defaultPath, @Nls @NotNull String title, @Nls @NotNull String libraryCaption) {
        super(null, defaultPath, title, libraryCaption);
        updateComponents();
    }
}
