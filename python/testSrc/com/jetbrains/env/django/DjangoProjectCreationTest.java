package com.jetbrains.env.django;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.UsefulTestCase;
import com.jetbrains.django.DjangoNames;
import com.jetbrains.django.model.DjangoAdmin;
import com.jetbrains.django.util.DjangoUtil;
import com.jetbrains.env.python.debug.PyEnvTestCase;
import com.jetbrains.env.python.debug.PyExecutionFixtureTestTask;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyBlockEvaluator;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

/**
 * Tests projection creation for Django
 *
 * @author Ilya.Kazakevich
 */
public class DjangoProjectCreationTest extends PyEnvTestCase {

  private static final String ROOT_FOLDER_NAME = "content_root";
  private static final String WEB_SITE_NAME = "myWebSite";

  public DjangoProjectCreationTest() {
    super("django");
  }


  // TODO: Doc
  public void testProjectWithTemplates() throws Exception {
    runPythonTest(new MyDjangoProjectCreationTest());
  }

  private static class MyDjangoProjectCreationTest extends PyExecutionFixtureTestTask {
    @Override
    public void runTestOn(@NotNull final String sdkHome) throws Exception {
      final Sdk sdk = createTempSdk(sdkHome, SdkCreationType.SDK_PACKAGES_ONLY);
      final String djangoPath = DjangoUtil.getDjangoPath(sdk);
      assert djangoPath != null : "No django found under " + sdkHome;

      final VirtualFile contentRoot = myFixture.getTempDirFixture().findOrCreateDir(ROOT_FOLDER_NAME);
      final String templatesDir = myFixture.getTempDirFixture().findOrCreateDir(ROOT_FOLDER_NAME + "/templates").getCanonicalPath();
      assert templatesDir != null : "Failed to create template dirs";

      UsefulTestCase.edt(new Runnable() {
        @Override
        public void run() {
          try {
            DjangoAdmin.createProject(
              myFixture.getProject(),
              sdk,
              contentRoot,
              WEB_SITE_NAME,
              "myMainApp",
              templatesDir,
              djangoPath,
              false,
              false
            );
          }
          catch (final ExecutionException ex) {
            Assert.fail(ex.toString());
          }
        }
      });

      final PyFile settingsPy =
        PyUtil.as(myFixture.configureByFile(ROOT_FOLDER_NAME + '/' + WEB_SITE_NAME + '/' + DjangoNames.SETTINGS_FILE), PyFile.class);
      Assert.assertNotNull(DjangoNames.SETTINGS_FILE + " not created", settingsPy);
      Assert.assertEquals("Bad file name for settings", DjangoNames.SETTINGS_FILE, settingsPy.getName());

      final PyBlockEvaluator evaluator = new PyBlockEvaluator();
      evaluator.evaluate(settingsPy);
      Object baseDirPath = evaluator.getValue("TEMPLATE_DIRS");

      // TODO: Finish test
    }
  }
}
