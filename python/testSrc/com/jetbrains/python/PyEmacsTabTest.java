/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
      "        print n<caret>ame",
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
      "        if name == \"test\"\n" +
      "            print 123\n" +
      " print n<caret>ame",
      "def test(name):\n" +
      "    if name != \"\":\n" +
      "        if name == \"test\"\n" +
      "            print 123\n" +
      "    print n<caret>ame"
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
      "        print n<caret>ame"
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
    setLanguageLevel(LanguageLevel.PYTHON26);
    try {
      doTest(
        "def test(value):\n" +
        "    with 1 + 1 as a:\n" +
        "   print <caret>a",
        "def test(value):\n" +
        "    with 1 + 1 as a:\n" +
        "        print <caret>a"
      );
    }
    finally {
      setLanguageLevel(null);
    }
  }
  
  private void doTest(String before, String after) {
    final String fileName = getTestName(false);
    myFixture.configureByText(fileName + ".py", before);
    CommandProcessor.getInstance().executeCommand(myFixture.getProject(), new Runnable() {
        @Override
        public void run() {
          myFixture.testAction(new EmacsStyleIndentAction());
        }
      }, "", null);
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
