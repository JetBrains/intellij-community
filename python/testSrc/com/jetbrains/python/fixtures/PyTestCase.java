/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.find.findUsages.CustomUsageSearcher;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.util.CommonProcessors.CollectProcessor;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.PythonMockSdk;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/../testData/")
public abstract class PyTestCase extends UsefulTestCase {
  public static String PYTHON_2_MOCK_SDK = "2.7";
  public static String PYTHON_3_MOCK_SDK = "3.2";

  private static final PyLightProjectDescriptor ourPyDescriptor = new PyLightProjectDescriptor(PYTHON_2_MOCK_SDK);
  protected static final PyLightProjectDescriptor ourPy3Descriptor = new PyLightProjectDescriptor(PYTHON_3_MOCK_SDK);
  private static final String PARSED_ERROR_MSG = "Operations should have been performed on stubs but caused file to be parsed";

  protected CodeInsightTestFixture myFixture;

  @Nullable
  protected static VirtualFile getVirtualFileByName(String fileName) {
    return LocalFileSystem.getInstance().findFileByPath(fileName.replace(File.separatorChar, '/'));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initPlatformPrefix();
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(getProjectDescriptor());
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
      myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture,
                                                                                      new LightTempDirTestFixtureImpl(true));
      myFixture.setUp();

      myFixture.setTestDataPath(getTestDataPath());
    }

  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath();
  }

  @Override
  protected void tearDown() throws Exception {
    setLanguageLevel(null);
    myFixture.tearDown();
    myFixture = null;
    final PythonLanguageLevelPusher levelPusher = Extensions.findExtension(FilePropertyPusher.EP_NAME, PythonLanguageLevelPusher.class);
    levelPusher.flushLanguageLevelCache();
    super.tearDown();
    clearFields(this);
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

  /**
   * Searches for quickfix itetion by its class
   *
   * @param clazz quick fix class
   * @param <T>   quick fix class
   * @return quick fix or null if nothing found
   */
  @Nullable
  public <T extends LocalQuickFix> T findQuickFixByClassInIntentions(@NotNull final Class<T> clazz) {

    for (final IntentionAction action : myFixture.getAvailableIntentions()) {
      if ((action instanceof QuickFixWrapper)) {
        final QuickFixWrapper quickFixWrapper = (QuickFixWrapper)action;
        final LocalQuickFix fix = quickFixWrapper.getFix();
        if (clazz.isInstance(fix)) {
          @SuppressWarnings("unchecked")
          final T result = (T)fix;
          return result;
        }
      }
    }
    return null;
  }


  protected static void assertNotParsed(PyFile file) {
    assertNull(PARSED_ERROR_MSG, ((PyFileImpl)file).getTreeElement());
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
    final Collection<PsiElement> result = new ArrayList<PsiElement>();
    final CollectProcessor<Usage> usageCollector = new CollectProcessor<Usage>();
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

  protected static class PyLightProjectDescriptor implements LightProjectDescriptor {
    private final String myPythonVersion;

    public PyLightProjectDescriptor(String pythonVersion) {
      myPythonVersion = pythonVersion;
    }

    @Override
    public ModuleType getModuleType() {
      return PythonModuleTypeBase.getInstance();
    }

    @Override
    public Sdk getSdk() {
      return PythonMockSdk.findOrCreate(myPythonVersion);
    }

    @Override
    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
    }

    protected void createLibrary(ModifiableRootModel model, final String name, final String path) {
      final Library.ModifiableModel modifiableModel = model.getModuleLibraryTable().createLibrary(name).getModifiableModel();
      final VirtualFile home =
        LocalFileSystem.getInstance().refreshAndFindFileByPath(PathManager.getHomePath() + path);

      modifiableModel.addRoot(home, OrderRootType.CLASSES);
      modifiableModel.commit();
    }
  }

  public static void initPlatformPrefix() {
    PlatformTestCase.autodetectPlatformPrefix();
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
}

