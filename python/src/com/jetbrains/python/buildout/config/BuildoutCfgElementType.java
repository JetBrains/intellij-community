package com.jetbrains.python.buildout.config;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


public class BuildoutCfgElementType extends IElementType {
    public BuildoutCfgElementType(@NotNull @NonNls String s) {
        super(s, BuildoutCfgLanguage.INSTANCE);
    }
}
