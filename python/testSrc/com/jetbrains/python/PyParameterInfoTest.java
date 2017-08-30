/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContextEx;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Tests parameter info available via ^P at call sites.
 * <br/>User: dcheryasov
 * Date: Jul 14, 2009 3:42:44 AM
 */
public class PyParameterInfoTest extends LightMarkedTestCase {

  @Override
  protected Map<String, PsiElement> loadTest() {
    String fname = "/paramInfo/" + getTestName(false) + ".py";
    return configureByFile(fname);
  }

  private Map<String, PsiElement> loadTest(int expected_marks) {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), expected_marks);
    return marks;
  }

  @NotNull
  private Map<String, PsiElement> loadMultiFileTest(int expectedMarks) {
    final String relativeDirectory = "/paramInfo/" + getTestName(false);
    final String relativeMainFile = relativeDirectory + "/a.py";

    final Map<String, PsiElement> marks = configureByFile(relativeMainFile);
    assertEquals("Test data sanity", marks.size(), expectedMarks);

    final String absoluteDirectory = getTestDataPath() + relativeDirectory;
    final String absoluteMainFile =  getTestDataPath() + relativeMainFile;

    Arrays
      .stream(new File(absoluteDirectory).listFiles())
      .map(File::getPath)
      .filter(path -> !path.equals(absoluteMainFile))
      .forEach(path -> myFixture.copyFileToProject(path, new File(path).getName()));

    return marks;
  }

  public void testSimpleFunction() {
    Map<String, PsiElement> marks = loadTest(3);

    PsiElement arg1 = marks.get("<arg1>");
    feignCtrlP(arg1.getTextOffset()).check("a, b, c", new String[]{"a, "});
    feignCtrlP(arg1.getTextOffset()+1).check("a, b, c", new String[]{"a, "});
    feignCtrlP(arg1.getTextOffset()-3).assertNotFound(); // ^P before arglist gives nothing

    PsiElement arg2 = marks.get("<arg2>");
    feignCtrlP(arg2.getTextOffset()).check("a, b, c", new String[]{"b, "});
    feignCtrlP(arg2.getTextOffset()+1).check("a, b, c", new String[]{"b, "});
    feignCtrlP(arg2.getTextOffset()+2).check("a, b, c", new String[]{"c"}); // one too far after arg2, and we came to arg3

    PsiElement arg3 = marks.get("<arg3>");
    feignCtrlP(arg3.getTextOffset()).check("a, b, c", new String[]{"c"});
    feignCtrlP(arg3.getTextOffset()+1).check("a, b, c", new String[]{"c"});
    feignCtrlP(arg3.getTextOffset()-1).check("a, b, c", new String[]{"c"}); // space before arg goes to that arg
    feignCtrlP(arg3.getTextOffset()+2).check("a, b, c", new String[]{}); // ^P on a ")" gives nothing
  }

  public void testStarredFunction() {
    Map<String, PsiElement> marks = loadTest(4);

    PsiElement arg1 = marks.get("<arg1>");
    feignCtrlP(arg1.getTextOffset()).check("a, b, *c", new String[]{"a, "});
    feignCtrlP(arg1.getTextOffset()+1).check("a, b, *c", new String[]{"a, "});
    
    PsiElement arg2 = marks.get("<arg2>");
    feignCtrlP(arg2.getTextOffset()).check("a, b, *c", new String[]{"b, "});
    feignCtrlP(arg2.getTextOffset()+1).check("a, b, *c", new String[]{"b, "});
    
    PsiElement arg3 = marks.get("<arg3>");
    feignCtrlP(arg3.getTextOffset()).check("a, b, *c", new String[]{"*c"});
    feignCtrlP(arg3.getTextOffset()+1).check("a, b, *c", new String[]{"*c"});

    PsiElement arg4 = marks.get("<arg4>");
    feignCtrlP(arg4.getTextOffset()).check("a, b, *c", new String[]{"*c"});
    feignCtrlP(arg4.getTextOffset()+1).check("a, b, *c", new String[]{"*c"});
    feignCtrlP(arg4.getTextOffset()+2).check("a, b, *c", new String[]{"*c"}); // sticks to *arg
  }

  public void testKwdFunction() {
    Map<String, PsiElement> marks = loadTest(5);

    PsiElement arg1 = marks.get("<arg1>");
    feignCtrlP(arg1.getTextOffset()).check("a, b, **c", new String[]{"a, "});
    feignCtrlP(arg1.getTextOffset()+1).check("a, b, **c", new String[]{"a, "});

    PsiElement arg2 = marks.get("<arg2>");
    feignCtrlP(arg2.getTextOffset()).check("a, b, **c", new String[]{"b, "});
    feignCtrlP(arg2.getTextOffset()+1).check("a, b, **c", new String[]{"b, "});


    PsiElement arg3 = marks.get("<arg3>");
    feignCtrlP(arg3.getTextOffset()).check("a, b, **c", new String[]{"**c"});
    feignCtrlP(arg3.getTextOffset()+1).check("a, b, **c", new String[]{"**c"});

    PsiElement arg4 = marks.get("<arg4>");
    feignCtrlP(arg4.getTextOffset()).check("a, b, **c", new String[]{"**c"});
    feignCtrlP(arg4.getTextOffset()+1).check("a, b, **c", new String[]{"**c"});

    PsiElement arg5 = marks.get("<arg5>");
    feignCtrlP(arg5.getTextOffset()).check("a, b, **c", new String[]{"**c"});
  }

  public void testKwdOutOfOrder() {
    Map<String, PsiElement> marks = loadTest(4);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b, **c", new String[]{"**c"});

    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a, b, **c", new String[]{"b, "});

    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a, b, **c", new String[]{"a, "});

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b, **c", new String[]{"**c"});
  }

  public void testStarArg() {
    Map<String, PsiElement> marks = loadTest(3);

    //feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b, c", new String[]{"a, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a, b, c", new String[]{"b, ","c"});
    //feignCtrlP(marks.get("<arg2a>").getTextOffset()).check("a, b, c", new String[]{"b, ","c"});
  }

  public void testKwdArg() {
    Map<String, PsiElement> marks = loadTest(3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b, c", new String[]{"a, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a, b, c", new String[]{"b, ","c"});
    feignCtrlP(marks.get("<arg2a>").getTextOffset()).check("a, b, c", new String[]{"b, ","c"});
  }

  public void testKwdArgInClass() {
    Map<String, PsiElement> marks = loadTest(1);
    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self: A, **kw", new String[]{"**kw"}, new String[]{"self: A, "});
  }

  public void testKwdArgOutOfOrder() {
    Map<String, PsiElement> marks = loadTest(3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b, c", new String[]{"b, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a, b, c", new String[]{"a, ","c"});
    feignCtrlP(marks.get("<arg2a>").getTextOffset()).check("a, b, c", new String[]{"a, ","c"});
  }

  public void testStarredAndKwdFunction() {
    Map<String, PsiElement> marks = loadTest(6);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b, *c, **d", new String[]{"a, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a, b, *c, **d", new String[]{"b, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a, b, *c, **d", new String[]{"*c, "});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("a, b, *c, **d", new String[]{"*c, "});
    feignCtrlP(marks.get("<arg5>").getTextOffset()).check("a, b, *c, **d", new String[]{"**d"});
    feignCtrlP(marks.get("<arg6>").getTextOffset()).check("a, b, *c, **d", new String[]{"**d"});
  }

  public void testNestedArg() {
    Map<String, PsiElement> marks = loadTest(4);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, (b, c), d", new String[]{"a, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a, (b, c), d", new String[]{"b, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a, (b, c), d", new String[]{"c"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("a, (b, c), d", new String[]{"d"});

    feignCtrlP(marks.get("<arg2>").getTextOffset()-2).check("a, (b, c), d", new String[]{}); // before nested tuple: no arg matches
  }

  public void testDoubleNestedArg() {
    Map<String, PsiElement> marks = loadTest(5);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, (b, (c, d)), e", new String[]{"a, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a, (b, (c, d)), e", new String[]{"b, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a, (b, (c, d)), e", new String[]{"c, "});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("a, (b, (c, d)), e", new String[]{"d"});
    feignCtrlP(marks.get("<arg5>").getTextOffset()).check("a, (b, (c, d)), e", new String[]{"e"});
  }

  public void testNestedMultiArg() {
    Map<String, PsiElement> marks = loadTest(3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, (b, c), d", new String[]{"a, "});
    feignCtrlP(marks.get("<arg23>").getTextOffset()).check("a, (b, c), d", new String[]{"b, ","c"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("a, (b, c), d", new String[]{"d"});
  }

  public void testStarredParam() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b, c", new String[]{"a, "});
    feignCtrlP(marks.get("<arg23>").getTextOffset()).check("a, b, c", new String[]{"b, ","c"});
  }

  public void testStarredParamAndArg() {
    Map<String, PsiElement> marks = loadTest(4);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b, *c", new String[]{"a, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a, b, *c", new String[]{"b, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a, b, *c", new String[]{"*c"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("a, b, *c", new String[]{"*c"});
  }


  public void testSimpleMethod() {
    Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self: A, a", new String[]{"a"}, new String[]{"self: A, "});
  }

  public void testSimpleClassFunction() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self: A, a", new String[]{"self: A, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self: A, a", new String[]{"a"});
  }

  public void testReassignedFunction() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b", new String[]{"a, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a, b", new String[]{"b"});
  }

  public void testReassignedInstanceMethod() {
    Map<String, PsiElement> marks = loadTest(3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self: Foo, a, b, c", new String[]{"a, "}, new String[]{"self: Foo, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self: Foo, a, b, c", new String[]{"b, "}, new String[]{"self: Foo, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("self: Foo, a, b, c", new String[]{"c"}, new String[]{"self: Foo, "});
  }

  public void testReassignedClassInit() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self: Bar, a, b", new String[]{"a, "}, new String[]{"self: Bar, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self: Bar, a, b", new String[]{"b"}, new String[]{"self: Bar, "});
  }

  public void testInheritedClassInit() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self: Bar, a, b", new String[]{"a, "}, new String[]{"self: Bar, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self: Bar, a, b", new String[]{"b"}, new String[]{"self: Bar, "});
  }

  public void testRedefinedNewConstructorCall() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("cls: A, a, b", new String[]{"a, "}, new String[]{"cls: A, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("cls: A, a, b", new String[]{"b"}, new String[]{"cls: A, "});
  }

  public void testRedefinedNewDirectCall() {
    Map<String, PsiElement> marks = loadTest(3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("cls: A, a, b", new String[]{"cls: A, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("cls: A, a, b", new String[]{"a, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("cls: A, a, b", new String[]{"b"});
  }

  public void testIgnoreNewInOldStyleClass() {
    Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self: A, one", new String[]{"one"}, new String[]{"self: A, "});
  }


  public void testBoundMethodSimple() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self: A, a, b", new String[]{"a, "}, new String[]{"self: A, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self: A, a, b", new String[]{"b"}, new String[]{"self: A, "});
  }

  public void testBoundMethodReassigned() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self: A, a, b", new String[]{"a, "}, new String[]{"self: A, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self: A, a, b", new String[]{"b"}, new String[]{"self: A, "});
  }

  public void testConstructorFactory() {
    Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg>").getTextOffset()).check("self: Foo, color", new String[]{"color"}, new String[]{"self: Foo, "});
  }


  public void testBoundMethodStatic() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b", new String[]{"a, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a, b", new String[]{"b"});
  }

  public void testSimpleLambda() {
    Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("x", new String[]{"x"});
  }

  public void testReassignedLambda() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("x, y", new String[]{"x, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("x, y", new String[]{"y"});
  }

  public void testLambdaVariousArgs() {
    Map<String, PsiElement> marks = loadTest(4);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("x, y=1, *args, **kwargs", new String[]{"x, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("x, y=1, *args, **kwargs", new String[]{"y=1, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("x, y=1, *args, **kwargs", new String[]{"*args, "});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("x, y=1, *args, **kwargs", new String[]{"**kwargs"});
  }

  public void testTupleAndNamedArg1() {
    // PY-1268
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg_c>").getTextOffset()).check("a, b, c", new String[]{"c"});
    feignCtrlP(marks.get("<arg_star>").getTextOffset()).check("a, b, c", new String[]{"a, ", "b, "});
  }

  public void testTupleParam() {
    // PY-3817
    Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg>").getTextOffset()).check("a, b", new String[]{"a, "});
  }

  public void testTupleAndNamedArg2() {
    // PY-1268
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg_star>").getTextOffset()).check("a, b, c", new String[]{"a, ", "b, "});
    feignCtrlP(marks.get("<arg_c>").getTextOffset()).check("a, b, c", new String[]{"c"});
  }

  public void testTupleArgPlainParam() {
    Map<String, PsiElement> marks = loadTest(1);
    feignCtrlP(marks.get("<arg>").getTextOffset()).check("a, b, c", new String[]{"b, "});
  }


  public void testStaticmethod() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b", new String[]{"a, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a, b", new String[]{"b"});
  }

  public void testPartialSimple() {
    Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b, c", new String[]{"c"});
  }

  public void testPartialWithList() { // PY-3383
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b, c, **kwargs", new String[]{"b, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a, b, c, **kwargs", new String[]{"c, "});
  }

  public void testPartialNamed() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b, c: int=1, d: int=2, e: int=3", new String[]{"d: int=2, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a, b, c: int=1, d: int=2, e: int=3", new String[]{"e: int=3"}); // no logical next
  }

  public void testPy3kPastTupleArg() {
    Map<String, PsiElement> marks = loadTest(4);
    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("*arg, a: int=1, b: int=2", new String[]{"*arg, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("*arg, a: int=1, b: int=2", new String[]{"*arg, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("*arg, a: int=1, b: int=2", new String[]{"b: int=2"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("*arg, a: int=1, b: int=2", new String[]{"a: int=1, "});
  }

  public void testNoArgs() {
    Map<String, PsiElement> marks = loadTest(1);
    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b, c", new String[]{"a, "});
  }

  public void testNoArgsException() {
    Map<String, PsiElement> marks = loadTest(1);
    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("<no parameters>", ArrayUtil.EMPTY_STRING_ARRAY, new String[]{"<no parameters>"});
  }

  public void testMultilineStringDefault() {
    final int offset = loadTest(1).get("<arg2>").getTextOffset();
    feignCtrlP(offset).check("length: int=12, allowed_chars: str='abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'",
                             new String[]{"allowed_chars: str='abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'"},
                             ArrayUtil.EMPTY_STRING_ARRAY);
  }

  // PY-22005
  public void testWithSpecifiedType() {
    myFixture.copyDirectoryToProject("typing", "");

    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final int offset = loadTest(1).get("<arg1>").getTextOffset();
        final String expectedInfo = "a1: str, a2: Optional[str]=None, a3: Union[str, int, None]=None, a4: int, *args: int, **kwargs: int";

        feignCtrlP(offset).check(expectedInfo, new String[]{"a1: str, "});
      }
    );
  }

  // PY-23055
  public void testWithoutTypeButWithNoneDefaultValue() {
    final int offset = loadTest(1).get("<arg1>").getTextOffset();
    final String expectedInfo = "b=None";

    feignCtrlP(offset).check(expectedInfo, new String[]{"b=None"});
  }

  // PY-22004
  public void testMultiResolved() {
    myFixture.copyDirectoryToProject("typing", "");

    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final int offset = loadTest(1).get("<arg2>").getTextOffset();

        final List<String> texts = Arrays.asList("self: C1, x", "self: C2, x, y: str");
        final List<String[]> highlighted = Arrays.asList(ArrayUtil.EMPTY_STRING_ARRAY, new String[]{"y: str"});
        final List<String[]> disabled = Arrays.asList(new String[]{"self: C1, "}, new String[]{"self: C2, "});

        feignCtrlP(offset).check(texts, highlighted, disabled);
      }
    );
  }

  public void testOverloadsInImportedClass() {
    myFixture.copyDirectoryToProject("typing", "");

    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final int offset = loadMultiFileTest(1).get("<arg1>").getTextOffset();

        final List<String> texts = Arrays.asList("self: C, a: str, b: str", "self: C, a: int, b: int");
        final List<String[]> highlighted = Arrays.asList(new String[]{"a: str, "}, new String[]{"a: int, "});
        final List<String[]> disabled = Arrays.asList(new String[]{"self: C, "}, new String[]{"self: C, "});

        feignCtrlP(offset).check(texts, highlighted, disabled);
      }
    );
  }

  public void testOverloadsInImportedModule() {
    myFixture.copyDirectoryToProject("typing", "");

    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final int offset = loadMultiFileTest(1).get("<arg1>").getTextOffset();

        final List<String> texts = Arrays.asList("a: int, b: int", "a: str, b: str");
        final List<String[]> highlighted = Arrays.asList(new String[]{"a: int, "}, new String[]{"a: str, "});

        feignCtrlP(offset).check(texts, highlighted, Arrays.asList(ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY));
      }
    );
  }

  public void testOverloadsWithDifferentNumberOfArgumentsInImportedClass() {
    myFixture.copyDirectoryToProject("typing", "");

    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final int offset = loadMultiFileTest(1).get("<arg1>").getTextOffset();

        final List<String> texts = Arrays.asList("self: C, a: str, b: str", "self: C, a: int");
        final List<String[]> highlighted = Arrays.asList(new String[]{"a: str, "}, new String[]{"a: int"});
        final List<String[]> disabled = Arrays.asList(new String[]{"self: C, "}, new String[]{"self: C, "});

        feignCtrlP(offset).check(texts, highlighted, disabled);
      }
    );
  }

  public void testOverloadsWithDifferentNumberOfArgumentsInImportedModule() {
    myFixture.copyDirectoryToProject("typing", "");

    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final int offset = loadMultiFileTest(1).get("<arg1>").getTextOffset();

        final List<String> texts = Arrays.asList("a: int", "a: str, b: str");
        final List<String[]> highlighted = Arrays.asList(new String[]{"a: int"}, new String[]{"a: str, "});

        feignCtrlP(offset).check(texts, highlighted, Arrays.asList(ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY));
      }
    );
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementation() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final int offset = loadTest(1).get("<arg1>").getTextOffset();

        final List<String> texts = Arrays.asList("value: None", "value: int", "value: str");
        final List<String[]> highlighted = Arrays.asList(new String[]{"value: None"}, new String[]{"value: int"}, new String[]{"value: str"});

        feignCtrlP(offset).check(texts, highlighted, Arrays.asList(ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY));
      }
    );
  }

  // PY-22971
  public void testOverloadsAndImplementationInClass() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final int offset = loadTest(1).get("<arg1>").getTextOffset();

        final List<String> texts = Arrays.asList("self: A, value: None", "self: A, value: int", "self: A, value: str");
        final List<String[]> highlighted = Arrays.asList(new String[]{"value: None"}, new String[]{"value: int"}, new String[]{"value: str"});
        final List<String[]> disabled = Arrays.asList(new String[]{"self: A, "}, new String[]{"self: A, "}, new String[]{"self: A, "});

        feignCtrlP(offset).check(texts, highlighted, disabled);
      }
    );
  }

  // PY-22971
  public void testOverloadsAndImplementationInImportedModule() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final int offset = loadMultiFileTest(1).get("<arg1>").getTextOffset();

        final List<String> texts = Arrays.asList("value: str", "value: int", "value: None");
        final List<String[]> highlighted = Arrays.asList(new String[]{"value: str"}, new String[]{"value: int"}, new String[]{"value: None"});

        feignCtrlP(offset).check(texts, highlighted, Arrays.asList(ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY));
      }
    );
  }

  // PY-22971
  public void testOverloadsAndImplementationInImportedClass() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final int offset = loadMultiFileTest(1).get("<arg1>").getTextOffset();

        final List<String> texts = Arrays.asList("self: A, value: None", "self: A, value: int", "self: A, value: str");
        final List<String[]> highlighted = Arrays.asList(new String[]{"value: None"}, new String[]{"value: int"}, new String[]{"value: str"});
        final List<String[]> disabled = Arrays.asList(new String[]{"self: A, "}, new String[]{"self: A, "}, new String[]{"self: A, "});

        feignCtrlP(offset).check(texts, highlighted, disabled);
      }
    );
  }

  // PY-23625
  public void testEscapingInDefaultValue() {
    final int offset = loadTest(1).get("<arg1>").getTextOffset();

    feignCtrlP(offset).check("p: str=\"\\n\", t: str=\"\\t\", r: str=\"\\r\"", new String[]{"p: str=\"\\n\", "});
  }

  public void testJustTypingCallable() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final int offset = loadTest(1).get("<arg1>").getTextOffset();

        feignCtrlP(offset).check(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
      }
    );
  }

  public void testTypingCallableWithUnknownParameters() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final int offset = loadTest(1).get("<arg1>").getTextOffset();

        feignCtrlP(offset).check(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
      }
    );
  }

  public void testTypingCallableWithKnownParameters() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {

        final int offset = loadTest(1).get("<arg1>").getTextOffset();

        final List<String> texts = Collections.singletonList("...: int, ...: str");
        final List<String[]> highlighted = Collections.singletonList(new String[]{"...: int, "});

        feignCtrlP(offset).check(texts, highlighted, Collections.singletonList(ArrayUtil.EMPTY_STRING_ARRAY));
      }
    );
  }

  // PY-22249
  public void testInitializingCollectionsNamedTuple() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final Map<String, PsiElement> test = loadTest(2);

        for (int offset : StreamEx.of(test.values()).map(PsiElement::getTextOffset)) {
          final List<String> texts = Collections.singletonList("bar, baz");
          final List<String[]> highlighted = Collections.singletonList(new String[]{"bar, "});

          feignCtrlP(offset).check(texts, highlighted, Collections.singletonList(ArrayUtil.EMPTY_STRING_ARRAY));
        }
      }
    );
  }

  public void testInitializingTypingNamedTuple() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        final Map<String, PsiElement> test = loadTest(7);

        for (int offset : StreamEx.of(1, 2, 3, 4).map(number -> test.get("<arg" + number + ">").getTextOffset())) {
          final List<String> texts = Collections.singletonList("bar: int, baz: str");
          final List<String[]> highlighted = Collections.singletonList(new String[]{"bar: int, "});

          feignCtrlP(offset).check(texts, highlighted, Collections.singletonList(ArrayUtil.EMPTY_STRING_ARRAY));
        }

        final List<String> texts1 = Collections.singletonList("bar: int, baz: str, foo: int");
        final List<String[]> highlighted1 = Collections.singletonList(new String[]{"bar: int, "});
        feignCtrlP(test.get("<arg5>").getTextOffset()).check(texts1, highlighted1, Collections.singletonList(ArrayUtil.EMPTY_STRING_ARRAY));

        final List<String> texts2 = Collections.singletonList("names: List[str], ages: List[int]");
        final List<String[]> highlighted2 = Collections.singletonList(new String[]{"names: List[str], "});
        feignCtrlP(test.get("<arg6>").getTextOffset()).check(texts2, highlighted2, Collections.singletonList(ArrayUtil.EMPTY_STRING_ARRAY));

        final List<String> texts3 = Collections.singletonList("bar: int, baz: str=\"\"");
        final List<String[]> highlighted3 = Collections.singletonList(new String[]{"bar: int, "});
        feignCtrlP(test.get("<arg7>").getTextOffset()).check(texts3, highlighted3, Collections.singletonList(ArrayUtil.EMPTY_STRING_ARRAY));
      }
    );
  }

  // PY-24930
  public void testCallOperator() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON35,
      () -> {
        for (int offset : StreamEx.of(loadTest(2).values()).map(PsiElement::getTextOffset)) {
          feignCtrlP(offset).check("self: Foo, arg: int", new String[]{"arg: int"}, new String[]{"self: Foo, "});
        }
      }
    );
  }

  /**
   * Imitates pressing of Ctrl+P; fails if results are not as expected.
   * @param offset offset of 'cursor' where Ctrl+P is pressed.
   * @return a {@link Collector} with collected hint info.
   */
  @NotNull
  private Collector feignCtrlP(int offset) {
    final PyParameterInfoHandler handler = new PyParameterInfoHandler();

    final Collector collector = new Collector(myFixture.getFile(), offset);
    collector.setParameterOwner(handler.findElementForParameterInfo(collector));

    if (collector.getParameterOwner() != null) {
      handler.updateParameterInfo((PyArgumentList)collector.getParameterOwner(), collector);

      for (Object itemToShow : collector.getItemsToShow()) {
        //noinspection unchecked
        handler.updateUI((Pair<PyCallExpression, PyCallExpression.PyMarkedCallee>)itemToShow, collector);
      }
    }

    return collector;
  }

  /**
   * Imitates the normal UI contexts to the extent we use it. Collects highlighting.
   */
  private static class Collector implements ParameterInfoUIContextEx, CreateParameterInfoContext, UpdateParameterInfoContext {

    @NotNull
    private final PsiFile myFile;
    private final int myOffset;

    @NotNull
    private final List<String[]> myListOfTexts;

    @NotNull
    private final List<EnumSet<Flag>[]> myListOfFlags;

    @Nullable
    private PyArgumentList myParameterOwner;

    @NotNull
    private Object[] myItemsToShow;

    private int myIndex;

    private Collector(@NotNull PsiFile file, int offset) {
      myFile = file;
      myOffset = offset;
      myListOfTexts = new ArrayList<>();
      myListOfFlags = new ArrayList<>();
      myItemsToShow = ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    @NotNull
    public String setupUIComponentPresentation(@NotNull String[] texts, @NotNull EnumSet<Flag>[] flags, @NotNull Color background) {
      assertEquals(texts.length, flags.length);
      myListOfTexts.add(texts);
      myListOfFlags.add(flags);
      return StringUtil.join(texts, "");
    }

    @Override
    public void setEscapeFunction(@Nullable Function<String, String> escapeFunction) {
    }

    @Override
    public String setupUIComponentPresentation(String text, int highlightStartOffset, int highlightEndOffset, boolean isDisabled,
                                               boolean strikeout, boolean isDisabledBeforeHighlight, Color background) {
      // nothing, we don't use it
      return text;
    }

    @Override
    public boolean isUIComponentEnabled() {
      return true;
    }

    @Override
    public boolean isUIComponentEnabled(int index) {
      return true;
    }

    @Override
    public void setUIComponentEnabled(boolean enabled) { }

    @Override
    public void setUIComponentEnabled(int index, boolean enabled) { }

    @Override
    public int getCurrentParameterIndex() {
      return myIndex;
    }

    @Override
    public void removeHint() { }

    @Override
    public void setParameterOwner(@Nullable PsiElement o) {
      assertTrue("Found element is not `null` and not " + PyArgumentList.class.getName(), o == null || o instanceof PyArgumentList);
      myParameterOwner = (PyArgumentList)o;
    }

    @Override
    @Nullable
    public PsiElement getParameterOwner() {
      return myParameterOwner;
    }

    @Override
    public boolean isSingleParameterInfo() {
      return false;
    }

    @Override
    public void setHighlightedParameter(Object parameter) {
      // nothing, we don't use it
    }

    @Override
    public Object getHighlightedParameter() {
      return null;
    }

    @Override
    public void setCurrentParameter(int index) {
      myIndex = index;
    }

    @Override
    @NotNull
    public Color getDefaultParameterColor() {
      return java.awt.Color.BLACK;
    }

    @Override
    @NotNull
    public Object[] getItemsToShow() {
      return myItemsToShow;
    }

    @Override
    public void setItemsToShow(@NotNull Object[] items) {
      myItemsToShow = items;
    }

    @Override
    public void showHint(PsiElement element, int offset, ParameterInfoHandler handler) { }

    @Override
    public int getParameterListStart() {
      return 0; // we don't use it
    }

    @Override
    public Object[] getObjectsToView() {
      return null; // we don't use it
    }

    @Override
    public boolean isPreservedOnHintHidden() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setPreservedOnHintHidden(boolean value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isInnermostContext() {
      return false;
    }

    @Override
    public PsiElement getHighlightedElement() {
      return null;  // we don't use it
    }

    @Override
    public void setHighlightedElement(PsiElement elements) {
      // nothing, we don't use it
    }

    @Override
    public Project getProject() {
      throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public PsiFile getFile() {
      return myFile;
    }

    @Override
    public int getOffset() {
      return myOffset;
    }

    @Override
    @NotNull
    public Editor getEditor() {
      throw new UnsupportedOperationException();
    }

    private void check(@NotNull String text, @NotNull String[] highlighted) {
      check(text, highlighted, ArrayUtil.EMPTY_STRING_ARRAY);
    }

    private void check(@NotNull String text, @NotNull String[] highlighted, @NotNull String[] disabled) {
      assertEquals("Number of collected hints is wrong", 1, myItemsToShow.length);
      check(text, highlighted, disabled, 0);
    }

    private void check(@NotNull List<String> texts, @NotNull List<String[]> highlighted, @NotNull List<String[]> disabled) {
      assertEquals("Number of collected hints is wrong", texts.size(), myItemsToShow.length);
      for (int i = 0; i < texts.size(); i++) {
        check(texts.get(i), highlighted.get(i), disabled.get(i), i);
      }
    }

    /**
     * Checks if hint data looks as expected.
     *
     * @param text        expected text of the hint, without formatting
     * @param highlighted expected highlighted substrings of hint
     * @param disabled    expected disabled substrings of hint
     * @param index       hint index
     */
    private void check(@NotNull String text, @NotNull String[] highlighted, @NotNull String[] disabled, int index) {
      final String[] hintText = myListOfTexts.get(index);
      final EnumSet<Flag>[] hintFlags = myListOfFlags.get(index);

      assertEquals("Signature", text, StringUtil.join(hintText, ""));

      final StringBuilder wrongs = new StringBuilder();

      // see if highlighted matches
      final Set<String> highlightSet = new HashSet<>(Arrays.asList(highlighted));
      for (int i = 0; i < hintText.length; i++) {
        if (hintFlags[i].contains(Flag.HIGHLIGHT) && !highlightSet.contains(hintText[i])) {
          wrongs.append("Highlighted unexpected '").append(hintText[i]).append("'. ");
        }
      }
      for (int i = 0; i < hintText.length; i++) {
        if (!hintFlags[i].contains(Flag.HIGHLIGHT) && highlightSet.contains(hintText[i])) {
          wrongs.append("Not highlighted expected '").append(hintText[i]).append("'. ");
        }
      }

      // see if disabled matches
      final Set<String> disabledSet = new HashSet<>(Arrays.asList(disabled));
      for (int i = 0; i < hintText.length; i++) {
        if (hintFlags[i].contains(Flag.DISABLE) && !disabledSet.contains(hintText[i])) {
          wrongs.append("Highlighted a disabled '").append(hintText[i]).append("'. ");
        }
      }
      for (int i = 0; i < hintText.length; i++) {
        if (!hintFlags[i].contains(Flag.DISABLE) && disabledSet.contains(hintText[i])) {
          wrongs.append("Not disabled expected '").append(hintText[i]).append("'. ");
        }
      }
      //

      if (wrongs.length() > 0) fail(wrongs.toString());
    }

    private void assertNotFound() {
      assertNull(myParameterOwner);
    }
  }
}
