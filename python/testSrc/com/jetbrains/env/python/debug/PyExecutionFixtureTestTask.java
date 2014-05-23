package com.jetbrains.env.python.debug;

import com.google.common.collect.Lists;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.util.projectWizard.EmptyModuleBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.ModuleFixtureBuilderImpl;
import com.intellij.testFramework.fixtures.impl.ModuleFixtureImpl;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.fixtures.PyProfessionalTestCase;
import com.jetbrains.python.sdk.InvalidSdkException;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher;
import com.jetbrains.python.sdk.skeletons.SkeletonVersionChecker;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author traff
 */
public abstract class PyExecutionFixtureTestTask extends PyTestTask {
  public static final int NORMAL_TIMEOUT = 30000;
  public static final int LONG_TIMEOUT = 120000;
  private int myTimeout = NORMAL_TIMEOUT;
  protected CodeInsightTestFixture myFixture;

  public Project getProject() {
    return myFixture.getProject();
  }

  public void useNormalTimeout() {
    myTimeout = NORMAL_TIMEOUT;
  }

  public void useLongTimeout() {
    myTimeout = LONG_TIMEOUT;
  }

  public void setUp(final String testName) throws Exception {
    initFixtureBuilder();

    final TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(
      testName);

    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixtureBuilder.getFixture());

    UIUtil.invokeAndWaitIfNeeded(
      new Runnable() {
        @Override
        public void run() {
          ModuleFixtureBuilder moduleFixtureBuilder = fixtureBuilder.addModule(MyModuleFixtureBuilder.class);
          moduleFixtureBuilder.addSourceContentRoot(myFixture.getTempDirPath());
          moduleFixtureBuilder.addSourceContentRoot(getTestDataPath());
          final List<String> contentRoots = getContentRoots();
          for (String contentRoot : contentRoots) {
            moduleFixtureBuilder.addContentRoot(getTestDataPath() + contentRoot);
          }
        }
      }
    );


    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
  }

  protected List<String> getContentRoots() {
    return Lists.newArrayList();
  }

  protected String getTestDataPath() {
    return PyProfessionalTestCase.getProfessionalTestDataPath();
  }

  protected void initFixtureBuilder() {
    IdeaTestFixtureFactory.getFixtureFactory().registerFixtureBuilder(MyModuleFixtureBuilder.class, MyModuleFixtureBuilderImpl.class);
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

  protected void disposeProcess(ProcessHandler h) throws InterruptedException {
    h.destroyProcess();
    if (!waitFor(h)) {
      new Throwable("Can't stop process").printStackTrace();
    }
  }

  protected boolean waitFor(ProcessHandler p) throws InterruptedException {
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
   * @param sdkCreationType SDK creation strategy (see {@link com.jetbrains.env.python.debug.PyExecutionFixtureTestTask.SdkCreationType} doc)
   * @return sdk
   */
  @NotNull
  protected Sdk createTempSdk(@NotNull final String sdkHome, @NotNull final SdkCreationType sdkCreationType)
    throws InvalidSdkException, IOException {
    final VirtualFile binary = LocalFileSystem.getInstance().findFileByPath(sdkHome);
    Assert.assertNotNull("Interpreter file not found: " + sdkHome, binary);
    final Ref<Sdk> ref = Ref.create();
    UsefulTestCase.edt(new Runnable() {

      @Override
      public void run() {
        final Sdk sdk = SdkConfigurationUtil.setupSdk(new Sdk[0], binary, PythonSdkType.getInstance(), true, null, null);
        Assert.assertNotNull(sdk);
        ref.set(sdk);
      }
    });
    final Sdk sdk = ref.get();
    if (sdkCreationType != SdkCreationType.EMPTY_SDK) {
      generateTempSkeletonsOrPackages(sdk, sdkCreationType == SdkCreationType.SDK_PACKAGES_AND_SKELETONS);
    }
    UsefulTestCase.edt(new Runnable() {
      @Override
      public void run() {
        SdkConfigurationUtil.addSdk(sdk);
      }
    });
    return sdk;
  }


  // TODO: Doc
  private void generateTempSkeletonsOrPackages(@NotNull final Sdk sdk, final boolean addSkeletons) throws InvalidSdkException, IOException {
    final Project project = myFixture.getProject();
    ModuleRootModificationUtil.setModuleSdk(myFixture.getModule(), sdk);

    UsefulTestCase.edt(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            ProjectRootManager.getInstance(project).setProjectSdk(sdk);
          }
        });
      }
    });


    final SdkModificator modificator = sdk.getSdkModificator();
    modificator.removeRoots(OrderRootType.CLASSES);

    for (final String path : PythonSdkType.getSysPathsFromScript(sdk.getHomePath())) {
      PythonSdkType.addSdkRoot(modificator, path);
    }
    if (!addSkeletons) {
      UsefulTestCase.edt(new Runnable() {
        @Override
        public void run() {
          modificator.commitChanges();
        }
      });
      return;
    }

    final File tempDir = FileUtil.createTempDirectory(getClass().getName(), null);
    final File skeletonsDir = new File(tempDir, PythonSdkType.SKELETON_DIR_NAME);
    FileUtil.createDirectory(skeletonsDir);
    final String skeletonsPath = skeletonsDir.toString();
    PythonSdkType.addSdkRoot(modificator, skeletonsPath);

    UsefulTestCase.edt(new Runnable() {
      @Override
      public void run() {
        modificator.commitChanges();
      }
    });

    final SkeletonVersionChecker checker = new SkeletonVersionChecker(0);
    final PySkeletonRefresher refresher = new PySkeletonRefresher(project, null, sdk, skeletonsPath, null, null);
    final List<String> errors = refresher.regenerateSkeletons(checker, null);
    Assert.assertThat("Errors found", errors, Matchers.empty());
  }

  /**
   * SDK creation type
   */
  public enum SdkCreationType {
    /**
     * SDK only (no packages nor skeletons)
     */
    EMPTY_SDK,
    /**
     * SDK + installed packages from syspath
     */
    SDK_PACKAGES_ONLY,
    /**
     * SDK + installed packages from syspath + skeletons
     */
    SDK_PACKAGES_AND_SKELETONS
  }

  public interface MyModuleFixtureBuilder extends ModuleFixtureBuilder<ModuleFixture> {

  }
}
