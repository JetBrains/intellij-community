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

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import junit.framework.Assert;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Tests assignment mapping.
 */
public class PyAssignmentMappingTest extends LightMarkedTestCase {

  @Override
  public String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/psi/assignment/";
  }


  public void testSimple() {
    Map<String, PsiElement> marks = loadTest();
    Assert.assertEquals(2, marks.size());
    PsiElement src = marks.get("<src>").getParent(); // const -> expr;
    PsiElement dst = marks.get("<dst>").getParent(); // ident -> target expr
    Assert.assertTrue(dst instanceof PyTargetExpression);
    PyAssignmentStatement stmt = (PyAssignmentStatement)dst.getParent();
    List<Pair<PyExpression, PyExpression>> mapping = stmt.getTargetsToValuesMapping();
    Assert.assertEquals(1, mapping.size());
    Pair<PyExpression, PyExpression> pair = mapping.get(0);
    Assert.assertEquals(dst, pair.getFirst());
    Assert.assertEquals(src, pair.getSecond());
  }

  public void testSubscribedSource() {
    Map<String, PsiElement> marks = loadTest();
    Assert.assertEquals(2, marks.size());
    PsiElement src = marks.get("<src>").getParent().getParent(); // const -> ref foo -> subscr expr;
    PsiElement dst = marks.get("<dst>").getParent(); // ident -> target expr
    Assert.assertTrue(dst instanceof PyTargetExpression);
    PyAssignmentStatement stmt = (PyAssignmentStatement)dst.getParent();
    List<Pair<PyExpression, PyExpression>> mapping = stmt.getTargetsToValuesMapping();
    Assert.assertEquals(1, mapping.size());
    Pair<PyExpression, PyExpression> pair = mapping.get(0);
    Assert.assertEquals(dst, pair.getFirst());
    Assert.assertEquals(src, pair.getSecond());
  }

  public void testSubscribedTarget() {
    Map<String, PsiElement> marks = loadTest();
    Assert.assertEquals(2, marks.size());
    PsiElement src = marks.get("<src>").getParent(); // const -> expr;
    PsiElement dst = marks.get("<dst>").getParent().getParent(); // ident -> target expr
    Assert.assertTrue(dst instanceof PySubscriptionExpression);
    PyAssignmentStatement stmt = (PyAssignmentStatement)src.getParent();
    List<Pair<PyExpression, PyExpression>> mapping = stmt.getTargetsToValuesMapping();
    Assert.assertEquals(1, mapping.size());
    Pair<PyExpression, PyExpression> pair = mapping.get(0);
    Assert.assertEquals(dst, pair.getFirst());
    Assert.assertEquals(src, pair.getSecond());
  }


  public void testMultiple() {
    Map<String, PsiElement> marks = loadTest();
    final int TARGET_NUM = 3;
    Assert.assertEquals(TARGET_NUM + 1, marks.size());
    PsiElement src = marks.get("<src>").getParent(); // const -> expr;
    PsiElement[] dsts = new PsiElement[TARGET_NUM];
    for (int i=0; i<TARGET_NUM; i+=1) {
      PsiElement dst = marks.get("<dst" + (i + 1) + ">").getParent(); // ident -> target expr
      Assert.assertTrue(dst instanceof PyTargetExpression);
      dsts[i] = dst;
    }
    PyAssignmentStatement stmt = (PyAssignmentStatement)src.getParent();
    List<Pair<PyExpression, PyExpression>> mapping = stmt.getTargetsToValuesMapping();
    Assert.assertEquals(TARGET_NUM, mapping.size());
    for (int i=0; i<TARGET_NUM; i+=1) {
      Pair<PyExpression, PyExpression> pair = mapping.get(i);
      Assert.assertEquals(dsts[i], pair.getFirst());
      Assert.assertEquals(src, pair.getSecond());
    }
  }

  public void testTupleMapped() {
    List<Pair<PyExpression, PyExpression>> expectedMappings = loadMultiMappingTest(IntStream.of(1, 2));
    PyAssignmentStatement stmt =
      PsiTreeUtil.getParentOfType(expectedMappings.get(0).second, PyAssignmentStatement.class);
    assertSameElements(stmt.getTargetsToValuesMapping(), expectedMappings);
  }

  public void testNestedTupleMapped() {
    List<Pair<PyExpression, PyExpression>> expectedMappings = loadMultiMappingTest(IntStream.rangeClosed(1, 3));
    PyAssignmentStatement stmt =
      PsiTreeUtil.getParentOfType(expectedMappings.get(0).second, PyAssignmentStatement.class);
    assertSameElements(stmt.getTargetsToValuesMapping(), expectedMappings);
  }

  public void testParenthesizedTuple() { //PY-2648
    List<Pair<PyExpression, PyExpression>> expectedMappings = loadMultiMappingTest(IntStream.of(1, 2));
    PyAssignmentStatement stmt = PsiTreeUtil.getParentOfType(expectedMappings.get(0).second, PyAssignmentStatement.class);
    assertSameElements(stmt.getTargetsToValuesMapping(), expectedMappings);
  }

  public void testTuplePack() {
    Map<String, PsiElement> marks = loadTest();
    final int SRC_NUM = 2;
    Assert.assertEquals(SRC_NUM + 1, marks.size());
    PsiElement[] srcs = new PsiElement[SRC_NUM];
    for (int i=0; i<SRC_NUM; i+=1) {
      PsiElement src = marks.get("<src" + (i + 1) + ">").getParent(); // ident -> target expr
      Assert.assertTrue(src instanceof PyExpression);
      srcs[i] = src;
    }
    PsiElement dst = marks.get("<dst>").getParent(); // ident -> target expr
    PyAssignmentStatement stmt = (PyAssignmentStatement)dst.getParent();
    List<Pair<PyExpression, PyExpression>> mapping = stmt.getTargetsToValuesMapping();
    Assert.assertEquals(1, mapping.size());
    Pair<PyExpression, PyExpression> pair = mapping.get(0);
    Assert.assertEquals(dst, pair.getFirst());
    for (PsiElement src : srcs) {
      Assert.assertEquals(src.getParent(), pair.getSecond()); // numeric expr -> tuple
    }
  }

  public void testTupleUnpack() {
    Map<String, PsiElement> marks = loadTest();
    List<Pair<PyExpression, PyExpression>> expectedMapping = getMapping(marks, IntStream.rangeClosed(1, 2));

    PsiElement src = marks.get("<src>").getParent();
    PyAssignmentStatement stmt = (PyAssignmentStatement)src.getParent().getParent();
    List<Pair<PyExpression, PyExpression>> mapping = stmt.getTargetsToValuesMapping();

    assertSameElements(
      ContainerUtil.map(mapping, pair -> Pair.create(pair.first, pair.second.getText())),
      ContainerUtil.map(expectedMapping, pair -> Pair.create(pair.first, pair.second.getText()))
    );
  }

  public void testNestedTupleUnpack() {
    Map<String, PsiElement> marks = loadTest();
    List<Pair<PyExpression, PyExpression>> expectedMapping = getMapping(marks, IntStream.rangeClosed(1, 4));

    PsiElement src = marks.get("<src>").getParent();
    PyAssignmentStatement stmt = PsiTreeUtil.getParentOfType(src, PyAssignmentStatement.class);
    List<Pair<PyExpression, PyExpression>> mapping = stmt.getTargetsToValuesMapping();

    assertSameElements(
      ContainerUtil.map(mapping, pair -> Pair.create(pair.first, pair.second.getText())),
      ContainerUtil.map(expectedMapping, pair -> Pair.create(pair.first, pair.second.getText()))
    );
  }

  private List<Pair<PyExpression, PyExpression>> loadMultiMappingTest(IntStream indices) {
    Map<String, PsiElement> marks = loadTest();
    return getMapping(marks, indices);
  }

  private static List<Pair<PyExpression, PyExpression>> getMapping(Map<String, PsiElement> marks, IntStream indices) {
    return indices.mapToObj(i -> Pair.create(getMarkedExpression(marks, "<dst%d>".formatted(i)),
                                             getMarkedExpression(marks, "<src%d>".formatted(i)))).toList();
  }

  private static PyExpression getMarkedExpression(Map<String, PsiElement> marks, String marker) {
    PsiElement element = marks.get(marker);
    return PyPsiUtils.flattenParens((PyExpression)element.getParent());
  }
}
