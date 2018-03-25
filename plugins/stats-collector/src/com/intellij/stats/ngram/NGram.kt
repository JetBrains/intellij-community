package com.intellij.stats.ngram

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.KeyDescriptor
import java.io.DataInput
import java.io.DataOutput

data class NGram(val elements: List<String>) {

    fun append(token: String): NGram {
        return NGram(listOf(*elements.toTypedArray(), token))
    }

    fun dropHead(): NGram {
        return NGram(elements.subList(1, elements.size))
    }

    companion object {
        val INVALID = NGram(emptyList())

        val N = 5

        fun processFile(psiFile: PsiFile): HashMap<NGram, Int> {
            val result = HashMap<NGram, Int>()
            val elements = TreeTraversal.getElements(psiFile)
            if (elements.size > N) {
                for (i in N until elements.size) {
                    if (NGramElementProvider.shouldIndex(elements[i])) {
                        val nGramElements = ArrayList<String>()
                        for (j in 1..N) {
                            nGramElements.add(NGramElementProvider.getElementRepresentation(elements[i - j]))
                        }
                        nGramElements.add(elements[i].text)
                        for (j in 0..N) {
                            val ngram = NGram(nGramElements.subList(j, nGramElements.size))
                            val oldValue = result.putIfAbsent(ngram, 1)
                            oldValue?.let {
                                result.put(ngram, oldValue + 1)
                            }
                        }
                    }
                }
            }
            return result
        }

        fun getNGramForElement(element: PsiElement) : NGram {
            val elements = TreeTraversal.getElements(element.containingFile ?: return NGram.INVALID)
            val index = elements.indexOf(element.parent)
            if (index == -1) {
                return NGram.INVALID
            }
            val nGramElements = ArrayList<String>()
            for (i in 1..NGram.N) {
                nGramElements.add(elements[index - i].node.elementType.toString())
            }
            return NGram(nGramElements)
        }
    }
}

fun DataOutput.writeINT(x : Int) = DataInputOutputUtil.writeINT(this, x)
fun DataInput.readINT() : Int = DataInputOutputUtil.readINT(this)

object NGramKeyDescriptor: KeyDescriptor<NGram> {
    override fun save(out: DataOutput, nGram: NGram?) {
        val instance = NGramEnumeratingService.getInstance()
        out.writeINT(nGram!!.elements.size)
        nGram.elements.forEach { out.writeINT(instance.enumerateString(it)) }
    }

    override fun read(`in`: DataInput): NGram {
        val instance = NGramEnumeratingService.getInstance()
        val size = `in`.readINT()
        return NGram((1..size).map { instance.valueOf(`in`.readINT()) })
    }

    override fun isEqual(p0: NGram?, p1: NGram?): Boolean {
        return p0 == p1
    }

    override fun getHashCode(p0: NGram?): Int {
        return p0!!.hashCode()
    }
}