package org.jetbrains.plugins.textmate;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.textmate.bundles.BundleType;
import org.jetbrains.plugins.textmate.bundles.TextMateBundleReader;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jetbrains.plugins.textmate.bundles.BundleReaderKt.readSublimeBundle;
import static org.jetbrains.plugins.textmate.bundles.BundleReaderKt.readTextMateBundle;
import static org.jetbrains.plugins.textmate.bundles.VSCBundleReaderKt.readVSCBundle;

public final class TestUtil {
  @NonNls public static final String BAT = "bat";
  @NonNls public static final String GO = "go";
  @NonNls public static final String TERRAFORM = "terraform";
  @NonNls public static final String MAKE = "make";
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
  @NonNls public static final String RESTRUCTURED_TEXT = "restructuredtext";

  public static Path getBundleDirectory(String bundleName) {
    Path bundleDirectory = Path.of(getCommunityHomePath() + "/plugins/textmate/testData/bundles", bundleName);
    if (Files.exists(bundleDirectory)) {
      return bundleDirectory;
    }
    return Path.of(getCommunityHomePath() + "/plugins/textmate/lib/bundles", bundleName);
  }

  public static TextMateBundleReader readBundle(String bundleName) {
    Path bundleDirectory = getBundleDirectory(bundleName);
    BundleType bundleType = BundleType.detectBundleType(bundleDirectory);
    return switch (bundleType) {
      case TEXTMATE -> readTextMateBundle(bundleDirectory);
      case SUBLIME -> readSublimeBundle(bundleDirectory);
      case VSCODE -> readVSCBundle(relativePath -> {
        try {
          return Files.newInputStream(bundleDirectory.resolve(relativePath));
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
      case UNDEFINED -> throw new RuntimeException("Unknown bundle type: " + bundleName);
    };
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
