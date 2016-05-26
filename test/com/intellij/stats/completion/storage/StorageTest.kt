package com.intellij.stats.completion.storage

import com.intellij.stats.completion.UniqueFilesProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileFilter


class FilesProviderTest {
    
    var root = File(".")
    
    lateinit var provider: UniqueFilesProvider

    @Before
    fun setUp() {
        provider = UniqueFilesProvider("chunk", root)
        removeAllFilesInStatsDataDirectory()
    }

    @After
    fun tearDown() {
        removeAllFilesInStatsDataDirectory()
    }

    private fun removeAllFilesInStatsDataDirectory() {
        val dir = provider.getStatsDataDirectory()
        dir.listFiles(FileFilter { it.isFile }).forEach { it.delete() }
    }

    @Test
    fun test_three_new_files_created() {
        val provider = UniqueFilesProvider("chunk", root)
        
        provider.getUniqueFile().createNewFile()
        provider.getUniqueFile().createNewFile()
        provider.getUniqueFile().createNewFile()

        val directory = provider.getStatsDataDirectory()
        val createdFiles = directory
                .listFiles(FileFilter { it.isFile })
                .filter { it.name.startsWith("chunk") }
                .count()
        
        assertThat(createdFiles).isEqualTo(3)
    }
    
    
    
}