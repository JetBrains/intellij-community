// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.editorActions.EmacsStyleIndentAction;
import com.intellij.openapi.command.CommandProcessor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author Denis Zhdanov
 */
public class PyEmacsTabTest extends PyTestCase {
  
  public void testIndentToRightAfterCompositeStatementStart() {
    doTest(
      """
        def test(name):
            if name != "":
         <caret> print name""",
      """
        def test(name):
            if name != "":
                <caret>print name"""
    );

    doTest(
      """
        def test(name):
            if name != "":
        <caret>  print name""",
      """
        def test(name):
            if name != "":
                <caret>print name"""
    );

    doTest(
      """
        def test(name):
            if name != "":
             <caret>print name""",
      """
        def test(name):
            if name != "":
                <caret>print name"""
    );

    doTest(
      """
        def test(name):
            if name != "":
            print n<caret>ame""",
      """
        def test(name):
            if name != "":
                print n<caret>ame"""
    );

    doTest(
      """
        def test(name):
            if name != "":
                 print n<caret>ame""",
      """
        def test(name):
            if name != "":
                print n<caret>ame"""
    );
  }

  public void testIndentToRight() {
    doTest(
      """
        def test(name):
            if name != "":
                if name == "test"
                    print 123
        print n<caret>ame""",
      """
        def test(name):
            if name != "":
                if name == "test"
                    print 123
            print n<caret>ame"""
    );
    
    doTest(
      """
        def test(name):
            if name != "":
                if name == "test":
                    print 123
         print n<caret>ame""",
      """
        def test(name):
            if name != "":
                if name == "test":
                    print 123
                print n<caret>ame"""
    );

    doTest(
      """
        def test(name):
            if name != "":
                if name == "test"
                    print 123
            print n<caret>ame""",
      """
        def test(name):
            if name != "":
                if name == "test"
                    print 123
                print n<caret>ame"""
    );

    doTest(
      """
        def test(name):
            if name != "":
                if name == "test"
                    print 123
              print n<caret>ame""",
      """
        def test(name):
            if name != "":
                if name == "test"
                    print 123
                    print n<caret>ame"""
    );

    doTest(
      """
        def test(name):
            if name != "":
                if name == "test"
                    print 123
                print n<caret>ame""",
      """
        def test(name):
            if name != "":
                if name == "test"
                    print 123
                    print n<caret>ame"""
    );

    doTest(
      """
        def test(name):
            if name != "":
                if name == "test"
                    print 123
                  print n<caret>ame""",
      """
        def test(name):
            if name != "":
                if name == "test"
                    print 123
                    print n<caret>ame"""
    );
  }

  public void testIndentToLeft() {
    doTest(
      """
        __author__ = "doe"
         def test(name):
             if name == ""
                 print 123
                 p<caret>rint 123""",
      """
        __author__ = "doe"
         def test(name):
             if name == ""
                 print 123
         p<caret>rint 123"""
    );
    
    doTest(
      "  print 123\n" +
      "print <caret>123",
      "  print 123\n" +
      "  print <caret>123"
    );
  }
  
  public void testAllCompoundStatements() {
    // Class.
    doTest(
      "class Foo:\n" +
      " print <caret>123",
      "class Foo:\n" +
      "    print <caret>123"
    );
    
    // Function.
    doTest(
      "def test(value):\n" +
      "  print <caret>value",
      "def test(value):\n" +
      "    print <caret>value"
    );
    
    // If.
    doTest(
      """
        def test(value):
            if value == 123:
           print <caret>value""",
      """
        def test(value):
            if value == 123:
                print <caret>value"""
    );
    doTest(
      """
        def test(value):
            if value == 123:
                pass
            elif value == 124:
           print <caret>value""",
      """
        def test(value):
            if value == 123:
                pass
            elif value == 124:
                print <caret>value"""
    );
    doTest(
      """
        def test(value):
            if value == 123:
                pass
            else:
           print <caret>value""",
      """
        def test(value):
            if value == 123:
                pass
            else:
                print <caret>value"""
    );
    
    // While.
    doTest(
      """
        def test(value):
            while value == 123:
           print <caret>value""",
      """
        def test(value):
            while value == 123:
                print <caret>value"""
    );
    doTest(
      """
        def test(value):
            while value == 123:
                pass
            else:
           print <caret>value""",
      """
        def test(value):
            while value == 123:
                pass
            else:
                print <caret>value"""
    );
    
    // For.
    doTest(
      """
        def test(value):
            for i in value:
           print <caret>value""",
      """
        def test(value):
            for i in value:
                print <caret>value"""
    );
    doTest(
      """
        def test(value):
            for i in value:
                pass
            else
           print <caret>value""",
      """
        def test(value):
            for i in value:
                pass
            else
                print <caret>value"""
    );
    
    // Try.
    doTest(
      """
        def test(value):
            try:
           open()<caret>
            except:
                pass""",
      """
        def test(value):
            try:
                open()<caret>
            except:
                pass"""
    );
    doTest(
      """
        def test(value):
            try:
                open()
            except:
           print <caret>123""",
      """
        def test(value):
            try:
                open()
            except:
                print <caret>123"""
    );
    doTest(
      """
        def test(value):
            try:
                open()
            finally:
           print <caret>123""",
      """
        def test(value):
            try:
                open()
            finally:
                print <caret>123"""
    );
    
    //With.
    runWithLanguageLevel(
      LanguageLevel.PYTHON26,
      () -> doTest(
        """
          def test(value):
              with 1 + 1 as a:
             print <caret>a""",
        """
          def test(value):
              with 1 + 1 as a:
                  print <caret>a"""
      )
    );
  }

  public void testSequence() {
    doTest("""
             l = [
             <caret>'alpha',
             'beta'
             ]""",
           """
             l = [
                 'alpha',
             'beta'
             ]""");
  }
  private void doTest(String before, String after) {
    final String fileName = getTestName(false);
    myFixture.configureByText(fileName + ".py", before);
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), () -> myFixture.testAction(new EmacsStyleIndentAction()), "", null);
    myFixture.checkResult(after);
  }
  
  //private static String changeIndentToTabs(String s) {
  //  StringBuilder result = new StringBuilder();
  //  String caretMark = "<caret>";
  //  for (String line : s.split("\n")) {
  //    int start = 0;
  //    int end = start;
  //    for (; end < line.length(); end++) {
  //      char c = line.charAt(end);
  //      if (c == '<' && end + caretMark.length() < line.length() && caretMark.equals(line.substring(end, end + caretMark.length()))) {
  //        result.append(StringUtil.repeatSymbol('\t', end - start));
  //        result.append(caretMark);
  //        start = end + caretMark.length();
  //        end = start - 1;
  //        continue;
  //      }
  //      if (c != ' ') {
  //        result.append(result.append(StringUtil.repeatSymbol('\t', end - start))).append(line.substring(end)).append("\n");
  //        break;
  //      }
  //    }
  //  }
  //  return result.toString();
  //}
}
