// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.reStructuredText;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class RestElementType extends IElementType {
    public RestElementType(@NotNull @NonNls String s) {
        super(s, RestLanguage.INSTANCE);
    }
}
