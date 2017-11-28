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

import com.intellij.psi.PsiElement;
import com.jetbrains.python.fixtures.LightMarkedTestCase;
import com.jetbrains.python.psi.*;
import junit.framework.Assert;

import java.util.Map;

/**
 * Tests statement parts.
 * User: dcheryasov
 * Date: Mar 15, 2009 3:11:01 AM
 */
public class PyStatementPartsTest extends LightMarkedTestCase {

  @Override
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/psi/parts/";
  }

  public void testIf() {
    Map<String, PsiElement> marks = loadTest();
    Assert.assertEquals(2, marks.size());
    PsiElement elt = marks.get("<the_if>").getParent().getParent(); // if_keyword -> if_part -> if_stmt
    Assert.assertTrue(elt instanceof PyIfStatement);
    PyIfStatement if_stmt = (PyIfStatement)elt;
    PyIfPart if_part = if_stmt.getIfPart();
    Assert.assertFalse(if_part.isElif());
    PyExpression if_cond = if_part.getCondition();
    Assert.assertEquals(marks.get("<the_cond>").getParent(), if_cond);
  }

  public void testIfElse() {
    Map<String, PsiElement> marks = loadTest();
    Assert.assertEquals(4, marks.size());
    PsiElement elt = marks.get("<the_if>").getParent().getParent(); // if_keyword -> if_part -> if_stmt
    Assert.assertTrue(elt instanceof PyIfStatement);
    PyIfStatement if_stmt = (PyIfStatement)elt;

    PyIfPart if_part = if_stmt.getIfPart();
    Assert.assertFalse(if_part.isElif());
    PyExpression if_cond = if_part.getCondition();
    Assert.assertEquals(marks.get("<the_cond>").getParent(), if_cond);

    PyStatementList stmt_list = if_part.getStatementList();
    Assert.assertNotNull(stmt_list);
    Assert.assertEquals(marks.get("<then_stmt>").getParent().getParent(), stmt_list); // keyword -> stmt -> stmt_list

    PyElsePart else_part = if_stmt.getElsePart();
    Assert.assertNotNull(else_part);

    stmt_list = else_part.getStatementList();
    Assert.assertNotNull(stmt_list);
    Assert.assertEquals(marks.get("<else_stmt>").getParent().getParent(), stmt_list); // keyword -> stmt -> stmt_list

  }

  public void testIfElifElse() {
    Map<String, PsiElement> marks = loadTest();
    Assert.assertEquals(6, marks.size());
    PsiElement elt = marks.get("<the_if>").getParent().getParent(); // if_keyword -> if_part -> if_stmt
    Assert.assertTrue(elt instanceof PyIfStatement);
    PyIfStatement if_stmt = (PyIfStatement)elt;

    PyIfPart if_part = if_stmt.getIfPart();
    Assert.assertFalse(if_part.isElif());
    PyExpression if_cond = if_part.getCondition();
    Assert.assertEquals(marks.get("<the_cond>").getParent(), if_cond);

    PyStatementList stmt_list = if_part.getStatementList();
    Assert.assertNotNull(stmt_list);
    Assert.assertEquals(marks.get("<then_stmt>").getParent().getParent(), stmt_list); // keyword -> stmt -> stmt_list

    PyElsePart else_part = if_stmt.getElsePart();
    Assert.assertNotNull(else_part);

    stmt_list = else_part.getStatementList();
    Assert.assertNotNull(stmt_list);
    Assert.assertEquals(marks.get("<else_stmt>").getParent().getParent(), stmt_list); // keyword -> stmt -> stmt_list

    PyIfPart elif_part = if_stmt.getElifParts()[0];
    Assert.assertTrue(elif_part.isElif());
    if_cond = elif_part.getCondition();
    Assert.assertEquals(marks.get("<elif_cond>").getParent(), if_cond);

    stmt_list = elif_part.getStatementList();
    Assert.assertNotNull(stmt_list);
    Assert.assertEquals(marks.get("<elif_stmt>").getParent().getParent(), stmt_list); // keyword -> stmt -> stmt_list

  }

  public void testWhile() {
    Map<String, PsiElement> marks = loadTest();
    Assert.assertEquals(3, marks.size());

    PsiElement elt = marks.get("<stmt>").getParent().getParent(); // keyword -> part -> stmt
    Assert.assertTrue(elt instanceof PyWhileStatement);
    PyWhileStatement while_stmt = (PyWhileStatement)elt;

    PyWhilePart while_part = while_stmt.getWhilePart();

    PyExpression cond = while_part.getCondition();
    Assert.assertEquals(marks.get("<cond>").getParent(), cond);

    elt = marks.get("<else>").getParent(); // keyword -> part
    Assert.assertTrue(elt instanceof PyElsePart);
    Assert.assertEquals(while_stmt.getElsePart(), elt);
  }

  public void testFor() {
    Map<String, PsiElement> marks = loadTest();
    Assert.assertEquals(4, marks.size());

    PsiElement elt = marks.get("<stmt>").getParent().getParent(); // keyword -> part -> stmt
    Assert.assertTrue(elt instanceof PyForStatement);
    PyForStatement stmt = (PyForStatement)elt;

    PyForPart for_part = stmt.getForPart();

    PyExpression target = for_part.getTarget();
    Assert.assertEquals(marks.get("<target>").getParent(), target); // ident -> expr

    PyExpression source = for_part.getSource();
    Assert.assertEquals(marks.get("<source>").getParent(), source); // ident -> expr

    elt = marks.get("<else>").getParent(); // keyword -> part
    Assert.assertTrue(elt instanceof PyElsePart);
    Assert.assertEquals(stmt.getElsePart(), elt);
  }

  public void testTry() {
    Map<String, PsiElement> marks = loadTest();
    Assert.assertEquals(6, marks.size());

    PsiElement elt = marks.get("<stmt>").getParent().getParent(); // keyword -> part -> stmt
    Assert.assertTrue(elt instanceof PyTryExceptStatement);
    PyTryExceptStatement stmt = (PyTryExceptStatement)elt;

    PyTryPart try_part = stmt.getTryPart();
    PyStatementList stmt_list = try_part.getStatementList();
    Assert.assertNotNull(stmt_list);
    Assert.assertEquals(marks.get("<body>").getParent().getParent(), stmt_list); // keyword -> stmt -> stmt_list

    PyExceptPart exc_part = stmt.getExceptParts()[0];
    Assert.assertEquals("ArithmeticError", exc_part.getExceptClass().getText());
    Assert.assertEquals(marks.get("<ex1>").getParent(), exc_part);

    exc_part = (PyExceptPart)marks.get("<ex2>").getParent(); // keyword -> part
    Assert.assertEquals(stmt.getExceptParts()[1], exc_part);
    Assert.assertNull(exc_part.getExceptClass());

    elt = marks.get("<else>").getParent(); // keyword -> part
    Assert.assertTrue(elt instanceof PyElsePart);
    Assert.assertEquals(stmt.getElsePart(), elt);

    elt = marks.get("<finally>").getParent(); // keyword -> part
    Assert.assertTrue(elt instanceof PyFinallyPart);
    Assert.assertEquals(stmt.getFinallyPart(), elt);
  }
}
