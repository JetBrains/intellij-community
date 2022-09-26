// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.sh.actions.ShBaseGenerateActionsTest;
import com.intellij.sh.codeInsight.ShFunctionResolverInImportedFileTest;
import com.intellij.sh.codeInsight.ShFunctionResolverTest;
import com.intellij.sh.codeInsight.ShIncludeCommandResolverTest;
import com.intellij.sh.completion.ShCompletionTest;
import com.intellij.sh.completion.ShFileCompletionTest;
import com.intellij.sh.completion.ShKeywordCompletionTest;
import com.intellij.sh.editor.ShTypingTest;
import com.intellij.sh.formatter.ShLineIndentProviderTest;
import com.intellij.sh.lexer.ShFileLexerTest;
import com.intellij.sh.lexer.ShOldLexerVersion3Test;
import com.intellij.sh.lexer.ShOldLexerVersion4Test;
import com.intellij.sh.oldParser.ShOldParserTest;
import com.intellij.sh.parser.ShParserTest;
import com.intellij.sh.parser.ShShebangParserUtilTest;
import com.intellij.sh.rename.ShFunctionRenamingTest;
import com.intellij.sh.rename.ShRenameAllOccurrencesTest;
import com.intellij.sh.shellcheck.ShShellcheckInspectionTest;
import com.intellij.sh.shellcheck.ShShellcheckTestSetup;
import com.intellij.sh.template.ShArrayLiveTemplateTest;
import com.intellij.sh.template.ShLiveTemplateTest;
import junit.framework.Test;
import junit.framework.TestSuite;

public class ShTestSuite {
  public static Test suite() {
    TestSuite testSuite = new TestSuite("All Shell Script tests");
    testSuite.addTest(LexerSuite.suite());
    testSuite.addTest(ParserSuite.suite());
    testSuite.addTest(CompletionAndTemplateSuite.suite());
    testSuite.addTest(InspectionsSuite.suite());
    testSuite.addTest(OthersSuite.suite());
    return testSuite;
  }

  public static class LexerSuite {
    public static Test suite() {
      TestSuite suite = new TestSuite("Lexer");
      suite.addTestSuite(ShFileLexerTest.class);
      suite.addTestSuite(ShOldLexerVersion3Test.class);
      suite.addTestSuite(ShOldLexerVersion4Test.class);
      return suite;
    }
  }

  public static class ParserSuite {
    public static Test suite() {
      TestSuite suite = new TestSuite("Parser");
      suite.addTestSuite(ShParserTest.class);
      suite.addTestSuite(ShOldParserTest.class);
      suite.addTestSuite(ShShebangParserUtilTest.class);
      return suite;
    }
  }

  public static class CompletionAndTemplateSuite {
    public static Test suite() {
      TestSuite suite = new TestSuite("Completion & Template");
      suite.addTestSuite(ShCompletionTest.class);
      suite.addTestSuite(ShFileCompletionTest.class);
      suite.addTestSuite(ShKeywordCompletionTest.class);
      suite.addTestSuite(ShArrayLiveTemplateTest.class);
      suite.addTestSuite(ShLiveTemplateTest.class);
      suite.addTestSuite(ShBaseGenerateActionsTest.class);
      return suite;
    }
  }

  public static class InspectionsSuite {
    public static Test suite() {
      return new ShShellcheckTestSetup(new TestSuite(ShShellcheckInspectionTest.class, "Inspections"));
    }
  }

  public static class OthersSuite {
    public static Test suite() {
      TestSuite suite = new TestSuite("Others");
      suite.addTestSuite(ShRenameAllOccurrencesTest.class);
      suite.addTestSuite(ShFunctionRenamingTest.class);
      suite.addTestSuite(ShTypingTest.class);
      suite.addTestSuite(ShLineIndentProviderTest.class);
      suite.addTestSuite(ShFunctionResolverTest.class);
      suite.addTestSuite(ShIncludeCommandResolverTest.class);
      suite.addTestSuite(ShFunctionResolverInImportedFileTest.class);
      return suite;
    }
  }
}
