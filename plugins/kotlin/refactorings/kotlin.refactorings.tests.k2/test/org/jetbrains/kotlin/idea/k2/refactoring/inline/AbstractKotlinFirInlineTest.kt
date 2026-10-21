// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.inline

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.lang.refactoring.InlineActionHandler
import com.intellij.openapi.util.io.FileUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.idea.util.ClassImportFilter
import java.io.File

abstract class AbstractKotlinFirInlineTest : KotlinLightCodeInsightFixtureTestCase() {

    protected fun doTest(unused: String) {
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            dataFile().toPath(),
            IgnoreTests.DIRECTIVES.IGNORE_K2,
            directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE
        ) {
            doTestInner(unused)
        }
    }

    protected fun doTestInner(unused: String) {
        val testDataFile = dataFile()
        val afterFile = dataFile("${fileName()}.k2.after").takeIf { it.exists() } ?: dataFile("${fileName()}.after")

        val mainFileName = testDataFile.name
        val mainFileBaseName = FileUtil.getNameWithoutExtension(mainFileName)
        val extraFiles = testDataFile.parentFile.listFiles { _, name ->
            name != mainFileName && name.startsWith("$mainFileBaseName.") && (name.endsWith(".kt") || name.endsWith(".java"))
        } ?: emptyArray()

        val allFiles = (extraFiles + testDataFile).associateBy { myFixture.configureByFile(it.name) }
        val fileWithCaret = allFiles.values.singleOrNull { "<caret>" in it.readText() } ?: error("Must have one <caret>")
        val file = myFixture.configureByFile(fileWithCaret.name)

        val fileText = file.text
        withCustomCompilerOptions(fileText, project, module) {
            var vetoedClassInfo: ClassImportFilter.ClassInfo? = null
            InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// CLASS_IMPORT_FILTER_VETO_REGEX:").forEach {
                val regex = Regex(".*${it.trim()}.*")
                val filterExtension = ClassImportFilter { classInfo, _ ->
                    val vetoed = classInfo.fqName.asString().matches(regex)
                    if (vetoed) vetoedClassInfo = classInfo
                    !vetoed
                }
                ClassImportFilter.EP_NAME.point.registerExtension(filterExtension, this.testRootDisposable)
            }

            val afterFileExists = afterFile.exists()
            val targetElement = TargetElementUtil.findTargetElement(
                myFixture.editor,
                TargetElementUtil.ELEMENT_NAME_ACCEPTED or TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
            )

            val handler = if (targetElement != null)
                InlineActionHandler.EP_NAME.extensions.firstOrNull { it.canInlineElement(targetElement) }
            else
                null

            val expectedErrors = InTextDirectivesUtils.findLinesWithPrefixesRemoved(myFixture.file.text, "// ERROR_K2: ").takeIf { it.isNotEmpty() }
                ?: InTextDirectivesUtils.findLinesWithPrefixesRemoved(myFixture.file.text, "// ERROR: ")
            val inlinePropertyKeepValue = InTextDirectivesUtils.getPrefixedBoolean(myFixture.file.text, "// INLINE_PROPERTY_KEEP: ")
            val inlineMethodThis = InTextDirectivesUtils.getPrefixedBoolean(myFixture.file.text, "// INLINE_METHOD_THIS: ")
            val settings = KotlinCommonRefactoringSettings.getInstance()
            val oldInlinePropertyKeepValue = settings.INLINE_PROPERTY_KEEP
            val oldInlineMethodThis = settings.INLINE_METHOD_THIS
            if (handler != null) {
                try {
                    inlinePropertyKeepValue?.let { settings.INLINE_PROPERTY_KEEP = it }
                    inlineMethodThis?.let { settings.INLINE_METHOD_THIS = it }
                    handler.inlineElement(project, editor, targetElement)
                    for ((extraPsiFile, extraFile) in allFiles) {
                        if (!afterFileExists && InTextDirectivesUtils.isDirectiveDefined(myFixture.file.text, IgnoreTests.DIRECTIVES.IGNORE_K2)) {
                            fail("K1 is unable to inline property")
                        }
                        KotlinTestUtils.assertEqualsToFile(File("${extraFile.path}.k2.after").takeIf { it.exists() } ?: File("${extraFile.path}.after"), extraPsiFile.text)
                    }
                } catch (e: CommonRefactoringUtil.RefactoringErrorHintException) {
                    assertFalse("Refactoring not available: ${e.message}", afterFileExists)
                    assertEquals("Expected errors", 1, expectedErrors.size)
                    assertEquals("Error message", expectedErrors[0].replace("\\n", "\n"), e.message)
                } catch (e: BaseRefactoringProcessor.ConflictsInTestsException) {
                    assertFalse("Conflicts: ${e.message}", afterFileExists)
                    assertEquals("Expected errors", 1, expectedErrors.size)
                    assertEquals("Error message", expectedErrors[0].replace("\\n", "\n"), e.message)
                } finally {
                    settings.INLINE_PROPERTY_KEEP = oldInlinePropertyKeepValue
                    settings.INLINE_METHOD_THIS = oldInlineMethodThis
                }
            } else {
                assertFalse("No refactoring handler available", afterFileExists)
            }
            if (InTextDirectivesUtils.isDirectiveDefined(fileText, "CLASS_IMPORT_FILTER_ASSERT_VETO")) {
                assertNotNull("The class import filter did not veto a class", vetoedClassInfo)
            }
            InTextDirectivesUtils.getPrefixedBoolean(fileText, "// CLASS_IMPORT_FILTER_EXPECTED_IS_NESTED: ")?.let {
                assertEquals(it, vetoedClassInfo?.isNested)
            }
            InTextDirectivesUtils.findLinesWithPrefixesRemoved(fileText, "// CLASS_IMPORT_FILTER_EXPECTED_CLASS_KIND: ").singleOrNull()?.let {
                assertEquals(ClassKind.valueOf(it.trim()), vetoedClassInfo?.classKind)
            }
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
}

abstract class AbstractInlineTestWithSomeDescriptors : AbstractKotlinFirInlineTest() {
    override fun getProjectDescriptor(): LightProjectDescriptor = getProjectDescriptorFromFileDirective()
}
