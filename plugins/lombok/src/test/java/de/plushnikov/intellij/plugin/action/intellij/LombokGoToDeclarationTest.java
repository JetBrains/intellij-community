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
    return LombokTestUtil.LOMBOK_NEW_DESCRIPTOR;
  }

  public void testExtensionMethod() {
    configureFromFileText("Test.java", """
      import lombok.experimental.ExtensionMethod;

      @ExtensionMethod({Extensions.class})
      public class ExtensionMethodDfa {
        public String test() {
          String iAmNull = "hELlO, WORlD!".toTitle<caret>Case();
        }
      }
      class Extensions {
        public static String toTitleCase(String in) {
          if (in.isEmpty()) return in;
          return "" + Character.toTitleCase(in.charAt(0)) +
                 in.substring(1).toLowerCase(Locale.ROOT);
        }
      }
      """);

    PsiElement element = GotoDeclarationAction.findTargetElement(getProject(), getEditor(), getEditor().getCaretModel().getOffset());
    assertTrue(element instanceof PsiMethod);
    assertEquals("Extensions", ((PsiMethod)element).getContainingClass().getQualifiedName());
  }
}
