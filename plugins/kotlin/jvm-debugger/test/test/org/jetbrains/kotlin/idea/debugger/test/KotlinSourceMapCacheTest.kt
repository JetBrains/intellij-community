// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.kotlin.idea.debugger.base.util.KotlinSourceMapCache
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.resolve.jvm.JvmClassName
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream

@RunWith(JUnit4::class)
class KotlinSourceMapCacheTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR

    @Test
    fun testReadsSmapFromJarOutputRoot() {
        val sourceRoot = myFixture.tempDirFixture.findOrCreateDir("src")
        PsiTestUtil.addSourceRoot(module, sourceRoot)

        val sourceFile = myFixture.addFileToProject(
            "src/test/Foo.kt",
            """
                package test

                fun foo() = 42
            """.trimIndent()
        ).virtualFile

        val outputJar = Files.createTempFile("kotlin-source-map-cache", ".jar")
        try {
            PsiTestUtil.setCompilerOutputPath(module, outputJar.toString(), false)

            val jvmName = JvmClassName.byInternalName("test/FooKt")
            createJarWithClass(
                outputJar,
                "${jvmName.internalName}.class",
                createClassBytes(jvmName.internalName, sourceFile.name)
            )

            val sourceMap = KotlinSourceMapCache.getInstance(project).getSourceMap(sourceFile, jvmName)
            assertNotNull(sourceMap)
            assertTrue(sourceMap!!.fileMappings.any { it.name == "Foo.kt" })
            assertNotNull(sourceMap.findRange(1))
        } finally {
            outputJar.deleteIfExists()
        }
    }

    private fun createJarWithClass(jarFile: Path, entryName: String, classBytes: ByteArray) {
        ZipOutputStream(jarFile.outputStream().buffered()).use { zipStream ->
            zipStream.putNextEntry(ZipEntry(entryName))
            zipStream.write(classBytes)
            zipStream.closeEntry()
        }
    }

    private fun createClassBytes(classInternalName: String, sourceName: String): ByteArray {
        val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS or ClassWriter.COMPUTE_FRAMES)
        classWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL, classInternalName, null, "java/lang/Object", null)
        classWriter.visitSource(sourceName, createSmap(sourceName, classInternalName))

        val fooMethod: MethodVisitor = classWriter.visitMethod(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL,
            "foo",
            "()I",
            null,
            null
        )
        fooMethod.visitCode()
        fooMethod.visitLdcInsn(42)
        fooMethod.visitInsn(Opcodes.IRETURN)
        fooMethod.visitMaxs(0, 0)
        fooMethod.visitEnd()

        classWriter.visitEnd()
        return classWriter.toByteArray()
    }

    companion object {
        private fun createSmap(sourceName: String, classInternalName: String): String = """
            SMAP
            $sourceName
            Kotlin
            *S Kotlin
            *F
            + 1 $sourceName
            $classInternalName
            *L
            1#1,1:1
            *E
        """.trimIndent() + "\n"
    }
}
