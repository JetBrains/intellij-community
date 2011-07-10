package com.jetbrains.python.findUsages;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonLexer;

/**
 * @author yole
 */
public class PyWordsScanner extends DefaultWordsScanner {
    public PyWordsScanner() {
        super(new PythonLexer(),
                TokenSet.create(PyTokenTypes.IDENTIFIER),
                TokenSet.create(PyTokenTypes.END_OF_LINE_COMMENT),
                PyTokenTypes.STRING_NODES);
    }
}
