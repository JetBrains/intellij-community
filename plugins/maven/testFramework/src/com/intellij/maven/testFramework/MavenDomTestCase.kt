// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase.*
import com.intellij.maven.testFramework.utils.RealMavenPreventionFixture
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlTagValue
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.usages.UsageTargetUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.idea.maven.dom.MavenDomElement
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.inspections.MavenModelInspection
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel
import org.jetbrains.idea.maven.dom.references.MavenPsiElementWrapper
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.maven.utils.MavenLog
import java.util.*
import java.util.function.Function

abstract class MavenDomTestCase : MavenMultiVersionImportingTestCase() {
  private var myFixture: CodeInsightTestFixture? = null
  private val myConfigTimestamps: MutableMap<VirtualFile, Long> = HashMap()
  private var myOriginalAutoCompletion = false

  protected val fixture: CodeInsightTestFixture
    get() = myFixture!!

  override fun setUpFixtures() {
    testFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name).fixture

    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(testFixture)
    fixture.setUp()

    // org.jetbrains.idea.maven.utils.MavenRehighlighter
    (fixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)

    fixture.enableInspections(MavenModelInspection::class.java, XmlUnresolvedReferenceInspection::class.java)

    myOriginalAutoCompletion = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
  }

  override fun tearDownFixtures() {
    try {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = myOriginalAutoCompletion
      myConfigTimestamps.clear()

      fixture.tearDown()
    }
    finally {
      myFixture = null
    }
  }


  override suspend fun importProjectsAsync(files: List<VirtualFile>) {
    files.forEach {
      checkNoFixtureTags(it)
    }
    super.importProjectsAsync(files)
  }

  private fun checkNoFixtureTags(file: VirtualFile) {
    assertFalse("There should be no any <caret> tag in pom.xml during import", file.contentsToByteArray().toString().contains("<caret"))
    assertFalse("There should be no any <error> tag in pom.xml during import", file.contentsToByteArray().toString().contains("<error"))
  }

  protected suspend fun findPsiFile(f: VirtualFile?): PsiFile {
    return readAction { PsiManager.getInstance(project).findFile(f!!)!! }
  }

  protected suspend fun configureProjectPom(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String?) {
    val file = createProjectPom(xml!!)
    configTest(file)
  }

  protected suspend fun configTest(f: VirtualFile) {
    if (Comparing.equal(myConfigTimestamps[f], f.timeStamp)) {
      MavenLog.LOG.warn("MavenDomTestCase configTest skipped")
      return
    }
    refreshFiles(listOf(f))
    awaitConfiguration()
    fixture.configureFromExistingVirtualFile(f)
    myConfigTimestamps[f] = f.timeStamp
    MavenLog.LOG.warn("MavenDomTestCase configTest performed")
  }

  protected suspend fun type(f: VirtualFile, c: Char) {
    configTest(f)
    fixture.type(c)
  }

  protected suspend fun getReferenceAtCaret(f: VirtualFile): PsiReference? {
    configTest(f)
    val editorOffset = getEditorOffset(f)
    MavenLog.LOG.warn("MavenDomTestCase getReferenceAtCaret offset $editorOffset")
    val psiFile = findPsiFile(f)
    return readAction { psiFile.findReferenceAt(editorOffset) }
  }

  private suspend fun getReferenceAt(f: VirtualFile, offset: Int): PsiReference? {
    configTest(f)
    val psiFile = findPsiFile(f)
    return readAction { psiFile.findReferenceAt(offset) }
  }

  protected suspend fun getElementAtCaret(f: VirtualFile): PsiElement? {
    configTest(f)
    return findPsiFile(f).findElementAt(getEditorOffset(f))
  }

  protected suspend fun getEditor() = getEditor(projectPom)

  protected suspend fun getEditor(f: VirtualFile): Editor {
    configTest(f)
    return fixture.editor
  }

  protected suspend fun getEditorOffset() = getEditorOffset(projectPom)

  protected suspend fun getEditorOffset(f: VirtualFile): Int {
    val editor = getEditor(f)
    return readAction { editor.caretModel.offset }
  }

  private val caretElement = "<caret>"
  protected suspend fun moveCaretTo(f: VirtualFile, textWithCaret: String) {
    val caretOffset = textWithCaret.indexOf(caretElement)
    assertTrue(caretOffset > 0)
    val textWithoutCaret = textWithCaret.replaceFirst(caretElement, "")
    val documentText = getEditor(f).document.text
    val textOffset = documentText.indexOf(textWithoutCaret)
    assertTrue(textOffset > 0)
    val offset = textOffset + caretOffset
    withContext(Dispatchers.EDT) {
      val editor = getEditor(f)
      //maybe readaction
      writeIntentReadAction {
        editor.caretModel.moveToOffset(offset)
      }
    }
  }

  protected suspend fun getTestPsiFile() = getTestPsiFile(projectPom)

  private suspend fun getTestPsiFile(f: VirtualFile): PsiFile {
    configTest(f)
    return fixture.file
  }

  protected suspend fun findTag(path: String): XmlTag {
    return findTag(projectPom, path)
  }

  protected suspend fun findTag(file: VirtualFile, path: String, clazz: Class<out MavenDomElement> = MavenDomProjectModel::class.java): XmlTag {
    return readAction {
      val model = MavenDomUtil.getMavenDomModel(project, file, clazz)
      assertNotNull("Model is not of $clazz", model)
      MavenDomUtil.findTag(model!!, path)!!
    }
  }

  protected suspend fun findTagValue(file: VirtualFile, path: String, clazz: Class<out MavenDomElement> = MavenDomProjectModel::class.java): XmlTagValue {
    val tag = findTag(file, path, clazz)
    return readAction {
      tag.value
    }
  }

  protected suspend fun assertNoReferences(file: VirtualFile, refClass: Class<*>) {
    val ref = getReferenceAtCaret(file)
    if (ref == null) return
    val refs = if (ref is PsiMultiReference) ref.references else arrayOf(ref)
    for (each in refs) {
      assertFalse(each.toString(), refClass.isInstance(each))
    }
  }

  protected suspend fun assertUnresolved(file: VirtualFile) {
    val ref = getReferenceAtCaret(file)
    assertNotNull(ref)
    readAction { assertNull(ref!!.resolve()) }
  }

  protected suspend fun assertUnresolved(file: VirtualFile, expectedText: String?) {
    val ref = getReferenceAtCaret(file)
    assertNotNull(ref)
    assertNull(ref!!.resolve())
    assertEquals(expectedText, ref.canonicalText)
  }

  protected suspend fun assertResolved(file: VirtualFile, expected: PsiElement) {
    doAssertResolved(file, expected)
  }

  protected suspend fun getReference(file: VirtualFile, referenceText: String): PsiReference? {
    val text = VfsUtilCore.loadText(file)
    val index = text.indexOf(referenceText)
    assert(index >= 0)
    assert(text.indexOf(referenceText, index + referenceText.length) == -1) {
      "Reference text '" +
      referenceText +
      "' occurs more than one times"
    }
    return getReferenceAt(file, index)
  }

  protected suspend fun resolveReference(file: VirtualFile, referenceText: String, index: Int): PsiElement? {
    var index = index
    val text = VfsUtilCore.loadText(file)
    var k = -1

    do {
      k = text.indexOf(referenceText, k + 1)
      assert(k >= 0) { index }
    }
    while (--index >= 0)

    val psiReference = getReferenceAt(file, k)!!
    return readAction { psiReference.resolve() }
  }

  protected suspend fun resolveReference(file: VirtualFile, referenceText: String): PsiElement? {
    val ref = getReference(file, referenceText)
    assertNotNull(ref)

    var resolved = readAction { ref!!.resolve() }
    if (resolved is MavenPsiElementWrapper) {
      resolved = resolved.wrappee
    }

    return resolved
  }

  protected suspend fun assertResolved(file: VirtualFile, expected: PsiElement, expectedText: String?) {
    val ref = doAssertResolved(file, expected)
    assertEquals(expectedText, ref!!.canonicalText)
  }

  private suspend fun doAssertResolved(file: VirtualFile, expected: PsiElement): PsiReference? {
    assertNotNull("expected reference is null", expected)

    val ref = getReferenceAtCaret(file)
    assertNotNull("reference at caret is null", ref)
    var resolved = readAction { ref!!.resolve() }
    if (resolved is MavenPsiElementWrapper) {
      resolved = resolved.wrappee
    }
    val expectedText = readAction { expected.text }
    val resolvedText = readAction { resolved?.text }
    assertEquals(expectedText, resolvedText)
    assertEquals(expected, resolved)
    return ref
  }

  protected suspend fun assertCompletionVariants(f: VirtualFile, vararg expected: String?) {
    assertCompletionVariants(f, LOOKUP_STRING, *expected)
  }

  /**
   * bypass DependencySearchService cache
   */
  protected suspend fun assertCompletionVariantsNoCache(
    f: VirtualFile,
    lookupElementStringFunction: Function<LookupElement, String?>,
    vararg expected: String?,
  ) {
    val actual = getCompletionVariantsNoCache(f, lookupElementStringFunction)
    assertUnorderedElementsAreEqual(actual, *expected)
  }

  protected suspend fun assertCompletionVariants(
    f: VirtualFile,
    lookupElementStringFunction: Function<LookupElement, String?>,
    vararg expected: String?,
  ) {
    val actual = getCompletionVariants(f, lookupElementStringFunction)
    assertUnorderedElementsAreEqual(actual, *expected)
  }

  protected fun assertCompletionVariants(
    f: CodeInsightTestFixture,
    lookupElementStringFunction: Function<LookupElement, String?>,
    vararg expected: String?,
  ) {
    val actual = getCompletionVariants(f, lookupElementStringFunction)
    assertNotEmpty(actual)
    assertUnorderedElementsAreEqual(actual!!.toList(), expected.toList())
  }

  protected suspend fun assertCompletionVariantsInclude(
    f: VirtualFile,
    vararg expected: String?,
  ) {
    assertCompletionVariantsInclude(f, LOOKUP_STRING, *expected)
  }

  protected suspend fun assertCompletionVariantsInclude(
    f: VirtualFile,
    lookupElementStringFunction: Function<LookupElement, String?>,
    vararg expected: String?,
  ) {
    assertContain(getCompletionVariants(f, lookupElementStringFunction), *expected)
  }

  protected suspend fun assertDependencyCompletionVariantsInclude(f: VirtualFile, vararg expected: String?) {
    assertContain(getDependencyCompletionVariants(f), *expected)
  }

  protected suspend fun assertCompletionVariantsDoNotInclude(f: VirtualFile, vararg expected: String?) {
    assertDoNotContain(getCompletionVariants(f), *expected)
  }

  protected suspend fun getCompletionVariants(f: VirtualFile): List<String?> {
    return getCompletionVariants(f) { li: LookupElement -> li.lookupString }
  }

  protected suspend fun getCompletionVariants(f: VirtualFile, lookupElementStringFunction: Function<LookupElement, String?>): List<String?> {
    configTest(f)
    val variants = fixture.completeBasic()

    val result: MutableList<String?> = ArrayList()
    for (each in variants) {
      result.add(lookupElementStringFunction.apply(each))
    }
    return result
  }

  protected suspend fun getCompletionVariantsNoCache(f: VirtualFile, lookupElementStringFunction: Function<LookupElement, String?>): List<String?> {
    configTest(f)
    val variants = fixture.complete(CompletionType.BASIC, 2)

    val result: MutableList<String?> = ArrayList()
    for (each in variants) {
      result.add(lookupElementStringFunction.apply(each))
    }
    return result
  }

  protected suspend fun getDependencyCompletionVariants(f: VirtualFile): Set<String> {
    return getDependencyCompletionVariants(f) { it: MavenRepositoryArtifactInfo? -> MavenDependencyCompletionUtil.getPresentableText(it) }
  }

  protected suspend fun getDependencyCompletionVariants(
    f: VirtualFile,
    lookupElementStringFunction: Function<in MavenRepositoryArtifactInfo?, String>,
  ): Set<String> {
    configTest(f)
    val variants = fixture.completeBasic()

    val result: MutableSet<String> = TreeSet()
    for (each in variants) {
      val `object` = each.getObject()
      if (`object` is MavenRepositoryArtifactInfo) {
        result.add(lookupElementStringFunction.apply(`object`))
      }
    }
    return result
  }

  protected fun getCompletionVariants(
    fixture: CodeInsightTestFixture,
    lookupElementStringFunction: Function<LookupElement, String?>,
  ): List<String?>? {
    val variants = fixture.lookupElements
    if (variants == null) return null

    val result: MutableList<String?> = ArrayList()
    for (each in variants) {
      result.add(lookupElementStringFunction.apply(each))
    }
    return result
  }

  protected suspend fun assertDocumentation(expectedText: String?) {
    val originalElement = getElementAtCaret(projectPom)
    val editor = getEditor()
    val psiFile = getTestPsiFile()
    readAction {
      val targetElement = DocumentationManager.getInstance(project)
        .findTargetElement(editor, psiFile, originalElement)

      val provider = DocumentationManager.getProviderFromElement(targetElement)
      assertEquals(expectedText, provider.generateDoc(targetElement, originalElement))

      // should work for lookup as well as for tags
      val lookupElement = provider.getDocumentationElementForLookupItem(
        PsiManager.getInstance(project), originalElement!!.text, originalElement)
      assertSame(targetElement, lookupElement)
    }
  }

  protected open suspend fun checkHighlighting() {
    checkHighlighting(projectPom)
  }

  protected suspend fun checkHighlighting(f: VirtualFile) {
    MavenLog.LOG.warn("checkHighlighting started")
    configTest(f)
    MavenLog.LOG.warn("checkHighlighting: test configured")
    try {
      withContext(Dispatchers.EDT) {
        //readaction is not enough
        writeIntentReadAction {
          fixture.testHighlighting(true, false, true, f)
        }
      }
    }
    catch (throwable: Throwable) {
      MavenLog.LOG.error("Exception during highlighting", throwable)
      val cause1 = throwable.cause
      if (null != cause1) {
        MavenLog.LOG.error("Cause 1", cause1)
        val cause2 = cause1.cause
        if (null != cause2) {
          MavenLog.LOG.error("Cause 2", cause2)
        }
      }
      throw RuntimeException(throwable)
    }
    finally {
      MavenLog.LOG.warn("checkHighlighting finished")
    }
  }

  protected suspend fun getIntentionAtCaret(intentionName: String?): IntentionAction? {
    return getIntentionAtCaret(projectPom, intentionName)
  }

  protected suspend fun getIntentionAtCaret(pomFile: VirtualFile, intentionName: String?): IntentionAction? {
    configTest(pomFile)
    try {
      val intentions = fixture.availableIntentions

      return CodeInsightTestUtil.findIntentionByText(intentions, intentionName!!)
    }
    catch (throwable: Throwable) {
      throw RuntimeException(throwable)
    }
  }

  protected suspend fun assertRenameResult(value: String, expectedXml: String?) {
    doRename(projectPom, value)
    assertEquals(createPomXml(expectedXml), getTestPsiFile(projectPom).text)
  }

  protected suspend fun doRename(f: VirtualFile, value: String) {
    val context = createRenameDataContext(f, value)
    val renameHandler = readAction { RenameHandlerRegistry.getInstance().getRenameHandler(context) }
    assertNotNull(renameHandler)
    invokeRename(context, renameHandler!!)
  }

  protected suspend fun doInlineRename(f: VirtualFile, value: String) {
    val context = createRenameDataContext(f, value)
    val renameHandler = readAction {
      RenameHandlerRegistry.getInstance().getRenameHandler(context)
    }
    assertNotNull(renameHandler)
    assertInstanceOf(renameHandler, VariableInplaceRenameHandler::class.java)
    withContext(Dispatchers.EDT) {
      //maybe readaction
      writeIntentReadAction {
        CodeInsightTestUtil.doInlineRename(renameHandler as VariableInplaceRenameHandler?, value, fixture)
      }
    }
  }

  protected suspend fun assertCannotRename() {
    val context = createRenameDataContext(projectPom, "new name")
    val handler = readAction { RenameHandlerRegistry.getInstance().getRenameHandler(context) }
    if (null == handler) return
    try {
      invokeRename(context, handler)
    }
    catch (e: RefactoringErrorHintException) {
      if (!e.message!!.startsWith("Cannot perform refactoring.")) {
        throw e
      }
    }
  }

  private suspend fun invokeRename(context: DataContext, renameHandler: RenameHandler) {
    withContext(Dispatchers.EDT) {
      //maybe readaction
      writeIntentReadAction {
        renameHandler.invoke(project, PsiElement.EMPTY_ARRAY, context)
      }
    }
  }

  private suspend fun createRenameDataContext(f: VirtualFile, value: String?): DataContext {
    val editor = getEditor(f)
    val psiFile = getTestPsiFile(f)
    val context = CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
      sink[CommonDataKeys.EDITOR] = editor
      sink[PsiElementRenameHandler.DEFAULT_NAME] = value
      sink.lazy(CommonDataKeys.PSI_FILE) { psiFile }
      sink.lazy(CommonDataKeys.PSI_ELEMENT) {
        TargetElementUtil.findTargetElement(
          editor, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED or TargetElementUtil.ELEMENT_NAME_ACCEPTED)
      }
    }
    return context
  }

  protected suspend fun assertSearchResults(file: VirtualFile, vararg expected: PsiElement?) {
    assertUnorderedElementsAreEqual(search(file), *expected)
  }

  protected suspend fun assertSearchResultsInclude(file: VirtualFile, vararg expected: PsiElement?) {
    assertContain(search(file), *expected)
  }

  protected suspend fun search(file: VirtualFile): List<PsiElement> {
    val editor = getEditor(file)
    val psiFile = getTestPsiFile(file)
    return readAction {
      val psiElement = TargetElementUtil.findTargetElement(
        editor, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED or TargetElementUtil.ELEMENT_NAME_ACCEPTED)
      val targets = UsageTargetUtil.findUsageTargets(editor, psiFile, psiElement)
      val target = (targets?.firstOrNull() as? PsiElement2UsageTargetAdapter)?.element ?: return@readAction emptyList()
      val result = ArrayList(ReferencesSearch.search(target).findAll())
      result.map { it.element }
    }
  }

  protected suspend fun assertHighlighted(file: VirtualFile, vararg expected: HighlightPointer) {
    val editor = getEditor(file)
    val psiFile = getTestPsiFile(file)
    withContext(Dispatchers.EDT) {
      //readaction is not enough
      writeIntentReadAction {
        HighlightUsagesHandler.invoke(project, editor, psiFile)
      }
    }

    val highlighters = editor.markupModel.allHighlighters
    val actual: MutableList<HighlightPointer> = ArrayList()
    for (each in highlighters) {
      if (!each.isValid) continue
      val offset = each.startOffset
      val elementAtOffset = readAction { psiFile.findElementAt(offset) }
      val element = readAction {
        PsiTreeUtil.getParentOfType(elementAtOffset, XmlTag::class.java)
      }
      val text = editor.document.text.substring(offset, each.endOffset)
      actual.add(HighlightPointer(element, text))
    }

    assertUnorderedElementsAreEqual(actual, *expected)
  }

  class Highlight(
    val severity: HighlightSeverity = HighlightSeverity.ERROR,
    val text: String? = null,
    val description: String? = null,
  ) {
    fun matches(info: HighlightInfo): Boolean {
      return severity == info.severity
             && (text == null || text == info.text)
             && (description == null || description == info.description)
    }

    override fun toString(): String {
      return "Highlight(severity=$severity, text=$text, description=$description)"
    }
  }

  protected suspend fun checkHighlighting(file: VirtualFile, vararg expectedHighlights: Highlight) {
    assertHighlighting(doHighlighting(file), *expectedHighlights)
  }

  protected suspend fun doHighlighting(file: VirtualFile): Collection<HighlightInfo> {
    return withContext(Dispatchers.EDT) {
      //readaction is not enough
      writeIntentReadAction {
        refreshFiles(listOf(file))
        val content = String(file.contentsToByteArray())
        MavenLog.LOG.warn("Checking highlighting in file $file:\n$content")
        fixture.openFileInEditor(file)
        MavenLog.LOG.warn("Text in editor: ${fixture.editor.document.text}")
        val highlightingInfos = fixture.doHighlighting()
        MavenLog.LOG.warn("Highlighting results: ${highlightingInfos.joinToString { "\n${it.severity} ${it.description} (${it.startOffset}, ${it.endOffset})" }}")
        highlightingInfos
      }
    }
  }

  protected fun assertHighlighting(highlightingInfos: Collection<HighlightInfo>, vararg expectedHighlights: Highlight) {
    expectedHighlights.forEach { assertHighlighting(highlightingInfos, it) }
  }

  private fun assertHighlighting(highlightingInfos: Collection<HighlightInfo>, expectedHighlight: Highlight) {
    val highlightingInfo = highlightingInfos.firstOrNull { expectedHighlight.matches(it) }
    assertNotNull("Not highlighted: $expectedHighlight", highlightingInfo)
  }

  protected class HighlightPointer(var element: PsiElement?, var text: String?) {
    override fun equals(o: Any?): Boolean {
      if (this === o) return true
      if (o == null || javaClass != o.javaClass) return false

      val that = o as HighlightPointer

      if (if (element != null) element != that.element else that.element != null) return false
      if (if (text != null) text != that.text else that.text != null) return false

      return true
    }

    override fun hashCode(): Int {
      var result = if (element != null) element.hashCode() else 0
      result = 31 * result + (if (text != null) text.hashCode() else 0)
      return result
    }

    override fun toString(): String {
      return "HighlightInfo{" +
             "element=" + element +
             ", text='" + text + '\'' +
             '}'
    }
  }

  protected val RENDERING_TEXT: Function<LookupElement, String?> = Function { li: LookupElement ->
    val presentation = LookupElementPresentation()
    li.renderElement(presentation)
    presentation.itemText
  }

  protected val LOOKUP_STRING: Function<LookupElement, String?> = Function { obj: LookupElement -> obj.lookupString }

  protected suspend fun withoutSync(test: suspend () -> Unit) {
    val fixture = RealMavenPreventionFixture(project)
    fixture.setUp()
    try {
      test()
    }
    finally {
      fixture.tearDown()
    }
  }

  protected fun runBlockingNoSync(test: suspend () -> Unit) {
    val fixture = RealMavenPreventionFixture(project)
    fixture.setUp()
    try {
      @Suppress("SSBasedInspection")
      runBlocking {
        test()
      }

    }
    finally {
      fixture.tearDown()
    }
  }


}