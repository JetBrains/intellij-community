package com.jetbrains.env.django;

import com.google.common.collect.Sets;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.xdebugger.XDebuggerTestUtil;
import com.jetbrains.django.util.VirtualFileUtil;
import com.jetbrains.env.python.debug.PyEnvTestCase;
import com.jetbrains.python.debugger.django.DjangoExceptionBreakpointProperties;
import com.jetbrains.python.debugger.django.DjangoExceptionBreakpointType;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Set;

/**
 * @author traff
 */
public class DjangoTemplateDebuggerTest extends PyEnvTestCase {

  public void testBreakpointStopAndEval() throws IOException {
    final int[] ports = findFreePorts(2);
    runPythonTest(new BreakpointStopAndEvalTask("/djangoDebug", "manage.py", "runserver --noreload ", ports[1]));
  }

  private static class BreakpointStopAndEvalTask extends DjangoEnvTestTask {
    public BreakpointStopAndEvalTask(String workingFolder, String scriptName, String scriptParameters, int port) {
      super(workingFolder, scriptName, scriptParameters, port);
    }

    @Override
    public void before() throws Exception {
      //setShouldPrintOutput(true);
      String file = getFilePath("/templates/test1.html");
      checkModuleForFile(getProject(), VirtualFileUtil.findFile(file));

      toggleBreakpoint(file, 6);
    }

    @Override
    public void testing() throws Exception {
      waitForStart();

      DjangoEnvTestTask.LoadingPage page = loadPage(myPort + "/test1");

      waitForPause();

      eval("x").hasValue("1");

      resume();

      waitForPause();

      eval("x").hasValue("2");

      resume();

      waitForPause();

      eval("x").hasValue("3");

      resume();

      page.get().contains("TemplateDebugging");
    }
  }

  public void testSetVal() throws IOException {
//setShouldPrintOutput(true);
    final int[] ports = findFreePorts(2);
    runPythonTest(new DjangoEnvTestTask("/djangoDebug", "manage.py", "runserver --noreload ", ports[1]) {
      @Override
      public void before() throws Exception {
        String file = getFilePath("/templates/test1.html");
        checkModuleForFile(getProject(), VirtualFileUtil.findFile(file));

        toggleBreakpoint(file, 6);
      }

      @Override
      public void testing() throws Exception {
        waitForStart();

        LoadingPage page = loadPage(myPort + "/test1");

        waitForPause();

        eval("x").hasValue("1");
        eval("name").hasValue("'TemplateDebugging'");

        setVal("name", "'TemplateDjangoDebugging'");

        resume();

        waitForPause();

        eval("x").hasValue("2");
        eval("name").hasValue("'TemplateDjangoDebugging'");

        resume();

        waitForPause();

        eval("x").hasValue("3");
        eval("name").hasValue("'TemplateDjangoDebugging'");

        resume();

        page.get().contains("templatedjangodebugging");
      }

      
    });
  }

  public void testVariableDoesNotExistExceptionBreak() throws IOException {
//setShouldPrintOutput(true);
    final int[] ports = findFreePorts(2);
    runPythonTest(new DjangoEnvTestTask("/djangoDebug", "manage.py", "runserver --noreload ", ports[1]) {
      @Override
      public void before() throws Exception {
        addDjangoExceptionBreakpoint(myFixture);
      }

      @Override
      public void testing() throws Exception {
        waitForStart();

        LoadingPage page = loadPage(myPort + "/test2");

        waitForPause();

        eval("x").hasValue("1");
        eval("name").hasValue("'TemplateDebugging'");

        setVal("name", "'TemplateDjangoDebugging'");

        resume();

        waitForPause();

        eval("x").hasValue("2");
        eval("name").hasValue("'TemplateDjangoDebugging'");

        resume();

        waitForPause();

        eval("x").hasValue("3");
        eval("name").hasValue("'TemplateDjangoDebugging'");

        resume();

        page.get().contains("templatedjangodebugging");
      }
    });
  }

  public void testTemplateDoseNotExistsExceptionBreak() throws IOException {
//setShouldPrintOutput(true);
    final int[] ports = findFreePorts(2);
    runPythonTest(new DjangoEnvTestTask("/djangoDebug", "manage.py", "runserver --noreload ", ports[1]) {
      @Override
      public void before() throws Exception {
        addDjangoExceptionBreakpoint(myFixture);
      }

      @Override
      public void testing() throws Exception {
        waitForStart();

        LoadingPage page = loadPage(myPort + "/test3");

        waitForPause();

        resume();

        page.get().contains("Server returned HTTP response code: 500");
      }
    });
  }

  public void testConditionalBreakpointStop() throws IOException {

    final int[] ports = findFreePorts(2);
    runPythonTest(new DjangoEnvTestTask("/djangoDebug", "manage.py", "runserver --noreload ", ports[1]) {
      @Override
      public void before() throws Exception {
        //setShouldPrintOutput(true);
        String file = getFilePath("/templates/test1.html");
        checkModuleForFile(getProject(), VirtualFileUtil.findFile(file));

        toggleBreakpoint(file, 6);
        XDebuggerTestUtil.setBreakpointCondition(getProject(), 6, "x == 1 or x == 3");
      }

      @Override
      public void testing() throws Exception {
        waitForStart();

        LoadingPage page = loadPage(myPort + "/test1");

        waitForPause();

        eval("x").hasValue("1");

        resume();

        waitForPause();

        eval("x").hasValue("3");

        resume();

        page.get().contains("TemplateDebugging");
      }
    });
  }

  public void testBreakpointStopAndStepOver() throws IOException {

    final int[] ports = findFreePorts(2);
    runPythonTest(new DjangoEnvTestTask("/djangoDebug", "manage.py", "runserver --noreload ", ports[1]) {
      @Override
      public void before() throws Exception {
        //setShouldPrintOutput(true);
        String file = getFilePath("/templates/test4.html");
        checkModuleForFile(getProject(), VirtualFileUtil.findFile(file));

        toggleBreakpoint(file, 6);
      }

      @Override
      public void testing() throws Exception {
        waitForStart();

        loadPage(ports[1] + "/test4");

        waitForPause();

        eval("x").hasValue("1");

        stepOver();

        waitForPause();

        stepOver();

        waitForPause();

        eval("y").hasValue("1");

        resume();
      }
    });
  }

  public void testBreakpointLogExpression() throws IOException {

    final int[] ports = findFreePorts(2);
    runPythonTest(new DjangoEnvTestTask("/djangoDebug", "manage.py", "runserver --noreload ", ports[1]) {
      @Override
      public void before() throws Exception {
        //setShouldPrintOutput(true);
        String file = getFilePath("/templates/test1.html");
        checkModuleForFile(getProject(), VirtualFileUtil.findFile(file));

        toggleBreakpoint(file, 6);
        XDebuggerTestUtil.setBreakpointLogExpression(getProject(), 6, "'x = %d'%x");
      }

      @Override
      public void testing() throws Exception {
        waitForStart();

        loadPage(ports[1] + "/test1");

        waitForPause();
        resume();
        waitForOutput("x = 2");
      }
    });
  }


  public void testStepInto() throws IOException {

    final int[] ports = findFreePorts(2);
    runPythonTest(new DjangoEnvTestTask("/djangoDebug", "manage.py", "runserver --noreload ", ports[1]) {
      @Override
      public void before() throws Exception {
        //setShouldPrintOutput(true);
        String file = getFilePath("/templates/test5.html");
        checkModuleForFile(getProject(), VirtualFileUtil.findFile(file));

        toggleBreakpoint(file, 5);
      }

      @Override
      public void testing() throws Exception {
        waitForStart();

        loadPage(ports[1] + "/test5");

        waitForPause();
        stepInto();
        waitForPause();
        stepOver();
        waitForPause();

        eval("res").hasValue("'Result'");
        stepOver();
        waitForPause();
        eval("name").hasValue("'TemplateDebugging5'");
      }
    });
  }

  private static void addDjangoExceptionBreakpoint(IdeaProjectTestFixture fixture) {
    XDebuggerTestUtil.addBreakpoint(fixture.getProject(), DjangoExceptionBreakpointType.class, new DjangoExceptionBreakpointProperties());
  }

  public static int[] findFreePorts(int count)
    throws IOException {
    ServerSocket[] servers = new ServerSocket[count];
    int[] port = new int[count];

    for (int i = 0; i < count; i++) {
      servers[i] = new ServerSocket(0);
      port[i] = servers[i].getLocalPort();
    }

    for (int i = 0; i < count; i++) {
      servers[i].close();
    }

    return port;
  }


  public void testBreakpointStopAndEvalInAutoReloadMode() throws IOException {
    final int[] ports = findFreePorts(4);
    BreakpointStopAndEvalTask testTask = new BreakpointStopAndEvalTask("/djangoDebug", "manage.py", "runserver ", ports[3]) {
      @Override
      public Set<String> getTags() {
        Set<String> tags = Sets.newHashSet(super.getTags());
        tags.add("-django13");  //Django 1.3 doesn't work in reload mode on Unix
        return tags;
      }
    };
    //testTask.setShouldPrintOutput(true);
    testTask.setMultiprocessDebug(true);
    runPythonTest(testTask);
  }
}


