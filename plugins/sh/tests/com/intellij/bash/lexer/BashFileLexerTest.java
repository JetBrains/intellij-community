package com.intellij.bash.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.LexerTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class BashFileLexerTest extends LexerTestCase {
  @Override
  protected Lexer createLexer() {
    return new BashLexer();
  }

  @Override
  protected String getDirPath() {
    return "testData/lexer";
  }

  @NotNull
  @Override
  protected String getPathToTestDataFile(String extension) {
    return getDirPath() + "/" + getTestName(true) + extension;
  }

  public void testFirst()       { doFileTest("sh"); }
  public void testHello()       { doFileTest("sh"); }
  public void testCase()        { doFileTest("sh"); }
  public void testFor()         { doFileTest("sh"); }
  public void testIf()          { doFileTest("sh"); }
  public void testHeredoc()     { doFileTest("sh"); }
  public void testTrap()        { doFileTest("sh"); }
  public void testTrap2()       { doFileTest("sh"); }
  public void testLet()         { doFileTest("sh"); }
  public void testParams()      { doFileTest("sh"); }
  public void testBinaryData()  { doFileTest("bash"); }

  public void testPerf() throws IOException {
    String text = FileUtil.loadFile(new File("/Users/ignatov/src/BashSupport/testData/editor/highlighting/syntaxHighlighter/performance/functions_issue96.bash"));

    BashLexer lexer = new BashLexer();
    long start = System.currentTimeMillis();

    lexer.start(text, 0, text.length());
//    StringBuilder result = new StringBuilder();
//    ArrayList<IElementType> types = new ArrayList<>();
    IElementType tokenType;
       int i=0;
    while ((tokenType = lexer.getTokenType()) != null) {

      i++;
//      result.append(printSingleToken(text, tokenType, lexer.getTokenStart(), lexer.getTokenEnd()));
      lexer.advance();
    }

    System.out.println(System.currentTimeMillis() - start + " for " + i);

//    System.out.println(result.length());
//    return result.toString();
  }
}
