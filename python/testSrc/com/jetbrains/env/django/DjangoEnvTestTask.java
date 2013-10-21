package com.jetbrains.env.django;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.django.facet.DjangoFacet;
import com.jetbrains.django.fixtures.DjangoTestCase;
import com.jetbrains.env.python.debug.PyDebuggerTask;
import com.jetbrains.python.fixtures.PyProfessionalTestCase;
import com.jetbrains.python.templateLanguages.TemplatesService;
import org.junit.Assert;

import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;
import java.util.Set;

/**
 * @author traff
 */
public class DjangoEnvTestTask extends PyDebuggerTask {

  protected final int myPort;

  protected String getTestDataPath() {
    return PyProfessionalTestCase.getProfessionalTestDataPath() + "/django/debug";
  }

  public DjangoEnvTestTask(String workingFolder, String scriptName, String scriptParameters, int port) {
    setWorkingFolder(getTestDataPath() + workingFolder);
    setScriptName(scriptName);
    setScriptParameters(scriptParameters + port);
    myPort = port;
  }

  @Override
  public void setUp(String testName) throws Exception {
    super.setUp(testName);
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      public void run() {
        Module module = myFixture.getModule();
        if (module != null && !DjangoFacet.isPresent(module)) {
          DjangoTestCase.addDjangoFacet(module);
          TemplatesService.getInstance(module).setTemplateLanguage(TemplatesService.DJANGO);
        }
        TemplatesService.getInstance(module).setTemplateLanguage(TemplatesService.DJANGO);
      }
    });
  }

  @Override
  public void tearDown() throws Exception {
    TemplatesService.getInstance(myFixture.getModule()).setTemplateLanguage(TemplatesService.NONE);
    super.tearDown();
  }

  public static void checkModuleForFile(Project project, VirtualFile vFile) {
    Module module = ModuleUtil.findModuleForFile(vFile, project);
    Assert.assertNotNull(module); //should be in our module for correct breakpoints setting
    Assert.assertTrue(DjangoFacet.isPresent(module));
  }

  static class Response {
    private final String content;


    public Response(String content) {
      this.content = content;
    }

    public void contains(String string) {
      Assert.assertTrue("Cant find '" + string + "' in content: \n" + content, content.contains(string));
    }

    public static Response load(String address) {
      StringBuilder sb = new StringBuilder();
      try {
        Thread.sleep(500);
        // Send data
        URL url = new URL(address);
        URLConnection conn = url.openConnection();
        conn.setDoOutput(true);
        conn.setConnectTimeout(0);
        conn.setReadTimeout(0);
        conn.setUseCaches(false);

        // Get the response
        Scanner s = new Scanner(conn.getInputStream());


        while (s.hasNextLine()) {
          String line = s.nextLine();
          sb.append(line + "\n");
          // Process line...
        }
      }
      catch (Exception e) {
        sb = new StringBuilder(e.getMessage());
      }
      return new Response(sb.toString());
    }
  }

  protected static LoadingPage loadPage(final String url) {
    final Ref<DjangoEnvTestTask.Response> response = Ref.create(null);

    Thread thread = new Thread(new Runnable() {
      @Override
      public void run() {
        response.set(Response.load("http://127.0.0.1:" + url));
      }
    });
    thread.start();
    return new LoadingPage(thread, response);
  }

  protected static class LoadingPage {
    private Thread myThread;
    private Ref<Response> myResponse;

    public LoadingPage(Thread thread, Ref<Response> response) {
      myThread = thread;
      myResponse = response;
    }

    public void join() throws InterruptedException {
      myThread.join();
    }

    public Response get() throws InterruptedException {
      join();
      return myResponse.get();
    }
  }

  @Override
  public Set<String> getTags() {
    return ImmutableSet.of("django");
  }
}
