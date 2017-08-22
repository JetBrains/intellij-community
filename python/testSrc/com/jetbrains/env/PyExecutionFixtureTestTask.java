package com.jetbrains.env;

import com.google.common.collect.Lists;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.util.projectWizard.EmptyModuleBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.ModuleFixtureBuilderImpl;
import com.intellij.testFramework.fixtures.impl.ModuleFixtureImpl;
import com.jetbrains.extensions.ModuleExtKt;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.packaging.PyCondaPackageManagerImpl;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.tools.sdkTools.PySdkTools;
import com.jetbrains.python.tools.sdkTools.SdkCreationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.File;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * <h1>Task to execute code using {@link CodeInsightTestFixture}</h1>
 * <h2>How to use it</h2>
 * <p>
 * Each test may have some test data somewhere in VCS (like <strong>testData</strong> folder).
 * It is called <strong>test data path</strong>.
 * This task copies test data to some temporary location, and launches your test against it.
 * To get this location, use {@link CodeInsightTestFixture#getTempDirFixture()}
 * or {@link CodeInsightTestFixture#getTempDirPath()}.
 * </p>
 * <p>
 * You provide path to test data using 2 parts: base ({@link #getTestDataPath()} and relative
 * path as argument to {@link PyExecutionFixtureTestTask#PyExecutionFixtureTestTask(String)}.
 * Path is merged then, and data copied to temporary location, available with {@link CodeInsightTestFixture#getTempDirFixture()}.
 * You may provide <strong>null</strong> to argument if you do not want to copy anything.
 * </p>
 * <h2>Things to check to make sure you use this code correctly</h2>
 * <ol>
 * <li>
 * You never access {@link #getTestDataPath()} or {@link CodeInsightTestFixture#getTempDirPath()} in tests.
 * <strong>Always</strong> work with {@link CodeInsightTestFixture#getTempDirFixture()}
 * </li>
 * <li>
 * When overwriting {@link #getTestDataPath()} you return path to your <strong>testData</strong> (see current impl.)
 * </li>
 * </ol>
 *
 * @author traff
 * @author Ilya.Kazakevich
 */
public abstract class PyExecutionFixtureTestTask extends PyTestTask {
  public static final int NORMAL_TIMEOUT = 30000;
  public static final int LONG_TIMEOUT = 120000;
  protected int myTimeout = NORMAL_TIMEOUT;
  protected CodeInsightTestFixture myFixture;

  @Nullable
  private final String myRelativeTestDataPath;


  /**
   * @param relativeTestDataPath path that will be added to {@link #getTestDataPath()} to obtain test data path (the one
   *                             that will be copied to temp folder. See class doc.).
   *                             Pass null if you do not want to copy anything.
   */
  protected PyExecutionFixtureTestTask(@Nullable final String relativeTestDataPath) {
    myRelativeTestDataPath = relativeTestDataPath;
  }

  public Project getProject() {
    return myFixture.getProject();
  }

  public void useNormalTimeout() {
    myTimeout = NORMAL_TIMEOUT;
  }

  public void useLongTimeout() {
    myTimeout = LONG_TIMEOUT;
  }

  /**
   * Returns virt file by path. May be relative or not.
   *
   * @return file or null if file does not exist
   */
  @Nullable
  protected VirtualFile getFileByPath(@NotNull final String path) {
    final File fileToWorkWith = new File(path);

    return (fileToWorkWith.isAbsolute()
            ? LocalFileSystem.getInstance().findFileByIoFile(fileToWorkWith)
            : myFixture.getTempDirFixture().getFile(path));
  }

  @Override
  public void setUp(final String testName) throws Exception {
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    fixtureFactory.registerFixtureBuilder(MyModuleFixtureBuilder.class, MyModuleFixtureBuilderImpl.class);
    final TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder =
      fixtureFactory.createFixtureBuilder(testName);
    fixtureBuilder.addModule(MyModuleFixtureBuilder.class);
    myFixture = fixtureFactory.createCodeInsightFixture(fixtureBuilder.getFixture());
    myFixture.setTestDataPath(getTestDataPath());
    myFixture.setUp();

    final Module module = myFixture.getModule();
    assert module != null;

    if (StringUtil.isNotEmpty(myRelativeTestDataPath)) {
      myFixture.copyDirectoryToProject(myRelativeTestDataPath, ".").getPath();
    }

    final VirtualFile projectRoot = myFixture.getTempDirFixture().getFile(".");
    PsiTestUtil.addSourceRoot(module, projectRoot);
    PsiTestUtil.addContentRoot(module, projectRoot);

    for (final String contentRoot : getContentRoots()) {
      final VirtualFile file = myFixture.getTempDirFixture().getFile(contentRoot);
      assert file != null && file.exists() : String.format("Content root does not exist %s", file);
      PsiTestUtil.addContentRoot(module, file);
    }
  }

  @NotNull
  public LanguageLevel getLevelForSdk() {
    return PythonSdkType.getLanguageLevelForSdk(ModuleExtKt.getSdk(myFixture.getModule()));
  }

  /**
   * @return additional content roots
   */
  @NotNull
  protected List<String> getContentRoots() {
    return Lists.newArrayList();
  }

  protected String getFilePath(@NotNull final String path) {
    final VirtualFile virtualFile = myFixture.getTempDirFixture().getFile(path);
    assert virtualFile != null && virtualFile.exists() : String.format("No file in %s", myFixture.getTempDirPath());
    return virtualFile.getPath();
  }

  /**
   * @return root of your test data path on filesystem (this is base folder: class will add its relative path from ctor
   * to create full path and copy it to temp folder, see class doc.)
   */
  @NotNull
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath();
  }

  public void tearDown() throws Exception {
    if (myFixture != null) {
      myFixture.tearDown();
      myFixture = null;
    }
  }

  @Nullable
  protected LightProjectDescriptor getProjectDescriptor() {
    return null;
  }

  protected void disposeProcess(ProcessHandler h) {
    h.destroyProcess();
    if (!waitFor(h)) {
      new Throwable("Can't stop process").printStackTrace();
    }
  }

  protected boolean waitFor(ProcessHandler p) {
    return p.waitFor(myTimeout);
  }

  protected boolean waitFor(@NotNull Semaphore s) throws InterruptedException {
    return waitFor(s, myTimeout);
  }

  protected static boolean waitFor(@NotNull Semaphore s, long timeout) throws InterruptedException {
    return s.tryAcquire(timeout, TimeUnit.MILLISECONDS);
  }

  public static class MyModuleFixtureBuilderImpl extends ModuleFixtureBuilderImpl<ModuleFixture> implements MyModuleFixtureBuilder {
    public MyModuleFixtureBuilderImpl(TestFixtureBuilder<? extends IdeaProjectTestFixture> fixtureBuilder) {
      super(new PlatformPythonModuleType(), fixtureBuilder);
    }

    @Override
    protected ModuleFixture instantiateFixture() {
      return new ModuleFixtureImpl(this);
    }
  }

  public static class PlatformPythonModuleType extends PythonModuleTypeBase<EmptyModuleBuilder> {
    @NotNull
    public static PlatformPythonModuleType getInstance() {
      return (PlatformPythonModuleType)ModuleTypeManager.getInstance().findByID(PYTHON_MODULE);
    }


    @NotNull
    @Override
    public EmptyModuleBuilder createModuleBuilder() {
      return new EmptyModuleBuilder() {
        @Override
        public ModuleType getModuleType() {
          return getInstance();
        }
      };
    }
  }


  /**
   * Creates SDK by its path
   *
   * @param sdkHome         path to sdk (probably obtained by {@link #runTestOn(String)})
   * @param sdkCreationType SDK creation strategy (see {@link sdkTools.SdkCreationType} doc)
   * @return sdk
   */
  @NotNull
  protected Sdk createTempSdk(@NotNull final String sdkHome, @NotNull final SdkCreationType sdkCreationType)
    throws InvalidSdkException {
    final VirtualFile sdkHomeFile = LocalFileSystem.getInstance().findFileByPath(sdkHome);
    Assert.assertNotNull("Interpreter file not found: " + sdkHome, sdkHomeFile);
    final Sdk sdk = PySdkTools.createTempSdk(sdkHomeFile, sdkCreationType, myFixture.getModule());
    // We use gradle script to create environment. This script utilizes Conda.
    // Conda supports 2 types of package installation: conda native and pip. We use pip.
    // PyCharm Conda support ignores packages installed via pip ("conda list -e" does it, see PyCondaPackageManagerImpl)
    // So we need to either fix gradle (PythonEnvsPlugin.groovy on github) or use helper instead of "conda list" to get all packages
    // We do the latter.
    final PyPackageManager packageManager = PyPackageManager.getInstance(sdk);
    if (packageManager instanceof PyCondaPackageManagerImpl) {
      ((PyCondaPackageManagerImpl)packageManager).useConda = false;
    }
    return sdk;
  }


  public interface MyModuleFixtureBuilder extends ModuleFixtureBuilder<ModuleFixture> {

  }
}
