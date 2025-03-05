// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.versions

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import org.jetbrains.kotlin.load.java.JvmAnnotationNames.*
import org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import org.jetbrains.org.objectweb.asm.AnnotationVisitor
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes

private val LOG = Logger.getInstance(KotlinJvmMetadataVersionIndex::class.java)

class KotlinJvmMetadataVersionIndex internal constructor() : KotlinMetadataVersionIndexBase<MetadataVersion>() {
    companion object {
        val NAME: ID<MetadataVersion, Void> = ID.create(KotlinJvmMetadataVersionIndex::class.java.canonicalName)
    }

    override fun getName(): ID<MetadataVersion, Void> = NAME

    override fun createBinaryVersion(versionArray: IntArray, extraBoolean: Boolean?): MetadataVersion =
        MetadataVersion(versionArray, isStrictSemantics = extraBoolean!!)

    override fun getIndexer() = INDEXER

    override fun getLogger() = LOG

    override fun getInputFilter() = DefaultFileTypeSpecificInputFilter(JavaClassFileType.INSTANCE)

    override fun getVersion() = 5

    override fun isExtraBooleanNeeded(): Boolean = true

    override fun getExtraBoolean(version: MetadataVersion): Boolean = version.isStrictSemantics

    private val kindsToIndex: Set<KotlinClassHeader.Kind> by lazy {
        setOf(
            KotlinClassHeader.Kind.CLASS,
            KotlinClassHeader.Kind.FILE_FACADE,
            KotlinClassHeader.Kind.MULTIFILE_CLASS
        )
    }

    private val INDEXER: DataIndexer<MetadataVersion, Void, FileContent> by lazy {
        DataIndexer<MetadataVersion, Void, FileContent> { inputData: FileContent ->
            var versionArray: IntArray? = null
            var isStrictSemantics = false
            var annotationPresent = false
            var kind: KotlinClassHeader.Kind? = null

            tryBlock(inputData) {
                val classReader = ClassReader(inputData.content)
                classReader.accept(object : ClassVisitor(Opcodes.API_VERSION) {
                    override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor? {
                        if (desc != METADATA_DESC) return null

                        annotationPresent = true
                        return object : AnnotationVisitor(Opcodes.API_VERSION) {
                            override fun visit(name: String, value: Any) {
                                when (name) {
                                    METADATA_VERSION_FIELD_NAME -> if (value is IntArray) {
                                        versionArray = value
                                    }
                                    KIND_FIELD_NAME -> if (value is Int) {
                                        kind = KotlinClassHeader.Kind.getById(value)
                                    }
                                    METADATA_EXTRA_INT_FIELD_NAME -> if (value is Int) {
                                        isStrictSemantics = (value and METADATA_STRICT_VERSION_SEMANTICS_FLAG) != 0
                                    }
                                }
                            }
                        }
                    }
                }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            }

            var version =
                if (versionArray != null) createBinaryVersion(versionArray!!, isStrictSemantics) else null

            if (kind !in kindsToIndex) {
                // Do not index metadata version for synthetic classes
                version = null
            } else if (annotationPresent && version == null) {
                // No version at all because the class is too old, or version is set to something weird
                version = MetadataVersion.INVALID_VERSION
            }

            if (version != null) mapOf(version to null) else emptyMap()
        }
    }
}
