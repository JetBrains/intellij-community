// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.env;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.util.projectWizard.EmptyModuleBuilder;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueueImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.ModuleFixtureBuilderImpl;
import com.intellij.testFramework.fixtures.impl.ModuleFixtureImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.PythonTestUtil;
import com.jetbrains.python.extensions.ModuleExtKt;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

  @Nullable
  protected String getRelativeTestDataPath() {
    return myRelativeTestDataPath;
  }

  /**
   * Debug output of this classes will be captured and reported in case of test failure
   */
  @NotNull
  public Collection<Class<?>> getClassesToEnableDebug() {
    return Collections.emptyList();
  }

  public Project getProject() {
    return myFixture.getProject();
  }

  @Override
  public void useNormalTimeout() {
    myTimeout = NORMAL_TIMEOUT;
  }

  @Override
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
    PlatformPythonModuleType.ensureModuleRegistered();

    if (StringUtil.isNotEmpty(myRelativeTestDataPath)) {
      // Without performing the copy deliberately in the EDT, this code may stuck in a livelock for unclear reason.
      EdtTestUtil.runInEdtAndWait(() -> myFixture.copyDirectoryToProject(myRelativeTestDataPath, ".").getPath());
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
    return new ArrayList<>();
  }

  protected String getFilePath(@NotNull final String path) {
    final VirtualFile virtualFile = myFixture.getTempDirFixture().getFile(path);
    assert virtualFile != null && virtualFile.exists() : String.format("No file '%s' in %s", path, myFixture.getTempDirPath());
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

  @Override
  public void tearDown() throws Exception {
    if (myFixture != null) {
      EdtTestUtil.runInEdtAndWait(() -> {
        UIUtil.dispatchAllInvocationEvents();
        while (RefreshQueueImpl.isRefreshInProgress()) {
          UIUtil.dispatchAllInvocationEvents();
        }
        for (Sdk sdk : ProjectJdkTable.getInstance().getSdksOfType(PythonSdkType.getInstance())) {
          WriteAction.run(() -> {
            if (sdk instanceof Disposable && !Disposer.isDisposed((Disposable)sdk)) {
              ProjectJdkTable.getInstance().removeJdk(sdk);
            }
          });
        }
      });
      // Teardown should be called on main thread because fixture teardown checks for
      // thread leaks, and blocked main thread is considered as leaked
      Project project = myFixture.getProject();
      myFixture.tearDown();
      if (project != null && !project.isDisposed()) {
        ProjectManagerEx.getInstanceEx().forceCloseProject(project);
      }
      myFixture = null;
    }
    super.tearDown();
  }

  @Nullable
  protected LightProjectDescriptor getProjectDescriptor() {
    return null;
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

    @NotNull
    @Override
    protected ModuleFixture instantiateFixture() {
      return new ModuleFixtureImpl(this);
    }
  }

  public static class PlatformPythonModuleType extends PythonModuleTypeBase<EmptyModuleBuilder> {

    private static final String MODULE_ID = PyNames.PYTHON_MODULE_ID;

    @NotNull
    public static PlatformPythonModuleType getInstance() {
      ensureModuleRegistered();
      return (PlatformPythonModuleType)ModuleTypeManager.getInstance().findByID(PyNames.PYTHON_MODULE_ID);
    }

    static void ensureModuleRegistered() {
      ModuleTypeManager moduleManager = ModuleTypeManager.getInstance();
      if (!(moduleManager.findByID(MODULE_ID) instanceof PythonModuleTypeBase)) {
        moduleManager.registerModuleType(new PlatformPythonModuleType());
      }
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
   * @param sdkHome         path to sdk (probably obtained by {@link PyTestTask#runTestOn(String, Sdk)})
   * @param sdkCreationType SDK creation strategy (see {@link sdkTools.SdkCreationType} doc)
   * @return sdk
   */
  @NotNull
  protected Sdk createTempSdk(@NotNull final String sdkHome, @NotNull final SdkCreationType sdkCreationType) throws InvalidSdkException {

    final VirtualFile sdkHomeFile = LocalFileSystem.getInstance().findFileByPath(sdkHome);
    Assert.assertNotNull("Interpreter file not found: " + sdkHome, sdkHomeFile);

    // There can't be two SDKs with same path
    removeSdkIfExists(sdkHomeFile.toNioPath());

    CompletableFuture<Sdk> sdkRef = new CompletableFuture<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        sdkRef.complete(PySdkTools.createTempSdk(sdkHomeFile, sdkCreationType, myFixture.getModule(), myFixture.getTestRootDisposable()));
      }
      catch (InvalidSdkException e) {
        sdkRef.completeExceptionally(e);
      }
    });
    Sdk sdk;
    try {
      sdk = sdkRef.join();
    }
    catch (CompletionException err) {
      if (err.getCause() instanceof InvalidSdkException cause) throw cause;
      if (err.getCause() instanceof Error cause) throw cause;
      if (err.getCause() instanceof RuntimeException cause) throw cause;
      throw err;
    }
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

  private static void removeSdkIfExists(@NotNull Path sdkHomePath) {
    var sdkTable = ProjectJdkTable.getInstance();
    var existingSdk =
      ContainerUtil.find(sdkTable.getAllJdks(), sdk -> sdkHomePath.equals(Path.of(sdk.getHomePath().trim())));
    if (existingSdk != null) {
      ApplicationManager.getApplication().invokeAndWait(() -> {
        WriteAction.run(() -> {
          sdkTable.removeJdk(existingSdk);
        });
      });
    }
  }


  public interface MyModuleFixtureBuilder extends ModuleFixtureBuilder<ModuleFixture> {

  }
}
