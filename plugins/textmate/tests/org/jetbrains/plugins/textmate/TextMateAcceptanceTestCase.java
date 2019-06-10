package org.jetbrains.plugins.textmate;

import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.plugins.textmate.configuration.BundleConfigBean;
import org.jetbrains.plugins.textmate.configuration.TextMateSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.newHashSet;

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
public abstract class TextMateAcceptanceTestCase extends LightPlatformCodeInsightFixtureTestCase {
  private static final Set<String> loadingBundles = newHashSet(TestUtil.MARKDOWN_TEXTMATE, TestUtil.HTML, TestUtil.LATEX, TestUtil.PHP, TestUtil.BAT, TestUtil.JAVASCRIPT);

  @Override
  public void setUp() throws Exception {
    super.setUp();
    TextMateSettings.TextMateSettingsState state = TextMateSettings.getInstance().getState();
    if (state == null) {
      state = new TextMateSettings.TextMateSettingsState();
    }
    Set<String> enabledBundles = ContainerUtil.map2Set(state.getBundles(), bean -> bean.getName());
    if (!loadingBundles.equals(enabledBundles)) {
      List<BundleConfigBean> bundles = new ArrayList<>();
      for (String bundleName : loadingBundles) {
        bundles.add(new BundleConfigBean(bundleName, TestUtil.getBundleDirectoryPath(bundleName), true));
      }
      state.setBundles(bundles);
      TextMateSettings.getInstance().loadState(state);
      TextMateService.getInstance().unregisterAllBundles(true);
      TextMateService.getInstance().registerEnabledBundles(false);
      UIUtil.dispatchAllInvocationEvents();
    }
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      TextMateSettings.getInstance().loadState(new TextMateSettings.TextMateSettingsState());
      TextMateService.getInstance().unregisterAllBundles(true);
      TextMateService.getInstance().registerEnabledBundles(true);
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
  protected final String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/textmate/tests/org/jetbrains/plugins/textmate" + getTestPath();
  }
}
