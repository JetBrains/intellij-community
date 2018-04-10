package com.intellij.stats.ngram

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.PersistentStringEnumerator
import java.io.File

class NGramEnumeratingService : Disposable {

    private val stringFile = File(File(PathManager.getIndexRoot(), KEY), "strings")

    private val stringEnumerator: PersistentStringEnumerator

    private val stringCache = ContainerUtil.createConcurrentSoftMap<String, Int>()

    init {
        FileUtilRt.createIfNotExists(stringFile)
        stringEnumerator = PersistentStringEnumerator(stringFile)
    }

    fun enumerateString(s : String) : Int {
        return stringCache.computeIfAbsent(s, { enumerateStringImpl(it) } )
    }

    @Synchronized
    fun valueOf(index: Int) : String {
        return stringEnumerator.valueOf(index) ?: throw IllegalArgumentException(index.toString())
    }

    @Synchronized
    private fun enumerateStringImpl(s : String) : Int {
        return stringEnumerator.enumerate(s)
    }

    override fun dispose() {
        stringEnumerator.close()
    }

    companion object {
        const val KEY = "ngram.stringEnumerator"
        fun getInstance(): NGramEnumeratingService = ServiceManager.getService(NGramEnumeratingService::class.java)
    }
}
