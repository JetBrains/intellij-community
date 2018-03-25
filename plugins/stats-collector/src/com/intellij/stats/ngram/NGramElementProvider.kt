package com.intellij.stats.ngram

import com.intellij.openapi.extensions.Extension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiElement

interface NGramElementProvider {

    /**
     * @return file types which should be indexed
     */
    fun getSupportedFileTypes() : Set<FileType>

    /**
     * @return a language-specific representation for the element
     * if the element is not supported by a provider it should return empty string
     */
    fun getElementRepresentation(element: PsiElement) : String

    /**
     * @return true if the element could appear in a completion list
     */
    fun shouldIndex(element: PsiElement) : Boolean

    companion object {

        fun getSupportedFileTypes() : Set<FileType>  {
            return Extensions.getExtensions(EP_NAME).flatMap { it.getSupportedFileTypes() }.toSet()
        }

        fun getElementRepresentation(element: PsiElement) : String {
            Extensions.getExtensions(EP_NAME).forEach {
                val representation = it.getElementRepresentation(element)
                if (representation.isNotEmpty()) {
                    return@getElementRepresentation representation
                }
            }
            throw IllegalStateException("no suitable representation found")
        }

        fun shouldIndex(element: PsiElement) : Boolean {
            return Extensions.getExtensions(EP_NAME).all { it.shouldIndex(element) }
        }

        val EP_NAME = ExtensionPointName.create<NGramElementProvider>("com.intellij.stats.ngram.ngramElementProvider")
    }
}

abstract class AbstractNGramElementProvider : NGramElementProvider {
    override fun getElementRepresentation(element: PsiElement): String {
        return element.node.elementType.toString()
    }
}
