package de.plushnikov.intellij.plugin.processor;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

public class ValTest extends AbstractLombokLightCodeInsightTestCase {

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
    myFixture.configureByText("a.java", "import lombok.val;\n" +
      "abstract class Test {\n" +
      "    private void test() {\n" +
      "       int[] myArray = new int[] {1, 2, 3, 4, 5};\n" +
      "       for(val my<caret>Var: myArray) {" +
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

  public void testConditionalExpressionThen260() {
    configureClass("TYPE_ID_MAPPINGS.containsKey(\"key\") ? newHashSet(TYPE_ID_MAPPINGS.get(\"key\")) : MULTIPLE_SEARCH_TYPES",
      "private static java.util.Map<java.lang.String, java.lang.String> TYPE_ID_MAPPINGS = new java.util.HashMap<>();\n" +
        " private static java.util.Set<java.lang.String> MULTIPLE_SEARCH_TYPES = new java.util.HashSet<>();\n" +
        " public static <E> java.util.Collection<E> newHashSet(E foo) {\n" +
        "    return new java.util.HashSet<>();\n" +
        "  }");
    verifyLocalVariableType("java.util.Collection<java.lang.String>");
  }

  public void testConditionalExpressionElse260() {
    configureClass("TYPE_ID_MAPPINGS.containsKey(\"key\") ? newHashSet(TYPE_ID_MAPPINGS.get(\"key\")) : MULTIPLE_SEARCH_TYPES",
      "private static java.util.Map<java.lang.String, java.lang.String> TYPE_ID_MAPPINGS = new java.util.HashMap<>();\n" +
        " private static java.util.Set<java.lang.String> MULTIPLE_SEARCH_TYPES = new java.util.HashSet<>();\n" +
        " public static <E> java.util.SortedSet<E> newHashSet(E foo) {\n" +
        "    return new java.util.TreeSet<>();\n" +
        "  }");
    verifyLocalVariableType("java.util.Set<java.lang.String>");
  }

  public void testIssue802GenericTypeParamShouldNotBecomeObjectAfterMappingOuterCollection() {
    PsiFile file = myFixture.configureByText("a.java",
      "import lombok.val;\n" +
        "import java.util.Optional;\n" +
        "class Test {\n" +
        "    public void test() {\n" +
        "        val strOpt = Optional.of(\"1\");\n" +
        "        val intOptInferred<caret>Lambda = strOpt.map(str -> Integer.valueOf(str));\n" +
        "    }\n" +
        "}\n");

    PsiLocalVariable var = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiLocalVariable.class);
    assertNotNull(var);

    PsiType typeBefore = var.getType();
    assertNotNull(typeBefore);
    assertEquals("java.util.Optional<java.lang.Integer>", typeBefore.getCanonicalText(false));
    myFixture.testHighlighting(false, false, false, file.getVirtualFile());

    myFixture.type('2');
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    // this line will fail
    myFixture.testHighlighting(false, false, false, file.getVirtualFile());

    assertTrue(var.isValid());

    PsiType typeAfter = var.getType();
    assertNotNull(typeAfter);
    assertEquals("java.util.Optional<java.lang.Integer>", typeAfter.getCanonicalText(false));
  }

  private void configureClass(String valDefinition) {
    configureClass(valDefinition, "");
  }

  private void configureClass(String valDefinition, String extraDefinition) {
    myFixture.configureByText("a.java", "import lombok.val;\n" +
      "abstract static class Test {\n" +
      "    private void test() {\n" +
      "       val my<caret>Var = " + valDefinition + "; \n" +
      "       myVar.toString(); \n" +
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
