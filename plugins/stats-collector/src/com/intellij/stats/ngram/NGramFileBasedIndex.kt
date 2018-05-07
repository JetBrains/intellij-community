package com.intellij.stats.ngram

import com.intellij.lang.ASTNode
import com.intellij.openapi.module.ModuleUtil
import com.intellij.plugin.NGramIndexingProperty
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import java.io.DataInput
import java.io.DataOutput

class NGramFileBasedIndex : FileBasedIndexExtension<NGram, Int>() {
    private val inputFilter = DefaultFileTypeSpecificInputFilter(*NGramElementProvider.getSupportedFileTypes().toTypedArray())

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

        fun requestRebuild() {
            FileBasedIndex.getInstance().requestRebuild(KEY)
        }

        fun getNumberOfOccurrences(key: NGram, scope: GlobalSearchScope): Int {
            return FileBasedIndex.getInstance().getValues(KEY, key, scope).sum()
        }
    }
}

private object NGramIndexer : DataIndexer<NGram, Int, FileContent> {
    override fun map(fileContent: FileContent): Map<NGram, Int> {
        if (ModuleUtil.findModuleForFile(fileContent.file, fileContent.project) == null ||
                !NGramIndexingProperty.isEnabled(fileContent.project)) {
            return emptyMap()
        }
        return NGram.processFile(fileContent.psiFile, fileContent.contentAsText)
    }
}

class TreeTraversal : PsiRecursiveElementVisitor() {
    val elements = ArrayList<ASTNode>()

    private fun dfs(node: ASTNode) {
        var start = node.firstChildNode
        if (start != null) {
            elements.add(node)
        }
        while (start != null) {
            dfs(start)
            start = start.treeNext
        }
    }


    companion object {
        fun getElements(file: PsiFile): ArrayList<ASTNode> {
            val start = file.node
            val treeTraversal = TreeTraversal()
            treeTraversal.dfs(start)
            return treeTraversal.elements
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

