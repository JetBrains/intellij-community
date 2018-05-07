package com.intellij.stats.ngram

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.PagedFileStorage
import com.intellij.util.io.PersistentStringEnumerator
import java.io.File

class NGramEnumeratingService : Disposable {

    private val stringFile = File(File(PathManager.getIndexRoot(), KEY), "strings")

    private val stringEnumerator: PersistentStringEnumerator

    private val stringCache = ContainerUtil.createConcurrentSoftMap<String, Int>()
    private val storageLockContext = PagedFileStorage.StorageLockContext(true)

    init {
        FileUtilRt.createIfNotExists(stringFile)
        stringEnumerator = PersistentStringEnumerator(stringFile, storageLockContext)
    }

    fun enumerateString(s: String): Int {
        return stringCache.computeIfAbsent(s, { enumerateStringImpl(it) })
    }

    fun valueOf(index: Int): String {
        try {
            storageLockContext.lock()
            return stringEnumerator.valueOf(index) ?: throw IllegalArgumentException(index.toString())
        } finally {
            storageLockContext.unlock()
        }
    }

    private fun enumerateStringImpl(s: String): Int {
        try {
            storageLockContext.lock()
            return stringEnumerator.enumerate(s)
        } finally {
            storageLockContext.unlock()
        }
    }

    override fun dispose() {
        stringEnumerator.close()
    }

    companion object {
        const val KEY = "ngram.stringEnumerator"
        fun getInstance(): NGramEnumeratingService = ServiceManager.getService(NGramEnumeratingService::class.java)
    }
}
