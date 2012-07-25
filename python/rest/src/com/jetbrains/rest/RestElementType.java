package com.jetbrains.rest;

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
