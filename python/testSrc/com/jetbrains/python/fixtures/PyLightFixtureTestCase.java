package com.jetbrains.python.fixtures;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.jetbrains.python.PythonMockSdk;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyFileImpl;
import com.jetbrains.python.psi.impl.PythonLanguageLevelPusher;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
@TestDataPath("$CONTENT_ROOT/../testData/")
public abstract class PyLightFixtureTestCase extends UsefulTestCase {
  private static final PyLightProjectDescriptor ourPyDescriptor = new PyLightProjectDescriptor("2.5");
  protected static final PyLightProjectDescriptor ourPy3Descriptor = new PyLightProjectDescriptor("3.1");
  private static final String PARSED_ERROR_MSG = "Operations should have been performed on stubs but caused file to be parsed";

  protected CodeInsightTestFixture myFixture;
  private static boolean ourPlatformPrefixInitialized;

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
    super.tearDown();
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

  protected static void assertNotParsed(PyFile file) {
    assertNull(PARSED_ERROR_MSG, ((PyFileImpl)file).getTreeElement());
  }

  protected static class PyLightProjectDescriptor implements LightProjectDescriptor {
    private final String myPythonVersion;

    public PyLightProjectDescriptor(String pythonVersion) {
      myPythonVersion = pythonVersion;
    }

    @Override
    public ModuleType getModuleType() {
      return EmptyModuleType.getInstance();
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
    if (!ourPlatformPrefixInitialized) {
      ourPlatformPrefixInitialized = true;
      boolean isIDEA = true;
      try {
        PyLightFixtureTestCase.class.getClassLoader().loadClass("com.intellij.openapi.project.impl.IdeaProjectManagerImpl");
      }
      catch (ClassNotFoundException e) {
        isIDEA = false;
      }
      if (!isIDEA) {
        System.setProperty("idea.platform.prefix", "Python");
      }
    }
  }
}
