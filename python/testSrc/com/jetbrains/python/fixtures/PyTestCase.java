// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.fixtures;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.find.findUsages.CustomUsageSearcher;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.util.CommonProcessors.CollectProcessor;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.codeInsight.completion.PyModuleNameCompletionContributor;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.namespacePackages.PyNamespacePackagesService;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.psi.search.PySearchUtilBase;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;


@TestDataPath("$CONTENT_ROOT/../testData/")
public abstract class PyTestCase extends UsefulTestCase {

  protected static final PyLightProjectDescriptor ourPy2Descriptor = new PyLightProjectDescriptor(LanguageLevel.PYTHON27);
  protected static final PyLightProjectDescriptor ourPyLatestDescriptor = new PyLightProjectDescriptor(LanguageLevel.getLatest());

  protected CodeInsightTestFixture myFixture;

  protected void assertProjectFilesNotParsed(@NotNull PsiFile currentFile) {
    assertRootNotParsed(currentFile, myFixture.getTempDirFixture().getFile("."), null);
  }

  protected void assertProjectFilesNotParsed(@NotNull TypeEvalContext context) {
    assertRootNotParsed(context.getOrigin(), myFixture.getTempDirFixture().getFile("."), context);
  }

  protected void assertSdkRootsNotParsed(@NotNull PsiFile currentFile) {
    final Sdk testSdk = PythonSdkUtil.findPythonSdk(currentFile);
    for (VirtualFile root : testSdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
      assertRootNotParsed(currentFile, root, null);
    }
  }

  private void assertRootNotParsed(@NotNull PsiFile currentFile, @NotNull VirtualFile root, @Nullable TypeEvalContext context) {
    for (VirtualFile file : VfsUtil.collectChildrenRecursively(root)) {
      final PyFile pyFile = PyUtil.as(myFixture.getPsiManager().findFile(file), PyFile.class);
      if (pyFile != null && !pyFile.equals(currentFile) && (context == null || !context.maySwitchToAST(pyFile))) {
        assertNotParsed(pyFile);
      }
    }
  }

  @Nullable
  protected static VirtualFile getVirtualFileByName(String fileName) {
    final VirtualFile path = LocalFileSystem.getInstance().findFileByPath(fileName.replace(File.separatorChar, '/'));
    if (path != null) {
      refreshRecursively(path);
      return path;
    }
    return null;
  }

  /**
   * Reformats currently configured file.
   */
  protected final void reformatFile() {
    WriteCommandAction.runWriteCommandAction(null, () -> doPerformFormatting());
  }

  private void doPerformFormatting() throws IncorrectOperationException {
    final PsiFile file = myFixture.getFile();
    final TextRange myTextRange = file.getTextRange();
    CodeStyleManager.getInstance(myFixture.getProject()).reformatText(file, myTextRange.getStartOffset(), myTextRange.getEndOffset());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(getProjectDescriptor(), getTestName(false));
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, createTempDirFixture());
    myFixture.setTestDataPath(getTestDataPath());
    myFixture.setUp();
  }

  /**
   * @return fixture to be used as temporary dir.
   */
  @NotNull
  protected TempDirTestFixture createTempDirFixture() {
    return new LightTempDirTestFixtureImpl(true); // "tmp://" dir by default
  }

  protected void runWithAdditionalFileInLibDir(@NotNull String relativePath,
                                               @NotNull String text,
                                               @NotNull Consumer<VirtualFile> fileConsumer) {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    final VirtualFile libDir = PySearchUtilBase.findLibDir(sdk);
    if (libDir != null) {
      runWithAdditionalFileIn(relativePath, text, libDir, fileConsumer);
    }
    else {
      createAdditionalRootAndRunWithIt(
        sdk,
        "Lib",
        OrderRootType.CLASSES,
        root -> runWithAdditionalFileIn(relativePath, text, root, fileConsumer)
      );
    }
  }

  protected void runWithAdditionalFileInSkeletonDir(@NotNull String relativePath,
                                                    @NotNull String text,
                                                    @NotNull Consumer<VirtualFile> fileConsumer) {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    final VirtualFile skeletonsDir = PythonSdkUtil.findSkeletonsDir(sdk);
    if (skeletonsDir != null) {
      runWithAdditionalFileIn(relativePath, text, skeletonsDir, fileConsumer);
    }
    else {
      createAdditionalRootAndRunWithIt(
        sdk,
        PythonSdkUtil.SKELETON_DIR_NAME,
        PythonSdkUtil.BUILTIN_ROOT_TYPE,
        root -> runWithAdditionalFileIn(relativePath, text, root, fileConsumer)
      );
    }
  }

  private static void runWithAdditionalFileIn(@NotNull String relativePath,
                                              @NotNull String text,
                                              @NotNull VirtualFile dir,
                                              @NotNull Consumer<VirtualFile> fileConsumer) {
    final VirtualFile file = VfsTestUtil.createFile(dir, relativePath, text);
    try {
      fileConsumer.accept(file);
    }
    finally {
      VfsTestUtil.deleteFile(file);
    }
  }

  protected void runWithAdditionalClassEntryInSdkRoots(@NotNull VirtualFile directory, @NotNull Runnable runnable) {
    final Sdk sdk = PythonSdkUtil.findPythonSdk(myFixture.getModule());
    assertNotNull(sdk);
    runWithAdditionalRoot(sdk, directory, OrderRootType.CLASSES, (__) -> runnable.run());
  }

  protected void runWithAdditionalClassEntryInSdkRoots(@NotNull String relativeTestDataPath, @NotNull Runnable runnable) {
    final String absPath = getTestDataPath() + "/" + relativeTestDataPath;
    final VirtualFile testDataDir = StandardFileSystems.local().findFileByPath(absPath);
    assertNotNull("Additional class entry directory '" + absPath + "' not found", testDataDir);
    runWithAdditionalClassEntryInSdkRoots(testDataDir, runnable);
  }

  private static void createAdditionalRootAndRunWithIt(@NotNull Sdk sdk,
                                                       @NotNull String rootRelativePath,
                                                       @NotNull OrderRootType rootType,
                                                       @NotNull Consumer<VirtualFile> rootConsumer) {
    final VirtualFile tempRoot = VfsTestUtil.createDir(sdk.getHomeDirectory().getParent().getParent(), rootRelativePath);
    try {
      runWithAdditionalRoot(sdk, tempRoot, rootType, rootConsumer);
    }
    finally {
      VfsTestUtil.deleteFile(tempRoot);
    }
  }

  private static void runWithAdditionalRoot(@NotNull Sdk sdk,
                                            @NotNull VirtualFile root,
                                            @NotNull OrderRootType rootType,
                                            @NotNull Consumer<VirtualFile> rootConsumer) {
    WriteAction.run(() -> {
      final SdkModificator modificator = sdk.getSdkModificator();
      assertNotNull(modificator);
      modificator.addRoot(root, rootType);
      modificator.commitChanges();
    });
    IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects();
    try {
      rootConsumer.accept(root);
    }
    finally {
      WriteAction.run(() -> {
        final SdkModificator modificator = sdk.getSdkModificator();
        assertNotNull(modificator);
        modificator.removeRoot(root, rootType);
        modificator.commitChanges();
      });
      IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects();
    }
  }

  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      PyNamespacePackagesService.getInstance(myFixture.getModule()).resetAllNamespacePackages();
      PyModuleNameCompletionContributor.ENABLED = true;
      setLanguageLevel(null);
      myFixture.tearDown();
      myFixture = null;
      FilePropertyPusher.EP_NAME.findExtensionOrFail(PythonLanguageLevelPusher.class).flushLanguageLevelCache();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Nullable
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPyLatestDescriptor;
  }

  @Nullable
  protected PsiReference findReferenceBySignature(final String signature) {
    int pos = findPosBySignature(signature);
    return findReferenceAt(pos);
  }

  @Nullable
  protected PsiReference findReferenceAt(int pos) {
    return myFixture.getFile().findReferenceAt(pos);
  }

  protected int findPosBySignature(String signature) {
    return PsiDocumentManager.getInstance(myFixture.getProject()).getDocument(myFixture.getFile()).getText().indexOf(signature);
  }

  private void setLanguageLevel(@Nullable LanguageLevel languageLevel) {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), languageLevel);
    IndexingTestUtil.waitUntilIndexesAreReady(myFixture.getProject());
  }

  protected void runWithLanguageLevel(@NotNull LanguageLevel languageLevel, @NotNull Runnable runnable) {
    setLanguageLevel(languageLevel);
    try {
      runnable.run();
    }
    finally {
      setLanguageLevel(null);
    }
  }

  protected void runWithDocStringFormat(@NotNull DocStringFormat format, @NotNull Runnable runnable) {
    final PyDocumentationSettings settings = PyDocumentationSettings.getInstance(myFixture.getModule());
    final DocStringFormat oldFormat = settings.getFormat();
    settings.setFormat(format);
    try {
      runnable.run();
    }
    finally {
      settings.setFormat(oldFormat);
    }
  }

  protected void runWithSourceRoots(@NotNull List<VirtualFile> sourceRoots, @NotNull Runnable runnable) {
    final Module module = myFixture.getModule();
    sourceRoots.forEach(root -> PsiTestUtil.addSourceRoot(module, root));
    try {
      runnable.run();
    }
    finally {
      sourceRoots.forEach(root -> PsiTestUtil.removeSourceRoot(module, root));
    }
  }

  protected static void assertNotParsed(PsiFile file) {
    assertInstanceOf(file, PyFileImpl.class);
    assertNull("Operations should have been performed on stubs but caused file to be parsed: " + file.getVirtualFile().getPath(),
               ((PyFileImpl)file).getTreeElement());
  }

  /**
   * @return class by its name from file
   */
  @NotNull
  protected PyClass getClassByName(@NotNull final String name) {
    return myFixture.findElementByText("class " + name, PyClass.class);
  }

  /**
   * @see #moveByText(com.intellij.testFramework.fixtures.CodeInsightTestFixture, String)
   */
  protected void moveByText(@NotNull final String testToFind) {
    moveByText(myFixture, testToFind);
  }

  /**
   * Finds some text and moves cursor to it (if found)
   *
   * @param fixture    test fixture
   * @param testToFind text to find
   * @throws AssertionError if element not found
   */
  public static void moveByText(@NotNull final CodeInsightTestFixture fixture, @NotNull final String testToFind) {
    final PsiElement element = fixture.findElementByText(testToFind, PsiElement.class);
    assert element != null : "No element found by text: " + testToFind;
    fixture.getEditor().getCaretModel().moveToOffset(element.getTextOffset());
  }

  /**
   * Finds all usages of element. Works much like method in {@link com.intellij.testFramework.fixtures.CodeInsightTestFixture#findUsages(com.intellij.psi.PsiElement)},
   * but supports {@link com.intellij.find.findUsages.CustomUsageSearcher} and {@link com.intellij.psi.search.searches.ReferencesSearch} as well
   *
   * @param element what to find
   * @return usages
   */
  @NotNull
  protected Collection<PsiElement> findUsage(@NotNull final PsiElement element) {
    final Collection<PsiElement> result = new ArrayList<>();
    final CollectProcessor<Usage> usageCollector = new CollectProcessor<>();
    for (final CustomUsageSearcher searcher : CustomUsageSearcher.EP_NAME.getExtensions()) {
      searcher.processElementUsages(element, usageCollector, new FindUsagesOptions(myFixture.getProject()));
    }
    for (final Usage usage : usageCollector.getResults()) {
      if (usage instanceof PsiElementUsage) {
        result.add(((PsiElementUsage)usage).getElement());
      }
    }
    for (final PsiReference reference : ReferencesSearch.search(element).findAll()) {
      result.add(reference.getElement());
    }

    for (final UsageInfo info : myFixture.findUsages(element)) {
      result.add(info.getElement());
    }

    return result;
  }

  /**
   * Returns elements certain element allows to navigate to (emulates CTRL+Click, actually).
   * You need to pass element as argument or
   * make sure your fixture is configured for some element (see {@link com.intellij.testFramework.fixtures.CodeInsightTestFixture#getElementAtCaret()})
   *
   * @param element element to fetch navigate elements from (may be null: element under caret would be used in this case)
   * @return elements to navigate to
   */
  @NotNull
  protected Set<PsiElement> getElementsToNavigate(@Nullable final PsiElement element) {
    final Set<PsiElement> result = new HashSet<>();
    final PsiElement elementToProcess = ((element != null) ? element : myFixture.getElementAtCaret());
    for (final PsiReference reference : elementToProcess.getReferences()) {
      final PsiElement directResolve = reference.resolve();
      if (directResolve != null) {
        result.add(directResolve);
      }
      if (reference instanceof PsiPolyVariantReference) {
        for (final ResolveResult resolveResult : ((PsiPolyVariantReference)reference).multiResolve(true)) {
          result.add(resolveResult.getElement());
        }
      }
    }
    return result;
  }

  /**
   * Clears provided file
   *
   * @param file file to clear
   */
  protected void clearFile(@NotNull final PsiFile file) {
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> {
      for (final PsiElement element : file.getChildren()) {
        element.delete();
      }
    }), null, null);
  }

  /**
   * Runs refactoring using special handler
   *
   * @param handler handler to be used
   */
  protected void refactorUsingHandler(@NotNull final RefactoringActionHandler handler) {
    final Editor editor = myFixture.getEditor();
    assertInstanceOf(editor, EditorEx.class);
    handler.invoke(myFixture.getProject(), editor, myFixture.getFile(), ((EditorEx)editor).getDataContext());
  }

  /**
   * Compares sets with string sorting them and displaying one-per-line to make comparision easier
   *
   * @param message  message to display in case of error
   * @param actual   actual set
   * @param expected expected set
   */
  protected static void compareStringSets(@NotNull final String message,
                                          @NotNull final Set<String> actual,
                                          @NotNull final Set<String> expected) {
    final Joiner joiner = Joiner.on("\n");
    Assert.assertEquals(message, joiner.join(new TreeSet<>(actual)), joiner.join(new TreeSet<>(expected)));
  }


  /**
   * Clicks certain button in document on caret position
   *
   * @param action what button to click (const from {@link IdeActions}) (btw, there should be some way to express it using annotations)
   * @see IdeActions
   */
  protected final void pressButton(@NotNull final String action) {
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), () -> myFixture.performEditorAction(action), "", null);
  }

  @NotNull
  protected CommonCodeStyleSettings getCommonCodeStyleSettings() {
    return getCodeStyleSettings().getCommonSettings(PythonLanguage.getInstance());
  }

  @NotNull
  protected CodeStyleSettings getCodeStyleSettings() {
    return CodeStyle.getSettings(myFixture.getProject());
  }

  @NotNull
  protected CommonCodeStyleSettings.IndentOptions getIndentOptions() {
    return getCommonCodeStyleSettings().getIndentOptions();
  }

  /**
   * When you have more than one completion variant, you may use this method providing variant to choose.
   * Since it puts tab after completion, be sure to limit line somehow (i.e. with comment).
   * <br/>
   * Example: "user.n[caret]." There are "name" and "nose" fields.
   * By calling this function with "nose" you will end with "user.nose  ".
   */
  protected final void completeCaretWithMultipleVariants(final String @NotNull ... desiredVariants) {
    final LookupElement[] lookupElements = myFixture.completeBasic();
    final LookupEx lookup = myFixture.getLookup();
    if (lookupElements != null && lookupElements.length > 1) {
      // More than one element returned, check directly because completion can't work in this case
      for (final LookupElement element : lookupElements) {
        final String suggestedString = element.getLookupString();
        if (Arrays.asList(desiredVariants).contains(suggestedString)) {
          myFixture.getLookup().setCurrentItem(element);
          lookup.setCurrentItem(element);
          myFixture.completeBasicAllCarets('\t');
          return;
        }
      }
    }
  }

  /**
   * The same as completeCaretWithMultipleVariants but for multiple carets in the file
   */
  protected final void completeAllCaretsWithMultipleVariants(final String @NotNull ... desiredVariants) {
    CaretModel caretModel = myFixture.getEditor().getCaretModel();
    List<Caret> carets = caretModel.getAllCarets();

    List<Integer> originalOffsets = new ArrayList<>(carets.size());

    for (Caret caret : carets) {
      originalOffsets.add(caret.getOffset());
    }
    caretModel.removeSecondaryCarets();

    // We do it in reverse order because completions would affect offsets
    // i.e.: when you complete "spa" to "spam", the next caret offset increased by 1
    for (int i = originalOffsets.size() - 1; i >= 0; i--) {
      int originalOffset = originalOffsets.get(i);
      caretModel.moveToOffset(originalOffset);
      completeCaretWithMultipleVariants(desiredVariants);
    }
  }

  @NotNull
  protected PsiElement getElementAtCaret() {
    final PsiFile file = myFixture.getFile();
    assertNotNull(file);
    return file.findElementAt(myFixture.getCaretOffset());
  }

  public static void assertType(@NotNull String expectedType, @NotNull PyTypedElement element, @NotNull TypeEvalContext context) {
    assertType("Failed in " + context + " context", expectedType, element, context);
  }

  public static void assertType(@NotNull String message,
                                @NotNull String expectedType,
                                @NotNull PyTypedElement element,
                                @NotNull TypeEvalContext context) {
    final PyType actual = context.getType(element);
    final String actualType = PythonDocumentationProvider.getTypeName(actual, context);
    assertEquals(message, expectedType, actualType);
  }

  public void addExcludedRoot(String rootPath) {
    final VirtualFile dir = myFixture.findFileInTempDir(rootPath);
    final Module module = myFixture.getModule();
    assertNotNull(dir);
    PsiTestUtil.addExcludedRoot(module, dir);
    Disposer.register(myFixture.getProjectDisposable(), () -> PsiTestUtil.removeExcludedRoot(module, dir));
  }

  public <T> void assertContainsInRelativeOrder(@NotNull final Iterable<? extends T> actual, final T @Nullable ... expected) {
    final List<T> actualList = Lists.newArrayList(actual);
    if (expected.length > 0) {
      T prev = expected[0];
      int prevIndex = actualList.indexOf(prev);
      assertTrue(prev + " is not found in " + actualList, prevIndex >= 0);
      for (int i = 1; i < expected.length; i++) {
        final T next = expected[i];
        final int nextIndex = actualList.indexOf(next);
        assertTrue(next + " is not found in " + actualList, nextIndex >= 0);
        assertTrue(prev + " should precede " + next + " in " + actualList, prevIndex < nextIndex);
        prev = next;
        prevIndex = nextIndex;
      }
    }
  }
}

