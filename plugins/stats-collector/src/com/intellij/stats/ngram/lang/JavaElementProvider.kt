package com.intellij.stats.ngram.lang

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiElement
import com.intellij.stats.ngram.AbstractNGramElementProvider


class JavaElementProvider : AbstractNGramElementProvider() {
    private val identifierRegex = Regex("[a-zA-Z_][a-zA-z_0-9]*")

    override fun getSupportedFileTypes(): Set<FileType> {
        return setOf(JavaFileType.INSTANCE)
    }

    override fun shouldIndex(element: PsiElement): Boolean {
        return identifierRegex.matches(element.text)
    }

}