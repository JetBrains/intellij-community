// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContextEx;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import com.jetbrains.python.codeInsight.parameterInfo.PyParameterInfoUtils;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Tests parameter info available via ^P at call sites.
 */
public class PyParameterInfoTest extends LightMarkedTestCase {

  @Override
  protected Map<String, PsiElement> loadTest() {
    String fname = "/paramInfo/" + getTestName(false) + ".py";
    return configureByFile(fname);
  }

  private Map<String, PsiElement> loadTest(int expectedMarks) {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", expectedMarks, marks.size());
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
    feignCtrlP(arg3.getTextOffset()+2).check("a, b, c", ArrayUtil.EMPTY_STRING_ARRAY); // ^P on a ")" gives nothing
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

    feignCtrlP(marks.get("<arg2>").getTextOffset()-2).check("a, (b, c), d", ArrayUtil.EMPTY_STRING_ARRAY); // before nested tuple: no arg matches
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

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("cls: type[A], a, b", new String[]{"a, "}, new String[]{"cls: type[A], "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("cls: type[A], a, b", new String[]{"b"}, new String[]{"cls: type[A], "});
  }

  public void testRedefinedNewDirectCall() {
    Map<String, PsiElement> marks = loadTest(3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("cls: type[A], a, b", new String[]{"cls: type[A], "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("cls: type[A], a, b", new String[]{"a, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("cls: type[A], a, b", new String[]{"b"});
  }

  public void testIgnoreNewInOldStyleClass() {
    runWithLanguageLevel(LanguageLevel.PYTHON27, () -> {
      Map<String, PsiElement> marks = loadTest(1);

      feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self: A, one", new String[]{"one"}, new String[]{"self: A, "});
    });
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

  // PY-53671
  public void testUnboundMethodReassignedAndImportedWithQualifiedImport() {
    Map<String, PsiElement> marks = loadMultiFileTest(1);
    feignCtrlP(marks.get("<arg>").getTextOffset()).check("self: C, param", new String[]{"self: C, "}, ArrayUtil.EMPTY_STRING_ARRAY);
  }

  // PY-53671
  public void testBoundMethodReassignedAndImportedWithQualifiedImport() {
    Map<String, PsiElement> marks = loadMultiFileTest(1);
    feignCtrlP(marks.get("<arg>").getTextOffset()).check("self: C, param", new String[]{"param"}, new String[]{"self: C, "});
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

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b, c: int = 1, d: int = 2, e: int = 3", new String[]{"d: int = 2, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a, b, c: int = 1, d: int = 2, e: int = 3", new String[]{"e: int = 3"}); // no logical next
  }

  public void testPy3kPastTupleArg() {
    Map<String, PsiElement> marks = loadTest(4);
    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("*arg, a: int = 1, b: int = 2", new String[]{"*arg, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("*arg, a: int = 1, b: int = 2", new String[]{"*arg, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("*arg, a: int = 1, b: int = 2", new String[]{"b: int = 2"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("*arg, a: int = 1, b: int = 2", new String[]{"a: int = 1, "});
  }

  public void testNoArgs() {
    Map<String, PsiElement> marks = loadTest(1);
    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b, c", new String[]{"a, "});
  }

  public void testNoArgsException() {
    Map<String, PsiElement> marks = loadTest(1);
    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("<no parameters>", ArrayUtilRt.EMPTY_STRING_ARRAY, new String[]{"<no parameters>"});
  }

  public void testMultilineStringDefault() {
    final int offset = loadTest(1).get("<arg2>").getTextOffset();
    feignCtrlP(offset).check("length: int = 12, allowed_chars: str = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'",
                             new String[]{"allowed_chars: str = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'"},
                             ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  // PY-22005
  public void testWithSpecifiedType() {
    final int offset = loadTest(1).get("<arg1>").getTextOffset();
    final String expectedInfo = "a1: str, a2: str | None = None, a3: str | int | None = None, a4: int | Any, *args: int, **kwargs: int";

    feignCtrlP(offset).check(expectedInfo, new String[]{"a1: str, "});
  }

  // PY-23055
  public void testWithoutTypeButWithNoneDefaultValue() {
    final int offset = loadTest(1).get("<arg1>").getTextOffset();
    final String expectedInfo = "b=None";

    feignCtrlP(offset).check(expectedInfo, new String[]{"b=None"});
  }

  // PY-22004
  public void testMultiResolved() {
    final int offset = loadTest(1).get("<arg2>").getTextOffset();

    final List<String> texts = Arrays.asList("self: C1, x", "self: C2, x, y: str");
    final List<String[]> highlighted = Arrays.asList(ArrayUtilRt.EMPTY_STRING_ARRAY, new String[]{"y: str"});
    final List<String[]> disabled = Arrays.asList(new String[]{"self: C1, "}, new String[]{"self: C2, "});

    feignCtrlP(offset).check(texts, highlighted, disabled);
  }

  public void testOverloadsInImportedClass() {
    final int offset = loadMultiFileTest(1).get("<arg1>").getTextOffset();

    final List<String> texts = Arrays.asList("self: C, a: str, b: str", "self: C, a: int, b: int");
    final List<String[]> highlighted = Arrays.asList(new String[]{"a: str, "}, new String[]{"a: int, "});
    final List<String[]> disabled = Arrays.asList(new String[]{"self: C, "}, new String[]{"self: C, "});

    feignCtrlP(offset).check(texts, highlighted, disabled);
  }

  public void testOverloadsInImportedModule() {
    final int offset = loadMultiFileTest(1).get("<arg1>").getTextOffset();

    final List<String> texts = Arrays.asList("a: str, b: str", "a: int, b: int");
    final List<String[]> highlighted = Arrays.asList(new String[]{"a: str, "}, new String[]{"a: int, "});

    feignCtrlP(offset).check(texts, highlighted, Arrays.asList(ArrayUtilRt.EMPTY_STRING_ARRAY, ArrayUtilRt.EMPTY_STRING_ARRAY));
  }

  public void testOverloadsWithDifferentNumberOfArgumentsInImportedClass() {
    final int offset = loadMultiFileTest(1).get("<arg1>").getTextOffset();

    final List<String> texts = Arrays.asList("self: C, a: str, b: str", "self: C, a: int");
    final List<String[]> highlighted = Arrays.asList(new String[]{"a: str, "}, new String[]{"a: int"});
    final List<String[]> disabled = Arrays.asList(new String[]{"self: C, "}, new String[]{"self: C, "});

    feignCtrlP(offset).check(texts, highlighted, disabled);
  }

  public void testOverloadsWithDifferentNumberOfArgumentsInImportedModule() {
    final int offset = loadMultiFileTest(1).get("<arg1>").getTextOffset();

    final List<String> texts = Arrays.asList("a: str, b: str", "a: int");
    final List<String[]> highlighted = Arrays.asList(new String[]{"a: str, "}, new String[]{"a: int"});

    feignCtrlP(offset).check(texts, highlighted, Arrays.asList(ArrayUtilRt.EMPTY_STRING_ARRAY, ArrayUtilRt.EMPTY_STRING_ARRAY));
  }

  // PY-22971
  public void testTopLevelOverloadsAndImplementation() {
    final int offset = loadTest(1).get("<arg1>").getTextOffset();

    final List<String> texts = Arrays.asList("value: None", "value: int", "value: str");
    final List<String[]> highlighted = Arrays.asList(new String[]{"value: None"}, new String[]{"value: int"}, new String[]{"value: str"});

    feignCtrlP(offset).check(texts, highlighted, Arrays.asList(ArrayUtilRt.EMPTY_STRING_ARRAY, ArrayUtilRt.EMPTY_STRING_ARRAY,
                                                               ArrayUtilRt.EMPTY_STRING_ARRAY));
  }

  // PY-22971
  public void testOverloadsAndImplementationInClass() {
    final int offset = loadTest(1).get("<arg1>").getTextOffset();

    final List<String> texts = Arrays.asList("self: A, value: None", "self: A, value: int", "self: A, value: str");
    final List<String[]> highlighted = Arrays.asList(new String[]{"value: None"}, new String[]{"value: int"}, new String[]{"value: str"});
    final List<String[]> disabled = Arrays.asList(new String[]{"self: A, "}, new String[]{"self: A, "}, new String[]{"self: A, "});

    feignCtrlP(offset).check(texts, highlighted, disabled);
  }

  // PY-22971
  public void testOverloadsAndImplementationInImportedModule() {
    final int offset = loadMultiFileTest(1).get("<arg1>").getTextOffset();

    final List<String> texts = Arrays.asList("value: None", "value: int", "value: str");
    final List<String[]> highlighted = Arrays.asList(new String[]{"value: None"}, new String[]{"value: int"}, new String[]{"value: str"});

    feignCtrlP(offset).check(texts, highlighted, Arrays.asList(ArrayUtilRt.EMPTY_STRING_ARRAY, ArrayUtilRt.EMPTY_STRING_ARRAY,
                                                               ArrayUtilRt.EMPTY_STRING_ARRAY));
  }

  // PY-22971
  public void testOverloadsAndImplementationInImportedClass() {
    final int offset = loadMultiFileTest(1).get("<arg1>").getTextOffset();

    final List<String> texts = Arrays.asList("self: A, value: None", "self: A, value: int", "self: A, value: str");
    final List<String[]> highlighted = Arrays.asList(new String[]{"value: None"}, new String[]{"value: int"}, new String[]{"value: str"});
    final List<String[]> disabled = Arrays.asList(new String[]{"self: A, "}, new String[]{"self: A, "}, new String[]{"self: A, "});

    feignCtrlP(offset).check(texts, highlighted, disabled);
  }

  // PY-23625
  public void testEscapingInDefaultValue() {
    final int offset = loadTest(1).get("<arg1>").getTextOffset();

    feignCtrlP(offset).check("p: str = \"\\n\", t: str = \"\\t\", r: str = \"\\r\"", new String[]{"p: str = \"\\n\", "});
  }

  public void testJustTypingCallable() {
    final int offset = loadTest(1).get("<arg1>").getTextOffset();

    feignCtrlP(offset).check(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
  }

  public void testTypingCallableWithUnknownParameters() {
    final int offset = loadTest(1).get("<arg1>").getTextOffset();

    feignCtrlP(offset).check(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
  }

  public void testTypingCallableWithKnownParameters() {
    final int offset = loadTest(1).get("<arg1>").getTextOffset();

    final List<String> texts = Collections.singletonList("...: int, ...: str");
    final List<String[]> highlighted = Collections.singletonList(new String[]{"...: int, "});

    feignCtrlP(offset).check(texts, highlighted, Collections.singletonList(ArrayUtilRt.EMPTY_STRING_ARRAY));
  }

  // PY-22249, PY-45473
  public void testInitializingCollectionsNamedTuple() {
    final Map<String, PsiElement> test = loadTest(3);

    for (int offset : StreamEx.of(test.values()).map(PsiElement::getTextOffset)) {
      final List<String> texts = Collections.singletonList("bar, baz");
      final List<String[]> highlighted = Collections.singletonList(new String[]{"bar, "});

      feignCtrlP(offset).check(texts, highlighted, Collections.singletonList(ArrayUtilRt.EMPTY_STRING_ARRAY));
    }
  }

  // PY-33140
  public void testInitializingTypingNamedTuple() {
    final Map<String, PsiElement> test = loadTest(8);

    for (int offset : StreamEx.of(1, 2, 3, 4, 8).map(number -> test.get("<arg" + number + ">").getTextOffset())) {
      final List<String> texts = Collections.singletonList("bar: int, baz: str");
      final List<String[]> highlighted = Collections.singletonList(new String[]{"bar: int, "});

      feignCtrlP(offset).check(texts, highlighted, Collections.singletonList(ArrayUtilRt.EMPTY_STRING_ARRAY));
    }

    final List<String> texts1 = Collections.singletonList("bar: int, baz: str, foo: int");
    final List<String[]> highlighted1 = Collections.singletonList(new String[]{"bar: int, "});
    feignCtrlP(test.get("<arg5>").getTextOffset()).check(texts1, highlighted1, Collections.singletonList(ArrayUtilRt.EMPTY_STRING_ARRAY));

    final List<String> texts2 = Collections.singletonList("names: list[str], ages: list[int]");
    final List<String[]> highlighted2 = Collections.singletonList(new String[]{"names: list[str], "});
    feignCtrlP(test.get("<arg6>").getTextOffset()).check(texts2, highlighted2, Collections.singletonList(ArrayUtilRt.EMPTY_STRING_ARRAY));

    final List<String> texts3 = Collections.singletonList("bar: int, baz: str = \"\"");
    final List<String[]> highlighted3 = Collections.singletonList(new String[]{"bar: int, "});
    feignCtrlP(test.get("<arg7>").getTextOffset()).check(texts3, highlighted3, Collections.singletonList(ArrayUtilRt.EMPTY_STRING_ARRAY));
  }

  // PY-24930
  public void testCallOperator() {
    for (int offset : StreamEx.of(loadTest(2).values()).map(PsiElement::getTextOffset)) {
      feignCtrlP(offset).check("self: Foo, arg: int", new String[]{"arg: int"}, new String[]{"self: Foo, "});
    }
  }

  // PY-27148
  public void testCollectionsNamedTupleReplace() {
    final Map<String, PsiElement> test = loadTest(4);

    for (int offset : StreamEx.of("<arg1>", "<arg2>").map(test::get).map(PsiElement::getTextOffset)) {
      feignCtrlP(offset).check("*, bar=..., baz=...", ArrayUtilRt.EMPTY_STRING_ARRAY);
    }

    feignCtrlP(test.get("<arg3>").getTextOffset()).check("self: MyTup1, *, bar=..., baz=...", ArrayUtilRt.EMPTY_STRING_ARRAY);
    feignCtrlP(test.get("<arg4>").getTextOffset()).check("self: MyTup2, *, bar=..., baz=...", ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  // PY-27148
  public void testTypingNamedTupleReplace() {
    final Map<String, PsiElement> test = loadTest(4);

    for (int offset : StreamEx.of("<arg1>", "<arg2>").map(test::get).map(PsiElement::getTextOffset)) {
      feignCtrlP(offset).check("*, bar: int = ..., baz: str = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
    }

    feignCtrlP(test.get("<arg3>").getTextOffset()).check("self: MyTup1, *, bar: int = ..., baz: str = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
    feignCtrlP(test.get("<arg4>").getTextOffset()).check("self: MyTup2, *, bar: int = ..., baz: str = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  // PY-26582
  public void testStructuralType() {
    final Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("p1, p2: int", new String[]{"p1, "});
  }

  // PY-27398
  public void testInitializingDataclass() {
    final Map<String, PsiElement> marks = loadMultiFileTest(11);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("x: int, y: str, z: float = 0.0", new String[]{"x: int, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("x: int, y: str, z: float = 0.0", new String[]{"x: int, "});

    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("self: object", ArrayUtilRt.EMPTY_STRING_ARRAY, new String[]{"self: object"});

    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("self: B2, x: int", new String[]{"x: int"}, new String[]{"self: B2, "});
    feignCtrlP(marks.get("<arg5>").getTextOffset()).check("b: int", new String[]{"b: int"});
    feignCtrlP(marks.get("<arg6>").getTextOffset()).check("b: int", new String[]{"b: int"});
    feignCtrlP(marks.get("<arg7>").getTextOffset()).check("a: int, b: int", new String[]{"a: int, "});

    feignCtrlP(marks.get("<arg8>").getTextOffset()).check("a: int, b: int, d: int = ..., e: int = ...", new String[]{"a: int, "});

    feignCtrlP(marks.get("<arg9>").getTextOffset()).check("x: int, y: str, z: float = 0.0", new String[]{"x: int, "});
    feignCtrlP(marks.get("<arg10>").getTextOffset()).check(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    feignCtrlP(marks.get("<arg11>").getTextOffset()).check("baz: str", new String[]{"baz: str"});
  }

  // PY-28506
  public void testInitializingDataclassHierarchy() {
    final Map<String, PsiElement> marks = loadMultiFileTest(4);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a: int, b: str", new String[]{"a: int, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a: int, b: str", new String[]{"a: int, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a: int", new String[]{"a: int"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("self: object", ArrayUtilRt.EMPTY_STRING_ARRAY, new String[]{"self: object"});
  }

  // PY-28506
  public void testInitializingDataclassMixedHierarchy() {
    final Map<String, PsiElement> marks = loadMultiFileTest(5);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a: int", new String[]{"a: int"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("b: str", new String[]{"b: str"});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("self: B3, b: str", new String[]{"b: str"}, new String[]{"self: B3, "});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("self: object", ArrayUtilRt.EMPTY_STRING_ARRAY, new String[]{"self: object"});
    feignCtrlP(marks.get("<arg5>").getTextOffset()).check("x: int, z: str", new String[]{"x: int, "});
  }

  // PY-28506, PY-31762, PY-35548
  public void testInitializingDataclassOverridingField() {
    final Map<String, PsiElement> marks = loadMultiFileTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("x: int = 15, y: int = 0, z: int = 10", new String[]{"x: int = 15, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a: int", new String[]{"a: int"});
  }

  // PY-26354
  public void testInitializingAttrsUsingPep526() {
    runWithAdditionalClassEntryInSdkRoots(
      "packages",
      () -> {
        final Map<String, PsiElement> marks = loadTest(9);

        feignCtrlP(marks.get("<arg1>").getTextOffset()).check("x: int, y: str, z: float = 0.0", new String[]{"x: int, "});
        feignCtrlP(marks.get("<arg2>").getTextOffset()).check("x: int, y: str, z: float = 0.0", new String[]{"x: int, "});

        feignCtrlP(marks.get("<arg3>").getTextOffset()).check("self: object", ArrayUtilRt.EMPTY_STRING_ARRAY, new String[]{"self: object"});

        feignCtrlP(marks.get("<arg4>").getTextOffset()).check("self: B2, x: int", new String[]{"x: int"}, new String[]{"self: B2, "});
        feignCtrlP(marks.get("<arg5>").getTextOffset()).check("b: int", new String[]{"b: int"});
        feignCtrlP(marks.get("<arg6>").getTextOffset()).check("x: int, y: str = \"0\"", new String[]{"x: int, "});
        feignCtrlP(marks.get("<arg7>").getTextOffset()).check("x: int", new String[]{"x: int"});
        feignCtrlP(marks.get("<arg8>").getTextOffset()).check("baz: str", new String[]{"baz: str"});
        feignCtrlP(marks.get("<arg9>").getTextOffset()).check("bar: str", new String[]{"bar: str"});
      }
    );
  }

  // PY-26354
  public void testInitializingAttrs() {
    runWithAdditionalClassEntryInSdkRoots(
      "packages",
      () -> {
        final Map<String, PsiElement> marks = loadTest(8);

        feignCtrlP(marks.get("<arg1>").getTextOffset()).check("x, y, z: int = ...", new String[]{"x, "});
        feignCtrlP(marks.get("<arg2>").getTextOffset()).check("x, y, z: int = ...", new String[]{"x, "});
        feignCtrlP(marks.get("<arg3>").getTextOffset()).check("x, z: int = ...", new String[]{"x, "});
        feignCtrlP(marks.get("<arg4>").getTextOffset()).check("x, y, z: list = ...", new String[]{"x, "});
        feignCtrlP(marks.get("<arg5>").getTextOffset()).check("x, y: int = ...", new String[]{"x, "});
        feignCtrlP(marks.get("<arg6>").getTextOffset()).check("x, y: str = ...", new String[]{"x, "});
        feignCtrlP(marks.get("<arg7>").getTextOffset()).check("x: int | Any = ...", new String[]{"x: int | Any = ..."});
        feignCtrlP(marks.get("<arg8>").getTextOffset()).check("x, y, z: list = ...", new String[]{"x, "});
      }
    );
  }

  // PY-31762
  public void testInitializingAttrsHierarchy() {
    // same as for std dataclasses + overriding

    runWithAdditionalClassEntryInSdkRoots(
      "packages",
      () -> {
        final Map<String, PsiElement> marks = loadTest(6);

        feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a: int, b: str", new String[]{"a: int, "});
        feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a: int, b: str", new String[]{"a: int, "});
        feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a: int", new String[]{"a: int"});
        feignCtrlP(marks.get("<arg4>").getTextOffset()).check("self: object", ArrayUtilRt.EMPTY_STRING_ARRAY, new String[]{"self: object"});
        feignCtrlP(marks.get("<arg5>").getTextOffset()).check("y: int = 0, z: int = 10, x: int = 15", new String[]{"y: int = 0, "});
        feignCtrlP(marks.get("<arg6>").getTextOffset()).check("type: int = ..., locations: str = ...", new String[]{"type: int = ..., "});
      }
    );
  }

  // PY-31762
  public void testInitializingAttrsMixedHierarchy() {
    // same as for std dataclasses

    runWithAdditionalClassEntryInSdkRoots(
      "packages",
      () -> {
        final Map<String, PsiElement> marks = loadTest(5);

        feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a: int", new String[]{"a: int"});
        feignCtrlP(marks.get("<arg2>").getTextOffset()).check("b: str", new String[]{"b: str"});
        feignCtrlP(marks.get("<arg3>").getTextOffset()).check("self: B3, b: str", new String[]{"b: str"}, new String[]{"self: B3, "});
        feignCtrlP(marks.get("<arg4>").getTextOffset()).check("self: object", ArrayUtilRt.EMPTY_STRING_ARRAY, new String[]{"self: object"});
        feignCtrlP(marks.get("<arg5>").getTextOffset()).check("x: int, z: str", new String[]{"x: int, "});
      }
    );
  }

  // PY-34374
  public void testInitializingAttrsKwOnlyOnClass() {
    runWithAdditionalClassEntryInSdkRoots(
      "packages",
      () -> {
        final Map<String, PsiElement> marks = loadTest(5);

        feignCtrlP(marks.get("<arg1>").getTextOffset()).check("*, a: int", new String[]{"*, a: int"});
        feignCtrlP(marks.get("<arg2>").getTextOffset()).check("*, a: int, b: int", new String[]{"*, a: int"});
        feignCtrlP(marks.get("<arg3>").getTextOffset()).check("*, a: int, b: int = ...", new String[]{"*, a: int"});
        feignCtrlP(marks.get("<arg4>").getTextOffset()).check("*, a: int = ..., b: int", new String[]{"*, a: int"});
        feignCtrlP(marks.get("<arg5>").getTextOffset()).check("*, a: int = ..., b: int = ...", new String[]{"*, a: int"});
      }
    );
  }

  // PY-34374
  public void testInitializingAttrsKwOnlyOnBaseClass() {
    runWithAdditionalClassEntryInSdkRoots(
      "packages",
      () -> {
        final Map<String, PsiElement> marks = loadTest(4);

        feignCtrlP(marks.get("<arg1>").getTextOffset()).check("b: int, *, a: int", new String[]{"b: int, "});
        feignCtrlP(marks.get("<arg2>").getTextOffset()).check("b: int = ..., *, a: int", new String[]{"b: int = ..., "});
        feignCtrlP(marks.get("<arg3>").getTextOffset()).check("b: int, *, a: int = ...", new String[]{"b: int, "});
        feignCtrlP(marks.get("<arg4>").getTextOffset()).check("b: int = ..., *, a: int = ...", new String[]{"b: int = ..., "});
      }
    );
  }

  // PY-34374
  public void testInitializingAttrsKwOnlyOnDerivedClass() {
    runWithAdditionalClassEntryInSdkRoots(
      "packages",
      () -> {
        final Map<String, PsiElement> marks = loadTest(4);

        feignCtrlP(marks.get("<arg1>").getTextOffset()).check("*, a: int, b: int", new String[]{"*, a: int"});
        // non-working case PY-39461
        //feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a: int, *, b: int = ...", new String[]{"a: int, "});
        feignCtrlP(marks.get("<arg3>").getTextOffset()).check("*, a: int = ..., b: int", new String[]{"*, a: int"});
        feignCtrlP(marks.get("<arg4>").getTextOffset()).check("*, a: int = ..., b: int = ...", new String[]{"*, a: int"});
      }
    );
  }

  // PY-34374
  public void testInitializingAttrsKwOnlyOnClassOverridingHierarchy() {
    runWithAdditionalClassEntryInSdkRoots(
      "packages",
      () -> {
        final Map<String, PsiElement> marks = loadTest(3);

        feignCtrlP(marks.get("<arg1>").getTextOffset()).check("*, a: int", new String[]{"*, a: int"});
        feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a: int", new String[]{"a: int"});
        feignCtrlP(marks.get("<arg3>").getTextOffset()).check("*, a: int", new String[]{"*, a: int"});
      }
    );
  }

  // PY-33189
  public void testInitializingAttrsKwOnlyOnFields() {
    runWithAdditionalClassEntryInSdkRoots(
      "packages",
      () -> {
        final Map<String, PsiElement> marks = loadTest(5);

        feignCtrlP(marks.get("<arg1>").getTextOffset()).check("b: int, *, a", new String[]{"b: int, "});
        feignCtrlP(marks.get("<arg2>").getTextOffset()).check("b: int, *, a", new String[]{"b: int, "});
        feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a: int, *, b", new String[]{"a: int, "});
        feignCtrlP(marks.get("<arg4>").getTextOffset()).check("*, a", ArrayUtil.EMPTY_STRING_ARRAY);
        feignCtrlP(marks.get("<arg5>").getTextOffset()).check("a: int", new String[]{"a: int"});
      }
    );
  }

  // PY-59198
  public void testInitializingAttrsFieldAlias() {
    runWithAdditionalClassEntryInSdkRoots(
      "packages",
      () -> {
        final Map<String, PsiElement> marks = loadTest(2);

        feignCtrlP(marks.get("<arg1>").getTextOffset()).check("foo, bar, baz", new String[]{"foo, "});
        feignCtrlP(marks.get("<arg2>").getTextOffset()).check("foo, bar, baz", new String[]{"foo, "});
      }
    );
  }

  // PY-28957
  public void testDataclassesReplace() {
    final Map<String, PsiElement> marks = loadMultiFileTest(4);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("obj: A, *, a: int = ..., b: str = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("obj: B, *, a: int = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("obj: C, *, a: int = ..., b: str = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("obj: _DataclassT, /, **changes", new String[]{"**changes"});
  }

  // PY-28506
  public void testDataclassesHierarchyReplace() {
    final Map<String, PsiElement> marks = loadMultiFileTest(5);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("obj: B1, *, a: int = ..., b: str = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("obj: B2, *, a: int = ..., b: str = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("obj: B3, *, a: int = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("obj: _DataclassT, /, **changes", new String[]{"**changes"});
    feignCtrlP(marks.get("<arg5>").getTextOffset()).check("obj: B5, *, x: int = ..., y: int = ..., z: int = ...",
                                                          ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  // PY-28506
  public void testDataclassesMixedHierarchyReplace() {
    final Map<String, PsiElement> marks = loadMultiFileTest(5);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("obj: B1, *, a: int = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("obj: B2, *, b: str = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("obj: _DataclassT, /, **changes", new String[]{"**changes"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("obj: _DataclassT, /, **changes", new String[]{"**changes"});
    feignCtrlP(marks.get("<arg5>").getTextOffset()).check("obj: C5, *, x: int = ..., z: str = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  // PY-26354
  public void testAttrsReplace() {
    runWithAdditionalClassEntryInSdkRoots(
      "packages",
      () -> {
        final Map<String, PsiElement> marks = loadTest(10);

        feignCtrlP(marks.get("<arg1>").getTextOffset()).check("inst: A, *, a: int = ..., b: str = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
        feignCtrlP(marks.get("<arg2>").getTextOffset()).check("inst: A, *, a: int = ..., b: str = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
        feignCtrlP(marks.get("<arg3>").getTextOffset()).check("inst: B, *, a: int = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
        feignCtrlP(marks.get("<arg4>").getTextOffset()).check("inst: B, *, a: int = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
        feignCtrlP(marks.get("<arg5>").getTextOffset()).check("inst: _T, **changes", new String[]{"**changes"});
        feignCtrlP(marks.get("<arg6>").getTextOffset()).check("inst: _T, **changes", new String[]{"**changes"});
        feignCtrlP(marks.get("<arg7>").getTextOffset()).check("inst: D, *, a: int = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
        feignCtrlP(marks.get("<arg8>").getTextOffset()).check("inst: D, *, a: int = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
        feignCtrlP(marks.get("<arg9>").getTextOffset()).check("inst: E, *, a: int = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
        feignCtrlP(marks.get("<arg10>").getTextOffset()).check("inst: E, *, a: int = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
      }
    );
  }

  // PY-31762
  public void testAttrsHierarchyReplace() {
    // same as for std dataclasses except overridding

    runWithAdditionalClassEntryInSdkRoots(
      "packages",
      () -> {
        final Map<String, PsiElement> marks = loadTest(5);

        feignCtrlP(marks.get("<arg1>").getTextOffset()).check("inst: B1, *, a: int = ..., b: str = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
        feignCtrlP(marks.get("<arg2>").getTextOffset()).check("inst: B2, *, a: int = ..., b: str = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
        feignCtrlP(marks.get("<arg3>").getTextOffset()).check("inst: B3, *, a: int = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
        feignCtrlP(marks.get("<arg4>").getTextOffset()).check("inst: _T, **changes", new String[]{"**changes"});

        feignCtrlP(marks.get("<arg5>").getTextOffset()).check("inst: B5, *, y: int = ..., z: int = ..., x: int = ...",
                                                              ArrayUtilRt.EMPTY_STRING_ARRAY);
      }
    );
  }

  // PY-31762
  public void testAttrsMixedHierarchyReplace() {
    // same as for std dataclasses

    runWithAdditionalClassEntryInSdkRoots(
      "packages",
      () -> {
        final Map<String, PsiElement> marks = loadTest(5);

        feignCtrlP(marks.get("<arg1>").getTextOffset()).check("inst: B1, *, a: int = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
        feignCtrlP(marks.get("<arg2>").getTextOffset()).check("inst: B2, *, b: str = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
        feignCtrlP(marks.get("<arg3>").getTextOffset()).check("inst: _T, **changes", new String[]{"**changes"});
        feignCtrlP(marks.get("<arg4>").getTextOffset()).check("inst: _T, **changes", new String[]{"**changes"});
        feignCtrlP(marks.get("<arg5>").getTextOffset()).check("inst: C5, *, x: int = ..., z: str = ...", ArrayUtilRt.EMPTY_STRING_ARRAY);
      }
    );
  }

  // PY-47532
  public void testAttrDataclassDecoratorAliases() {
    final Map<String, PsiElement> marks = loadTest(11);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("x: int, y: str", new String[]{"x: int, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("x: int, y: str", new String[]{"x: int, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("x: int, y: str", new String[]{"x: int, "});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("x: int, y: str", new String[]{"x: int, "});
    feignCtrlP(marks.get("<arg5>").getTextOffset()).check("x: int, y: str", new String[]{"x: int, "});
    feignCtrlP(marks.get("<arg6>").getTextOffset()).check("x: int, y: str", new String[]{"x: int, "});
    feignCtrlP(marks.get("<arg7>").getTextOffset()).check("x: int, y: str", new String[]{"x: int, "});
    feignCtrlP(marks.get("<arg8>").getTextOffset()).check("x: int, y: str", new String[]{"x: int, "});
    feignCtrlP(marks.get("<arg9>").getTextOffset()).check("x: int, y: str", new String[]{"x: int, "});
    feignCtrlP(marks.get("<arg10>").getTextOffset()).check("x: int, y: str", new String[]{"x: int, "});
    feignCtrlP(marks.get("<arg11>").getTextOffset()).check("x, y", new String[]{"x, "});
  }

  // EA-102450
  public void testKeywordOnlyWithFilledPositional() {
    final Map<String, PsiElement> test = loadTest(4);

    feignCtrlP(test.get("<arg1>").getTextOffset()).check("*, kw1, kw2", ArrayUtilRt.EMPTY_STRING_ARRAY);
    feignCtrlP(test.get("<arg2>").getTextOffset()).check("*, kw1, kw2", ArrayUtilRt.EMPTY_STRING_ARRAY);

    feignCtrlP(test.get("<arg3>").getTextOffset()).check("*, kw1, kw2", new String[]{"kw1, "});
    feignCtrlP(test.get("<arg4>").getTextOffset()).check("*, kw1, kw2", new String[]{"kw2"});
  }

  // PY-28127 PY-31424
  public void testInitializingTypeVar() {
    final int offset = loadTest(1).get("<arg1>").getTextOffset();

    feignCtrlP(offset).check(Arrays.asList("cls: type[TypeVar], name: str, *constraints, bound: Any | None = None, contravariant: bool = False, covariant: bool = False, infer_variance: bool = False, default=..."),
                             Arrays.asList(new String[]{"name: str, "}, new String[]{"name: str, "}),
                             Arrays.asList(new String[]{"cls: type[TypeVar], "}, new String[]{"cls: type[TypeVar], "}));
  }

  // PY-36008
  public void testInitializingTypedDictBasedType() {
    final Map<String, PsiElement> test = loadTest(2);

    feignCtrlP(test.get("<arg1>").getTextOffset()).check("*, name: str, year: int",
                                                         ArrayUtilRt.EMPTY_STRING_ARRAY,
                                                         ArrayUtilRt.EMPTY_STRING_ARRAY);
    feignCtrlP(test.get("<arg2>").getTextOffset()).check("<no parameters>",
                                                         ArrayUtilRt.EMPTY_STRING_ARRAY,
                                                         new String[]{"<no parameters>"});
  }

  // PY-36008
  public void testInitializingTypedDictBasedTypeWithTotal() {
    final Map<String, PsiElement> test = loadTest(2);

    feignCtrlP(test.get("<arg1>").getTextOffset()).check("*, name: str = ..., year: int = ...",
                                                         ArrayUtilRt.EMPTY_STRING_ARRAY,
                                                         ArrayUtilRt.EMPTY_STRING_ARRAY);
    feignCtrlP(test.get("<arg2>").getTextOffset()).check("*, name: str, year: int",
                                                         ArrayUtilRt.EMPTY_STRING_ARRAY,
                                                         ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  // PY-36008
  public void testInitializingInheritedTypedDictType() {
    final Map<String, PsiElement> test = loadTest(2);

    feignCtrlP(test.get("<arg1>").getTextOffset()).check("*, name: str, year: int, based_on: str = ...",
                                                         ArrayUtilRt.EMPTY_STRING_ARRAY,
                                                         ArrayUtilRt.EMPTY_STRING_ARRAY);
    feignCtrlP(test.get("<arg2>").getTextOffset()).check("*, name: str, year: int, based_on: str = ..., rating: float",
                                                         ArrayUtilRt.EMPTY_STRING_ARRAY,
                                                         ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  // PY-36008
  public void testDefiningTypedDictTypeAlternativeSyntax() {
    final Map<String, PsiElement> test = loadTest(1);

    feignCtrlP(test.get("<arg1>").getTextOffset()).check("typename: str, fields: dict[str, type], *, /, total: bool = True",
                                                         new String[]{"typename: str, "},
                                                         ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  // PY-36008
  public void testTypedDictGet() {
    final Map<String, PsiElement> test = loadTest(1);

    feignCtrlP(test.get("<arg1>").getTextOffset()).check("key: str, default=None",
                                                         new String[]{"key: str, "},
                                                         ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  // PY-42205
  public void testNonReferenceCallee() {
    final Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self: CallableTest, arg=None",
                                                          new String[]{"arg=None"},
                                                          new String[]{"self: CallableTest, "});
  }

  // PY-53611
  public void testTypedDictWithRequiredAndNotRequiredKeys() {
    final Map<String, PsiElement> test = loadTest(2);

    feignCtrlP(test.get("<arg1>").getTextOffset()).check("*, x: int, y: int = ...",
                                                         new String[]{"x: int, "},
                                                         ArrayUtilRt.EMPTY_STRING_ARRAY);
    feignCtrlP(test.get("<arg2>").getTextOffset()).check("*, x: int, y: int = ...",
                                                         new String[]{"x: int, "},
                                                         ArrayUtilRt.EMPTY_STRING_ARRAY);
  }

  // PY-48338
  public void testNotAnnotatedDecoratorPreservesParametersOfOriginalFunction() {
    final Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("input_a: int, input_b: float",
                                                          new String[]{"input_a: int, "},
                                                          new String[]{""});
  }

  // PY-48338
  public void testNotAnnotatedDecoratorRetainsParametersOfOriginalFunctionEvenIfItChangesItsSignature() {
    final Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("input_a: int, input_b: float",
                                                          new String[]{"input_a: int, "},
                                                          new String[]{""});
  }

  // PY-48338
  public void testAnnotatedDecoratorPreservesParametersOfOriginalFunctionWithParamSpec() {
    final Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("input_a: int, input_b: float",
                                                          new String[]{"input_a: int, "},
                                                          new String[]{""});
  }

  // PY-48338
  public void testAnnotatedDecoratorAddsParametersToOriginalFunctionWithConcatenate() {
    final Map<String, PsiElement> marks = loadTest(1);
    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("...: str, input_a: int, input_b: float",
                                                          new String[]{"...: str, "},
                                                          new String[]{""});
  }

  // TODO add a test on annotated
  // PY-48338
  public void testDecoratedDataClassParameters() {
    final Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("bar: int",
                                                          new String[]{"bar: int"},
                                                          new String[]{""});
  }

  // PY-48338
  public void testAnnotatedDecoratorReplacesParametersOfOriginalFunction() {
    final Map<String, PsiElement> marks = loadTest(1);
    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("...: int",
                                                          new String[]{"...: int"},
                                                          new String[]{""});
  }

  // PY-46053
  public void testLongTypeHintReplaceWithAnnotation() {
    final Map<String, PsiElement> test = loadTest(2);
    // Long non-current type hints should be replaced with shorter annotation
    feignCtrlP(test.get("<arg1>").getTextOffset()).check(
      "file: str | bytes | PathLike[str] | PathLike[bytes] | int, mode: OpenTextMode, buffering: int = ...",
      new String[]{"file: str | bytes | PathLike[str] | PathLike[bytes] | int, "});
    feignCtrlP(test.get("<arg2>").getTextOffset()).check(
      "file: _OpenFile, mode: Literal[\"w\", \"wt\", \"tw\", \"a\", \"at\", \"ta\", \"x\", \"xt\", \"tx\", \"r\", \"rt\", \"tr\", " +
      "\"U\"] = ..., buffering: int = ...",
      new String[]{
        "mode: Literal[\"w\", \"wt\", \"tw\", \"a\", \"at\", \"ta\", \"x\", \"xt\", \"tx\", \"r\", \"rt\", \"tr\", \"U\"] = ..., "});
  }

  // PY-46053
  public void testLongTypeHintWithoutAnnotation() {
    final Map<String, PsiElement> test = loadTest(2);
    // Long non-current type hints without annotation should stay without changes
    feignCtrlP(test.get("<arg1>").getTextOffset()).check(
      "parameter: MyClassWithVeryVeryVeryLongName | MyClassWithVeryVeryVeryLongNameNumberTwo | int, short_param: str",
      new String[]{"short_param: str"});
    feignCtrlP(test.get("<arg2>").getTextOffset()).check(
      "p, u: Literal[\"A\", \"B\", \"C\", \"D\", \"E\", \"F\", \"G\", \"H\", \"I\", \"J\", \"K\", \"L\", \"M\", \"N\", \"O\", " +
      "\"P\", \"Q\", \"R\", \"S\", \"T\", \"U\", \"V\", \"W\", \"X\", \"Y\", \"Z\"], l: Lower"
      , new String[]{
        "u: Literal[\"A\", \"B\", \"C\", \"D\", \"E\", \"F\", \"G\", \"H\", \"I\", \"J\", \"K\", \"L\", \"M\", \"N\", \"O\", \"P\", " +
        "\"Q\", \"R\", \"S\", \"T\", \"U\", \"V\", \"W\", \"X\", \"Y\", \"Z\"], "});
  }

  // PY-46053
  public void testLongStarSlashParameter() {
    final Map<String, PsiElement> test = loadTest(4);
    feignCtrlP(test.get("<arg1>").getTextOffset()).check(
      "a, /, b: Upper, *, c: Upper",
      new String[]{""});

    feignCtrlP(test.get("<arg2>").getTextOffset()).check(
      "a, /, b: Literal[\"A\", \"B\", \"C\", \"D\", \"E\", \"F\", \"G\", \"H\", \"I\", \"J\", \"K\", \"L\", \"M\", \"N\", \"O\", " +
      "\"P\", \"Q\", \"R\", \"S\", \"T\", \"U\", \"V\", \"W\", \"X\", \"Y\", \"Z\"], *, c: Upper"
      , new String[]{
        "b: Literal[\"A\", \"B\", \"C\", \"D\", \"E\", \"F\", \"G\", \"H\", \"I\", \"J\", \"K\", \"L\", \"M\", \"N\", \"O\", \"P\", " +
        "\"Q\", \"R\", \"S\", \"T\", \"U\", \"V\", \"W\", \"X\", \"Y\", \"Z\"], "});

    feignCtrlP(test.get("<arg3>").getTextOffset()).check(
      "a, /, b: Upper, *, c: Upper"
      , new String[]{""});

    feignCtrlP(test.get("<arg4>").getTextOffset()).check(
      "a, /, b: Upper, *, c: Literal[\"A\", \"B\", \"C\", \"D\", \"E\", \"F\", \"G\", \"H\", \"I\", \"J\", \"K\", \"L\", \"M\", " +
      "\"N\", \"O\", \"P\", \"Q\", \"R\", \"S\", \"T\", \"U\", \"V\", \"W\", \"X\", \"Y\", \"Z\"]"
      , new String[]{
        "c: Literal[\"A\", \"B\", \"C\", \"D\", \"E\", \"F\", \"G\", \"H\", \"I\", \"J\", \"K\", \"L\", \"M\", \"N\", \"O\", \"P\", " +
        "\"Q\", \"R\", \"S\", \"T\", \"U\", \"V\", \"W\", \"X\", \"Y\", \"Z\"]"});
  }

  // PY-46053
  public void testLongTypeHintMultiline() {
    final Map<String, PsiElement> test = loadTest(2);
    feignCtrlP(test.get("<arg1>").getTextOffset()).check(
      "parameter: Literal[\"A\", \"B\", \"C\", \"D\", \"E\", \"F\", \"G\", \"H\", \"I\", \"J\", \"K\", \"L\", \"M\", \"N\", \"O\", " +
      "\"P\", \"Q\", \"R\", \"S\", \"T\", \"U\", \"V\", \"W\", \"X\", \"Y\", \"Z\"] | MyClassWithVeryVeryVeryLongName | int, " +
      "short_param: str"
      , new String[]{
        "parameter: Literal[\"A\", \"B\", \"C\", \"D\", \"E\", \"F\", \"G\", \"H\", \"I\", \"J\", \"K\", \"L\", \"M\", \"N\", \"O\", " +
        "\"P\", \"Q\", \"R\", \"S\", \"T\", \"U\", \"V\", \"W\", \"X\", \"Y\", \"Z\"] | MyClassWithVeryVeryVeryLongName | int, "});

    feignCtrlP(test.get("<arg2>").getTextOffset()).check(
      "parameter: MyClassWithVeryVeryVeryLongName | Upper | int, short_param: str"
      , new String[]{"short_param: str"});
  }

  // PY-49946
  public void testInitializingDataclassKwOnlyOnClass() {
    final Map<String, PsiElement> marks = loadTest(4);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("b: int, *, a: int", new String[]{"b: int, "});
    // non-working case PY-39461
    //feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a: int, *, b: int", new String[]{"a: int, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("*, a: int, b: int", new String[]{"*, a: int"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("*, a: int", new String[]{"*, a: int"});
  }

  // PY-49946
  public void testInitializingDataclassKwOnlyOnField() {
    final Map<String, PsiElement> marks = loadTest(5);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("b: int, *, a: int", new String[]{"b: int, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a: int, *, b: int", new String[]{"a: int, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("*, a: int, b: int", new String[]{"*, a: int"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("*, a: int", new String[]{"*, a: int"});
    feignCtrlP(marks.get("<arg5>").getTextOffset()).check("a: int, *, b: int", new String[]{"a: int, "});
  }

  // PY-53693
  public void testInitializingDataclassKwOnlyAttribute() {
    final Map<String, PsiElement> marks = loadTest(6);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a: int, *, b: int", new String[]{"a: int, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a: int, b: int", new String[]{"a: int, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a: int, qq: int, *, b: int", new String[]{"a: int, "});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("a: int, c: int, *, b: int, d: int", new String[]{"a: int, "});
    feignCtrlP(marks.get("<arg5>").getTextOffset()).check("a: int, *, b: int, qq: int", new String[]{"a: int, "});
    feignCtrlP(marks.get("<arg6>").getTextOffset()).check("a: str", new String[]{"a: str"});
  }

  // PY-54560
  public void testInitializingDataclassTransformFieldSpecifierKwOnlyArgumentDecoratorApiFunctionSpecifiers() {
    final Map<String, PsiElement> marks = loadTest(5);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("not_kw_only_spec_default: int, not_kw_only_spec_arg: int, *, kw_only_inferred: int, kw_only_spec_default: int, kw_only_spec_arg: int", new String[]{"not_kw_only_spec_default: int, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("not_kw_only_spec_default: int, not_kw_only_spec_arg: int, *, kw_only_inferred: int, kw_only_spec_default: int, kw_only_spec_arg: int", new String[]{"not_kw_only_spec_default: int, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("not_kw_only_spec_default: int, not_kw_only_spec_arg: int, not_kw_only_inferred: int, *, kw_only_spec_default: int, kw_only_spec_arg: int", new String[]{"not_kw_only_spec_default: int, "});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("not_kw_only_spec_default: int, not_kw_only_spec_arg: int, not_kw_only_inferred: int, *, kw_only_spec_default: int, kw_only_spec_arg: int", new String[]{"not_kw_only_spec_default: int, "});
    feignCtrlP(marks.get("<arg5>").getTextOffset()).check("not_kw_only_spec_default: int, not_kw_only_spec_arg: int, not_kw_only_inferred: int, *, kw_only_spec_default: int, kw_only_spec_arg: int", new String[]{"not_kw_only_spec_default: int, "});
  }

  // PY-54560
  public void testInitializingDataclassTransformFieldSpecifierKwOnlyArgumentBaseClassApiClassSpecifiers() {
    final Map<String, PsiElement> marks = loadTest(5);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("not_kw_only_spec_default: int, not_kw_only_spec_arg: int, *, kw_only_inferred: int, kw_only_spec_default: int, kw_only_spec_arg: int", new String[]{"not_kw_only_spec_default: int, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("not_kw_only_spec_default: int, not_kw_only_spec_arg: int, *, kw_only_inferred: int, kw_only_spec_default: int, kw_only_spec_arg: int", new String[]{"not_kw_only_spec_default: int, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("not_kw_only_spec_default: int, not_kw_only_spec_arg: int, not_kw_only_inferred: int, *, kw_only_spec_default: int, kw_only_spec_arg: int", new String[]{"not_kw_only_spec_default: int, "});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("not_kw_only_spec_default: int, not_kw_only_spec_arg: int, not_kw_only_inferred: int, *, kw_only_spec_default: int, kw_only_spec_arg: int", new String[]{"not_kw_only_spec_default: int, "});
    feignCtrlP(marks.get("<arg5>").getTextOffset()).check("not_kw_only_spec_default: int, not_kw_only_spec_arg: int, not_kw_only_inferred: int, *, kw_only_spec_default: int, kw_only_spec_arg: int", new String[]{"not_kw_only_spec_default: int, "});
  }

  // PY-54560
  public void testInitializingDataclassTransformFieldSpecifierInitArgument() {
    final Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("init_spec_param: int, init_inferred: int", new String[]{"init_spec_param: int, "});
  }

  // PY-54560
  public void testInitializingDataclassTransformDistinguishingFieldSpecifierFromDefaults() {
    final Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("field1: int, field2: int = ..., field3: int = not_field(), field4: int = not_field(default=42)", new String[]{"field1: int, "});
  }

  // PY-54560
  public void testInitializingDataclassTransformOverridingAncestorFieldType() {
    final Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("super_attr: str, sub_attr: int", new String[]{"super_attr: str, "});
  }

  // PY-54560
  public void testInitializingDataclassTransformFieldAlias() {
    final Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("foo: int, bar: int", new String[]{"foo: int, "});
  }

  // PY-49946
  public void testInitializingDataclassKwOnlyOnClassOverridingHierarchy() {
    final Map<String, PsiElement> marks = loadTest(3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a: int", new String[]{"a: int"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("*, a: int", new String[]{"*, a: int"});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("*, a: int", new String[]{"*, a: int"});
  }

  // PY-61139
  public void testDoNotInferLiteralStringForParametersWithStrLiteralDefaultValue() {
    final Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("s: str = 'foo'", new String[]{"s: str = 'foo'"});
  }

  // PY-55044
  public void testTypedDictKwdFunction() {
    final Map<String, PsiElement> marks = loadTest(3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a: int, *, name: str, year: int", new String[]{"a: int, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a: int, *, name: str, year: int", new String[]{"name: str, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a: int, *, name: str, year: int", new String[]{"year: int"});
  }

  // PY-55044
  public void testTypedDictWithRequiredKeyKwdFunction() {
    final Map<String, PsiElement> marks = loadTest(3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a: int, *, name: str = ..., year: int", new String[]{"a: int, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a: int, *, name: str = ..., year: int", new String[]{"name: str = ..., "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a: int, *, name: str = ..., year: int", new String[]{"year: int"});
  }

  // PY-55044
  public void testTypedDictWithNotRequiredKeyKwdFunction() {
    final Map<String, PsiElement> marks = loadTest(3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a: int, *, name: str = ..., year: int", new String[]{"a: int, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a: int, *, name: str = ..., year: int", new String[]{"name: str = ..., "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a: int, *, name: str = ..., year: int", new String[]{"year: int"});
  }

  // PY-23067
  public void testFunctoolsWraps() {
    final Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self: MyClass, s: str, b: bool", new String[]{"s: str, "}, new String[]{"self: MyClass, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self: MyClass, s: str, b: bool", new String[]{"b: bool"}, new String[]{"self: MyClass, "});
  }

  // PY-58497
  public void testSimplePopupWithHintsOff() {
    Map<String, PsiElement> marks = loadTest(5);
    feignCtrlPWithHintsForHighlightedOnly(marks.get("<arg1>").getTextOffset()).check("a: int, b, c, d, e", new String[]{"a: int, "});
    feignCtrlPWithHintsForHighlightedOnly(marks.get("<arg2>").getTextOffset()).check("a, b: str, c, d, e", new String[]{"b: str, "});
    feignCtrlPWithHintsForHighlightedOnly(marks.get("<arg3>").getTextOffset()).check("a, b, c: bool, d, e", new String[]{"c: bool, "});
    feignCtrlPWithHintsForHighlightedOnly(marks.get("<arg4>").getTextOffset()).check("a, b, c, d: list, e", new String[]{"d: list, "});
    feignCtrlPWithHintsForHighlightedOnly(marks.get("<arg5>").getTextOffset()).check("a, b, c, d, e: set", new String[]{"e: set"});
  }

  // PY-58497
  public void testSimplePopupWithHintsOffAndDefaultArgument() {
    Map<String, PsiElement> marks = loadTest(1);
    feignCtrlPWithHintsForHighlightedOnly(marks.get("<arg1>").getTextOffset()).check("a, b, c: str = \"default\"", new String[]{"c: str = \"default\""});
  }

  // PY-76149
  public void testDataclassTransformConstructorSignatureWithFieldsAnnotatedWithGenericDescriptor() {
    final Map<String, PsiElement> marks = loadTest(1);
    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("id: int, name: str, year: int, new: bool", new String[]{"id: int, "});
  }

  // PY-78250
  public void testInitializingGenericDataclassWithDefaultType() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> {
      final Map<String, PsiElement> marks = loadTest(1);
      feignCtrlP(marks.get("<arg1>").getTextOffset()).check("x: T", new String[]{"x: T"});
    });
  }

  // PY-78250
  public void testInitializingGenericDataclass() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> {
      final Map<String, PsiElement> marks = loadTest(2);
      feignCtrlP(marks.get("<arg1>").getTextOffset()).check("x: T", new String[]{"x: T"});
      feignCtrlP(marks.get("<arg2>").getTextOffset()).check("x: T", new String[]{"x: T"});
    });
  }

  @NotNull
  private Collector feignCtrlP(int offset) {
    return feignCtrlP(offset, myFixture.getFile(), true, myFixture.getEditor());
  }

  @NotNull
  private Collector feignCtrlPWithHintsForHighlightedOnly(int offset) {
    return feignCtrlP(offset, myFixture.getFile(), false, myFixture.getEditor());
  }

  /**
   * Imitates pressing of Ctrl+P; fails if results are not as expected.
   *
   * @param offset offset of 'cursor' where Ctrl+P is pressed.
   * @return a {@link Collector} with collected hint info.
   */
  @NotNull
  private static Collector feignCtrlP(int offset, @NotNull PsiFile file, boolean showAllHints, Editor editor) {
    boolean oldKeyValue = Registry.is("python.parameter.info.show.all.hints");
    try {
      Registry.get("python.parameter.info.show.all.hints").setValue(showAllHints);
      final PyParameterInfoHandler handler = new PyParameterInfoHandler();
      final Collector collector = new Collector(file, offset, editor);
      collector.setParameterOwner(handler.findElementForParameterInfo(collector));

      if (collector.getParameterOwner() != null) {
        handler.updateParameterInfo((PyArgumentList)collector.getParameterOwner(), collector);

        for (Object itemToShow : collector.getItemsToShow()) {
          PyParameterInfoUtils.CallInfo callInfo = (PyParameterInfoUtils.CallInfo)itemToShow;
          //noinspection unchecked
          handler.updateUI(callInfo, collector);
        }
      }
      return collector;
    }
    finally {
      Registry.get("python.parameter.info.show.all.hints").setValue(oldKeyValue);
    }
  }

  public static void checkParameters(int offset, @NotNull PsiFile file, @NotNull String text, String @NotNull [] highlighted, Editor editor) {
    Collector collector = feignCtrlP(offset, file, true, editor);
    collector.check(text, highlighted);
  }

  /**
   * Imitates the normal UI contexts to the extent we use it. Collects highlighting.
   */
  private static final class Collector implements ParameterInfoUIContextEx, CreateParameterInfoContext, UpdateParameterInfoContext {

    @NotNull
    private final PsiFile myFile;
    private final int myOffset;

    @NotNull
    private final List<String[]> myListOfTexts;

    @NotNull
    private final List<EnumSet<Flag>[]> myListOfFlags;

    @Nullable
    private PyArgumentList myParameterOwner;

    private Object @NotNull [] myItemsToShow;

    private int myIndex;

    private final Editor myEditor;

    private Collector(@NotNull PsiFile file, int offset, Editor editor) {
      myFile = file;
      myOffset = offset;
      myEditor = editor;
      myListOfTexts = new ArrayList<>();
      myListOfFlags = new ArrayList<>();
      myItemsToShow = ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    @Override
    @NotNull
    public String setupUIComponentPresentation(String @NotNull [] texts, EnumSet<Flag> @NotNull [] flags, @NotNull Color background) {
      assertEquals(texts.length, flags.length);
      myListOfTexts.add(texts);
      myListOfFlags.add(flags);
      return StringUtil.join(texts, "");
    }

    @Override
    public void setEscapeFunction(@Nullable Function<? super String, String> escapeFunction) {
    }

    @Override
    public String setupUIComponentPresentation(String text, int highlightStartOffset, int highlightEndOffset, boolean isDisabled,
                                               boolean strikeout, boolean isDisabledBeforeHighlight, Color background) {
      // nothing, we don't use it
      return text;
    }

    @Override
    public void setupRawUIComponentPresentation(String htmlText) {
      throw new UnsupportedOperationException();
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
    public boolean isSingleOverload() {
      return myItemsToShow.length == 1;
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
      return Color.BLACK;
    }

    @Override
    public Object @NotNull [] getItemsToShow() {
      return myItemsToShow;
    }

    @Override
    public void setItemsToShow(Object @NotNull [] items) {
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
    public UserDataHolderEx getCustomContext() {
      throw new UnsupportedOperationException();
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
      return myEditor;
    }

    private void check(@NotNull String text, String @NotNull [] highlighted) {
      check(text, highlighted, ArrayUtilRt.EMPTY_STRING_ARRAY);
    }

    private void check(@NotNull String text, String @NotNull [] highlighted, String @NotNull [] disabled) {
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
    private void check(@NotNull String text, String @NotNull [] highlighted, String @NotNull [] disabled, int index) {
      final String[] hintText = myListOfTexts.get(index);
      final EnumSet<Flag>[] hintFlags = myListOfFlags.get(index);

      assertEquals("Signature", text, StringUtil.join(hintText, ""));

      final StringBuilder wrongs = new StringBuilder();

      // see if highlighted matches
      final Set<String> highlightSet = Set.of(highlighted);
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
      final Set<String> disabledSet = Set.of(disabled);
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
