// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.codeInsight.editorActions.EmacsStyleIndentAction;
import com.intellij.openapi.command.CommandProcessor;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author Denis Zhdanov
 * @since 4/13/11 5:18 PM
 */
public class PyEmacsTabTest extends PyTestCase {
  
  public void testIndentToRightAfterCompositeStatementStart() {
    doTest(
      "def test(name):\n" +
      "    if name != \"\":\n" +
      " <caret> print name",
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        <caret>print name"
    );

    doTest(
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "<caret>  print name",
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        <caret>print name"
    );

    doTest(
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "     <caret>print name",
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        <caret>print name"
    );

    doTest(
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "    print n<caret>ame",
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        print n<caret>ame"
    );

    doTest(
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "         print n<caret>ame",
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        print n<caret>ame"
    );
  }

  public void testIndentToRight() {
    doTest(
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        if name == \"test\"\n" +
      "            print 123\n" +
      "print n<caret>ame",
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        if name == \"test\"\n" +
      "            print 123\n" +
      "    print n<caret>ame"
    );
    
    doTest(
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        if name == \"test\":\n" +
      "            print 123\n" +
      " print n<caret>ame",
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        if name == \"test\":\n" +
      "            print 123\n" +
      "        print n<caret>ame"
    );

    doTest(
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        if name == \"test\"\n" +
      "            print 123\n" +
      "    print n<caret>ame",
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        if name == \"test\"\n" +
      "            print 123\n" +
      "        print n<caret>ame"
    );

    doTest(
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        if name == \"test\"\n" +
      "            print 123\n" +
      "      print n<caret>ame",
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        if name == \"test\"\n" +
      "            print 123\n" +
      "            print n<caret>ame"
    );

    doTest(
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        if name == \"test\"\n" +
      "            print 123\n" +
      "        print n<caret>ame",
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        if name == \"test\"\n" +
      "            print 123\n" +
      "            print n<caret>ame"
    );

    doTest(
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        if name == \"test\"\n" +
      "            print 123\n" +
      "          print n<caret>ame",
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        if name == \"test\"\n" +
      "            print 123\n" +
      "            print n<caret>ame"
    );
  }

  public void testIndentToLeft() {
    doTest(
      "__author__ = \"doe\"\n" +
      " def test(name):\n" +
      "     if name == \"\"\n" +
      "         print 123\n" +
      "         p<caret>rint 123",
      "__author__ = \"doe\"\n" +
      " def test(name):\n" +
      "     if name == \"\"\n" +
      "         print 123\n" +
      " p<caret>rint 123"
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
      "def test(value):\n" +
      "    if value == 123:\n" +
      "   print <caret>value",
      "def test(value):\n" +
      "    if value == 123:\n" +
      "        print <caret>value"
    );
    doTest(
      "def test(value):\n" +
      "    if value == 123:\n" +
      "        pass\n" +
      "    elif value == 124:\n" +
      "   print <caret>value",
      "def test(value):\n" +
      "    if value == 123:\n" +
      "        pass\n" +
      "    elif value == 124:\n" +
      "        print <caret>value"
    );
    doTest(
      "def test(value):\n" +
      "    if value == 123:\n" +
      "        pass\n" +
      "    else:\n" +
      "   print <caret>value",
      "def test(value):\n" +
      "    if value == 123:\n" +
      "        pass\n" +
      "    else:\n" +
      "        print <caret>value"
    );
    
    // While.
    doTest(
      "def test(value):\n" +
      "    while value == 123:\n" +
      "   print <caret>value",
      "def test(value):\n" +
      "    while value == 123:\n" +
      "        print <caret>value"
    );
    doTest(
      "def test(value):\n" +
      "    while value == 123:\n" +
      "        pass\n" +
      "    else:\n" +
      "   print <caret>value",
      "def test(value):\n" +
      "    while value == 123:\n" +
      "        pass\n" +
      "    else:\n" +
      "        print <caret>value"
    );
    
    // For.
    doTest(
      "def test(value):\n" +
      "    for i in value:\n" +
      "   print <caret>value",
      "def test(value):\n" +
      "    for i in value:\n" +
      "        print <caret>value"
    );
    doTest(
      "def test(value):\n" +
      "    for i in value:\n" +
      "        pass\n" +
      "    else\n" +
      "   print <caret>value",
      "def test(value):\n" +
      "    for i in value:\n" +
      "        pass\n" +
      "    else\n" +
      "        print <caret>value"
    );
    
    // Try.
    doTest(
      "def test(value):\n" +
      "    try:\n" +
      "   open()<caret>\n" +
      "    except:\n" +
      "        pass",
      "def test(value):\n" +
      "    try:\n" +
      "        open()<caret>\n" +
      "    except:\n" +
      "        pass"
    );
    doTest(
      "def test(value):\n" +
      "    try:\n" +
      "        open()\n" +
      "    except:\n" +
      "   print <caret>123",
      "def test(value):\n" +
      "    try:\n" +
      "        open()\n" +
      "    except:\n" +
      "        print <caret>123"
    );
    doTest(
      "def test(value):\n" +
      "    try:\n" +
      "        open()\n" +
      "    finally:\n" +
      "   print <caret>123",
      "def test(value):\n" +
      "    try:\n" +
      "        open()\n" +
      "    finally:\n" +
      "        print <caret>123"
    );
    
    //With.
    runWithLanguageLevel(
      LanguageLevel.PYTHON26,
      () -> doTest(
        "def test(value):\n" +
        "    with 1 + 1 as a:\n" +
        "   print <caret>a",
        "def test(value):\n" +
        "    with 1 + 1 as a:\n" +
        "        print <caret>a"
      )
    );
  }

  public void testSequence() {
    doTest("l = [\n" +
           "<caret>'alpha',\n" +
           "'beta'\n" +
           "]",
           "l = [\n" +
           "    'alpha',\n" +
           "'beta'\n" +
           "]");
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
