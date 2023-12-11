package org.jetbrains.idea.maven.inspections.dom;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.maven.testFramework.MavenDomTestCase;
import com.intellij.maven.testFramework.MavenTestCase;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import org.jetbrains.idea.maven.dom.inspections.MavenRedundantGroupIdInspection;
import org.junit.Test;

public class MavenRedundantGroupIdTest extends MavenDomTestCase {
  @Override
  public boolean runInDispatchThread() {
    return true;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    getFixture().enableInspections(MavenRedundantGroupIdInspection.class);
  }

  @Test
  public void testHighlighting1() {
    createProjectPom("""
                       <groupId>my.group</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>""");

    checkHighlighting();
  }

  @Test
  public void testHighlighting2() {
    createProjectPom("""
                       <groupId>childGroupId</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                         
                       <parent>
                         <groupId>my.group</groupId>
                         <artifactId>parent</artifactId>
                         <version>1.0</version>
                       </parent>""");

    checkHighlighting();
  }

  @Test
  public void testHighlighting3() {
    createProjectPom("""
                       <warning><groupId>my.group</groupId></warning>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                         
                       <parent>
                         <groupId>my.group</groupId>
                         <artifactId>parent</artifactId>
                         <version>1.0</version>
                       </parent>""");

    checkHighlighting();
  }

  @Test
  public void testQuickFix() {
    createProjectPom("""
                       <artifactId>childA</artifactId>
                       <groupId>mavenParen<caret>t</groupId>
                       <version>1.0</version>
                         
                       <parent>
                         <groupId>mavenParent</groupId>
                         <artifactId>childA</artifactId>
                         <version>1.0</version>
                       </parent>""");

    getFixture().configureFromExistingVirtualFile(myProjectPom);
    getFixture().doHighlighting();

    for (IntentionAction intention : getFixture().getAvailableIntentions()) {
      if (intention.getText().startsWith("Remove ") && intention.getText().contains("<groupId>")) {
        getFixture().launchAction(intention);
        break;
      }
    }


    //doPostponedFormatting(myProject)
    PostprocessReformattingAspect.getInstance(myProject).doPostponedFormatting();

    getFixture().checkResult(MavenTestCase.createPomXml("""
                                                       <artifactId>childA</artifactId>
                                                           <version>1.0</version>
                                                         
                                                       <parent>
                                                         <groupId>mavenParent</groupId>
                                                         <artifactId>childA</artifactId>
                                                         <version>1.0</version>
                                                       </parent>"""));
  }
}
