// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.changeSignature

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.TargetElementUtil.ELEMENT_NAME_ACCEPTED
import com.intellij.codeInsight.TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
import com.intellij.lang.LanguageRefactoringSupport
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.psi.*
import com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import com.intellij.refactoring.util.CanonicalTypes
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.VisibilityUtil
import org.jetbrains.kotlin.asJava.getRepresentativeLightMethod
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.descriptors.Visibilities.Internal
import org.jetbrains.kotlin.descriptors.Visibilities.Private
import org.jetbrains.kotlin.descriptors.Visibilities.Protected
import org.jetbrains.kotlin.descriptors.Visibilities.Public
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.base.util.allScope
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.utils.sure
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.declaredMemberFunctions

@RunWith(JUnit38ClassRunner::class)
abstract class BaseKotlinChangeSignatureTest<C: KotlinModifiableChangeInfo<P>, P: KotlinModifiableParameterInfo, TypeInfo, V, MethodDescriptor: KotlinModifiableMethodDescriptor<P, V>> : KotlinLightCodeInsightFixtureTestCase() {
    companion object {
        protected val EXTENSIONS = arrayOf(".kt", ".java")
    }

    fun C.createKotlinStringParameter(name: String = "s", defaultValueForCall: KtExpression? = null) =
        createKotlinParameter(name, "String", defaultValueForCall)

    fun C.createKotlinIntParameter(
        name: String = "i",
        defaultValueForCall: KtExpression? = null,
        defaultValueAsDefaultParameter: Boolean = false,
    ) = createKotlinParameter(name, "Int", defaultValueForCall, defaultValueAsDefaultParameter)

    protected abstract fun C.createKotlinParameter(
            name: String,
            originalType: String?,
            defaultValueForCall: KtExpression? = null,
            defaultValueAsDefaultParameter: Boolean = false,
            currentType: String? = null
    ): P

    protected abstract fun createParameterTypeInfo(type: String?, ktElement: PsiElement): TypeInfo

    protected abstract fun createChangeInfo(): C

    abstract fun doRefactoring(configure: C.() -> Unit = {})

    protected fun findCallers(method: PsiMethod): LinkedHashSet<PsiMethod> {
        val callers = LinkedHashSet<PsiMethod>()

        ActionUtil.underModalProgress(project, "Find method callers..." ) {
            val references = (method.getRepresentativeLightMethod()
                ?.let { MethodReferencesSearch.search(it, it.getUseScope(), true) }
                ?: ReferencesSearch.search(method, method.getUseScope()))

            references.asIterable().forEach { ref ->
                val element = ref.element
                val caller = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false)?.let { listOf(it) }
                    ?: PsiTreeUtil.getParentOfType(element, KtDeclaration::class.java, false)?.toLightMethods()
                if (caller != null) {
                    callers.addAll(caller)
                }
            }
        }

        return callers
    }

    override val testDataDirectory: File
        get() = IDEA_TEST_DATA_DIR.resolve("refactoring/changeSignature")

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override fun setUp() {
        super.setUp()
        myFixture.addClass(
            """package org.jetbrains.annotations;
import java.lang.annotation.*;
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER, ElementType.LOCAL_VARIABLE, ElementType.TYPE_USE})
public @interface NotNull {
  String value() default "";
}"""
        )
        CodeStyle.getSettings(project).clearCodeStyleSettings()
    }

    override fun tearDown() {
        try {
            files = emptyList()
            psiFiles = PsiFile.EMPTY_ARRAY
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    protected val kotlinDefaultIntValue: KtExpression get() = KtPsiFactory(project).createExpression("42")

    protected lateinit var files: List<String>
    protected lateinit var psiFiles: Array<PsiFile>

    protected fun configureFiles() {
        val fileList = mutableListOf<String>()
        var i = 0
        indexLoop@ while (true) {
            for (extension in EXTENSIONS) {
                val extraFileName = getTestName(false) + "Before" + (if (i > 0) ".$i" else "") + extension
                val extraFile = File(testDataDirectory, extraFileName)
                if (extraFile.exists()) {
                    fileList.add(extraFileName)
                    i++
                    continue@indexLoop
                }
            }
            break
        }

        psiFiles = myFixture.configureByFiles(*fileList.toTypedArray())
        files = fileList
    }

    protected fun findTargetElement(): PsiElement? {
        val provider = LanguageRefactoringSupport.getInstance().forContext(file)
        return provider!!.changeSignatureHandler!!.findTargetMember(file, editor)
    }

    protected inline fun <reified T> doTestTargetElement(code: String) {
        myFixture.configureByText("dummy.kt", code)
        val element = findTargetElement()!!
        assertEquals(T::class, element::class)
    }

    protected abstract fun doTestInvokePosition(code: String)

    protected fun createExpressionWithImports(context: PsiElement, expression: String, imports: List<String>): KtExpression? {
        val fragment = KtPsiFactory(project).createExpressionCodeFragment(expression, context)
        project.executeWriteCommand("add imports and qualifiers") {
            fragment.addImportsFromString(imports.joinToString(separator = KtCodeFragment.IMPORT_SEPARATOR) { "import $it" })
            addFullQualifier(fragment)
        }

        return fragment.getContentElement()
    }

    protected abstract fun addFullQualifier(fragment: KtExpressionCodeFragment)

    private fun C.addNewIntParameterWithValue(asParameter: Boolean, index: Int? = null) {
        val newIntParameter = createKotlinIntParameter(defaultValueForCall = kotlinDefaultIntValue,
                                                       defaultValueAsDefaultParameter = asParameter)
        if (index != null) {
            addParameter(newIntParameter, index)
        } else {
            addParameter(newIntParameter)
        }
    }


    protected class JavaRefactoringConfiguration(val method: PsiMethod) {
        val project = method.project

        var newName = method.name
        var newReturnType: PsiType = method.returnType ?: PsiTypes.voidType()
        val newParameters = ArrayList<ParameterInfoImpl>()
        val parameterPropagationTargets = LinkedHashSet<PsiMethod>()

        val psiFactory: PsiElementFactory
            get() = JavaPsiFacade.getInstance(project).elementFactory

        val objectPsiType: PsiType
            get() = PsiType.getJavaLangObject(method.manager, project.allScope())

        val stringPsiType: PsiType
            get() = PsiType.getJavaLangString(method.manager, project.allScope())

        var newVisibility = VisibilityUtil.getVisibilityModifier(method.modifierList)

        init {
            method.parameterList.parameters
                    .withIndex()
                    .mapTo(newParameters) {
                        val (i, param) = it
                        ParameterInfoImpl(i, param.name, param.type)
                    }
        }

        fun createProcessor(): ChangeSignatureProcessor = ChangeSignatureProcessor(
            project,
            method,
            false,
            newVisibility,
            newName,
            CanonicalTypes.createTypeWrapper(newReturnType),
            newParameters.toTypedArray(),
            arrayOf(),
            parameterPropagationTargets,
            emptySet<PsiMethod>()
        )
    }

    protected fun doTest(configureFiles: Boolean = true, configure: C.() -> Unit = {}) {
        doTestWithIgnoredDirective {
            if (configureFiles) {
                configureFiles()
            }
            withCustomCompilerOptions(file.text, project, module) {
                doRefactoring(configure)
                compareEditorsWithExpectedData()
            }
        }
    }

    protected fun compareEditorsWithExpectedData() {
        val checkErrorsAfter = checkErrorsAfter()
        for ((file, psiFile) in files zip psiFiles) {
            val afterFilePath: String = getAfterFilePath(file)
            try {
                myFixture.checkResultByFile(file, afterFilePath, true)
            } catch (e: FileComparisonFailedError) {
                KotlinTestUtils.assertEqualsToFile(File(testDataDirectory, afterFilePath), psiFile.text)
            }

            if (checkErrorsAfter && psiFile is KtFile) {
                DirectiveBasedActionUtils.checkForUnexpectedErrors(psiFile)
            }
        }
    }

    protected open fun checkErrorsAfter() = InTextDirectivesUtils.isDirectiveDefined(file!!.text, "// CHECK_ERRORS_AFTER")

    private fun getAfterFilePath(file: String): String {
        val suffix = getSuffix()
        if (suffix != null) {
            val afterFilePath = file.replace("Before.", "After.$suffix.")
            if (File(testDataDirectory, afterFilePath).exists()) {
                return afterFilePath
            }
        }
        return file.replace("Before.", "After.")
    }

    protected open fun getSuffix(): String? {
        return null
    }

    protected open fun getIgnoreDirective(): String? = null

    protected fun doTestWithIgnoredDirective(action: () -> Unit) {
        val directive = getIgnoreDirective()
        try {
            action()
        }
        catch (t: Throwable) {
            if (directive == null || !InTextDirectivesUtils.isDirectiveDefined(file!!.text, directive)) {
                throw t
            }
            return
        }
        if (directive != null && InTextDirectivesUtils.isDirectiveDefined(file!!.text, directive)) {
            throw AssertionError("Seems that test passes")
        }
    }

    protected fun doTestAndIgnoreConflicts(configure: C.() -> Unit = {}) {
        withIgnoredConflicts<Throwable> {
            doTest(configure = configure)
        }
    }

    protected fun C.swapParameters(i: Int, j: Int) {
        val newParameters = newParameters
        val temp = newParameters[i]
        setNewParameter(i, newParameters[j])
        setNewParameter(j, temp)
    }

    protected open fun doTestConflict(configure: C.() -> Unit = {}) = runAndCheckConflicts {
        configureFiles()

        doRefactoring(configure)
        compareEditorsWithExpectedData()
    }

    protected fun doTestUnmodifiable(configure: C.() -> Unit = {}) {
        doTestWithIgnoredDirective {
            try {
                configureFiles()
                doRefactoring(configure)
                compareEditorsWithExpectedData()

                fail("No conflicts found")
            } catch (e: RuntimeException) {
                if ((e.message ?: "").contains("Cannot modify file")) return@doTestWithIgnoredDirective

                val message = when {
                    e is BaseRefactoringProcessor.ConflictsInTestsException -> StringUtil.join(e.messages.sorted(), "\n")
                    e is CommonRefactoringUtil.RefactoringErrorHintException -> e.message
                    e.message!!.startsWith("Refactoring cannot be performed") -> e.message
                    else -> throw e
                }

                val conflictsFile = getConflictsFile()
                assertSameLinesWithFile(conflictsFile.absolutePath, message!!)
            }

        }
    }

    private fun getConflictsFile(): File {
        val suffix = getSuffix()
        if (suffix != null) {
            val temp = File(testDataDirectory, getTestName(false) + "Messages.$suffix.txt")
            if (temp.exists()) {
                return temp
            }
        }
        return File(testDataDirectory, getTestName(false) + "Messages.txt")
    }

    protected fun doJavaTest(configure: JavaRefactoringConfiguration.() -> Unit) {
        doTestWithIgnoredDirective {
            configureFiles()

            val targetElement = TargetElementUtil.findTargetElement(editor, ELEMENT_NAME_ACCEPTED or REFERENCED_ELEMENT_ACCEPTED)
            val targetMethod = (targetElement as? PsiMethod).sure { "<caret> is not on method name" }

            JavaRefactoringConfiguration(targetMethod).apply { configure() }.createProcessor().run()

            compareEditorsWithExpectedData()
        }
    }

    protected fun doJavaTestConflict(configure: JavaRefactoringConfiguration.() -> Unit) = runAndCheckConflicts { doJavaTest(configure) }


    protected fun runAndCheckConflicts(testAction: () -> Unit) {
        doTestWithIgnoredDirective {
            try {
                testAction()
                fail("No conflicts found")
            } catch (e: Throwable) {
                val message = when {
                    e is BaseRefactoringProcessor.ConflictsInTestsException -> StringUtil.join(e.messages.sorted(), "\n")
                    e is CommonRefactoringUtil.RefactoringErrorHintException -> e.message
                    e is RuntimeException && e.message!!.startsWith("Refactoring cannot be performed") -> e.message
                    else -> throw e
                }
                val conflictsFile = getConflictsFile()
                assertSameLinesWithFile(conflictsFile.absolutePath, message!!)
            }
        }
    }

    open fun ignoreTestData(fileName: String): Boolean = false

    // --------------------------------- Tests ---------------------------------

    fun testAllTestsPresented() {
        val classes = mutableListOf<KClass<*>>()
        classes.add(this::class)
        classes.addAll(this::class.allSuperclasses)
        val functionNames = classes.flatMap {
            it.declaredMemberFunctions
                    .asSequence()
                    .map { it.name }
                    .filter { it.startsWith("test") }
                    .map { it.removePrefix("test") }
                    .map { it + "Before" }
        }.toSet()

        for (file in testDataDirectory.listFiles()!!) {
            val fileName = file.name.substringBefore(".")
            if (fileName.endsWith("Messages") || fileName.endsWith("After")) continue
            if (ignoreTestData(fileName)) continue

            assertTrue(
              "test function for ${file.name} not found",
              fileName in functionNames,
            )
        }
    }

    fun testBadSelection() {
        myFixture.configureByFile(getTestName(false) + "Before.kt")
        assertNull(findTargetElement())
    }

    fun testDeconstructionEntry() {
        myFixture.configureByText("dummy.kt", """
            fun main(args: Array<String>) {

    val result = mapOf<String, Int>("A" to 1, "B" to 2, "C" to 3)

    result.forEach { (k<caret>ey, value) ->
    }
}""")
        assertNull(findTargetElement())
    }

    fun testPositionOnNameForPropertyInsideConstructor() = doTestTargetElement<KtParameter>("class A(val <caret>a: String)")
    fun testPositionOnValForPropertyInsideConstructor() = doTestTargetElement<KtParameter>("class A(v<caret>al a: String)")
    fun testPositionOnVarForPropertyInsideConstructor() = doTestTargetElement<KtParameter>("class A(v<caret>ar a: String)")
    fun testPositionBeforeColonForPropertyInsideConstructor() = doTestTargetElement<KtParameter>("class A(var a<caret>: String)")
    fun testPositionAfterColonForPropertyInsideConstructor() = doTestTargetElement<KtParameter>("class A(var a:<caret> String)")

    fun testPositionOnTypeForPropertyInsideConstructor() = doTestTargetElement<KtPrimaryConstructor>("class A(var a: St<caret>ring)")
    fun testPositionParameterInsideConstructor() = doTestTargetElement<KtPrimaryConstructor>("class A(<caret>a: String)")

    fun testPositionOnVarForProperty() = doTestTargetElement<KtProperty>("v<caret>ar a: String = \"\"")
    fun testPositionOnValForProperty() = doTestTargetElement<KtProperty>("v<caret>al a: String = \"\"")
    fun testPositionOnNameForProperty() = doTestTargetElement<KtProperty>("val <caret>a: String = \"\"")

    fun testPositionOnInvokeArgument() = doTestInvokePosition(
      """
            class WithInvoke
            operator fun WithInvoke.invoke() {}
            fun checkInvoke(w: WithInvoke) = w(<caret>)
    """.trimIndent()
    )

    fun testPositionOnInvokeObject() = doTestInvokePosition(
      """
            object InvokeObject {
                operator fun invoke() {}
            } 
        
            val invokeObjectCall = InvokeObject(<caret>)
    """.trimIndent()
    )

    fun testCaretAtReferenceAsValueParameter() = doTestConflict()

    fun testSynthesized() = doTestConflict()

    fun testPositionOnClassWithoutPrimaryButWithSecondaryConstructor() = doTestConflict()

    fun testUnmodifiableFromLibrary() = doTestUnmodifiable()

    fun testUnmodifiableFromBuiltins() = doTestUnmodifiable()


    fun testFunctionFromStdlibConflict() = doTestUnmodifiable()

    fun testExtensionFromStdlibConflict() = doTestUnmodifiable()


    // -----  top level properties ----

    fun testAddTopLevelPropertyReceiver() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("A()")
        receiverParameterInfo = createKotlinParameter("receiver", null, defaultValueForCall, currentType = "test.A")
    }

    fun testAddPropertyReceiverWithDefaultValue() = doTest {
        val expression = createExpressionWithImports(method, "Dep.MY_CONSTANT_FROM_DEP", listOf("a.b.c.Dep"))
        receiverParameterInfo = createKotlinIntParameter(defaultValueForCall = expression)
    }

    fun testAddPropertyReceiverWithDefaultValue2() = doTest {
        val expression = createExpressionWithImports(
            method,
            "MY_CONSTANT_FROM_DEP",
            listOf("a.b.c.Dep.Companion.MY_CONSTANT_FROM_DEP"),
        )

        receiverParameterInfo = createKotlinIntParameter(defaultValueForCall = expression)
    }

    fun testAddPropertyReceiverWithComplexDefaultValue() = doTest {
        val expression = createExpressionWithImports(
            context = method,
            expression = "Dep2().eval(MY_CONSTANT_FROM_DEP + NUMBER)",
            imports = listOf("a.b.c.Dep.Companion.MY_CONSTANT_FROM_DEP", "a.b.Dep2.Companion.NUMBER", "a.b.Dep2"),
        )

        receiverParameterInfo = createKotlinIntParameter(defaultValueForCall = expression)
    }

    fun testRemoveTopLevelPropertyReceiver() = doTest { receiverParameterInfo = null }

    //todo "String" is not available as receiver
    fun testChangeTopLevelPropertyReceiver() = doTest {
        receiverParameterInfo!!.setType("String")
    }

    //todo private property should java usages and usage in another file must raise a conflict
    fun testTopLevelPropertyVisibility() = doTest { setNewVisibility(Private) }

    //todo extension properties can't have initializers
    fun testAddTopLevelPropertyReceiverWithInitializer() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"\"")
        receiverParameterInfo = createKotlinParameter("receiver", null, defaultValueForCall, currentType = "kotlin.String")
    }


    // ----- member properties

    fun testAddPropertyReceiver() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"\"")
        receiverParameterInfo = createKotlinParameter("receiver", "String", defaultValueForCall)
    }

    //todo extension property in constructor parameters
    fun testAddPropertyReceiverWithConstructorOverride() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"\"")
        receiverParameterInfo = createKotlinParameter("receiver", "String", defaultValueForCall)
    }

    fun testAddPropertyReceiverConflict() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"\"")
        receiverParameterInfo = createKotlinParameter("receiver", "String", defaultValueForCall)
    }

    fun testAddPropertyReceiverConflict2() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"\"")
        receiverParameterInfo = createKotlinParameter("receiver", "String", defaultValueForCall)
    }

    //todo constructor declares parameters: disabled in dialog
    fun testAddReceiverPropertyInConstructor() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"\"")
        receiverParameterInfo = createKotlinParameter("receiver", null, defaultValueForCall, currentType = "kotlin.String")
    }

    fun testRemovePropertyReceiver() = doTest { receiverParameterInfo = null }

    fun testChangePropertyReceiver() = doTest {
        receiverParameterInfo!!.setType("Int")
    }

    fun testChangeProperty() = doTest {
        newName = "s"
        setType("String")
    }

    fun testAddPropertyReceiverInCompanion() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"\"")
        receiverParameterInfo = createKotlinParameter("receiver", "String", defaultValueForCall)
    }

    // ------------- add parameter ------------

    fun testAddParameterWithSameNameConflict() = doTestConflict {
        addParameter(createKotlinIntParameter())
    }

    fun testAddNewParameterWithDefaultValueToFunctionWithEmptyArguments() = withIgnoredConflicts<Throwable> {
        doTest {
            addNewIntParameterWithValue(true)
        }
    }

    fun testAddNewLastParameterWithDefaultValue() = doTest {
        addNewIntParameterWithValue(true)
    }

    fun testAddNewLastParameterWithDefaultValue2() = doTest {
        addNewIntParameterWithValue(true)
    }

    fun testAddNewLastParameterWithDefaultValue3() = doTest {
        addNewIntParameterWithValue(true)
    }

    fun testAddParameterToOperator() = doTest {
        addNewIntParameterWithValue(true)
    }

    fun testAddNewFirstParameterWithDefaultValue() = doTest {
        addNewIntParameterWithValue(true, 0)
    }

    fun testAddNewMiddleParameterWithDefaultValue() = doTest {
        addNewIntParameterWithValue(true, 2)
    }

    fun testAddNewParameterToFunctionWithEmptyArguments() = doTest {
        addNewIntParameterWithValue(false)
    }

    fun testAddNewLastParameter() = doTest {
        addNewIntParameterWithValue(false)
    }

    fun testAddNewLastParameter2() = doTest {
        addNewIntParameterWithValue(false)
    }

    fun testAddNewLastParameter3() = doTest {
        addNewIntParameterWithValue(false)
    }

    fun testAddNewFirstParameter() = doTest {
        addNewIntParameterWithValue(false, 0)
    }

    fun testAddNewMiddleParameter() = doTest {
        addNewIntParameterWithValue(false, 2)
    }

    fun testAddNewMiddleParameterKotlinWithoutMixedArgument() {
        configureFiles()
        withCustomCompilerOptions("// COMPILER_ARGUMENTS: -XXLanguage:-MixedNamedArgumentsInTheirOwnPosition", project, module) {
            doTest(configureFiles = false) {
                addNewIntParameterWithValue(false, 2)
            }
        }
    }

    fun testAddFunctionParameterWithDefaultValue() = doTest {
        val expression = createExpressionWithImports(
          method,
          "Dep.MY_CONSTANT_FROM_DEP",
          listOf("a.b.c.Dep"),
        )

        addParameter(createKotlinIntParameter(defaultValueForCall = expression))
    }

    fun testAddFunctionParameterWithDefaultValue2() = doTest {
        val expression = createExpressionWithImports(method, "MY_CONSTANT_FROM_DEP", listOf("a.b.c.Dep.Companion.MY_CONSTANT_FROM_DEP"))
        addParameter(createKotlinIntParameter(defaultValueForCall = expression))
    }

    fun testAddNewLastParameterWithDefaultValueToJava() = doTestConflict {
        addParameter(createKotlinIntParameter(defaultValueAsDefaultParameter = true))
    }

    fun testParameterListAddParam() = doTest {
        addParameter(createKotlinParameter("l", "Long"))
    }

    fun testParameterModifiers() = doTest {
        addParameter(createKotlinIntParameter(name = "n"))
    }

    fun testNoConflictWithReceiverName() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("0")
        addParameter(createKotlinIntParameter(defaultValueForCall = defaultValueForCall))
    }

    fun testOverridesInEnumEntries() = doTest {
        addParameter(createKotlinStringParameter())
    }

    fun testFakeOverride() = doTest {//todo multiple inheritance, java doesn't support
        addParameter(createKotlinIntParameter())
    }

    fun testAddParameterKeepFormat() = doTest {
        val psiFactory = KtPsiFactory(project)
        val defaultValue1 = psiFactory.createExpression("4")
        val defaultValue2 = psiFactory.createExpression("5")
        addParameter(createKotlinIntParameter("d", defaultValue1), 2)
        addParameter(
            createKotlinIntParameter("e", defaultValue2)
        )
    }

    fun testExpressionFunction() = doTest {
        newParameters[0].name = "x1"

        addParameter(createKotlinIntParameter("y1"))
    }

    fun testAddParameterAfterLambdaParameter() = doTest {
        addParameter(createKotlinIntParameter(defaultValueForCall = KtPsiFactory(project).createExpression("0")))
    }

    fun testNewParamValueRefsOtherParam() = doTest {
        val codeFragment = KtPsiFactory(project).createExpressionCodeFragment("p1 * p1", method)
        addParameter(createKotlinIntParameter("p2", codeFragment.getContentElement()!!))
    }

    fun testNewParamValueRefsProperty() = doTest {
        val codeFragment = KtPsiFactory(project).createExpressionCodeFragment("n + 1", method)
        addParameter(createKotlinIntParameter("p2", codeFragment.getContentElement()!!))
    }

    fun testNewParamValueRefsCallExpressions() = doTest {
        val codeFragment = KtPsiFactory(project).createExpressionCodeFragment("a * b", method)
        addParameter(createKotlinIntParameter("p2", codeFragment.getContentElement()!!))
    }

    fun testNewParamValueRefsIncrement() = doTest {
        val codeFragment = KtPsiFactory(project).createExpressionCodeFragment("a * b", method)
        addParameter(createKotlinIntParameter("p2", codeFragment.getContentElement()!!))
    }

    fun testNewParamValueRefsPrimaryConstructor() = doTest {
        val codeFragment = KtPsiFactory(project).createExpressionCodeFragment("a + 1", method)
        addParameter(createKotlinIntParameter("p2", codeFragment.getContentElement()!!))
    }

    fun testNewParamValueRefsOtherParamReceiver() = doTest {
        val codeFragment = KtPsiFactory(project).createExpressionCodeFragment("this + 1", method)
        addParameter(createKotlinIntParameter("p2", codeFragment.getContentElement()!!))
    }

    fun testNewParamValueRefsOtherParamRemoveReceiver() = doTest {
        val codeFragment = KtPsiFactory(project).createExpressionCodeFragment("n + 1", method)
        addParameter(createKotlinIntParameter("p2", codeFragment.getContentElement()!!))
        removeParameter(0)
    }

    fun testNewParamValueRefsSimpleNameWithDefaultValue() = doTest {
        val codeFragment = KtPsiFactory(project).createExpressionCodeFragment("prop", method)
        addParameter(createKotlinIntParameter("p2", codeFragment.getContentElement()!!))
        removeParameter(0)
    }

    fun testNewParamValueRefsOtherParamReceiver1() = doTest {
        val codeFragment = KtPsiFactory(project).createExpressionCodeFragment("this@foo.a", method)
        addParameter(createKotlinIntParameter("p2", codeFragment.getContentElement()!!))
    }

    fun testNewParamValueRefsOtherParamNeedVariable() = doTest {
        val codeFragment = KtPsiFactory(project).createExpressionCodeFragment("this.a", method)
        addParameter(createKotlinIntParameter("p2", codeFragment.getContentElement()!!))
    }

    fun testFqNameShortening() = doTest {
        addParameter(createKotlinParameter("s", originalType = "Any", currentType = "kotlin.String"))
    }

    fun testEnumEntriesWithoutSuperCalls() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))
    }

    fun testConstructorJavaUsages() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"abc\"")
        addParameter(createKotlinStringParameter(defaultValueForCall = defaultValueForCall))
    }

    fun testConstructorJavaUsages1() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"abc\"")
        addParameter(createKotlinStringParameter(defaultValueForCall = defaultValueForCall))
    }

    //------------ remove parameters ------------

    fun testRemoveParameterInOverriderOnly() = doTest { removeParameter(0) }

    fun testRemoveLastNonLambdaParameter() = doTest { removeParameter(0) }

    fun testRemoveLastNonLambdaParameter2() = doTest { removeParameter(1) }

    fun testRemoveParameterPreserveReceiver() = doTest { removeParameter(1) }

    fun testRemoveParameterInParentConflict() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveParameterInParentConflict2() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveParameterInParentConflict3() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveParameterInParentConflict4() = doTestConflict { removeParameter(0) }

    fun testRemoveParameterInParentConflict5() = doTestConflict { removeParameter(1) }

    fun testParameterListRemoveParam() = doTest { removeParameter(getNewParameters().size - 1) }

    fun testParameterListRemoveAllParams() = doTest { clearParameters() }

    fun testRemoveLambdaParameter() = doTest { removeParameter(2) }

    fun testRemoveLambdaParameterConflict() = doTestConflict { removeParameter(2) }

    fun testObjectMember() = doTest { removeParameter(0) }

    fun testParameterConflict() = doTestConflict {
        clearParameters()
    }

    fun testParameterConflict2() = doTestConflict {
        clearParameters()
    }

    fun testParameterConflictAlreadyExists() = doTestConflict {
        removeParameter(0)
    }

    fun testParameterConflictAlreadyExistsInSuper() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveParameterBeforeLambda() = doTest { removeParameter(1) }

    fun testRemoveParameterKeepFormat1() = doTest { removeParameter(0) }

    fun testRemoveParameterKeepFormat2() = doTest { removeParameter(1) }

    fun testRemoveParameterKeepFormat3() = doTest { removeParameter(2) }

    fun testRemoveLambdaParameter2() = doTest { removeParameter(0) }

    fun testRemoveDefaultParameterBeforeLambda() = doTest { removeParameter(1) }
    fun testRemoveParameterKeepOtherComments() = doTest { removeParameter(1) }

    fun testCalledByJvmName() = doTest { removeParameter(0) }

    //----------- receivers ---------------

    fun testAddFunctionReceiverWithDefaultValue() = doTest {
        val expression = createExpressionWithImports(method, "Dep.MY_CONSTANT_FROM_DEP", listOf("a.b.c.Dep"))
        receiverParameterInfo = createKotlinIntParameter(defaultValueForCall = expression)
    }

    fun testRemoveUsedReceiver() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveUsedReceiver2() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveUsedInParametersReceiver() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveUsedInParametersReceiver2() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveUsedReceiverExplicitThis() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveReceiver() = doTest { removeParameter(0) }

    fun testRemoveReceiverConflict() = doTestConflict { removeParameter(0) }

    fun testRemoveReceiverInParentConflict() = doTestConflict { removeParameter(0) }

    fun testRemoveReceiverInParentConflict2() = doTestConflict { removeParameter(0) }

    fun testRemoveReceiverInParentConflict3() = doTestConflict { removeParameter(0) }

    fun testRemoveReceiverInParentConflict4() = doTestConflict { removeParameter(0) }

    fun testRemoveReceiverForMember() = doTest { removeParameter(0) }

    fun testRemoveReceiverForMemberConflict() = doTestConflict { removeParameter(0) }

    fun testReceiverToParameterExplicitReceiver() = doTest { receiverParameterInfo = null }//suggested names for receiver is different

    fun testReceiverToParameterImplicitReceivers() = doTest { receiverParameterInfo = null }//suggested names for receiver is different

    fun testParameterToReceiverExplicitReceiver() = doTest { receiverParameterInfo = newParameters[0] }

    fun testParameterToReceiverImplicitReceivers() = doTest { receiverParameterInfo = newParameters[0] }

    fun testConvertLambdaParameterToReceiver() = doTest { receiverParameterInfo = newParameters[2] }

    fun testConvertReceiverToParameterForMember2() = doTest {//unrelated "this@label" is not collapsed
        receiverParameterInfo = null

        val newParameters = newParameters
        setNewParameter(0, newParameters[1])
        setNewParameter(1, newParameters[0])
    }

    fun testConvertReceiverToParameterForMember3() = doTest {//unrelated "this@label" is not collapsed
        receiverParameterInfo = null
    }

    fun testConvertReceiverToParameterWithNameClash() = doTest { receiverParameterInfo = null }

    fun testConvertReceiverToParameterAndChangeName() = doTest {
        receiverParameterInfo = null
        newParameters[0].name = "abc"
    }

    fun testChangeReceiver() = doTest { receiverParameterInfo = newParameters[1] }

    fun testChangeReceiverForMember() = doTest { receiverParameterInfo = newParameters[1] }

    fun testConvertParameterToReceiverForMember1() = doTest { receiverParameterInfo = newParameters[0] }

    fun testConvertParameterToReceiverForMemberUltraLight() = doTest { receiverParameterInfo = newParameters[0] }

    fun testConvertParameterToReceiverForMember2() = doTest { receiverParameterInfo = newParameters[1] }

    fun testConvertParameterToReceiverForMemberConflict() = doTestConflict { receiverParameterInfo = newParameters[0] }

    fun testConvertReceiverToParameterForMember1() = doTest { receiverParameterInfo = null }

    fun testAddNewReceiver() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("X(0)")
        receiverParameterInfo = createKotlinParameter("_", "Any", defaultValueForCall, currentType = "X")
    }

    fun testAddNewReceiverForMember() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("X(0)")
        receiverParameterInfo = createKotlinParameter("_", "Any", defaultValueForCall, currentType = "X")
    }

    fun testAddNewReceiverForMemberConflict() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("X(0)")
        receiverParameterInfo = createKotlinParameter("_", "Any", defaultValueForCall, currentType = "X")
    }

    fun testAddNewReceiverConflict() = doTestConflict {
        val defaultValueForCall = KtPsiFactory(project).createExpression("X(0)")
        receiverParameterInfo = createKotlinParameter("_", "Any", defaultValueForCall, currentType = "X")
    }

    fun testAddNewReceiverNoConflict() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("X(0)")
        receiverParameterInfo = createKotlinParameter("_", "Any", defaultValueForCall, currentType = "X")
    }

    fun testConvertParameterToReceiver1() = doTest { receiverParameterInfo = newParameters[0] }

    fun testConvertParameterToReceiver2() = doTest { receiverParameterInfo = newParameters[1] }

    fun testConvertReceiverToParameter1() = doTest { receiverParameterInfo = null }

    fun testConvertReceiverToParameter2() = doTest {
        receiverParameterInfo = null

        val newParameters = newParameters
        setNewParameter(0, newParameters[1])
        setNewParameter(1, newParameters[0])
    }

    fun testImplicitReceiverInRecursiveCall() = doTest {
        receiverParameterInfo = null
        newParameters[0].name = "a"
    }

    fun testReceiverInSafeCall() = doTestConflict { receiverParameterInfo = null }


    fun testConvertToExtensionAndRename() = doTest {
        receiverParameterInfo = newParameters[0]
        newName = "foo1"
    }

    fun testConvertParameterToReceiverAddParents() = doTest { receiverParameterInfo = newParameters[0] }

    fun testThisReplacement() = doTest { receiverParameterInfo = null }
    fun testThisReplacement1() = doTest { receiverParameterInfo = null }

    fun testImplicitThisToParameterWithChangedType() = doTest {
        receiverParameterInfo!!.setType("Older")
        receiverParameterInfo = null
    }

    fun testAddReceiverToGenericsWithOverrides() = doTest {
        val parameterInfo = newParameters[0]
        parameterInfo.setType("U<A>")
        receiverParameterInfo = parameterInfo
    }

    fun testConvertReceiverToParameterWithChild() = doTest {
        receiverParameterInfo = null
    }


    //-----  conflicts with parents ------------------------
    fun testAddReceiverConflict() = doTestConflict {
        receiverParameterInfo = createKotlinParameter("receiver", "String", currentType = "kotlin.String")
    }

    fun testRemoveParameterInContainingClassConflict() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveReceiverInContainingClassConflict() = doTestConflict {
        removeParameter(0)
    }

    fun testRemoveParameterUsedInClassBody() = doTestConflict {
        removeParameter(0)
    }

    fun testRemovePropertyUsedInAnotherClass() = doTestConflict {
        removeParameter(0)
    }

    // ----  renames ----------------------------------


    fun testVarargs() = doTestConflict()


    fun testInnerFunctionsConflict() = doTestConflict {
        newName = "inner2"
        newParameters[0].name = "y"
    }


    fun testMemberFunctionsConflict() = doTestConflict {
        newName = "inner2"
        newParameters[0].name = "y"
    }

    fun testTopLevelFunctionsConflict() = doTestConflict { newName = "fun2" }

    fun testConstructorsConflict() = doTestConflict {
        newParameters[0].name = "_x"
        newParameters[1].name = "_y"
        newParameters[2].name = "_z"
    }

    fun testFunctionRenameJavaUsages() = doTest { newName = "bar" }


    fun testRenameExtensionParameter() = doTest { newParameters[1].name = "b" }


    fun testChangeClassParameterWithInvalidCharacter() = doTest {
        newParameters[0].name = "a@bc"
    }

    fun testChangeClassParameterWithEscapedName() = doTest {
        newParameters[0].name = "`x@yz`"
    }

    fun testNamedLambdaArgumentIsNotMovedOutsideParentheses() = doTest {
        newParameters[0].name = "p11"
    }

    fun testMultipleLambdaArgumentsAreNotMovedOutsideParentheses() = doTest {
        newParameters[0].name = "p11"
    }

    fun testRenameExtensionParameterWithNamedArgs() = doTest { newParameters[2].name = "bb" }

    fun testOverrideInAnonymousObjectWithTypeParameters() = doTest { newName = "bar" }

    fun testRenameFunction() = doTest { newName = "after" }

    //--------------------reorder----------------------------

    fun testConstructorSwapArguments() = doTest {//default parameter is not passed explicitly
        newParameters[0].name = "_x1"
        newParameters[1].name = "_x2"

        swapParameters(0, 2)
    }

    fun testSwapArgumentsToHaveNamed() = doTest {
        val newParameters = newParameters
        setNewParameter(1, newParameters[2])
        setNewParameter(2, newParameters[1])
    }

    fun testNoDefaultValuesInOverrides() = doTest { swapParameters(0, 1) }

    fun testDefaultAfterLambda() = doTest { swapParameters(0, 1) }

    fun testSwapParametersKeepFormat() = doTest { swapParameters(0, 2) }

    fun testMoveLambdaParameterToLast() = doTest { swapParameters(0, 1) }


    fun testMoveLambdaParameter() = doTest {
        val newParameters = newParameters
        setNewParameter(1, newParameters[2])
        setNewParameter(2, newParameters[1])
    }


    //---------------------------------

    fun testParameterChangeInOverrides() = doTest {
        newParameters[0].name = "n"
        newParameters[0].setType("Int")
    }

    fun testParameterInConstructorConflictAlreadyExists() = doTestConflict {
        newParameters[0].setType("Int")
    }

    fun testMakePrimaryConstructorPrivateNoParams() = doTest { setNewVisibility(Private) }

    fun testMakePrimaryConstructorPublic() = doTest { setNewVisibility(Public) }

    fun testVisibilityFromJavaSuper() {
        withIgnoredConflicts<Throwable> {
            doJavaTest {
                newVisibility = "public"
            }
        }
    }


    fun testFunctionsAddRemoveArgumentsConflict() = doTestConflict {
        setNewVisibility(Internal)

        val defaultValueForCall = KtPsiFactory(project).createExpression("null")
        val newParameters = newParameters
        setNewParameter(2, newParameters[1])
        setNewParameter(1, newParameters[0])
        setNewParameter(
          index = 0,
          parameterInfo = createKotlinParameter("x0", "Any?", defaultValueForCall = defaultValueForCall)
        )
    }

    fun testFunctionsAddRemoveArgumentsConflict2() = doTestConflict {
        setNewVisibility(Internal)

        val defaultValueForCall = KtPsiFactory(project).createExpression("null")
        val newParameters = newParameters
        setNewParameter(2, newParameters[1])
        setNewParameter(1, newParameters[0])
        setNewParameter(
            index = 0,
            createKotlinParameter("x0", "Any?", defaultValueForCall)
        )
    }


    fun testFunctionJavaUsagesAndOverridesAddParam() = doTestAndIgnoreConflicts {
        val psiFactory = KtPsiFactory(project)
        val defaultValueForCall1 = psiFactory.createExpression("\"abc\"")
        val defaultValueForCall2 = psiFactory.createExpression("\"def\"")
        addParameter(createKotlinStringParameter(defaultValueForCall = defaultValueForCall1))
        addParameter(createKotlinParameter("o", "Any?", defaultValueForCall2))
    }



    fun testSetErrorReceiverType() = doTest { receiverParameterInfo!!.setType("XYZ") }

    fun testSetErrorParameterType() = doTest { newParameters[1].setType("XYZ") }


    fun testChangeParameterTypeWithImport() = doTest {
        newParameters[0].setType("a.Bar")
    }

    fun testFunctions() = doTest {
        setNewVisibility(Public)

        newParameters[0].name = "_x1"
        newParameters[1].name = "_x2"
        newParameters[2].name = "_x3"

        newParameters[1].setType("Float?")
    }

    fun testGenericFunctions() = doTest {
        setNewVisibility(Public)

        newParameters[0].name = "_x1"
        newParameters[1].name = "_x2"
        newParameters[2].name = "_x3"

        newParameters[1].setType("Double?")
    }

    fun testFunctionLiteral() = doTest {
        newParameters[1].name = "y1"
        addParameter(createKotlinParameter("x", "Any"))

        setType("Int")
    }

    fun testFunctionJavaUsagesAndOverridesChangeNullability() = doTest {//todo duplicated nullability
        newParameters[1].setType("String?")
        newParameters[2].setType("Any")

        setType("String?")
    }

    fun testFunctionJavaUsagesAndOverridesChangeTypes() = doTest {
        newParameters[0].setType("String?")
        newParameters[1].setType("Int")
        newParameters[2].setType("Long?")

        setType("Any?")
    }

    fun testGenericsWithOverrides() = doTest {
        newParameters[0].setType("List<C>")
        newParameters[1].setType("A?")
        newParameters[2].setType("U<B>")

        setType("U<C>?")
    }


    fun testChangeClassParameter() = doTest {
        newName = "s"
        setType("String")
    }


    //------------- return type -------------------

    fun testSetErrorReturnType() = doTest {
        setType("XYZ")
    }

    fun testChangeReturnTypeToNonUnit() = doTest {
        setType("Int")
    }


    fun testChangeReturnType() = doTest { setType("Float") }

    fun testAddReturnType() = doTest { setType("Float") }

    fun testRemoveReturnType() = doTest { setType("Unit") }

    fun testReturnTypeViaCodeFragment() = doTest {
        newName = "bar"
        setType("A<T, U>")
    }

    // ---- constructors --------------

    fun testAddParameterToAnnotation() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("3")
        addParameter(createKotlinIntParameter(name = "p3", defaultValueForCall = defaultValueForCall))
    }

    fun testSecondaryConstructor() = doTestAndIgnoreConflicts {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall = defaultValueForCall))
    }

    fun testPrimaryConstructorOnConstructorKeyword() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall = defaultValueForCall))
    }

    fun testPrimaryConstructorByThisRef() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall = defaultValueForCall))
    }

    fun testPrimaryConstructorBySuperRef() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall = defaultValueForCall))
    }

    fun testSecondaryConstructorByThisRef() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall = defaultValueForCall))
    }

    fun testSecondaryConstructorBySuperRef() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("\"foo\"")
        addParameter(createKotlinStringParameter(defaultValueForCall = defaultValueForCall))
    }

    fun testRemoveEnumConstructorParameter() = doTest { removeParameter(1) }

    fun testRemoveAllEnumConstructorParameters() = doTest { clearParameters() }

    fun testConstructor() = doTest {
        setNewVisibility(Public)

        newParameters[0].valOrVar = KotlinValVar.Var
        newParameters[1].valOrVar = KotlinValVar.None
        newParameters[2].valOrVar = KotlinValVar.Val

        newParameters[0].name = "_x1"
        newParameters[1].name = "_x2"
        newParameters[2].name = "_x3"

        newParameters[1].setType("Float?")
    }

    fun testAddValParameterToConstructorConflictWithClass() = doTestConflict {
        val parameter = createKotlinIntParameter("c")
        parameter.valOrVar = KotlinValVar.Val
        addParameter(parameter)
    }

    fun testAddValParameterToConstructorConflictWithProperty() = doTestConflict {
        val parameter = createKotlinIntParameter("p")
        parameter.valOrVar = KotlinValVar.Val
        addParameter(parameter)
    }

    fun testAddValParameterToConstructorConflictWithFunction() = doTest {
        val parameter = createKotlinParameter("f", "() -> Unit", null, false)
        parameter.valOrVar = KotlinValVar.Val
        addParameter(parameter)
    }

    fun testGenericConstructor() = doTest {
        setNewVisibility(Public)

        newParameters[0].valOrVar = KotlinValVar.Var
        newParameters[1].valOrVar = KotlinValVar.None
        newParameters[2].valOrVar = KotlinValVar.Val

        newParameters[0].name = "_x1"
        newParameters[1].name = "_x2"
        newParameters[2].name = "_x3"

        newParameters[1].setType("Double?")
    }

    fun testAddConstructorVisibility() = doTest {
        setNewVisibility(Protected)

        val newParameter = createKotlinParameter("x", "Any", KtPsiFactory(project).createExpression("12")).apply {
            valOrVar = KotlinValVar.Val
        }
        addParameter(newParameter)
    }

    fun testChangeConstructorPropertyWithChild() = doTest {
        val parameterInfo = newParameters[0]
        parameterInfo.name = "b"
        parameterInfo.setType("Int")
    }

    fun testChangeConstructorPropertyWithChild2() = doTest {
        val parameterInfo = newParameters[0]
        parameterInfo.name = "b"
        parameterInfo.setType("Int")
    }

    fun testPrimaryConstructorByRef() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))
    }

    fun testSyntheticPrimaryConstructorByRef() = doTest {
        val defaultValueForCall = KtPsiFactory(project).createExpression("1")
        addParameter(createKotlinIntParameter(name = "n", defaultValueForCall = defaultValueForCall))
    }

    fun testChangeConstructorVisibility() = doTest { setNewVisibility(Protected) }

    fun testDoNotApplyPrimarySignatureToSecondaryCalls() = doTest {
        val newParameters = newParameters
        setNewParameter(0, newParameters[1])
        setNewParameter(1, newParameters[0])
    }

    // ------- JvmOverloads ---------------

    private fun doTestJvmOverloadedAddDefault(index: Int) = doTestAndIgnoreConflicts {
        val defaultValue = KtPsiFactory(project).createExpression("2")
        addParameter(
            createKotlinIntParameter(name = "n", defaultValueForCall = defaultValue, defaultValueAsDefaultParameter = true),
            index,
        )
    }

    private fun doTestJvmOverloadedAddNonDefault(index: Int) = doTestAndIgnoreConflicts {
        val defaultValue = KtPsiFactory(project).createExpression("2")
        addParameter(
            createKotlinIntParameter(name = "n", defaultValueForCall = defaultValue),
            index,
        )
    }


    fun testJvmOverloadedSwapParams1() = doTestAndIgnoreConflicts { swapParameters(1, 2) }

    fun testJvmOverloadedSwapParams2() = doTestAndIgnoreConflicts { swapParameters(0, 2) }//todo

    fun testJvmOverloadedConstructorSwapParams() = doTestAndIgnoreConflicts { swapParameters(1, 2) }

    fun testJvmOverloadedAddDefault1() = doTestJvmOverloadedAddDefault(0)

    fun testJvmOverloadedAddDefault2() = doTestJvmOverloadedAddDefault(1)

    fun testJvmOverloadedAddDefault3() = doTestJvmOverloadedAddDefault(-1)

    fun testJvmOverloadedAddNonDefault1() = doTestJvmOverloadedAddNonDefault(0)

    fun testJvmOverloadedAddNonDefault2() = doTestJvmOverloadedAddNonDefault(1)

    fun testJvmOverloadedAddNonDefault3() = doTestJvmOverloadedAddNonDefault(-1)

    fun testJvmOverloadedRemoveDefault1() = doTestAndIgnoreConflicts { removeParameter(0) }

    fun testJvmOverloadedRemoveDefault2() = doTestAndIgnoreConflicts { removeParameter(1) }

    fun testJvmOverloadedRemoveDefault3() = doTestAndIgnoreConflicts { removeParameter(getNewParameters().size - 1) }

    fun testJvmOverloadedRemoveNonDefault1() = doTestAndIgnoreConflicts { removeParameter(0) }

    fun testJvmOverloadedRemoveNonDefault2() = doTestAndIgnoreConflicts { removeParameter(1) }

    fun testJvmOverloadedRemoveNonDefault3() = doTestAndIgnoreConflicts { removeParameter(getNewParameters().size - 1) }

    // ---- operators -------------------

    fun testAddParameterToInvokeFunction() = doTest {
        addNewIntParameterWithValue(false)
    }

    fun testAddParameterToInvokeFunctionFromObject() = doTest {
        addNewIntParameterWithValue(false)
    }

    fun testInvokeConventionAddParameter() = doTest {
        addParameter(createKotlinParameter("b", "Boolean", KtPsiFactory(project).createExpression("false")))
    }

    fun testInvokeOperatorInClass() = doTest {
        addParameter(createKotlinIntParameter(defaultValueForCall = kotlinDefaultIntValue))
    }

    fun testInvokeOperatorInObject() = doTest {
        addParameter(createKotlinIntParameter(defaultValueForCall = kotlinDefaultIntValue))
    }

    fun testGetConventionParameterToReceiver() = doTestConflict { receiverParameterInfo = newParameters[0] }

    fun testGetConventionReceiverToParameter() = doTest { receiverParameterInfo = null }

    fun testInvokeConventionParameterToReceiver() = doTestConflict { receiverParameterInfo = newParameters[0] }

    fun testInvokeConventionReceiverToParameter() = doTest { receiverParameterInfo = null }

    fun testInvokeConventionRemoveParameter() = doTest { removeParameter(0) }

    fun testGetConventionAddParameter() = doTest {
        addParameter(createKotlinParameter("b", originalType = "Boolean", KtPsiFactory(project).createExpression("false")))
    }

    fun testGetConventionRenameToFoo() = doTest { newName = "foo" }

    fun testGetConventionRenameToInvoke() = doTest { newName = "invoke" }

    fun testGetConventionSwapParameters() = doTest { swapParameters(0, 1) }

    fun testInvokeConventionSwapParameters() = doTest { swapParameters(0, 1) }

    fun testInvokeConventionRenameToFoo() = doTest { newName = "foo" }

    fun testInvokeConventionRenameToGet() = doTest { newName = "get" }

    fun testGetConventionRemoveParameter() = doTest { removeParameter(0) }

    fun testJvmOverloadedRenameParameter() = doTest { newParameters[0].name = "aa" }

    // ------- java super ------------
    fun testChangeJavaMethod() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "i", PsiTypes.intType())) }

    fun testChangeJavaMethodWithPrimitiveType() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType)) }

    fun testChangeJavaMethodWithBoxedType() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType)) }

    fun testChangeJavaMethodWithFlexibleMutableType() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType)) }

    fun testChangeJavaMethodWithNestedFlexibleType() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "i", PsiTypes.intType())) }

    fun testChangeJavaMethodWithFlexibleMutableType1() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType)) }

    fun testChangeJavaMethodWithRawType() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "i", PsiTypes.intType())) }

    fun testSimpleFlexibleType() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testSimpleFlexibleType2() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testMutableFlexibleType() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testMutableFlexibleType2() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testMutableFlexibleType3() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testMutableFlexibleType4() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testMutableFlexibleType5() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testMutableFlexibleType6() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testMutableFlexibleType7() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    fun testMutableFlexibleType8() = doJavaTest { newParameters.add(javaIntegerParameter()) }

    private fun javaIntegerParameter() = ParameterInfoImpl(-1, "integer", PsiTypes.intType())

    fun testJavaMethodKotlinUsages() = doJavaTest {
        newName = "bar"
        newParameters.removeAt(1)
    }

    fun testJavaConstructorKotlinUsages() = doJavaTest { newParameters.removeAt(1) }

    fun testSAMAddToEmptyParamList() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType)) }

    fun testSAMAddToEmptyParamListFromKotlin() = doTest {
        addParameter(createKotlinIntParameter())
    }

    fun testSAMAddToSingletonParamList() = doJavaTest { newParameters.add(0, ParameterInfoImpl(-1, "n", PsiTypes.intType())) }

    fun testSAMAddToNonEmptyParamList() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "o", objectPsiType)) }

    fun testSAMRemoveSingletonParamList() = doJavaTest { newParameters.clear() }

    fun testSAMRemoveParam() = doJavaTest { newParameters.removeAt(0) }

    fun testSAMRenameParam() = doJavaTest { newParameters[0].name = "p" }

    fun testSAMChangeParamType() = doJavaTest { newParameters[0].setType(objectPsiType) }

    fun testJavaParameterToNotNull() = doJavaTest {
        val notNullString =
            JavaPsiFacade.getElementFactory(project).createTypeFromText("@org.jetbrains.annotations.NotNull String", method)
        newParameters[0].setType(notNullString)
    }

    fun testSAMRenameMethod() = doJavaTest { newName = "bar" }

    fun testSAMChangeMethodReturnType() = doJavaTest { newReturnType = objectPsiType }

    fun testGenericsWithSAMConstructors() = doJavaTest {
        newParameters[0].setType(psiFactory.createTypeFromText("java.util.List<X<B>>", method.parameterList))
        newParameters[1].setType(psiFactory.createTypeFromText("X<java.util.Set<A>>", method.parameterList))

        newReturnType = psiFactory.createTypeFromText("X<java.util.List<A>>", method)
    }

    fun testJavaConstructorInDelegationCall() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType, "\"foo\"")) }


    fun testJavaConstructorBySuperRef() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType, "\"foo\"")) }

    fun testJavaMethodOverridesReplaceParam() = doJavaTestConflict {
        newReturnType = stringPsiType
        newParameters[0] = ParameterInfoImpl(-1, "x", PsiTypes.intType(), "1")
    }

    fun testJavaMethodOverridesChangeParam() = doJavaTest {
        newReturnType = stringPsiType

        newParameters[0].name = "x"
        newParameters[0].setType(PsiTypes.intType())
    }

    fun testJavaParameterPropagation() = doJavaTest {
        newParameters.add(ParameterInfoImpl(-1, "n", PsiTypes.intType(), "1"))
        newParameters.add(ParameterInfoImpl(-1, "s", stringPsiType, "\"abc\""))

        val classA = JavaFullClassNameIndex.getInstance().getClasses("A", project, project.allScope()).first { it.name == "A" }
        val methodBar = classA.methods.first { it.name == "bar" }
        parameterPropagationTargets.add(methodBar)

        val functionTest = KotlinTopLevelFunctionFqnNameIndex.get("test", project, project.allScope()).first()
        parameterPropagationTargets.add(functionTest.getRepresentativeLightMethod()!!)
    }


    fun testJavaConstructorParameterPropagation() = doJavaTest {
        newParameters.add(ParameterInfoImpl(-1, "n", PsiTypes.intType(), "1"))
        parameterPropagationTargets.addAll(findCallers(method))
    }

    fun testJavaMethodOverridesOmitUnitType() = doJavaTest {}

    fun testKotlinOverridingJavaWithDifferentParamName() = doJavaTest {
        newParameters.add(ParameterInfoImpl(-1, "n", PsiTypes.intType()))
    }

    fun testRemoveParameterFromFunctionWithReceiver() = doJavaTest { newParameters.clear() }

    fun testAddParameterFromFunctionWithReceiver() = doJavaTest { newParameters.add(ParameterInfoImpl(-1, "i", PsiTypes.intType())) }

    // ---- data class ---------
    open fun testRemoveDataClassParameter() = doTest { removeParameter(1) }
    fun testSwapDataClassParameters() = doTest {
        swapParameters(0, 2)
        swapParameters(1, 2)
    }

    fun testAddDataClassParameter() = doTest {
        //different name validation schema
        addParameter(
            createKotlinIntParameter("c", KtPsiFactory(project).createExpression("3")).apply { valOrVar = KotlinValVar.Val },
            1,
        )
    }

    open fun testRemoveAllOriginalDataClassParameters() = doTest {
        val psiFactory = KtPsiFactory(project)

        swapParameters(1, 2)
        setNewParameter(
            0,
            createKotlinIntParameter("d", psiFactory.createExpression("4")).apply { valOrVar = KotlinValVar.Val }
        )

        setNewParameter(
            2,
            createKotlinIntParameter("e", psiFactory.createExpression("5")).apply { valOrVar = KotlinValVar.Val }
        )
    }
}