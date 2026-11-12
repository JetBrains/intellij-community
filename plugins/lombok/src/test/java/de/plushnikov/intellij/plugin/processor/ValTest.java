package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ValTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture);

  @Test
  public void testSimpleInt() {
    configureClass("100");
    verifyLocalVariableType("int");
  }

  @Test
  public void testSimpleString() {
    configureClass("\"\"");
    verifyLocalVariableType("java.lang.String");
  }

  @Test
  public void testNewString() {
    configureClass("new java.lang.String(\"Hello World\")");
    verifyLocalVariableType("java.lang.String");
  }

  @Test
  public void testDoubleExpression() {
    configureClass("10.0 + 20.0");
    verifyLocalVariableType("double");
  }

  @Test
  public void testIntParameter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      import lombok.val;
      abstract class Test {
          private void test() {
             int[] myArray = new int[] {1, 2, 3, 4, 5};
             for(val my<caret>Var: myArray) {       }
          }\s
      }
      """);

    ReadAction.runBlocking(() -> {
      final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      assertTrue(elementAtCaret instanceof PsiIdentifier);
      final PsiElement localParameter = elementAtCaret.getParent();
      assertTrue(localParameter instanceof PsiParameter, localParameter.toString());
      final PsiType type = ((PsiParameter) localParameter).getType();
      assertNotNull(type, localParameter.toString());
      assertTrue(type.equalsToText("int"), type.getCanonicalText());
    });
  }

  @Test
  public void testBooleanExpression() {
    configureClass("10 == 10");
    verifyLocalVariableType("boolean");
  }

  @Test
  public void testGenericCollection() {
    configureClass("java.util.Arrays.asList(\"a\",\"b\")");
    verifyLocalVariableType("java.util.List<java.lang.String>");
  }

  @Test
  public void testGenericNewCollection() {
    configureClass("new java.util.ArrayList<Integer>()");
    verifyLocalVariableType("java.util.ArrayList<java.lang.Integer>");
  }

  @Test
  public void testGenericTypeDiamond296() {
    configureClass("new java.util.concurrent.atomic.AtomicReference<>(\"abc\")");
    verifyLocalVariableType("java.util.concurrent.atomic.AtomicReference<java.lang.String>");
  }

  @Test
  public void testGenericMethod168() {
    configureClass("forClass(Integer.class)",
                   """
                     public static <T> java.util.List<T> forClass(Class<T> clazz) {
                                 return new java.util.ArrayList<T>();
                             }
                     """);
    verifyLocalVariableType("java.util.List<java.lang.Integer>");
  }

  @Test
  public void testConditionalExpressionThen260() {
    configureClass("TYPE_ID_MAPPINGS.containsKey(\"key\") ? newHashSet(TYPE_ID_MAPPINGS.get(\"key\")) : MULTIPLE_SEARCH_TYPES",
                   """
                     private static java.util.Map<java.lang.String, java.lang.String> TYPE_ID_MAPPINGS = new java.util.HashMap<>();
                      private static java.util.Set<java.lang.String> MULTIPLE_SEARCH_TYPES = new java.util.HashSet<>();
                      public static <E> java.util.Collection<E> newHashSet(E foo) {
                         return new java.util.HashSet<>();
                       }""");
    verifyLocalVariableType("java.util.Collection<java.lang.String>");
  }

  @Test
  public void testConditionalExpressionElse260() {
    configureClass("TYPE_ID_MAPPINGS.containsKey(\"key\") ? newHashSet(TYPE_ID_MAPPINGS.get(\"key\")) : MULTIPLE_SEARCH_TYPES",
                   """
                     private static java.util.Map<java.lang.String, java.lang.String> TYPE_ID_MAPPINGS = new java.util.HashMap<>();
                      private static java.util.Set<java.lang.String> MULTIPLE_SEARCH_TYPES = new java.util.HashSet<>();
                      public static <E> java.util.SortedSet<E> newHashSet(E foo) {
                         return new java.util.TreeSet<>();
                       }""");
    verifyLocalVariableType("java.util.Set<java.lang.String>");
  }

  @Test
  public void testIssue802GenericTypeParamShouldNotBecomeObjectAfterMappingOuterCollection() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("a.java",
                                             """
                                               import lombok.val;
                                               import java.util.Optional;
                                               class Test {
                                                   public void test() {
                                                       val strOpt = Optional.of("1");
                                                       val intOptInferred<caret>Lambda = strOpt.map(str -> Integer.valueOf(str));
                                                   }
                                               }
                                               """);

    EdtTestUtil.runInEdtAndWait(() -> {
      PsiLocalVariable var = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiLocalVariable.class);
      assertNotNull(var);

      PsiType typeBefore = var.getType();
      assertNotNull(typeBefore);
      assertEquals("java.util.Optional<java.lang.Integer>", typeBefore.getCanonicalText(false));
      myFixture.testHighlighting(false, false, false, file.getVirtualFile());

      myFixture.type('2');
      PsiDocumentManager.getInstance(myFixture.getProject()).commitAllDocuments();
      // this line will fail
      myFixture.testHighlighting(false, false, false, file.getVirtualFile());

      assertTrue(var.isValid());

      PsiType typeAfter = var.getType();
      assertNotNull(typeAfter);
      assertEquals("java.util.Optional<java.lang.Integer>", typeAfter.getCanonicalText(false));
    });
  }

  private void configureClass(String valDefinition) {
    configureClass(valDefinition, "");
  }

  private void configureClass(String valDefinition, String extraDefinition) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    ReadAction.runBlocking(() -> {
      final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      assertTrue(elementAtCaret instanceof PsiIdentifier);
      final PsiElement localVariable = elementAtCaret.getParent();
      assertTrue(localVariable instanceof PsiLocalVariable, localVariable.toString());
      final PsiType type = ((PsiLocalVariable) localVariable).getType();
      assertNotNull(type, localVariable.toString());
      assertTrue(type.equalsToText(expectedType), type.getCanonicalText());
    });
  }
}
