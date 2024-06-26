// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K1_NEW
import org.jetbrains.kotlin.j2k.J2kConverterExtension.Kind.K2
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import java.io.File
import java.util.regex.Pattern
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.PsiImplUtil.setName
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.idea.base.test.IgnoreTests.DIRECTIVES.J2K_POSTPROCESSOR_EXTENSIONS
import org.jetbrains.kotlin.j2k.J2kPostprocessorExtension.Companion.j2kWriteAction
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType

private val testHeaderPattern: Pattern = Pattern.compile("//(expression|statement|method)\n")

private const val JPA_ANNOTATIONS_DIRECTIVE = "ADD_JPA_ANNOTATIONS"
private const val KOTLIN_API_DIRECTIVE = "ADD_KOTLIN_API"
private const val JAVA_API_DIRECTIVE = "ADD_JAVA_API"
private const val J2K_POSTPROCESSOR_EXTENSIONS_DIRECTIVE = "J2K_POSTPROCESSOR_EXTENSIONS"

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
        val customFileExtension = when (pluginMode) {
            KotlinPluginMode.K1 -> ".k1.kt"
            KotlinPluginMode.K2 -> ".k2.kt"
            else -> error("Can't determine the plugin mode")
        }
        val customFile = File(javaFile.path.replace(".java", customFileExtension)).takeIf(File::exists)
        return customFile ?: defaultFile
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

    private fun convertJavaToKotlin(prefix: String, javaCode: String, settings: ConverterSettings, directives: Directives): String =
        when (prefix) {
            "expression" -> expressionToKotlin(javaCode, settings)
            "statement" -> statementToKotlin(javaCode, settings)
            "method" -> methodToKotlin(javaCode, settings)
            "file" -> fileToKotlin(javaCode, settings, postprocessorExtensions = if (directives.contains(J2K_POSTPROCESSOR_EXTENSIONS_DIRECTIVE)) listOf(dummyEP) else emptyList())
            else -> error("Specify what it is: method, statement or expression using the first line of test data file")
        }

    private val dummyEP = object : J2kPostprocessorExtension {
        override suspend fun processFiles(
            project: Project,
            files: List<KtFile>,
        ) {
            for (file in files) {
                val firstNamedParameter = readAction {
                    file.findDescendantOfType<KtParameter> { it.nameIdentifier != null && it.name != "foo" }
                } ?: continue

                val references = ReferencesSearch.search(firstNamedParameter, LocalSearchScope(file)).findAll()
                j2kWriteAction {
                    setName(checkNotNull(firstNamedParameter.nameIdentifier), "bar")
                }
                for (reference in references) {
                    j2kWriteAction {
                        setName(reference.element, "bar")
                    }
                }
            }
        }
    }

    open fun fileToKotlin(text: String, settings: ConverterSettings, postprocessorExtensions: List<J2kPostprocessorExtension>): String {
        val file = createJavaFile(text)
        val j2kKind = if (isFirPlugin) K2 else K1_NEW
        val extension = J2kConverterExtension.extension(j2kKind)
        val converter = extension.createJavaToKotlinConverter(project, module, settings)
        val postProcessor = extension.createPostProcessor()
        return converter.filesToKotlin(listOf(file), postProcessor, EmptyProgressIndicator(), postprocessorExtensions).results.single()
    }

    private fun methodToKotlin(text: String, settings: ConverterSettings): String {
        val result = fileToKotlin("final class C {$text}", settings, emptyList())
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
