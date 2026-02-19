package de.plushnikov.intellij.plugin.action.intellij;

import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

public class RenameFieldActionTest extends AbstractLombokLightCodeInsightTestCase {

  public void testFieldRename() {
    myFixture.configureByText("Main.java", """
      @lombok.Data
      public class Main {
          private String someString<caret>;

          public static void main(String[] args) {
              final Main main = new Main();
              main.setSomeString("Hello World");
              System.out.println(main.getSomeString());
          }
      }
      """);

    myFixture.renameElementAtCaretUsingHandler("someString2");

    myFixture.checkHighlighting();
  }
}
