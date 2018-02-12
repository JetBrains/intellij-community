/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.fixtures;

import com.google.common.base.Joiner;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.find.findUsages.CustomUsageSearcher;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectConfigurator;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.util.CommonProcessors.CollectProcessor;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.PythonDocumentationProvider;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.File;
import java.util.*;

/**
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/../testData/")
public abstract class PyTestCase extends UsefulTestCase {
  public static final String PYTHON_2_MOCK_SDK = "2.7";
  public static final String PYTHON_3_MOCK_SDK = "3.4";

  protected static final PyLightProjectDescriptor ourPyDescriptor = new PyLightProjectDescriptor(PYTHON_2_MOCK_SDK);
  protected static final PyLightProjectDescriptor ourPy3Descriptor = new PyLightProjectDescriptor(PYTHON_3_MOCK_SDK);

  protected CodeInsightTestFixture myFixture;

  protected void assertProjectFilesNotParsed(@NotNull PsiFile currentFile) {
    assertRootNotParsed(currentFile, myFixture.getTempDirFixture().getFile("."), null);
  }

  protected void assertProjectFilesNotParsed(@NotNull TypeEvalContext context) {
    assertRootNotParsed(context.getOrigin(), myFixture.getTempDirFixture().getFile("."), context);
  }

  protected void assertSdkRootsNotParsed(@NotNull PsiFile currentFile) {
    final Sdk testSdk = PythonSdkType.findPythonSdk(currentFile);
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
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(getProjectDescriptor());
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture,
                                                                                    createTempDirFixture());
    myFixture.setUp();

    myFixture.setTestDataPath(getTestDataPath());
    PythonDialectsTokenSetProvider.reset();
  }

  /**
   * @return fixture to be used as temporary dir.
   */
  @NotNull
  protected TempDirTestFixture createTempDirFixture() {
    return new LightTempDirTestFixtureImpl(true); // "tmp://" dir by default
  }

  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      setLanguageLevel(null);
      myFixture.tearDown();
      myFixture = null;
      Extensions.findExtension(FilePropertyPusher.EP_NAME, PythonLanguageLevelPusher.class).flushLanguageLevelCache();
    }
    finally {
      super.tearDown();
      clearFields(this);
    }
  }

  @Nullable
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPyDescriptor;
  }

  protected PsiReference findReferenceBySignature(final String signature) {
    int pos = findPosBySignature(signature);
    return findReferenceAt(pos);
  }

  protected PsiReference findReferenceAt(int pos) {
    return myFixture.getFile().findReferenceAt(pos);
  }

  protected int findPosBySignature(String signature) {
    return PsiDocumentManager.getInstance(myFixture.getProject()).getDocument(myFixture.getFile()).getText().indexOf(signature);
  }

  protected void setLanguageLevel(@Nullable LanguageLevel languageLevel) {
    PythonLanguageLevelPusher.setForcedLanguageLevel(myFixture.getProject(), languageLevel);
  }

  protected void runWithLanguageLevel(@NotNull LanguageLevel languageLevel, @NotNull Runnable action) {
    setLanguageLevel(languageLevel);
    try {
      action.run();
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

  protected static void assertNotParsed(PsiFile file) {
    assertInstanceOf(file, PyFileImpl.class);
    assertNull("Operations should have been performed on stubs but caused file to be parsed: " + file.getVirtualFile().getPath(),
               ((PyFileImpl)file).getTreeElement());
  }

  /**
   * @param name
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
   * Configures project by some path. It is here to emulate {@link com.intellij.platform.PlatformProjectOpenProcessor}
   *
   * @param path         path to open
   * @param configurator configurator to use
   */
  protected void configureProjectByProjectConfigurators(@NotNull final String path,
                                                        @NotNull final DirectoryProjectConfigurator configurator) {
    final VirtualFile newPath =
      myFixture.copyDirectoryToProject(path, String.format("%s%s%s", "temp_for_project_conf", File.pathSeparator, path));
    final Ref<Module> moduleRef = new Ref<>(myFixture.getModule());
    configurator.configureProject(myFixture.getProject(), newPath, moduleRef);
  }

  public static String getHelpersPath() {
    return new File(PythonHelpersLocator.getPythonCommunityPath(), "helpers").getPath();
  }

  /**
   * Creates run configuration from right click menu
   *
   * @param fixture       test fixture
   * @param expectedClass expected class of run configuration
   * @param <C>           expected class of run configuration
   * @return configuration (if created) or null (otherwise)
   */
  @Nullable
  public static <C extends RunConfiguration> C createRunConfigurationFromContext(
    @NotNull final CodeInsightTestFixture fixture,
    @NotNull final Class<C> expectedClass) {
    final DataContext context = DataManager.getInstance().getDataContext(fixture.getEditor().getComponent());
    for (final RunConfigurationProducer<?> producer : RunConfigurationProducer.EP_NAME.getExtensions()) {
      final ConfigurationFromContext fromContext = producer.createConfigurationFromContext(ConfigurationContext.getFromContext(context));
      if (fromContext == null) {
        continue;
      }
      final C result = PyUtil.as(fromContext.getConfiguration(), expectedClass);
      if (result != null) {
        return result;
      }
    }
    return null;
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
  protected PyCodeStyleSettings getPythonCodeStyleSettings() {
    return getCodeStyleSettings().getCustomSettings(PyCodeStyleSettings.class);
  }

  @NotNull
  protected CodeStyleSettings getCodeStyleSettings() {
    return CodeStyleSettingsManager.getSettings(myFixture.getProject());
  }

  @NotNull
  protected CommonCodeStyleSettings.IndentOptions getIndentOptions() {
    //noinspection ConstantConditions
    return getCommonCodeStyleSettings().getIndentOptions();
  }

  /**
   * When you have more than one completion variant, you may use this method providing variant to choose.
   * It only works for one caret (multiple carets not supported) and since it puts tab after completion, be sure to limit
   * line somehow (i.e. with comment).
   * <br/>
   * Example: "user.n[caret]." There are "name" and "nose" fields.
   * By calling this function with "nose" you will end with "user.nose  ".
   */
  protected final void completeCaretWithMultipleVariants(@NotNull final String... desiredVariants) {
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
  
}

