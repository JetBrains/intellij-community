package org.jetbrains.plugins.textmate;

import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.textmate.bundles.Bundle;
import org.jetbrains.plugins.textmate.bundles.BundleFactory;
import org.jetbrains.plugins.textmate.plist.CompositePlistReader;
import org.jetbrains.plugins.textmate.plist.PlistReader;

import java.io.File;

public class TestUtil {
  @NonNls public static final String BAT = "bat";
  @NonNls public static final String JAVA = "java";
  @NonNls public static final String LOG = "log";
  @NonNls public static final String JAVASCRIPT = "javascript";
  @NonNls public static final String CHEF = "chef";
  @NonNls public static final String HTML = "html";
  @NonNls public static final String HTML_VSC = "html_vsc";
  @NonNls public static final String MARKDOWN_SUBLIME = "markdown_sublime";
  @NonNls public static final String MARKDOWN_TEXTMATE = "markdown_textmate";
  @NonNls public static final String LARAVEL_BLADE = "laravel-blade";
  @NonNls public static final String INVALID_BUNDLE = "invalid_bundle";
  @NonNls public static final String LATEX = "latex";
  @NonNls public static final String PERL = "perl";
  @NonNls public static final String SHELLSCRIPT = "shellscript";
  @NonNls public static final String ELIXIR = "elixir";
  @NonNls public static final String COLD_FUSION = "coldfusion";
  @NonNls public static final String PREFERENCES_TEST_BUNDLE = "preferences_test";
  @NonNls public static final String MARKDOWN_BLOGGING = "markdown_blogging";
  @NonNls public static final String PYTHON = "python";
  @NonNls public static final String RUBY = "ruby";
  @NonNls public static final String PHP = "php";
  @NonNls public static final String SMARTY = "smarty";
  @NonNls public static final String TURTLE = "turtle";

  public static final PlistReader PLIST_READER = new CompositePlistReader();
  private static final BundleFactory BUNDLE_FACTORY = new BundleFactory(PLIST_READER);

  public static File getBundleDirectory(String bundleName) {
    File bundleDirectory = new File(PathManager.getCommunityHomePath() + "/plugins/textmate/testData/bundles", bundleName);
    if (bundleDirectory.exists()) {
      return bundleDirectory;
    }
    return new File(PathManager.getCommunityHomePath() + "/plugins/textmate/lib/bundles", bundleName);
  }

  public static Bundle getBundle(String bundleName) {
    return BUNDLE_FACTORY.fromDirectory(getBundleDirectory(bundleName));
  }
}
