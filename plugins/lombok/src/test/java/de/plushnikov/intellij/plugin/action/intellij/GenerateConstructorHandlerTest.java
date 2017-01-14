package de.plushnikov.intellij.plugin.action.intellij;

import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.GenerateConstructorHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import de.plushnikov.intellij.plugin.action.generate.LombokGenerateConstructorHandler;

import java.util.List;

public class GenerateConstructorHandlerTest extends AbstractLombokLightCodeInsightTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/action/generateconstructor";
  }

  protected void doTest(final boolean preSelect) throws Exception {
    myFixture.configureByFile(getBasePath() + "/before" + getTestName(false) + ".java");

    new LombokGenerateConstructorHandler() {
      @Override
      protected ClassMember[] chooseMembers(ClassMember[] members, boolean allowEmpty, boolean copyJavadoc, Project project, Editor editor) {
        if (preSelect) {
          List<ClassMember> preselection = GenerateConstructorHandler.preselect(members);
          return preselection.toArray(new ClassMember[preselection.size()]);
        } else {
          return members;
        }
      }
    }.invoke(getProject(), getEditor(), getFile());

    checkResultByFile(getBasePath() + "/after" + getTestName(false) + ".java");
  }

  public void testGenerateConstructorEmpty() throws Exception {
    doTest(true);
  }

  public void testGenerateConstructorAll() throws Exception {
    doTest(false);
  }
}
