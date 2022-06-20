// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.perf.util

import com.intellij.openapi.util.io.FileUtilRt
import com.sun.management.HotSpotDiagnosticMXBean
import org.jetbrains.kotlin.idea.performance.tests.utils.TeamCity
import org.jetbrains.kotlin.idea.performance.tests.utils.logMessage
import java.lang.management.ManagementFactory
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

object HeapDumper {
    private const val HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic"

    private val hotspotMBean = initHotspotMBean()

    private fun initHotspotMBean() = ManagementFactory.newPlatformMXBeanProxy(
        ManagementFactory.getPlatformMBeanServer(),
        HOTSPOT_BEAN_NAME,
        HotSpotDiagnosticMXBean::class.java
    )

    fun dumpHeap(fileNamePrefix: String, live: Boolean = true) {
        val format = SimpleDateFormat("yyyyMMdd-HHmmss")
        val timestamp = format.format(Date())
        val tempFile = createTempFile(fileNamePrefix, ".hprof")
        tempFile.deleteIfExists()
        val fileName = "build/$fileNamePrefix-$timestamp.hprof.zip"
        logMessage { "Dumping a heap into $tempFile ..." }
        try {
            hotspotMBean.dumpHeap(tempFile.toString(), live)
            logMessage { "Heap dump is $tempFile ready." }

            zipFile(tempFile, Path(fileName))

            val testName = "Heap dump $timestamp"
            TeamCity.test(testName) {
                TeamCity.artifact(testName, "heapDump", fileName)
            }
        } catch (e: Exception) {
            logMessage { "Error on making a heap dump: ${e.message}" }
            e.printStackTrace()
        }
    }

    private fun zipFile(srcFile: Path, targetFile: Path) {
        srcFile.inputStream().use { fis ->
            ZipOutputStream(targetFile.outputStream()).use { os ->
                os.putNextEntry(ZipEntry(srcFile.name))
                FileUtilRt.copy(fis, os)
                os.closeEntry()
            }
        }
    }
}