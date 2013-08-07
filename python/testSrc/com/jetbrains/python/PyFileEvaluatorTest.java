package com.jetbrains.python;

import com.intellij.psi.PsiFileFactory;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyFileEvaluator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class PyFileEvaluatorTest extends PyTestCase {
  public void testSimple() {
    PyFileEvaluator eval = doEvaluate("a='b'");
    assertEquals("b", eval.getValue("a"));
  }

  public void testAugAssign() {
    PyFileEvaluator eval = doEvaluate("a='b'\na+='c'");
    assertEquals("bc", eval.getValue("a"));
  }

  public void testExtend() {
    PyFileEvaluator eval = doEvaluate("a=['b']\na.extend(['c'])");
    List<String> list = eval.getValueAsStringList("a");
    assertEquals(2, list.size());
    assertEquals("b", list.get(0));
    assertEquals("c", list.get(1));
  }

  public void testVar() {
    PyFileEvaluator eval = doEvaluate("a='b'\nc='d'\ne=a+c");
    assertEquals("bd", eval.getValue("e"));
  }

  public void testMixedList() {
    PyFileEvaluator eval = doEvaluate("a=['b',['c','d']]");
    List list = (List)eval.getValue("a");
    assertEquals(2, list.size());
    assertEquals("b", list.get(0));
    assertEquals(new ArrayList<String>(Arrays.asList("c", "d")), list.get(1));
  }

  private PyFileEvaluator doEvaluate(String text) {
    PyFileEvaluator eval = new PyFileEvaluator();
    PyFile file = (PyFile)PsiFileFactory.getInstance(myFixture.getProject()).createFileFromText("a.py", PythonFileType.INSTANCE, text);
    eval.evaluate(file);
    return eval;
  }
}
