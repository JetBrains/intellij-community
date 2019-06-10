package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.textmate.TestUtil;
import org.jetbrains.plugins.textmate.TextMateServiceImpl;
import org.jetbrains.plugins.textmate.bundles.Bundle;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTable;
import org.jetbrains.plugins.textmate.plist.Plist;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.io.FileUtilRt.getExtension;

@RunWith(com.intellij.testFramework.Parameterized.class)
abstract public class LexerTestCase extends UsefulTestCase {
  private static final String TEST_DATA_BASE_DIR =
    PathManager.getHomePath() + "/plugins/textmate/tests/org/jetbrains/plugins/textmate/language/syntax/lexer/data";

  private final Map<String, String> myLanguageDescriptors = new HashMap<>();
  private String myRootScope;
  private TextMateSyntaxTable mySyntaxTable;

  @Parameterized.Parameter()
  public String myFileName;
  @Parameterized.Parameter(1)
  public File myFile;

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> params() {
    return Collections.emptyList();
  }

  @com.intellij.testFramework.Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> createData(Class<?> klass) throws Throwable {
    LexerTestCase testCase = (LexerTestCase)klass.newInstance();

    File testDir = new File(FileUtil.join(TEST_DATA_BASE_DIR, testCase.getTestDirRelativePath()));
    File[] files = testDir.listFiles();
    if (files == null) return Collections.emptyList();
    return ContainerUtil.mapNotNull(files, file -> {
      String fileName = PathUtil.getFileName(file.getPath());
      return !fileName.contains("_after.") ? new Object[]{fileName, file} : null;
    });
  }

  @Before
  public void before() throws Exception {
    mySyntaxTable = new TextMateSyntaxTable();

    String scope = null;
    Bundle bundle = TestUtil.getBundle(getBundleName());
    for (File grammarFile : bundle.getGrammarFiles()) {
      Plist plist = TestUtil.PLIST_READER.read(grammarFile);
      final String rootScope = mySyntaxTable.loadSyntax(plist);
      final List<String> extensions = bundle.getExtensions(grammarFile, plist);
      for (final String extension : extensions) {
        myLanguageDescriptors.put(extension, rootScope);
      }
      if (scope == null) {
        scope = rootScope;
      }
    }

    assertNotNull(scope);
    final List<String> extraBundleNames = getExtraBundleNames();
    for (String bundleName : extraBundleNames) {
      for (File grammarFile : TestUtil.getBundle(bundleName).getGrammarFiles()) {
        mySyntaxTable.loadSyntax(TestUtil.PLIST_READER.read(grammarFile));
      }
    }

    for (String extension : TextMateServiceImpl.getExtensions(myFileName)) {
      myRootScope = myLanguageDescriptors.get(extension);
      if (myRootScope != null) {
        break;
      }
    }
    assertNotNull("scope is empty for file name: " + myFileName, myRootScope);
  }

  @Test
  public void lexerTest() throws IOException {
    String sourceData = StringUtil.convertLineSeparators(FileUtil.loadFile(myFile, StandardCharsets.UTF_8));

    StringBuilder output = new StringBuilder();
    String text = sourceData.replaceAll("$(\\n+)", "");
    TextMateLanguageDescriptor languageDescriptor = new TextMateLanguageDescriptor(myRootScope, mySyntaxTable.getSyntax(myRootScope));
    Lexer lexer = new TextMateHighlightingLexer(languageDescriptor.getScopeName(), languageDescriptor.getRootSyntaxNode());
    lexer.start(text);
    while (lexer.getTokenType() != null) {
      final int s = lexer.getTokenStart();
      final int e = lexer.getTokenEnd();
      final IElementType tokenType = lexer.getTokenType();
      final String str = tokenType + ": [" + s + ", " + e + "], {" + text.substring(s, e) + "}\n";
      output.append(str);
      lexer.advance();
    }

    String expectedFilePath = myFile.getParent() + "/" +
                              FileUtilRt.getNameWithoutExtension(myFileName) + "_after." + getExtension(myFileName);
    assertSameLinesWithFile(expectedFilePath, output.toString().trim());
  }

  protected abstract String getTestDirRelativePath();

  protected abstract String getBundleName();

  protected List<String> getExtraBundleNames() {
    return Collections.emptyList();
  }
}