package de.plushnikov.intellij.plugin.action.intellij;

import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;

public class RenameFieldActionTest extends AbstractLombokLightCodeInsightTestCase {

  public void testFieldRename() {
    myFixture.configureByText("Main.java", "@lombok.Data\n" +
                                                     "public class Main {\n" +
                                                     "    private String someString<caret>;\n" +
                                                     "\n" +
                                                     "    public static void main(String[] args) {\n" +
                                                     "        final Main main = new Main();\n" +
                                                     "        main.setSomeString(\"Hello World\");\n" +
                                                     "        System.out.println(main.getSomeString());\n" +
                                                     "    }\n" +
                                                     "}\n");

    myFixture.renameElementAtCaretUsingHandler("someString2");

    myFixture.checkHighlighting();
  }
}
