package org.jetbrains.plugins.textmate;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.textmate.bundles.Bundle;
import org.jetbrains.plugins.textmate.bundles.BundleFactory;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;
import org.jetbrains.plugins.textmate.plist.CompositePlistReader;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class TestUtil {
  @NonNls public static final String BAT = "bat";
  @NonNls public static final String JAVA = "java";
  @NonNls public static final String LOG = "log";
  @NonNls public static final String JAVASCRIPT = "javascript";
  @NonNls public static final String CHEF = "chef";
  @NonNls public static final String HTML = "html";
  @NonNls public static final String HTML_VSC = "html_vsc";
  @NonNls public static final String CSS_VSC = "css_vsc";
  @NonNls public static final String DOCKER = "docker";
  @NonNls public static final String MARKDOWN_SUBLIME = "markdown_sublime";
  @NonNls public static final String MARKDOWN_TEXTMATE = "markdown_textmate";
  @NonNls public static final String MARKDOWN_VSC = "markdown-basics";
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
  @NonNls public static final String PHP_VSC = "php_vsc";
  @NonNls public static final String SMARTY = "smarty";
  @NonNls public static final String TURTLE = "turtle";
  @NonNls public static final String GIT = "git";

  public static File getBundleDirectory(String bundleName) {
    File bundleDirectory = new File(getCommunityHomePath() + "/plugins/textmate/testData/bundles", bundleName);
    if (bundleDirectory.exists()) {
      return bundleDirectory;
    }
    return new File(getCommunityHomePath() + "/plugins/textmate/lib/bundles", bundleName);
  }

  public static Bundle getBundle(String bundleName) throws IOException {
    return new BundleFactory(new CompositePlistReader()).fromDirectory(getBundleDirectory(bundleName));
  }

  public static TextMateScope scopeFromString(String scopeString) {
    TextMateScope scope = TextMateScope.EMPTY;
    for (String scopeName : scopeString.split(" ")) {
      scope = scope.add(scopeName);
    }
    return scope;
  }

  private static String getCommunityHomePath() {
    URL url = TestUtil.class.getResource("/" + TestUtil.class.getName().replace('.', '/') + ".class");
    if (url != null && url.getProtocol().equals("file")) {
      try {
        File file = new File(url.toURI().getPath());
        while (file != null) {
          String[] children = file.list();
          if (children != null && ArrayUtil.contains(".idea", children)) {
            if (ArrayUtil.contains("community", children)) {
              return file.getPath() + "/community";
            }
            else {
              return file.getPath();
            }
          }
          file = file.getParentFile();
        }
      }
      catch (Exception e) {
        throw new Error(e);
      }
    }
    throw new IllegalStateException("Failed to find community home path");
  }
}
