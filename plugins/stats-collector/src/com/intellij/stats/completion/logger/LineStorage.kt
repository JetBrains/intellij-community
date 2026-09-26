/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.stats.completion.logger

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class LineStorage {

    private val lines = mutableListOf<ByteArray>()
    var size: Int = 0
        private set

    fun appendLine(line: String) {
        val gzippedLine = line.gzip()
        size += gzippedLine.size
        lines.add(gzippedLine)
    }

    fun dump(dest: File) {
        GZIPOutputStream(dest.outputStream()).bufferedWriter().use { out ->
            lines.forEach { out.appendLine(it.ungzip()) }
        }
    }

    companion object {
        fun readAsZipArray(file: File): ByteArray {
            if (file.name.endsWith(".gz")) {
                return file.readBytes()
            }

            // fallback if non-gzipped files remain from previous versions
            return file.readText().gzip()
        }

        fun readAsLines(file: File): List<String> {
            return readAsZipArray(file).ungzip().lines().filter { it.isNotEmpty() }
        }

        private fun String.gzip(): ByteArray {
            val outputStream = ByteArrayOutputStream()
            GZIPOutputStream(outputStream).use { it.write(toByteArray()) }
            return outputStream.toByteArray()
        }

        private fun ByteArray.ungzip(): String {
            return GZIPInputStream(this.inputStream()).reader().readText()
        }

    }
}