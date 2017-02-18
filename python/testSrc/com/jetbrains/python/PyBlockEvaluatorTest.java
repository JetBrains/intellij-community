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

import com.intellij.psi.PsiFileFactory;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.impl.blockEvaluator.PyBlockEvaluator;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class PyBlockEvaluatorTest extends PyTestCase {
  public void testSimple() {
    PyBlockEvaluator eval = doEvaluate("a='b'");
    assertEquals("b", eval.getValue("a"));
  }

  public void testAugAssign() {
    PyBlockEvaluator eval = doEvaluate("a='b'\na+='c'");
    assertEquals("bc", eval.getValue("a"));
  }

  public void testExtend() {
    PyBlockEvaluator eval = doEvaluate("a=['b']\na.extend(['c'])");
    List<String> list = eval.getValueAsStringList("a");
    assertEquals(2, list.size());
    assertEquals("b", list.get(0));
    assertEquals("c", list.get(1));
  }

  public void testVar() {
    PyBlockEvaluator eval = doEvaluate("a='b'\nc='d'\ne=a+c");
    assertEquals("bd", eval.getValue("e"));
  }

  public void testMixedList() {
    PyBlockEvaluator eval = doEvaluate("a=['b',['c','d']]");
    List list = (List)eval.getValue("a");
    assertEquals(2, list.size());
    assertEquals("b", list.get(0));
    assertEquals(new ArrayList<>(Arrays.asList("c", "d")), list.get(1));
  }

  public void testDict() {
    PyBlockEvaluator eval = doEvaluate("a={'b': 'c'}");
    Map map = (Map)eval.getValue("a");
    assertEquals(1, map.size());
    assertEquals("c", map.get("b"));
  }

  public void testDictNoEvaluate() {
    PyBlockEvaluator eval = doEvaluate("a={'b': 'c'}", true);
    Map map = (Map)eval.getValue("a");
    assertEquals(1, map.size());
    assertTrue(map.get("b") instanceof PyStringLiteralExpression);
  }

  public void testDictAssign() {
    PyBlockEvaluator eval = doEvaluate("a={}\na['b']='c'");
    Map map = (Map)eval.getValue("a");
    assertEquals(1, map.size());
    assertEquals("c", map.get("b"));
  }

  public void testDictAssignNoEvaluate() {
    PyBlockEvaluator eval = doEvaluate("a={}\na['b']='c'", true);
    Map map = (Map)eval.getValue("a");
    assertEquals(1, map.size());
    assertTrue(map.get("b") instanceof PyStringLiteralExpression);
  }

  public void testDictUpdate() {
    PyBlockEvaluator eval = doEvaluate("a={}\na.update({'b': 'c'})");
    Map map = (Map)eval.getValue("a");
    assertEquals(1, map.size());
    assertEquals("c", map.get("b"));
  }

  /**
   * Ensures module has any vars imported from external modules
   */
  public void testImport() {
    myFixture.copyDirectoryToProject("blockEvaluator", "");
    final PyFile file = PyUtil.as(myFixture.configureByFile("my_module.py"), PyFile.class);
    assert file != null : "Failed to read file";
    final PyBlockEvaluator sut = new PyBlockEvaluator();
    sut.evaluate(file);

    Assert.assertEquals("Failed to read var from package module", "foo", sut.getValueAsString("VARIABLE_IN_PACKAGE_MODULE"));
    Assert.assertEquals("Failed to read var from package", "foo", sut.getValueAsString("VARIABLE_IN_PACKAGE"));
    Assert.assertEquals("Failed to read list from another module", Arrays.asList("a", "b", "c", "d"), sut.getValueAsList("SOME_LIST"));
    Assert.assertEquals("Failed to read var from another module", "42", sut.getValueAsString("SOME_VARIABLE"));
    Assert.assertEquals("Failed to read var from another module with alias", "foo", sut.getValueAsString("MY_RENAMED_VAR"));
  }

  public void testFunction() {
    PyBlockEvaluator eval = new PyBlockEvaluator();
    PyFile file = (PyFile)PsiFileFactory.getInstance(myFixture.getProject())
      .createFileFromText("a.py", PythonFileType.INSTANCE, "def foo(): return 'a'");
    PyFunction foo = file.findTopLevelFunction("foo");
    eval.evaluate(foo);
    assertEquals("a", eval.getReturnValue());
  }

  private PyBlockEvaluator doEvaluate(String text) {
    return doEvaluate(text, false);
  }

  private PyBlockEvaluator doEvaluate(String text, boolean skipEvaluatingCollectionItems) {
    PyBlockEvaluator eval = new PyBlockEvaluator();
    if (skipEvaluatingCollectionItems) {
      eval.setEvaluateCollectionItems(false);
    }
    PyFile file = (PyFile)PsiFileFactory.getInstance(myFixture.getProject()).createFileFromText("a.py", PythonFileType.INSTANCE, text);
    eval.evaluate(file);
    return eval;
  }
}
