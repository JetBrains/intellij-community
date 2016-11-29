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

import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContextEx;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Tests parameter info available via ^P at call sites.
 * <br/>User: dcheryasov
 * Date: Jul 14, 2009 3:42:44 AM
 */
public class PyParameterInfoTest extends LightMarkedTestCase {
  protected Map<String, PsiElement> loadTest() {
    String fname = "/paramInfo/" + getTestName(false) + ".py";
    return configureByFile(fname);
  }

  protected Map<String, PsiElement> loadTest(int expected_marks) {
    Map<String, PsiElement> marks = loadTest();
    Assert.assertEquals("Test data sanity", marks.size(), expected_marks);
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
    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self, **kw", new String[]{"**kw"}, new String[]{"self, "});
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

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self, a", new String[]{"a"}, new String[]{"self, "});
  }

  public void testSimpleClassFunction() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self, a", new String[]{"self, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self, a", new String[]{"a"});
  }

  public void testReassignedFunction() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b", new String[]{"a, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a, b", new String[]{"b"});
  }

  public void testReassignedInstanceMethod() {
    Map<String, PsiElement> marks = loadTest(3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self, a, b, c", new String[]{"a, "}, new String[]{"self, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self, a, b, c", new String[]{"b, "}, new String[]{"self, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("self, a, b, c", new String[]{"c"}, new String[]{"self, "});
  }

  public void testReassignedClassInit() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self, a, b", new String[]{"a, "}, new String[]{"self, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self, a, b", new String[]{"b"}, new String[]{"self, "});
  }

  public void testInheritedClassInit() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self, a, b", new String[]{"a, "}, new String[]{"self, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self, a, b", new String[]{"b"}, new String[]{"self, "});
  }

  public void testRedefinedNewConstructorCall() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("cls, a, b", new String[]{"a, "}, new String[]{"cls, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("cls, a, b", new String[]{"b"}, new String[]{"cls, "});
  }

  public void testRedefinedNewDirectCall() {
    Map<String, PsiElement> marks = loadTest(3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("cls, a, b", new String[]{"cls, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("cls, a, b", new String[]{"a, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("cls, a, b", new String[]{"b"});
  }

  public void testIgnoreNewInOldStyleClass() {
    Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self, one", new String[]{"one"}, new String[]{"self, "});
  }


  public void testBoundMethodSimple() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self, a, b", new String[]{"a, "}, new String[]{"self, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self, a, b", new String[]{"b"}, new String[]{"self, "});
  }

  public void testBoundMethodReassigned() {
    Map<String, PsiElement> marks = loadTest(2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self, a, b", new String[]{"a, "}, new String[]{"self, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self, a, b", new String[]{"b"}, new String[]{"self, "});
  }

  public void testConstructorFactory() {
    Map<String, PsiElement> marks = loadTest(1);

    feignCtrlP(marks.get("<arg>").getTextOffset()).check("self, color", new String[]{"color"}, new String[]{"self, "});
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

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b, c=1, d=2, e=3", new String[]{"d=2, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a, b, c=1, d=2, e=3", new String[]{"e=3"}); // no logical next
  }

  public void testPy3kPastTupleArg() {
    Map<String, PsiElement> marks = loadTest(4);
    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("*arg, a=1, b=2", new String[]{"*arg, "});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("*arg, a=1, b=2", new String[]{"*arg, "});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("*arg, a=1, b=2", new String[]{"b=2"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("*arg, a=1, b=2", new String[]{"a=1, "});
  }

  public void testNoArgs() {
    Map<String, PsiElement> marks = loadTest(1);
    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a, b, c", new String[]{"a, "});
  }

  public void testNoArgsException() {
    Map<String, PsiElement> marks = loadTest(1);
    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("<no parameters>", new String[0], new String[]{"<no parameters>"});
  }

  public void testMultilineStringDefault() {
    Map<String, PsiElement> marks = loadTest(1);
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("length=12, allowed_chars='abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'",
                                                          new String[]{"allowed_chars='abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'"},
                                                          new String[0]);
  }

  /**
   * Imitates pressing of Ctrl+P; fails if results are not as expected.
   * @param offset offset of 'cursor' where ^P is pressed.
   * @return a {@link Collector} with collected hint info.
   * @throws Exception if it fails
   */
  private Collector feignCtrlP(int offset) {
    Collector collector = new Collector(myFixture.getProject(), myFixture.getFile(), offset);
    PyParameterInfoHandler handler = new PyParameterInfoHandler();
    final PyArgumentList parameterOwner = handler.findElementForParameterInfo(collector);
    collector.setParameterOwner(parameterOwner); // finds arglist, sets items to show
    if (collector.getParameterOwner() != null) {
      Assert.assertEquals("Collected one analysis result", 1, collector.myItems.length);
      handler.updateParameterInfo((PyArgumentList)collector.getParameterOwner(), collector); // moves offset to correct parameter
      handler.updateUI((PyCallExpression.PyArgumentsMapping)collector.getItemsToShow()[0], collector); // sets hint text and flags
    }
    return collector;
  }

  /**
   * Imitates the normal UI contexts to the extent we use it. Collects highlighting.
   */
  private static class Collector implements ParameterInfoUIContextEx, CreateParameterInfoContext, UpdateParameterInfoContext {

    private final PsiFile myFile;
    private final int myOffset;
    private int myIndex;
    private Object[] myItems;
    private final Project myProject;
    private final Editor myEditor;
    private PyArgumentList myParamOwner;
    private String[] myTexts;
    private EnumSet<Flag>[] myFlags;

    private Collector(Project project, PsiFile file, int offset) {
      myProject = project;
      myEditor = null;
      myFile = file;
      myOffset = offset;
    }

    @Override
    public String setupUIComponentPresentation(String[] texts, EnumSet<Flag>[] flags, Color background) {
      assert texts.length == flags.length;
      myTexts = texts;
      myFlags = flags;
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
    public void setUIComponentEnabled(int index, boolean b) { }

    @Override
    public int getCurrentParameterIndex() {
      return myIndex;
    }

    @Override
    public void removeHint() { }

    @Override
    public void setParameterOwner(PsiElement o) {
      Assert.assertTrue("Found element is a python arglist", o == null || o instanceof PyArgumentList);
      myParamOwner = (PyArgumentList)o;
    }

    @Override
    public PsiElement getParameterOwner() {
      return myParamOwner;
    }

    @Override
    public void setHighlightedParameter(Object parameter) {
      // nothing, we don't use it
    }

    @Override
    public void setCurrentParameter(int index) {
      myIndex = index;
    }

    @Override
    public Color getDefaultParameterColor() {
      return java.awt.Color.BLACK;
    }

    @Override
    public Object[] getItemsToShow() {
      return myItems;
    }

    @Override
    public void setItemsToShow(Object[] items) {
      myItems = items;
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
    public PsiElement getHighlightedElement() {
      return null;  // we don't use it
    }

    @Override
    public void setHighlightedElement(PsiElement elements) {
      // nothing, we don't use it
    }

    @Override
    public Project getProject() {
      return myProject;
    }

    @Override
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

    /**
     * Checks if hint data look as expected.
     * @param text expected text of the hint, without formatting
     * @param highlighted expected highlighted substrings of hint
     * @param disabled expected disabled substrings of hint
     */
    public void check(String text, String[] highlighted, String[] disabled) {
      Assert.assertEquals("Signature", text, StringUtil.join(myTexts, ""));
      StringBuilder wrongs = new StringBuilder();
      // see if highlighted matches
      Set<String> highlightSet = new HashSet<>();
      ContainerUtil.addAll(highlightSet, highlighted);
      for (int i = 0; i < myTexts.length; i += 1) {
        if (myFlags[i].contains(Flag.HIGHLIGHT) && !highlightSet.contains(myTexts[i])) {
          wrongs.append("Highlighted unexpected '").append(myTexts[i]).append("'. ");
        }
      }
      for (int i = 0; i < myTexts.length; i += 1) {
        if (!myFlags[i].contains(Flag.HIGHLIGHT) && highlightSet.contains(myTexts[i])) {
          wrongs.append("Not highlighted expected '").append(myTexts[i]).append("'. ");
        }
      }
      // see if disabled matches
      Set<String> disabledSet = new HashSet<>();
      ContainerUtil.addAll(disabledSet, disabled);
      for (int i = 0; i < myTexts.length; i += 1) {
        if (myFlags[i].contains(Flag.DISABLE) && !disabledSet.contains(myTexts[i])) {
          wrongs.append("Highlighted a disabled '").append(myTexts[i]).append("'. ");
        }
      }
      for (int i = 0; i < myTexts.length; i += 1) {
        if (!myFlags[i].contains(Flag.DISABLE) && disabledSet.contains(myTexts[i])) {
          wrongs.append("Not disabled expected '").append(myTexts[i]).append("'. ");
        }
      }
      //
      if (wrongs.length() > 0) Assert.fail(wrongs.toString());
    }

    public void check(String text, String[] highlighted) {
      check(text, highlighted, ArrayUtil.EMPTY_STRING_ARRAY);
    }

    public void assertNotFound() {
      Assert.assertNull(myParamOwner);
    }
  }
}
