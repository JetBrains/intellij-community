// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.usages.UsageTargetUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
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
import java.io.IOException
import java.util.*
import java.util.function.Function

abstract class MavenDomTestCase : MavenMultiVersionImportingTestCase() {
  private var myFixture: CodeInsightTestFixture? = null
  private val myConfigTimestamps: MutableMap<VirtualFile, Long> = HashMap()
  private var myOriginalAutoCompletion = false

  protected val fixture: CodeInsightTestFixture
    get() = myFixture!!

  @Throws(Exception::class)
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

  @Throws(Exception::class)
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

  protected fun findPsiFile(f: VirtualFile?): PsiFile {
    return PsiManager.getInstance(project).findFile(f!!)!!
  }

  protected fun configureProjectPom(@Language(value = "XML", prefix = "<project>", suffix = "</project>") xml: String?) {
    val file = createProjectPom(xml!!)
    configTest(file)
  }

  protected fun configTest(f: VirtualFile) {
    if (Comparing.equal(myConfigTimestamps[f], f.timeStamp)) {
      MavenLog.LOG.warn("MavenDomTestCase configTest skipped")
      return
    }
    fixture.configureFromExistingVirtualFile(f)
    myConfigTimestamps[f] = f.timeStamp
    MavenLog.LOG.warn("MavenDomTestCase configTest performed")
  }

  protected fun type(f: VirtualFile, c: Char) {
    configTest(f)
    fixture.type(c)
  }

  protected fun getReferenceAtCaret(f: VirtualFile): PsiReference? {
    configTest(f)
    val editorOffset = getEditorOffset(f)
    MavenLog.LOG.warn("MavenDomTestCase getReferenceAtCaret offset $editorOffset")
    return findPsiFile(f).findReferenceAt(editorOffset)
  }

  private fun getReferenceAt(f: VirtualFile, offset: Int): PsiReference? {
    configTest(f)
    return findPsiFile(f).findReferenceAt(offset)
  }

  protected fun getElementAtCaret(f: VirtualFile): PsiElement? {
    configTest(f)
    return findPsiFile(f).findElementAt(getEditorOffset(f))
  }

  protected val editor: Editor
    get() = getEditor(projectPom)

  protected fun getEditor(f: VirtualFile): Editor {
    configTest(f)
    return fixture.editor
  }

  protected val editorOffset: Int
    get() = getEditorOffset(projectPom)

  protected fun getEditorOffset(f: VirtualFile): Int {
    return getEditor(f).caretModel.offset
  }

  protected val testPsiFile: PsiFile
    get() = getTestPsiFile(projectPom)

  private fun getTestPsiFile(f: VirtualFile): PsiFile {
    configTest(f)
    return fixture.file
  }

  protected fun findTag(path: String?): XmlTag {
    return findTag(projectPom, path)
  }

  protected fun findTag(file: VirtualFile?, path: String?, clazz: Class<out MavenDomElement?> = MavenDomProjectModel::class.java): XmlTag {
    val model = MavenDomUtil.getMavenDomModel(project, file!!, clazz)
    assertNotNull("Model is not of $clazz", model)
    return MavenDomUtil.findTag(model!!, path!!)!!
  }

  protected fun assertNoReferences(file: VirtualFile, refClass: Class<*>) {
    val ref = getReferenceAtCaret(file)
    if (ref == null) return
    val refs = if (ref is PsiMultiReference) ref.references else arrayOf(ref)
    for (each in refs) {
      assertFalse(each.toString(), refClass.isInstance(each))
    }
  }

  protected fun assertUnresolved(file: VirtualFile) {
    val ref = getReferenceAtCaret(file)
    assertNotNull(ref)
    assertNull(ref!!.resolve())
  }

  protected fun assertUnresolved(file: VirtualFile, expectedText: String?) {
    val ref = getReferenceAtCaret(file)
    assertNotNull(ref)
    assertNull(ref!!.resolve())
    assertEquals(expectedText, ref.canonicalText)
  }

  @Throws(IOException::class)
  protected fun assertResolved(file: VirtualFile, expected: PsiElement) {
    doAssertResolved(file, expected)
  }

  @Throws(IOException::class)
  protected fun getReference(file: VirtualFile, referenceText: String): PsiReference? {
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

  @Throws(IOException::class)
  protected fun getReference(file: VirtualFile, referenceText: String, index: Int): PsiReference? {
    var index = index
    val text = VfsUtilCore.loadText(file)
    var k = -1

    do {
      k = text.indexOf(referenceText, k + 1)
      assert(k >= 0) { index }
    }
    while (--index >= 0)

    return getReferenceAt(file, k)
  }

  @Throws(IOException::class)
  protected fun resolveReference(file: VirtualFile, referenceText: String): PsiElement? {
    val ref = getReference(file, referenceText)
    assertNotNull(ref)

    var resolved = ref!!.resolve()
    if (resolved is MavenPsiElementWrapper) {
      resolved = resolved.wrappee
    }

    return resolved
  }

  @Throws(IOException::class)
  protected fun assertResolved(file: VirtualFile, expected: PsiElement, expectedText: String?) {
    val ref = doAssertResolved(file, expected)
    assertEquals(expectedText, ref!!.canonicalText)
  }

  private fun doAssertResolved(file: VirtualFile, expected: PsiElement): PsiReference? {
    assertNotNull("expected reference is null", expected)

    val ref = getReferenceAtCaret(file)
    assertNotNull("reference at caret is null", ref)
    var resolved = ref!!.resolve()
    if (resolved is MavenPsiElementWrapper) {
      resolved = resolved.wrappee
    }
    assertEquals(expected, resolved)
    return ref
  }

  protected fun assertCompletionVariants(f: VirtualFile, vararg expected: String?) {
    assertCompletionVariants(f, LOOKUP_STRING, *expected)
  }

  /**
   * bypass DependencySearchService cache
   */
  protected fun assertCompletionVariantsNoCache(f: VirtualFile,
                                                lookupElementStringFunction: Function<LookupElement, String?>,
                                                vararg expected: String?) {
    val actual = getCompletionVariantsNoCache(f, lookupElementStringFunction)
    assertUnorderedElementsAreEqual(actual, *expected)
  }

  protected fun assertCompletionVariants(f: VirtualFile,
                                         lookupElementStringFunction: Function<LookupElement, String?>,
                                         vararg expected: String?) {
    val actual = getCompletionVariants(f, lookupElementStringFunction)
    assertUnorderedElementsAreEqual(actual, *expected)
  }

  protected fun assertCompletionVariants(f: CodeInsightTestFixture,
                                         lookupElementStringFunction: Function<LookupElement, String?>,
                                         vararg expected: String?) {
    val actual = getCompletionVariants(f, lookupElementStringFunction)
    assertNotEmpty(actual)
    assertUnorderedElementsAreEqual(actual!!.toList(), expected.toList())
  }

  protected fun assertCompletionVariantsInclude(f: VirtualFile,
                                                vararg expected: String?) {
    assertCompletionVariantsInclude(f, LOOKUP_STRING, *expected)
  }

  protected fun assertCompletionVariantsInclude(f: VirtualFile,
                                                lookupElementStringFunction: Function<LookupElement, String?>,
                                                vararg expected: String?) {
    assertContain(getCompletionVariants(f, lookupElementStringFunction), *expected)
  }

  protected fun assertDependencyCompletionVariantsInclude(f: VirtualFile, vararg expected: String?) {
    assertContain(getDependencyCompletionVariants(f), *expected)
  }

  protected fun assertCompletionVariantsDoNotInclude(f: VirtualFile, vararg expected: String?) {
    assertDoNotContain(getCompletionVariants(f), *expected)
  }

  protected fun getCompletionVariants(f: VirtualFile): List<String?> {
    return getCompletionVariants(f) { li: LookupElement -> li.lookupString }
  }

  protected fun getCompletionVariants(f: VirtualFile, lookupElementStringFunction: Function<LookupElement, String?>): List<String?> {
    configTest(f)
    val variants = fixture.completeBasic()

    val result: MutableList<String?> = ArrayList()
    for (each in variants) {
      result.add(lookupElementStringFunction.apply(each))
    }
    return result
  }

  protected fun getCompletionVariantsNoCache(f: VirtualFile, lookupElementStringFunction: Function<LookupElement, String?>): List<String?> {
    configTest(f)
    val variants = fixture.complete(CompletionType.BASIC, 2)

    val result: MutableList<String?> = ArrayList()
    for (each in variants) {
      result.add(lookupElementStringFunction.apply(each))
    }
    return result
  }

  protected fun getDependencyCompletionVariants(f: VirtualFile): Set<String> {
    return getDependencyCompletionVariants(f) { it: MavenRepositoryArtifactInfo? -> MavenDependencyCompletionUtil.getPresentableText(it) }
  }

  protected fun getDependencyCompletionVariants(f: VirtualFile,
                                                lookupElementStringFunction: Function<in MavenRepositoryArtifactInfo?, String>): Set<String> {
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

  protected fun getCompletionVariants(fixture: CodeInsightTestFixture,
                                      lookupElementStringFunction: Function<LookupElement, String?>): List<String?>? {
    val variants = fixture.lookupElements
    if (variants == null) return null

    val result: MutableList<String?> = ArrayList()
    for (each in variants) {
      result.add(lookupElementStringFunction.apply(each))
    }
    return result
  }

  protected suspend fun assertDocumentation(expectedText: String?) {
    withContext(Dispatchers.EDT) {
      val originalElement = getElementAtCaret(projectPom)
      val targetElement = DocumentationManager.getInstance(project)
        .findTargetElement(editor, testPsiFile, originalElement)

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
    withContext(Dispatchers.EDT) {
      MavenLog.LOG.warn("checkHighlighting started")
      VirtualFileManager.getInstance().syncRefresh()
      MavenLog.LOG.warn("checkHighlighting: VFS refreshed")
      FileDocumentManager.getInstance().saveAllDocuments()
      UIUtil.dispatchAllInvocationEvents()

      val psiFile = findPsiFile(f)

      val document = fixture.getDocument(psiFile)
      if (null == document) {
        MavenLog.LOG.warn("checkHighlighting: document is null")
      }
      else {
        FileDocumentManager.getInstance().reloadFromDisk(document)
        MavenLog.LOG.warn("checkHighlighting: document reloaded from disk")
      }

      configTest(f)
      MavenLog.LOG.warn("checkHighlighting: test configured")

      try {
        UIUtil.dispatchAllInvocationEvents()
        fixture.testHighlighting(true, false, true, f)
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
  }

  protected fun getIntentionAtCaret(intentionName: String?): IntentionAction? {
    return getIntentionAtCaret(projectPom, intentionName)
  }

  protected fun getIntentionAtCaret(pomFile: VirtualFile, intentionName: String?): IntentionAction? {
    configTest(pomFile)
    try {
      val intentions = fixture.availableIntentions

      return CodeInsightTestUtil.findIntentionByText(intentions, intentionName!!)
    }
    catch (throwable: Throwable) {
      throw RuntimeException(throwable)
    }
  }

  @Throws(Exception::class)
  protected fun assertRenameResult(value: String?, expectedXml: String?) {
    doRename(projectPom, value)
    assertEquals(createPomXml(expectedXml), getTestPsiFile(projectPom).text)
  }

  protected fun doRename(f: VirtualFile, value: String?) {
    val context = createRenameDataContext(f, value)
    val renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(context)
    assertNotNull(renameHandler)

    invokeRename(context, renameHandler)
  }

  protected fun doInlineRename(f: VirtualFile, value: String) {
    val context = createRenameDataContext(f, value)
    val renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(context)
    assertNotNull(renameHandler)
    assertInstanceOf(renameHandler, VariableInplaceRenameHandler::class.java)
    CodeInsightTestUtil.doInlineRename(renameHandler as VariableInplaceRenameHandler?, value, fixture)
  }

  protected fun assertCannotRename() {
    val context = createRenameDataContext(projectPom, "new name")
    val handler = RenameHandlerRegistry.getInstance().getRenameHandler(context)
    if (handler == null) return
    try {
      invokeRename(context, handler)
    }
    catch (e: RefactoringErrorHintException) {
      if (!e.message!!.startsWith("Cannot perform refactoring.")) {
        throw e
      }
    }
  }

  private fun invokeRename(context: MapDataContext, renameHandler: RenameHandler?) {
    renameHandler!!.invoke(project, PsiElement.EMPTY_ARRAY, context)
  }

  private fun createDataContext(f: VirtualFile): MapDataContext {
    val context = MapDataContext()

    context.put(CommonDataKeys.EDITOR, getEditor(f))
    context.put(CommonDataKeys.PSI_FILE, getTestPsiFile(f))
    context.put(CommonDataKeys.PSI_ELEMENT, TargetElementUtil.findTargetElement(
      getEditor(f), TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED or TargetElementUtil.ELEMENT_NAME_ACCEPTED))

    return context
  }

  private fun createRenameDataContext(f: VirtualFile, value: String?): MapDataContext {
    val context = createDataContext(f)
    context.put(PsiElementRenameHandler.DEFAULT_NAME, value)
    return context
  }

  protected fun assertSearchResults(file: VirtualFile, vararg expected: PsiElement?) {
    assertUnorderedElementsAreEqual(search(file), *expected)
  }

  protected fun assertSearchResultsInclude(file: VirtualFile, vararg expected: PsiElement?) {
    assertContain(search(file), *expected)
  }

  protected fun search(file: VirtualFile): List<PsiElement> {
    val context = createDataContext(file)
    val targets = UsageTargetUtil.findUsageTargets { dataId: String? -> context.getData(dataId!!) }
    val target = (targets[0] as PsiElement2UsageTargetAdapter).element
    val result: List<PsiReference> = ArrayList(ReferencesSearch.search(target).findAll())
    return result.map { it.element }
  }

  protected fun assertHighlighted(file: VirtualFile, vararg expected: HighlightPointer) {
    val editor = getEditor(file)
    HighlightUsagesHandler.invoke(project, editor, getTestPsiFile(file))

    val highlighters = editor.markupModel.allHighlighters
    val actual: MutableList<HighlightPointer> = ArrayList()
    for (each in highlighters) {
      if (!each.isValid) continue
      val offset = each.startOffset
      var element = getTestPsiFile(file).findElementAt(offset)
      element = PsiTreeUtil.getParentOfType(element, XmlTag::class.java)
      val text = editor.document.text.substring(offset, each.endOffset)
      actual.add(HighlightPointer(element, text))
    }

    assertUnorderedElementsAreEqual(actual, *expected)
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
}