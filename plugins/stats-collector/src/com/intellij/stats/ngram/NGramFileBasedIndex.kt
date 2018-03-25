package com.intellij.stats.ngram

import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import java.io.DataInput
import java.io.DataOutput

class NGramFileBasedIndex : FileBasedIndexExtension<NGram, Int>() {
    val inputFilter = DefaultFileTypeSpecificInputFilter(*NGramElementProvider.getSupportedFileTypes().toTypedArray())

    override fun getValueExternalizer(): DataExternalizer<Int> {
        return IntDataExternalizer
    }

    override fun getName(): ID<NGram, Int> {
        return KEY
    }

    override fun getVersion(): Int {
        return INDEX_VERSION
    }

    override fun dependsOnFileContent(): Boolean {
        return true
    }

    override fun getIndexer(): DataIndexer<NGram, Int, FileContent> {
        return NGramIndexer
    }

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return inputFilter
    }

    override fun getKeyDescriptor(): KeyDescriptor<NGram> {
        return NGramKeyDescriptor
    }

    companion object {
        val KEY = ID.create<NGram, Int>("ngram.index")
        const val INDEX_VERSION = 1

        fun getNumberOfOccurrences(key: NGram, scope: GlobalSearchScope): Int {
            return FileBasedIndex.getInstance().getValues(KEY, key, scope).sum()
        }
    }
}

private object NGramIndexer : DataIndexer<NGram, Int, FileContent> {
    override fun map(fileContent: FileContent): MutableMap<NGram, Int> {
        if (ModuleUtil.findModuleForFile(fileContent.file, fileContent.project) == null) {
            return HashMap()
        }
        return NGram.processFile(fileContent.psiFile)
    }
}

class TreeTraversal : PsiRecursiveElementVisitor() {
    val elements = ArrayList<PsiElement>()


    override fun visitElement(element: PsiElement?) {
        element?.let { if (it.children.isNotEmpty()) elements.add(it) }
        super.visitElement(element)
    }

    companion object {
        fun getElements(file: PsiFile): List<PsiElement> {
            val visitor = TreeTraversal()
            file.accept(visitor)
            return visitor.elements
        }
    }
}

object IntDataExternalizer: DataExternalizer<Int> {
    override fun save(data: DataOutput, value: Int?) {
        data.writeInt(value!!)
    }

    override fun read(data: DataInput): Int {
        return data.readInt()
    }
}

