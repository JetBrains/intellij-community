// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.psi.PsiJavaFile
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_NEW
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K2
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.util.regex.Pattern

private val testHeaderPattern: Pattern = Pattern.compile("//(expression|statement|method)\n")

private const val JPA_ANNOTATIONS_DIRECTIVE = "ADD_JPA_ANNOTATIONS"
private const val KOTLIN_API_DIRECTIVE = "ADD_KOTLIN_API"
private const val JAVA_API_DIRECTIVE = "ADD_JAVA_API"

abstract class AbstractJavaToKotlinConverterSingleFileTest : AbstractJavaToKotlinConverterTest() {
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
        val fileContents = javaFile.getFileTextWithoutDirectives()

        IgnoreTests.runTestIfNotDisabledByFileDirective(javaFile.toPath(), getDisableTestDirective()) {
            withCustomCompilerOptions(fileContents, project, module) {
                doTest(javaFile, fileContents)
            }
        }
    }

    private fun doTest(javaFile: File, fileContents: String) {
        val (prefix, javaCode) = getPrefixAndJavaCode(fileContents)
        val directives = KotlinTestUtils.parseDirectives(javaCode)

        addExternalFiles(javaFile)
        addDependencies(directives)

        val settings = configureSettings(directives)
        val convertedText = convertJavaToKotlin(prefix, javaCode, settings)
        val expectedFile = File(javaFile.path.replace(".java", ".kt"))
        val shouldCheckForErrors = expectedFile.readText().contains(ERROR_HEADER)

        val actualText = if (prefix == "file" && shouldCheckForErrors) {
            // Optimization: K2 `getFileTextWithErrors` is expensive
            createKotlinFile(convertedText).getFileTextWithErrors()
        } else {
            convertedText
        }

        val actualTextWithoutRedundantImports = removeRedundantImports(actualText)
        KotlinTestUtils.assertEqualsToFile(expectedFile, actualTextWithoutRedundantImports)
    }

    private fun addDependencies(directives: Directives) {
        if (directives.contains(JPA_ANNOTATIONS_DIRECTIVE)) addJpaColumnAnnotations()
        if (directives.contains(KOTLIN_API_DIRECTIVE)) addFile("KotlinApi.kt", "kotlinApi")
        if (directives.contains(JAVA_API_DIRECTIVE)) addFile("JavaApi.java", "javaApi")
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
            directives["BASIC_MODE"]?.let {
                basicMode = it.toBoolean()
            }
        }

    private fun convertJavaToKotlin(prefix: String, javaCode: String, settings: ConverterSettings): String =
        when (prefix) {
            "expression" -> expressionToKotlin(javaCode, settings)
            "statement" -> statementToKotlin(javaCode, settings)
            "method" -> methodToKotlin(javaCode, settings)
            "file" -> fileToKotlin(javaCode, settings)
            else -> error("Specify what it is: method, statement or expression using the first line of test data file")
        }

    open fun fileToKotlin(text: String, settings: ConverterSettings): String {
        val file = createJavaFile(text)
        val j2kKind = if (isFirPlugin) K2 else K1_NEW
        val extension = J2kConverterExtension.extension(j2kKind)
        val converter = extension.createJavaToKotlinConverter(project, module, settings)
        val postProcessor = extension.createPostProcessor()
        return converter.filesToKotlin(listOf(file), postProcessor).results.single()
    }

    private fun methodToKotlin(text: String, settings: ConverterSettings): String {
        val result = fileToKotlin("final class C {$text}", settings)
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

    protected fun createJavaFile(text: String): PsiJavaFile =
        myFixture.configureByText("converterTestFile.java", text) as PsiJavaFile

    private fun createKotlinFile(text: String): KtFile =
        myFixture.configureByText("converterTestFile.kt", text) as KtFile
}
