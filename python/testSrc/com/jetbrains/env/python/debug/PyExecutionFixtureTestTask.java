package com.jetbrains.env.python.debug;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.util.projectWizard.EmptyModuleBuilder;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.ModuleFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.ModuleFixtureBuilderImpl;
import com.intellij.testFramework.fixtures.impl.ModuleFixtureImpl;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.PythonTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @author traff
 */
public abstract class PyExecutionFixtureTestTask extends PyTestTask {
  public static final int NORMAL_TIMEOUT = 30000;
  public static final int LONG_TIMEOUT = 120000;
  private int myTimeout = NORMAL_TIMEOUT;
  protected IdeaProjectTestFixture myFixture;

  public Project getProject() {
    return myFixture.getProject();
  }

  public void useNormalTimeout() {
    myTimeout = NORMAL_TIMEOUT;
  }

  public void useLongTimeout() {
    myTimeout = LONG_TIMEOUT;
  }

  public void setUp() throws Exception {
    initFixtureBuilder();

    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createFixtureBuilder();

    myFixture = fixtureBuilder.getFixture();

    UIUtil.invokeAndWaitIfNeeded(
      new Runnable() {
        @Override
        public void run() {
          ModuleFixtureBuilder moduleFixtureBuilder = fixtureBuilder.addModule(MyModuleFixtureBuilder.class);
          moduleFixtureBuilder.addSourceContentRoot(getTestDataPath());
        }
      });

    myFixture.setUp();
  }

  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath();
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
