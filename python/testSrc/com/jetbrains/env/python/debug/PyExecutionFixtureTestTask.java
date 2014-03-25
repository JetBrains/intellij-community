package com.jetbrains.env.python.debug;

import com.google.common.collect.Lists;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.util.projectWizard.EmptyModuleBuilder;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.ModuleFixtureBuilderImpl;
import com.intellij.testFramework.fixtures.impl.ModuleFixtureImpl;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.fixtures.PyProfessionalTestCase;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  /**
   * Creates sdk by SDK path
   * @param sdkHome python sdk path
   * @return sdk
   */
  @NotNull
  protected static Sdk getSdk(final String sdkHome) {
    Sdk sdk = PythonSdkType.findSdkByPath(sdkHome);

    return sdk != null ? sdk : new ProjectJdkImpl("Python Test Sdk " + sdkHome, PythonSdkType.getInstance()) {
      @Override
      public String getHomePath() {
        return sdkHome;
      }

      @Override
      public String getVersionString() {
        return "Python 2 Mock SDK";
      }
    };
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
      });


    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
  }

  protected List<String> getContentRoots() { return Lists.newArrayList();}

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

  public interface MyModuleFixtureBuilder extends ModuleFixtureBuilder<ModuleFixture> {

  }
}
