package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

public class VarTest extends AbstractLombokLightCodeInsightTestCase {
  public void testSimpleInt() {
    configureClass("100");
    verifyLocalVariableType("int");
  }

  public void testSimpleString() {
    configureClass("\"\"");
    verifyLocalVariableType("java.lang.String");
  }

  public void testNewString() {
    configureClass("new java.lang.String(\"Hello World\")");
    verifyLocalVariableType("java.lang.String");
  }

  public void testDoubleExpression() {
    configureClass("10.0 + 20.0");
    verifyLocalVariableType("double");
  }

  public void testIntParameter() {
    myFixture.configureByText("a.java", "import lombok.experimental.var;\n" +
      "abstract class Test {\n" +
      "    private void test() {\n" +
      "       int[] myArray = new int[] {1, 2, 3, 4, 5};\n" +
      "       for(var my<caret>Var: myArray) {" +
      "       }\n" +
      "    } \n" +
      "}\n");
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    assertTrue(elementAtCaret instanceof PsiIdentifier);
    final PsiElement localParameter = elementAtCaret.getParent();
    assertTrue(localParameter.toString(), localParameter instanceof PsiParameter);
    final PsiType type = ((PsiParameter) localParameter).getType();
    assertNotNull(localParameter.toString(), type);
    assertTrue(type.getCanonicalText(), type.equalsToText("int"));
  }

  public void testBooleanExpression() {
    configureClass("10 == 10");
    verifyLocalVariableType("boolean");
  }

  public void testGenericCollection() {
    configureClass("java.util.Arrays.asList(\"a\",\"b\")");
    verifyLocalVariableType("java.util.List<java.lang.String>");
  }

  public void testGenericNewCollection() {
    configureClass("new java.util.ArrayList<Integer>()");
    verifyLocalVariableType("java.util.ArrayList<java.lang.Integer>");
  }

  public void testGenericTypeDiamond296() {
    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());

    configureClass("new java.util.concurrent.atomic.AtomicReference<>(\"abc\")");
    verifyLocalVariableType("java.util.concurrent.atomic.AtomicReference<java.lang.String>");
  }

  public void testGenericMethod168() {
    configureClass("forClass(Integer.class)",
      "public static <T> java.util.List<T> forClass(Class<T> clazz) {\n" +
        "            return new java.util.ArrayList<T>();\n" +
        "        }\n");
    verifyLocalVariableType("java.util.List<java.lang.Integer>");
  }

  private void configureClass(String valDefinition) {
    configureClass(valDefinition, "");
  }

  private void configureClass(String valDefinition, String extraDefinition) {
    myFixture.configureByText("a.java", "import lombok.experimental.var;\n" +
      "abstract class Test {\n" +
      "    private void test() {\n" +
      "       var my<caret>Var = " + valDefinition + "; \n" +
      //"       my<caret>Var = " + valDefinition + "; \n" +
      "    } \n" +
      extraDefinition +
      "}\n");
  }

  private void verifyLocalVariableType(final String expectedType) {
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    assertTrue(elementAtCaret instanceof PsiIdentifier);
    final PsiElement localVariable = elementAtCaret.getParent();
    assertTrue(localVariable.toString(), localVariable instanceof PsiLocalVariable);
    final PsiType type = ((PsiLocalVariable) localVariable).getType();
    assertNotNull(localVariable.toString(), type);
    assertTrue(type.getCanonicalText(), type.equalsToText(expectedType));
  }
}
