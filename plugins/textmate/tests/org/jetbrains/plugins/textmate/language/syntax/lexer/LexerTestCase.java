package org.jetbrains.plugins.textmate.language.syntax.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Interner;
import com.intellij.util.containers.PathInterner;
import org.jetbrains.plugins.textmate.TestUtil;
import org.jetbrains.plugins.textmate.bundles.TextMateFileNameMatcher;
import org.jetbrains.plugins.textmate.bundles.TextMateGrammar;
import org.jetbrains.plugins.textmate.editor.TextMateEditorUtilsKt;
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor;
import org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.intellij.openapi.util.io.FileUtilRt.getExtension;

@RunWith(com.intellij.testFramework.Parameterized.class)
abstract public class LexerTestCase extends UsefulTestCase {
  private static final String TEST_DATA_BASE_DIR =
    PlatformTestUtil.getCommunityPath() + "/plugins/textmate/testData/lexer";

  private final Interner<CharSequence> myInterner = new PathInterner.PathEnumerator();
  private final Map<TextMateFileNameMatcher, CharSequence> myLanguageDescriptors = new HashMap<>();
  private CharSequence myRootScope;
  private TextMateSyntaxTable mySyntaxTable;

  @Parameterized.Parameter
  public String myFileName;
  @Parameterized.Parameter(1)
  public File myFile;

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> params() {
    return Collections.emptyList();
  }

  @com.intellij.testFramework.Parameterized.Parameters(name = "{0}")
  public static Iterable<Object[]> createData(Class<?> klass) throws Throwable {
    LexerTestCase testCase = (LexerTestCase)klass.getDeclaredConstructor().newInstance();

    File testDir = new File(FileUtil.join(TEST_DATA_BASE_DIR, testCase.getTestDirRelativePath()));
    File[] files = testDir.listFiles();
    if (files == null) return Collections.emptyList();
    return ContainerUtil.mapNotNull(files, file -> {
      String fileName = PathUtil.getFileName(file.getPath());
      return !fileName.contains("_after.") && !fileName.endsWith("_after") ? new Object[]{fileName, file} : null;
    });
  }

  @Before
  public void before() {
    mySyntaxTable = new TextMateSyntaxTable();

    CharSequence scope = null;
    Iterator<TextMateGrammar> grammars = TestUtil.readBundle(getBundleName()).readGrammars().iterator();
    while (grammars.hasNext()) {
      TextMateGrammar grammar = grammars.next();
      CharSequence rootScope = mySyntaxTable.loadSyntax(grammar.getPlist(), myInterner);
      grammar.getFileNameMatchers().forEach(matcher -> {
        myLanguageDescriptors.put(matcher, rootScope);
      });
      if (scope == null) {
        scope = rootScope;
      }
    }

    assertNotNull(scope);
    final List<String> extraBundleNames = getExtraBundleNames();
    for (String bundleName : extraBundleNames) {
      Iterator<TextMateGrammar> extraGrammars = TestUtil.readBundle(bundleName).readGrammars().iterator();
      while (extraGrammars.hasNext()) {
        TextMateGrammar grammar = extraGrammars.next();
        mySyntaxTable.loadSyntax(grammar.getPlist(), myInterner);
      }
    }
    mySyntaxTable.compact();

    CharSequence scopeForFileName = myLanguageDescriptors.get(new TextMateFileNameMatcher.Name(myFileName));
    if (scopeForFileName != null) {
      myRootScope = scopeForFileName;
    }
    else {
      Iterator<CharSequence> extensionsIterator = TextMateEditorUtilsKt.fileNameExtensions(myFileName).iterator();
      while (extensionsIterator.hasNext()) {
        CharSequence scopeForExtension = myLanguageDescriptors.get(new TextMateFileNameMatcher.Extension(extensionsIterator.next().toString()));
        if (scopeForExtension != null) {
          myRootScope = scopeForExtension;
          break;
        }
      }
    }
    assertNotNull("scope is empty for file name: " + myFileName, myRootScope);
  }

  @Test
  public void lexerTest() throws IOException {
    String sourceData = StringUtil.convertLineSeparators(FileUtil.loadFile(myFile, StandardCharsets.UTF_8));

    StringBuilder output = new StringBuilder();
    String text = sourceData.replaceAll("$(\\n+)", "");
    Lexer lexer = new TextMateHighlightingLexer(new TextMateLanguageDescriptor(myRootScope, mySyntaxTable.getSyntax(myRootScope)), -1);
    lexer.start(text);
    while (lexer.getTokenType() != null) {
      final int s = lexer.getTokenStart();
      final int e = lexer.getTokenEnd();
      final IElementType tokenType = lexer.getTokenType();
      final String str = tokenType + ": [" + s + ", " + e + "], {" + text.substring(s, e) + "}\n";
      output.append(str);
      lexer.advance();
    }

    String extension = getExtension(myFileName);
    String suffix = extension.isEmpty() ? "" : "." + extension;
    String expectedFilePath = myFile.getParent() + "/" + FileUtilRt.getNameWithoutExtension(myFileName) + "_after" + suffix;
    assertSameLinesWithFile(expectedFilePath, output.toString().trim());
  }

  protected abstract String getTestDirRelativePath();

  protected abstract String getBundleName();

  protected List<String> getExtraBundleNames() {
    return Collections.emptyList();
  }
}