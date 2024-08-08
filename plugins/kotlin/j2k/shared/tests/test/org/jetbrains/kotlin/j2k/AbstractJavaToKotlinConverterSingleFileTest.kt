// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.test.util.trimTrailingWhitespacesAndAddNewlineAtEOF
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.idea.util.application.executeCommand
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_NEW
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K2
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.util.regex.Pattern

private val testHeaderPattern: Pattern = Pattern.compile("//(expression|statement|method)\n")

private const val JPA_ANNOTATIONS_DIRECTIVE = "ADD_JPA_ANNOTATIONS"
private const val KOTLIN_API_DIRECTIVE = "ADD_KOTLIN_API"
private const val JAVA_API_DIRECTIVE = "ADD_JAVA_API"
private const val PREPROCESSOR_EXTENSIONS_DIRECTIVE = "INCLUDE_J2K_PREPROCESSOR_EXTENSIONS"
private const val POSTPROCESSOR_EXTENSIONS_DIRECTIVE = "INCLUDE_J2K_POSTPROCESSOR_EXTENSIONS"

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
        val convertedText = convertJavaToKotlin(prefix, javaCode, settings, directives)
        val expectedFile = getExpectedFile(javaFile)

        val actualText = if (prefix == "file") {
            createKotlinFile(convertedText).getFileTextWithErrors()
        } else {
            convertedText
        }

        val actualTextWithoutRedundantImports = removeRedundantImports(actualText)
        KotlinTestUtils.assertEqualsToFile(expectedFile, actualTextWithoutRedundantImports)
    }

    // 1. ".k1.kt" testdata is for trivial differences between K1 and K2 (for example, different wording of error messages).
    // Such files will be deleted along with the whole K1 plugin.
    // In such tests, the K2 testdata with the default ".kt" suffix is considered completely correct.
    //
    // 2. ".k2.kt" testdata is for tests that are mostly good on K2, but there are differences due to minor missing post-processings
    // that we may support later in "idiomatic" mode.
    // Still, we don't want to completely ignore such tests in K2.
    //
    // 3. If the test only has a default version of testdata ".kt", then:
    //   - it may have "IGNORE_K2" directive, in this case the test is completely ignored in K2
    //   - or, if no IGNORE directives are present, the K1 and K2 results are identical for such a test
    private fun getExpectedFile(javaFile: File): File {
        val defaultFile = File(javaFile.path.replace(".java", ".kt"))
        if (!defaultFile.exists()) {
            throw AssertionError("Expected file doesn't exist: ${defaultFile.path}.")
        }

        val customFileExtension = getCustomExpectedFileExtension()
        val customFile = File(javaFile.path.replace(".java", customFileExtension)).takeIf(File::exists)
        if (customFile == null) return defaultFile

        val defaultText = defaultFile.readText().trimTrailingWhitespacesAndAddNewlineAtEOF()
        val customText = customFile.readText().trimTrailingWhitespacesAndAddNewlineAtEOF()
        if (defaultText != customText) return customFile

        customFile.delete()
        throw AssertionError("""
            Custom expected file text is the same as the default one.
            Deleting custom file: ${customFile.path}.
            Please rerun the test now.""".trimIndent()
        )
    }

    private fun getCustomExpectedFileExtension(): String = when (pluginMode) {
        KotlinPluginMode.K1 -> ".k1.kt"
        KotlinPluginMode.K2 -> ".k2.kt"
        else -> error("Can't determine the plugin mode")
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

    private fun convertJavaToKotlin(prefix: String, javaCode: String, settings: ConverterSettings, directives: Directives): String {
        val preprocessorExtensions =
            if (directives.contains(PREPROCESSOR_EXTENSIONS_DIRECTIVE)) (listOf(J2kTestPreprocessorExtension) + J2kPreprocessorExtension.EP_NAME.extensionList) else J2kPreprocessorExtension.EP_NAME.extensionList
        val postprocessorExtensions =
            if (directives.contains(POSTPROCESSOR_EXTENSIONS_DIRECTIVE)) (listOf(J2kTestPostprocessorExtension) + J2kPostprocessorExtension.EP_NAME.extensionList) else J2kPostprocessorExtension.EP_NAME.extensionList

        return when (prefix) {
            "expression" -> expressionToKotlin(javaCode, settings)
            "statement" -> statementToKotlin(javaCode, settings)
            "method" -> methodToKotlin(javaCode, settings)
            "file" -> fileToKotlin(javaCode, settings, preprocessorExtensions, postprocessorExtensions)
            else -> error("Specify what it is: method, statement or expression using the first line of test data file")
        }
    }

    open fun fileToKotlin(
        text: String,
        settings: ConverterSettings,
        preprocessorExtensions: List<J2kPreprocessorExtension> = J2kPreprocessorExtension.EP_NAME.extensionList,
        postprocessorExtensions: List<J2kPostprocessorExtension> = J2kPostprocessorExtension.EP_NAME.extensionList
    ): String {
        val file = createJavaFile(text)
        val j2kKind = if (isFirPlugin) K2 else K1_NEW
        val extension = J2kConverterExtension.extension(j2kKind)
        val converter = extension.createJavaToKotlinConverter(project, module, settings)
        val postProcessor = extension.createPostProcessor()
        var converterResult: FilesResult? = null
        val process = {
            converterResult = converter.filesToKotlin(
                listOf(file),
                postProcessor,
                EmptyProgressIndicator(),
                preprocessorExtensions,
                postprocessorExtensions
            )
        }
        project.executeCommand("J2K") {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(process, "Testing J2K", /* canBeCanceled = */ true, project)
        }
        return checkNotNull(converterResult).results.single()
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
