package org.jetbrains.plugins.textmate;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.plugins.textmate.configuration.TextMatePersistentBundle;
import org.jetbrains.plugins.textmate.configuration.TextMateUserBundlesSettings;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Base class for tests needs in fixture.
 * It loads defined bundles on setUp.
 * <p/>
 * IMPORTANT!!!
 * Note that in order to use TextMate plugin for file types that already have native support,
 * some bundles are hacked. E.g. in php bundle added extension 'php_hack'.
 * So use HACKED extensions in your tests.
 * <p/>
 */
public abstract class TextMateAcceptanceTestCase extends BasePlatformTestCase {
  private static final Set<String> loadingBundles =
    ContainerUtil.newHashSet(TestUtil.MARKDOWN_TEXTMATE, TestUtil.HTML, TestUtil.LATEX, TestUtil.PHP, TestUtil.BAT, TestUtil.JAVASCRIPT);

  @Override
  public void setUp() throws Exception {
    super.setUp();
    TextMateUserBundlesSettings settings = TextMateUserBundlesSettings.getInstance();
    assertNotNull(settings);
    Set<String> enabledBundles = ContainerUtil.map2Set(settings.getBundles().values(), bean -> bean.getName());
    if (!loadingBundles.equals(enabledBundles)) {
      Map<String, TextMatePersistentBundle> bundles = new HashMap<>();
      for (String bundleName : loadingBundles) {
        String path = TestUtilMultiplatform.INSTANCE.getBundleDirectoryPath(bundleName);
        bundles.put(FileUtil.toSystemIndependentName(path), new TextMatePersistentBundle(bundleName, true));
      }
      settings.setBundlesConfig(bundles);
      ((TextMateServiceImpl)TextMateService.getInstance()).disableBuiltinBundles(getTestRootDisposable());
      UIUtil.dispatchAllInvocationEvents();
    }
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      TextMateUserBundlesSettings.getInstance().setBundlesConfig(Collections.emptyMap());
      UIUtil.dispatchAllInvocationEvents();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected String getTestPath() {
    return "";
  }

  @Override
  protected String getBasePath() {
    return "/plugins/textmate/testData" + getTestPath();
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }
}
