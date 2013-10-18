package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyPrintTarget;

/**
 * @author yole
 */
public class PyPrintTargetImpl extends PyElementImpl implements PyPrintTarget {
    public PyPrintTargetImpl(ASTNode astNode) {
        super(astNode);
    }
}
