// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.jps.incremental

import org.jetbrains.kotlin.incremental.LocalFileKotlinClass
import org.jetbrains.kotlin.incremental.ProtoData
import org.jetbrains.kotlin.incremental.storage.ProtoMapValue
import org.jetbrains.kotlin.incremental.toProtoData
import org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader
import org.jetbrains.kotlin.metadata.jvm.deserialization.BitEncoding
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import java.io.File

abstract class AbstractJvmProtoComparisonTest : AbstractProtoComparisonTest<LocalFileKotlinClass>() {
    override fun compileAndGetClasses(sourceDir: File, outputDir: File): Map<ClassId, LocalFileKotlinClass> {
        val extraOptions = listOf("-Xdisable-default-scripting-plugin")
        KotlinCompilerStandalone(listOf(sourceDir), target = outputDir, options = extraOptions).compile()

        val classFiles = outputDir.walkMatching { it.name.endsWith(".class") }
        val localClassFiles = classFiles.map { LocalFileKotlinClass.create(it)!! }
        return localClassFiles.associateBy { it.classId }
    }

    override fun LocalFileKotlinClass.toProtoData(): ProtoData? {
        assert(classHeader.metadataVersion.isCompatible()) { "Incompatible class ($classHeader): $location" }

        val bytes by lazy { BitEncoding.decodeBytes(classHeader.data!!) }
        val strings by lazy { classHeader.strings!! }
        val packageFqName = classId.packageFqName

        return when (classHeader.kind) {
            KotlinClassHeader.Kind.CLASS -> {
                ProtoMapValue(false, bytes, strings).toProtoData(packageFqName)
            }
            KotlinClassHeader.Kind.FILE_FACADE,
            KotlinClassHeader.Kind.MULTIFILE_CLASS_PART -> {
                ProtoMapValue(true, bytes, strings).toProtoData(packageFqName)
            }
            else -> {
                null
            }
        }
    }
}