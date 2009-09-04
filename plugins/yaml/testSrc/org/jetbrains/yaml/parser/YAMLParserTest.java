package org.jetbrains.yaml.parser;

import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.yaml.YAMLFileType;

/**
 * @author oleg
 */
public class YAMLParserTest extends LightPlatformTestCase {

  public void test2docs() throws Throwable {
    doTest("# Ranking of 1998 home runs\n" +
           "---\n" +
           "- Mark McGwire\n" +
           "- Sammy Sosa\n" +
           "- Ken Griffey\n" +
           "\n" +
           "# Team ranking\n" +
           "---\n" +
           "- Chicago Cubs\n" +
           "- St Louis Cardinals");
  }

  public void testIndentation() throws Throwable {
    doTest("name: Mark McGwire\n" +
           "accomplishment: >\n" +
           "  Mark set a major league\n" +
           "  home run record in 1998.\n" +
           "stats: |\n" +
           "  65 Home Runs\n" +
           "  0.278 Batting Average");
  }

  public void testMap_between_seq() throws Throwable {
    doTest("?\n" +
           "  - Detroit Tigers\n" +
           "  - Chicago cubs\n" +
           ":\n" +
           "  - 2001-07-23\n" +
           "\n" +
           "? [ New York Yankees,\n" +
           "    Atlanta Braves ]\n" +
           ": [ 2001-07-02, 2001-08-12,\n" +
           "    2001-08-14 ]");
  }

  public void testMap_map() throws Throwable {
    doTest("Mark McGwire: {hr: 65, avg: 0.278}\n" +
           "Sammy Sosa: {\n" +
           "    hr: 63,\n" +
           "    avg: 0.288\n" +
           "  }");
  }

  public void testRu_locale() throws Throwable {
    doTest("ru:\n" +
           "  hello: Привет\n" +
           "  hello_world: Привет Мир\n" +
           "  hello_yaml_parser: \"Ну здравствуй, йамль парсер :)\"");
  }

  public void testSample_log() throws Throwable {
    doTest("Stack:\n" +
           "  - file: TopClass.py\n" +
           "    line: 23\n" +
           "    code: |\n" +
           "      x = MoreObject(\"345\\n\")\n" +
           "  - file: MoreClass.py\n" +
           "    line: 58\n" +
           "    code: |-\n" +
           "      foo = bar");
  }

  public void testSeq_seq() throws Throwable {
    doTest("- [name        , hr, avg  ]\n" +
           "- [Mark McGwire, 65, 0.278]\n" +
           "- [Sammy Sosa  , 63, 0.288]");
  }

  public void testSequence_mappings() throws Throwable {
    doTest("-\n" +
           "  name: Mark McGwire\n" +
           "  hr:   65\n" +
           "  avg:  0.278\n" +
           "-\n" +
           "  name: Sammy Sosa\n" +
           "  hr:   63\n" +
           "  avg:  0.288");
  }

  private void doTest(@NonNls final String code) {
    final PsiFile psiFile = PsiFileFactory.getInstance(getProject()).createFileFromText("temp.yml", YAMLFileType.YML,
                                                                  code, LocalTimeCounter.currentTime(), true);
    final String tree = DebugUtil.psiTreeToString(psiFile, true);
    final String path = PathManager.getHomePath() + "/plugins/yaml/testSrc/org/jetbrains/yaml/parser/data/" + getTestName(false).toLowerCase() + ".txt";
    assertSameLinesWithFile(path, tree);
  }
}