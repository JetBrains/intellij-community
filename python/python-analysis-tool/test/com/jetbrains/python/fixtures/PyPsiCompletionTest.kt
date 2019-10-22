package com.jetbrains.python.fixtures

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.MockEditor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.jetbrains.python.psi.LanguageLevel

class PyPsiCompletionTest: PyPsiTestCase() {

  private val completionHandler = BaseCompletionHandler(myProject)

  override fun configureByFile(filePath: String): PsiFile? = super.configureByFile("completion/$filePath")

  private fun completeBasic() {
    val file = requireNotNull(myFile)
    val fileText = readFileText(file)
    val offset = fileText.indexOf(CARET)
    assertTrue(offset >= 0)
    val finalText = fileText.substring(0, offset) + fileText.substring(offset + CARET.length)
    VfsUtil.saveText(file, finalText)
    myPsiFile = PsiManager.getInstance(myProject).findFile(file)
    val psiFile = requireNotNull(myPsiFile)
    completionHandler.complete(psiFile, offset)
  }

  private fun checkResultByFile(filePath: String) {
    val assertFile = getFile("completion/$filePath")
    assertNotNull(assertFile)
    val assertText = readFileText(assertFile!!)
    var realText = readFileText(myFile!!)
    if (assertText != realText) {
      val offset = completionHandler.editor.caretModel.offset
      realText = realText.substring(0, offset) + CARET + realText.substring(offset)
    }
    assertEquals(assertText, realText)
  }

  private fun doTest() {
    val testName = getTestName(true)
    configureByFile("$testName.py")
    completeBasic()
    checkResultByFile("$testName.after.py")
  }

  private fun doMultiFileTest() {
    fail()
  }

  fun testLocalVar() {
    doTest()
  }

  fun testSelfMethod() {
    doTest()
  }

  fun testSelfField() {
    doTest()
  }

  fun testFuncParams() {
    doTest()
  }

  fun testFuncParamsStar() {
    doTest()
  }

  fun testInitParams() {
    doTest()
  }

  // PY-14044
  fun testNamedTupleInitParams() {
    doTest()
  }

  fun testSuperInitParams() {      // PY-505
    doTest()
  }

  fun testSuperInitKwParams() {      // PY-778
    doTest()
  }

  fun testPredefinedMethodName() {
    doTest()
  }

  fun testPredefinedMethodNot() {
    doTest()
  }

  fun testClassPrivate() {
    doTest()
  }

  fun testClassPrivateNotInherited() {
    doTest()
  }

  fun testClassPrivateNotPublic() {
    doTest()
  }

  fun testTwoUnderscores() {
    doTest()
  }

  fun testOneUnderscore() {
    val testName = getTestName(true)
    configureByFile("$testName.py")
    completeBasic()
    //type('\n')
    checkResultByFile("$testName.after.py")
  }

  fun testKwParamsInCodeUsage() { //PY-1002
    doTest()
  }

  fun testKwParamsInCodeGetUsage() { //PY-1002
    doTest()
  }

  fun testSuperInitKwParamsNotOnlySelfAndKwArgs() { //PY-1050
    doTest()
  }

  fun testSuperInitKwParamsNoCompletion() {
    doTest()
  }

  fun testIsInstance() {
    doTest()
  }

  fun testIsInstanceAssert() {
    doTest()
  }

  fun testIsInstanceTuple() {
    doTest()
  }

  fun testPropertyParens() {  // PY-1037
    doTest()
  }

  fun testClassNameFromVarName() {
    doTest()
  }

  fun testClassNameFromVarNameChained() {  // PY-5629
    doTest()
  }

  fun testPropertyType() {
    doTest()
  }

  fun testPySixTest() {
    doTest()
  }

  fun testClassMethod() {  // PY-833
    doTest()
  }

  fun testReturnType() {
    doTest()
  }

  fun testWithType() { // PY-4198
    runWithLanguageLevel(LanguageLevel.PYTHON26) { this.doTest() }
  }

  fun testChainedCall() {  // PY-1565
    doTest()
  }

  fun testNonExistingProperty() {  // PY-1748
    doTest()
  }

  fun testDictKeys() {  // PY-2245
    doTest()
  }

  fun testDictKeys2() { //PY-4181
    doTest()
  }

  fun testDictKeys3() { //PY-5546
    doTest()
  }

  fun testNoParensForDecorator() {  // PY-2210
    doTest()
  }

  fun testSuperMethod() {  // PY-170
    doTest()
  }

  fun testSuperMethodWithAnnotation() {
    doTest()
  }

  fun testSuperMethodWithCommentAnnotation() {
    doTest()
  }

  fun testLocalVarInDictKey() {  // PY-2558
    doTest()
  }

  fun testDictKeyPrefix() {
    doTest()
  }

  fun testDictKeyPrefix2() {      //PY-3683
    doTest()
  }

  fun testNoIdentifiersInImport() {
    doTest()
  }

  fun testSuperClassAttributes() {
    doTest()
  }

  fun testSuperClassAttributesNoCompletionInFunc() {
    doTest()
  }

  fun testImport() {
    doTest()
  }

  fun testDuplicateImportKeyword() {  // PY-3034
    doMultiFileTest()
  }

  fun testImportInMiddleOfHierarchy() {  // PY-3016
    doMultiFileTest()
  }

  fun testVeryPrivate() {  // PY-3246
    doTest()
  }

  fun testReexportModules() {  // PY-2385
    doMultiFileTest()
  }

  fun testModuleDotPy() {  // PY-5813
    doMultiFileTest()
  }

  fun testHasAttr() {  // PY-4423
    doTest()
  }
}