package com.jetbrains.env.django;

import com.google.common.collect.Sets;
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
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.List;
import java.util.Set;

/**
 * Tests projection creation for Django
 *
 * @author Ilya.Kazakevich
 */
public class DjangoProjectCreationTest extends PyEnvTestCase {

  private static final String ROOT_FOLDER_NAME = "content_root";
  private static final String WEB_SITE_NAME = "myWebSite";
  private static final String DJANGO_TAG = "django";

  public DjangoProjectCreationTest() {
    super(DJANGO_TAG);
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
      final String templateDir = myFixture.getTempDirFixture().findOrCreateDir(ROOT_FOLDER_NAME + "/templates_dir").getCanonicalPath();
      assert templateDir != null : "Failed to create template dirs";

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
              templateDir,
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

      final String pathToSettingsPy;
      if (DjangoAdmin.isDjangoVersionAtLeast(sdk, "1.4")) {
        pathToSettingsPy = ROOT_FOLDER_NAME + '/' + WEB_SITE_NAME + '/' + DjangoNames.SETTINGS_FILE;
      }
      else {
        pathToSettingsPy = ROOT_FOLDER_NAME + '/' + DjangoNames.SETTINGS_FILE;
      }

      final PyFile settingsPy =
        PyUtil.as(myFixture.configureByFile(pathToSettingsPy), PyFile.class);
      Assert.assertNotNull(DjangoNames.SETTINGS_FILE + " not created", settingsPy);
      Assert.assertEquals("Bad file name for settings", DjangoNames.SETTINGS_FILE, settingsPy.getName());

      final PyBlockEvaluator evaluator = new PyBlockEvaluator();
      evaluator.evaluate(settingsPy);

      final List<String> templateDirsFromConfig = evaluator.getValueAsStringList(DjangoNames.TEMPLATE_DIRS_SETTING);
      Assert.assertThat(String.format("Failed to find %s in %s", DjangoNames.TEMPLATE_DIRS_SETTING, settingsPy), templateDirsFromConfig,
                        Matchers.allOf(Matchers.notNullValue(), Matchers.not(Matchers.empty())));

      Assert.assertThat("Wrong template dirs in config", templateDirsFromConfig, Matchers.contains(templateDir));

    }

    @Override
    public Set<String> getTags() {
      return Sets.newHashSet(DJANGO_TAG);
    }
  }
}
