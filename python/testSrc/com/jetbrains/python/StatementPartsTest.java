package com.jetbrains.python;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.*;

import java.util.Map;

/**
 * Tests statement parts.
 * User: dcheryasov
 * Date: Mar 15, 2009 3:11:01 AM
 */
public class StatementPartsTest extends MarkedTestCase {

  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath() + "/psi/parts/";
  }

  public void testIf() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals(2, marks.size());
    PsiElement elt = marks.get("<the_if>").getParent().getParent(); // if_keyword -> if_part -> if_stmt
    assertTrue(elt instanceof PyIfStatement);
    PyIfStatement if_stmt = (PyIfStatement)elt;
    PyIfPart if_part = if_stmt.getIfPart();
    assertFalse(if_part.isElif());
    PyExpression if_cond = if_part.getCondition();
    assertEquals(marks.get("<the_cond>").getParent(), if_cond);
  }

  public void testIfElse() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals(4, marks.size());
    PsiElement elt = marks.get("<the_if>").getParent().getParent(); // if_keyword -> if_part -> if_stmt
    assertTrue(elt instanceof PyIfStatement);
    PyIfStatement if_stmt = (PyIfStatement)elt;

    PyIfPart if_part = if_stmt.getIfPart();
    assertFalse(if_part.isElif());
    PyExpression if_cond = if_part.getCondition();
    assertEquals(marks.get("<the_cond>").getParent(), if_cond);

    PyStatementList stmt_list = if_part.getStatementList();
    assertNotNull(stmt_list);
    assertEquals(marks.get("<then_stmt>").getParent().getParent(), stmt_list); // keyword -> stmt -> stmt_list

    PyElsePart else_part = if_stmt.getElsePart();
    assertNotNull(else_part);

    stmt_list = else_part.getStatementList();
    assertNotNull(stmt_list);
    assertEquals(marks.get("<else_stmt>").getParent().getParent(), stmt_list); // keyword -> stmt -> stmt_list

  }

  public void testIfElifElse() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals(6, marks.size());
    PsiElement elt = marks.get("<the_if>").getParent().getParent(); // if_keyword -> if_part -> if_stmt
    assertTrue(elt instanceof PyIfStatement);
    PyIfStatement if_stmt = (PyIfStatement)elt;

    PyIfPart if_part = if_stmt.getIfPart();
    assertFalse(if_part.isElif());
    PyExpression if_cond = if_part.getCondition();
    assertEquals(marks.get("<the_cond>").getParent(), if_cond);

    PyStatementList stmt_list = if_part.getStatementList();
    assertNotNull(stmt_list);
    assertEquals(marks.get("<then_stmt>").getParent().getParent(), stmt_list); // keyword -> stmt -> stmt_list

    PyElsePart else_part = if_stmt.getElsePart();
    assertNotNull(else_part);

    stmt_list = else_part.getStatementList();
    assertNotNull(stmt_list);
    assertEquals(marks.get("<else_stmt>").getParent().getParent(), stmt_list); // keyword -> stmt -> stmt_list

    PyIfPart elif_part = if_stmt.getElifParts()[0];
    assertTrue(elif_part.isElif());
    if_cond = elif_part.getCondition();
    assertEquals(marks.get("<elif_cond>").getParent(), if_cond);

    stmt_list = elif_part.getStatementList();
    assertNotNull(stmt_list);
    assertEquals(marks.get("<elif_stmt>").getParent().getParent(), stmt_list); // keyword -> stmt -> stmt_list

  }

  public void testWhile()  throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals(3, marks.size());

    PsiElement elt = marks.get("<stmt>").getParent().getParent(); // keyword -> part -> stmt
    assertTrue(elt instanceof PyWhileStatement);
    PyWhileStatement while_stmt = (PyWhileStatement)elt;

    PyWhilePart while_part = while_stmt.getWhilePart();

    PyExpression cond = while_part.getCondition();
    assertEquals(marks.get("<cond>").getParent(), cond);

    elt = marks.get("<else>").getParent(); // keyword -> part
    assertTrue(elt instanceof PyElsePart);
    assertEquals(while_stmt.getElsePart(), elt);
  }

  public void testFor()  throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals(4, marks.size());

    PsiElement elt = marks.get("<stmt>").getParent().getParent(); // keyword -> part -> stmt
    assertTrue(elt instanceof PyForStatement);
    PyForStatement stmt = (PyForStatement)elt;

    PyForPart for_part = stmt.getForPart();

    PyExpression target = for_part.getTarget();
    assertEquals(marks.get("<target>").getParent(), target); // ident -> expr

    PyExpression source = for_part.getSource();
    assertEquals(marks.get("<source>").getParent(), source); // ident -> expr

    elt = marks.get("<else>").getParent(); // keyword -> part
    assertTrue(elt instanceof PyElsePart);
    assertEquals(stmt.getElsePart(), elt);
  }

  public void testTry() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals(6, marks.size());

    PsiElement elt = marks.get("<stmt>").getParent().getParent(); // keyword -> part -> stmt
    assertTrue(elt instanceof PyTryExceptStatement);
    PyTryExceptStatement stmt = (PyTryExceptStatement)elt;

    PyTryPart try_part = stmt.getTryPart();
    PyStatementList stmt_list = try_part.getStatementList();
    assertNotNull(stmt_list);
    assertEquals(marks.get("<body>").getParent().getParent(), stmt_list); // keyword -> stmt -> stmt_list

    PyExceptPart exc_part = stmt.getExceptParts()[0];
    assertEquals("ArithmeticError", exc_part.getExceptClass().getText());
    assertEquals(marks.get("<ex1>").getParent(), exc_part);

    exc_part = (PyExceptPart)marks.get("<ex2>").getParent(); // keyword -> part
    assertEquals(stmt.getExceptParts()[1], exc_part);
    assertNull(exc_part.getExceptClass());

    elt = marks.get("<else>").getParent(); // keyword -> part
    assertTrue(elt instanceof PyElsePart);
    assertEquals(stmt.getElsePart(), elt);

    elt = marks.get("<finally>").getParent(); // keyword -> part
    assertTrue(elt instanceof PyFinallyPart);
    assertEquals(stmt.getFinallyPart(), elt);
  }
}
