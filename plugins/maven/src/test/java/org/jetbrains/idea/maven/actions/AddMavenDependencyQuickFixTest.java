/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.actions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.idea.maven.dom.MavenDomWithIndicesTestCase;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchDialog;
import org.jetbrains.idea.maven.model.MavenId;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

/**
 * @author Sergey Evdokimov
 */
public class AddMavenDependencyQuickFixTest extends MavenDomWithIndicesTestCase {

  private IntentionAction findAddMavenIntention() {
    for (IntentionAction intention : myFixture.getAvailableIntentions()) {
      if (intention.getText().contains("Add Maven")) {
        return intention;
      }
    }

    return null;
  }

  @Test
  public void testAddDependency() throws IOException {
    VirtualFile f = createProjectSubFile("src/main/java/A.java", "import org.apache.commons.io.IOUtils;\n" +
                                                                 "\n" +
                                                                 "public class Aaa {\n" +
                                                                 "\n" +
                                                                 "  public void xxx() {\n" +
                                                                 "    IOUtil<caret>s u;\n" +
                                                                 "  }\n" +
                                                                 "\n" +
                                                                 "}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    myFixture.configureFromExistingVirtualFile(f);

    IntentionAction intentionAction = findAddMavenIntention();

    MavenArtifactSearchDialog.ourResultForTest = Collections.singletonList(new MavenId("commons-io", "commons-io", "2.4"));

    intentionAction.invoke(myProject, myFixture.getEditor(), myFixture.getFile());
    waitForImportCompletion();

    String pomText = PsiManager.getInstance(myProject).findFile(myProjectPom).getText();
    assertTrue(pomText.matches(
      "(?s).*<dependency>\\s*<groupId>commons-io</groupId>\\s*<artifactId>commons-io</artifactId>\\s*<version>2.4</version>\\s*</dependency>.*"));
  }

  @Test 
  public void testAddDependencyTwice() throws IOException {
    VirtualFile f = createProjectSubFile("src/main/java/A.java", "import org.apache.commons.io.IOUtils;\n" +
                                                                 "\n" +
                                                                 "public class Aaa {\n" +
                                                                 "\n" +
                                                                 "  public void xxx() {\n" +
                                                                 "    IOUtil<caret>s u;\n" +
                                                                 "  }\n" +
                                                                 "\n" +
                                                                 "}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    myFixture.configureFromExistingVirtualFile(f);

    IntentionAction intentionAction = findAddMavenIntention();

    MavenArtifactSearchDialog.ourResultForTest = Collections.singletonList(new MavenId("commons-io", "commons-io", "2.4"));
    intentionAction.invoke(myProject, myFixture.getEditor(), myFixture.getFile());
    waitForImportCompletion();
    MavenArtifactSearchDialog.ourResultForTest = Collections.singletonList(new MavenId("commons-io", "commons-io", "2.4"));
    intentionAction.invoke(myProject, myFixture.getEditor(), myFixture.getFile());

    waitForImportCompletion();
    String pomText = PsiManager.getInstance(myProject).findFile(myProjectPom).getText();
    assertEquals("<?xml version=\"1.0\"?><project xmlns=\"http://maven.apache.org/POM/4.0.0\"         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">  <modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>project</artifactId><version>1</version>\n" +
                 "    <dependencies>\n" +
                 "        <dependency>\n" +
                 "            <groupId>commons-io</groupId>\n" +
                 "            <artifactId>commons-io</artifactId>\n" +
                 "            <version>2.4</version>\n" +
                 "        </dependency>\n" +
                 "    </dependencies>\n" +
                 "</project>", pomText);
  }

  @Test 
  public void testChangeDependencyScopeIfWasInTest() throws IOException {
    VirtualFile f = createProjectSubFile("src/main/java/A.java", "import org.apache.commons.io.IOUtils;\n" +
                                                                 "\n" +
                                                                 "public class Aaa {\n" +
                                                                 "\n" +
                                                                 "  public void xxx() {\n" +
                                                                 "    IOUtil<caret>s u;\n" +
                                                                 "  }\n" +
                                                                 "\n" +
                                                                 "}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>\n" +
                  "<dependencies>\n" +
                  "  <dependency>\n" +
                  "    <groupId>commons-io</groupId>\n" +
                  "    <artifactId>commons-io</artifactId>\n" +
                  "    <version>2.4</version>\n" +
                  "    <scope>test</scope>\n" +
                  "  </dependency>\n" +
                  "</dependencies>");

    myFixture.configureFromExistingVirtualFile(f);

    IntentionAction intentionAction = findAddMavenIntention();

    MavenArtifactSearchDialog.ourResultForTest = Collections.singletonList(new MavenId("commons-io", "commons-io", "2.4"));
    intentionAction.invoke(myProject, myFixture.getEditor(), myFixture.getFile());
    waitForImportCompletion();
    String pomText = PsiManager.getInstance(myProject).findFile(myProjectPom).getText();
    assertEquals("<?xml version=\"1.0\"?><project xmlns=\"http://maven.apache.org/POM/4.0.0\"         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">  <modelVersion>4.0.0</modelVersion><groupId>test</groupId><artifactId>project</artifactId><version>1</version>\n" +
                            "<dependencies>\n" +
                            "  <dependency>\n" +
                            "    <groupId>commons-io</groupId>\n" +
                            "    <artifactId>commons-io</artifactId>\n" +
                            "    <version>2.4</version>\n" +
                            "  </dependency>\n" +
                            "</dependencies>" +
                            "</project>", pomText);
  }

  @Test 
  public void testAddDependencyInTest() throws IOException {
    VirtualFile f = createProjectSubFile("src/test/java/A.java", "import org.apache.commons.io.IOUtils;\n" +
                                                                 "\n" +
                                                                 "public class Aaa {\n" +
                                                                 "\n" +
                                                                 "  public void xxx() {\n" +
                                                                 "    IOUtil<caret>s u;\n" +
                                                                 "  }\n" +
                                                                 "\n" +
                                                                 "}");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    myFixture.configureFromExistingVirtualFile(f);

    IntentionAction intentionAction = findAddMavenIntention();

    MavenArtifactSearchDialog.ourResultForTest = Collections.singletonList(new MavenId("commons-io", "commons-io", "2.4"));

    intentionAction.invoke(myProject, myFixture.getEditor(), myFixture.getFile());
    waitForImportCompletion();
    String pomText = PsiManager.getInstance(myProject).findFile(myProjectPom).getText();
    assertTrue(pomText.matches(
      "(?s).*<dependency>\\s*<groupId>commons-io</groupId>\\s*<artifactId>commons-io</artifactId>\\s*<version>2.4</version>\\s*<scope>test</scope>\\s*</dependency>.*"));
  }
}
