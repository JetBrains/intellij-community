// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.util.io.FileUtil
import com.intellij.pom.java.LanguageLevel.HIGHEST
import com.intellij.pom.java.LanguageLevel.JDK_1_8
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.util.regex.Pattern

private val testHeaderPattern: Pattern = Pattern.compile("//(expression|statement|method|class)\n")

abstract class AbstractJavaToKotlinConverterSingleFileTest : AbstractJavaToKotlinConverterTest() {
    override fun getProjectDescriptor(): LightProjectDescriptor {
        val languageLevel = if (testDataDirectory.toString().contains("newJavaFeatures")) HIGHEST else JDK_1_8
        val testDataFile = File(testDataDirectory, fileName())
        return descriptorByFileDirective(testDataFile, languageLevel)
    }

    override fun setUp() {
        super.setUp()
        JavaCodeStyleSettings.getInstance(project).USE_EXTERNAL_ANNOTATIONS = true
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { JavaCodeStyleSettings.getInstance(project).USE_EXTERNAL_ANNOTATIONS = false },
            ThrowableRunnable { super.tearDown() }
        )
    }

    open fun doTest(javaPath: String) {
        val javaFile = File(javaPath)
        val fileContents = FileUtil.loadFile(javaFile, /* convertLineSeparators = */ true)

        withCustomCompilerOptions(fileContents, project, module) {
            addExternalFiles(javaFile)

            val (prefix, javaCode) = getPrefixAndJavaCode(fileContents)
            val directives = KotlinTestUtils.parseDirectives(javaCode)
            val settings = configureSettings(directives)
            val convertedText = convertJavaToKotlin(prefix, javaFile, javaCode, settings)

            val actualText = if (prefix == "file") {
                createKotlinFile(convertedText).dumpTextWithErrors()
            } else {
                convertedText
            }
            val expectedFile = provideExpectedFile(javaPath)
            KotlinTestUtils.assertEqualsToFile(expectedFile, actualText)
        }
    }

    private fun addExternalFiles(javaFile: File) {
        val externalFileName = "${javaFile.nameWithoutExtension}.external"
        val externalFiles = javaFile.parentFile.listFiles { _, name ->
            name == "$externalFileName.kt" || name == "$externalFileName.java"
        }!!.filterNotNull()

        for (externalFile in externalFiles) {
            addFile(externalFile)
        }
    }

    private fun getPrefixAndJavaCode(fileContents: String): Pair<String, String> {
        val matcher = testHeaderPattern.matcher(fileContents)
        return if (matcher.find()) {
            Pair(matcher.group().trim().substring(2), matcher.replaceFirst(""))
        } else {
            Pair("file", fileContents)
        }
    }

    private fun configureSettings(directives: Directives): ConverterSettings =
        ConverterSettings.defaultSettings.copy().apply {
            directives["FORCE_NOT_NULL_TYPES"]?.let {
                forceNotNullTypes = it.toBoolean()
            }
            directives["SPECIFY_LOCAL_VARIABLE_TYPE_BY_DEFAULT"]?.let {
                specifyLocalVariableTypeByDefault = it.toBoolean()
            }
            directives["SPECIFY_FIELD_TYPE_BY_DEFAULT"]?.let {
                specifyFieldTypeByDefault = it.toBoolean()
            }
            directives["OPEN_BY_DEFAULT"]?.let {
                openByDefault = it.toBoolean()
            }
            directives["PUBLIC_BY_DEFAULT"]?.let {
                publicByDefault = it.toBoolean()
            }
        }

    private fun convertJavaToKotlin(prefix: String, javaFile: File, javaCode: String, settings: ConverterSettings): String {
        fun convertOriginalFile(): String {
            // Don't create a new file from text, just convert the original file, since it is already a proper Java file
            val psiManager = PsiManager.getInstance(project)
            val virtualFile = addFile(javaFile)
            val psiFile = psiManager.findFile(virtualFile) as PsiJavaFile
            return fileToKotlin(psiFile, settings)
        }

        return when (prefix) {
            "expression" -> expressionToKotlin(javaCode, settings)
            "statement" -> statementToKotlin(javaCode, settings)
            "method" -> methodToKotlin(javaCode, settings)
            "class" -> convertOriginalFile().replace("//class\n", "")
            "file" -> convertOriginalFile()
            else -> throw IllegalStateException(
                "Specify what is it: class, method, statement or expression using the first line of test data file"
            )
        }
    }

    open fun provideExpectedFile(javaPath: String): File {
        val kotlinPath = javaPath.replace(".java", ".kt")
        return File(kotlinPath)
    }

    abstract fun fileToKotlin(file: PsiJavaFile, settings: ConverterSettings): String

    private fun methodToKotlin(text: String, settings: ConverterSettings): String {
        val fileText = "final class C {$text}"
        val file = createJavaFile(fileText)
        val result = fileToKotlin(file, settings)
        return result
            .substringBeforeLast("}")
            .replace("internal class C {", "\n")
            .replace("internal object C {", "\n")
            .trimIndent().trim()
    }

    private fun statementToKotlin(text: String, settings: ConverterSettings): String {
        val funBody = text.lines().joinToString(separator = "\n", transform = { "  $it" })
        val result = methodToKotlin("public void main() {\n$funBody\n}", settings)

        return result
            .substringBeforeLast("}")
            .replaceFirst("fun main() {", "\n")
            .trimIndent().trim()
    }

    private fun expressionToKotlin(code: String, settings: ConverterSettings): String {
        val result = statementToKotlin("final Object o =$code}", settings)
        return result
            .replaceFirst("val o: Any? = ", "")
            .replaceFirst("val o: Any = ", "")
            .replaceFirst("val o = ", "")
            .trim()
    }

    private fun createJavaFile(text: String): PsiJavaFile =
        myFixture.configureByText("converterTestFile.java", text) as PsiJavaFile

    private fun createKotlinFile(text: String): KtFile =
        myFixture.configureByText("converterTestFile.kt", text) as KtFile
}
