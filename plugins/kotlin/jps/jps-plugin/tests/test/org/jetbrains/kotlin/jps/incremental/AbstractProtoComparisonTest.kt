// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.incremental

import org.jetbrains.kotlin.TestWithWorkingDir
import org.jetbrains.kotlin.incremental.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.utils.Printer
import java.io.File

abstract class AbstractProtoComparisonTest<PROTO_DATA> : TestWithWorkingDir() {
    protected abstract fun compileAndGetClasses(sourceDir: File, outputDir: File): Map<ClassId, PROTO_DATA>
    protected abstract fun PROTO_DATA.toProtoData(): ProtoData?

    protected open fun expectedOutputFile(testDir: File): File =
        File(testDir, "result.out")

    fun doTest(testDataPath: String) {
        val testDir = File(testDataPath)

        val oldClassMap = classesForPrefixedSources(testDir, workingDir, "old")
        val newClassMap = classesForPrefixedSources(testDir, workingDir, "new")

        val sb = StringBuilder()
        val p = Printer(sb)

        (oldClassMap.keys - newClassMap.keys).sortedBy { it.toString() }.forEach { classId ->
            p.println("REMOVED $classId")
        }

        (newClassMap.keys - oldClassMap.keys).sortedBy { it.toString() }.forEach { classId ->
            p.println("ADDED $classId")
        }

        (oldClassMap.keys.intersect(newClassMap.keys)).sortedBy { it.toString() }.forEach { classId ->
            val oldData = oldClassMap[classId]!!.toProtoData()
            val newData = newClassMap[classId]!!.toProtoData()

            if (oldData == null || newData == null) {
                p.println("SKIPPED $classId")
                return@forEach
            }

            val rawProtoDifference = when {
                oldData is ClassProtoData && newData is ClassProtoData -> {
                    ProtoCompareGenerated(
                        oldNameResolver = oldData.nameResolver,
                        newNameResolver = newData.nameResolver,
                        oldTypeTable = oldData.proto.typeTableOrNull,
                        newTypeTable = newData.proto.typeTableOrNull
                    ).difference(oldData.proto, newData.proto)
                }
                oldData is PackagePartProtoData && newData is PackagePartProtoData -> {
                    ProtoCompareGenerated(
                        oldNameResolver = oldData.nameResolver,
                        newNameResolver = newData.nameResolver,
                        oldTypeTable = oldData.proto.typeTableOrNull,
                        newTypeTable = newData.proto.typeTableOrNull
                    ).difference(oldData.proto, newData.proto)
                }
                else -> null
            }
            rawProtoDifference?.let {
                if (it.isNotEmpty()) {
                    p.println("PROTO DIFFERENCE in $classId: ${it.sortedBy { it.name }.joinToString()}")
                }
            }

            val changesInfo = ChangesCollector().apply { collectProtoChanges(oldData, newData) }.changes()
            if (changesInfo.isEmpty()) {
                return@forEach
            }

            val changes = changesInfo.map {
                when (it) {
                    is ChangeInfo.SignatureChanged -> "CLASS_SIGNATURE"
                    is ChangeInfo.MembersChanged -> "MEMBERS\n    ${it.names.sorted()}"
                    is ChangeInfo.ParentsChanged -> "PARENTS\n    ${it.parentsChanged.map { it.asString()}.sorted()}"

                }
            }.sorted()

            p.println("CHANGES in $classId: ${changes.joinToString()}")
        }

        KotlinTestUtils.assertEqualsToFile(expectedOutputFile(testDir), sb.toString())
    }

    private fun classesForPrefixedSources(testDir: File, workingDir: File, prefix: String): Map<ClassId, PROTO_DATA> {
        val srcDir = workingDir.createSubDirectory("$prefix/src")
        val outDir = workingDir.createSubDirectory("$prefix/out")
        copySourceFiles(testDir, srcDir, prefix)
        return compileAndGetClasses(srcDir, outDir)
    }

    private fun copySourceFiles(sourceDir: File, targetDir: File, prefix: String) {
        for (srcFile in sourceDir.walkMatching { it.name.startsWith(prefix) }) {
            val targetFile = File(targetDir, srcFile.name.replaceFirst(prefix, "main"))
            srcFile.copyTo(targetFile)
        }
    }

    protected fun File.createSubDirectory(relativePath: String): File =
            File(this, relativePath).apply { mkdirs() }

    protected fun File.walkMatching(predicate: (File)->Boolean): Sequence<File> =
            walk().filter { predicate(it) }
}
