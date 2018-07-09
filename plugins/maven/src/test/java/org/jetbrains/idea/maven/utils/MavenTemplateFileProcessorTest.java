/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils;

import com.intellij.ide.util.projectWizard.ProjectTemplateParameterFactory;
import com.intellij.platform.templates.SaveProjectAsTemplateAction;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.idea.maven.server.MavenServerManager;

import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class MavenTemplateFileProcessorTest extends LightPlatformCodeInsightFixtureTestCase {

  private static final String TEXT =
    "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
    "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n" +
    "    <modelVersion>4.0.0</modelVersion>\n" +
    "    <groupId>com.springapp</groupId>\n" +
    "    <artifactId>springapp</artifactId>\n" +
    "    <packaging>jar</packaging>\n" +
    "    <version>1.0-SNAPSHOT</version>\n" +
    "    <name>SpringApp</name>\n" +
    "</project>";

  @Override
  protected void tearDown() throws Exception {
    MavenServerManager.getInstance().shutdown(true);
    super.tearDown();
  }

  public void testProcessor() throws Exception {
    PsiFile file = myFixture.configureByText("pom.xml", TEXT);
    String s = new MavenTemplateFileProcessor().encodeFileText(TEXT, file.getVirtualFile(), getProject());
    assertEquals("<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                 "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n" +
                 "    <modelVersion>4.0.0</modelVersion>\n" +
                 "    <groupId>com.springapp</groupId>\n" +
                 "    <artifactId>${IJ_PROJECT_NAME}</artifactId>\n" +
                 "    <packaging>jar</packaging>\n" +
                 "    <version>1.0-SNAPSHOT</version>\n" +
                 "    <name>${IJ_PROJECT_NAME}</name>\n" +
                 "</project>", s);
  }

  public void testSaveAsTemplate() throws Exception {
    PsiFile file = myFixture.configureByText("pom.xml", TEXT);
    Map<String,String> map = SaveProjectAsTemplateAction.computeParameters(getProject(), true);
    map.put("com.springapp", ProjectTemplateParameterFactory.IJ_BASE_PACKAGE);
    String content = SaveProjectAsTemplateAction.getEncodedContent(file.getVirtualFile(), getProject(), map);
    assertEquals("<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                 "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n" +
                 "    <modelVersion>4.0.0</modelVersion>\n" +
                 "    <groupId>${IJ_BASE_PACKAGE}</groupId>\n" +
                 "    <artifactId>${IJ_PROJECT_NAME}</artifactId>\n" +
                 "    <packaging>jar</packaging>\n" +
                 "    <version>1.0-SNAPSHOT</version>\n" +
                 "    <name>${IJ_PROJECT_NAME}</name>\n" +
                 "</project>", content);
  }
}
