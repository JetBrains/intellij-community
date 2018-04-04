package com.intellij.stats.ngram.lang

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.ASTNode
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.stats.ngram.AbstractNGramElementProvider


class JavaElementProvider : AbstractNGramElementProvider() {

    override fun getSupportedFileTypes(): Set<FileType> {
        return setOf(JavaFileType.INSTANCE)
    }

    override fun shouldIndex(element: ASTNode, content: CharSequence): Boolean {
        return isJavaIdentifier(content.subSequence(element.textRange.startOffset, element.textRange.endOffset))
    }

    private fun isJavaIdentifier(identifier: CharSequence): Boolean {
        if (identifier.isEmpty()) {
            return false
        }
        if (!StringUtil.isJavaIdentifierStart(identifier[0])) {
            return false
        }
        for (i in 1 until identifier.length) {
            if (!StringUtil.isJavaIdentifierPart(identifier[i])) {
                return false
            }
        }
        return true
    }

}