package de.plushnikov.intellij.plugin.action.intellij;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

public class LombokGoToDeclarationTest extends LightJavaCodeInsightTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_DESCRIPTOR;
  }

  public void testExtensionMethod() {
    configureFromFileText("Test.java", "import lombok.experimental.ExtensionMethod;\n" +
                                       "\n" +
                                       "@ExtensionMethod({Extensions.class})\n" +
                                       "public class ExtensionMethodDfa {\n" +
                                       "  public String test() {\n" +
                                       "    String iAmNull = " +
                                       "\"hELlO, WORlD!\".toTitle<caret>Case();\n" +
                                       "  }\n" +
                                       "}\n" +
                                       "class Extensions {\n" +
                                       "  public static String toTitleCase(String in) {\n" +
                                       "    if (in.isEmpty()) return in;\n" +
                                       "    return \"\" + Character.toTitleCase(in.charAt(0)) +\n" +
                                       "           in.substring(1).toLowerCase(Locale.ROOT);\n" +
                                       "  }\n" +
                                       "}\n");

    PsiElement element = GotoDeclarationAction.findTargetElement(getProject(), getEditor(), getEditor().getCaretModel().getOffset());
    assertTrue(element instanceof PsiMethod);
    assertEquals("Extensions", ((PsiMethod)element).getContainingClass().getQualifiedName());
  }
}
