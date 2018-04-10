/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.dom;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;

import java.util.List;

public class MavenPropertyFindUsagesTest extends MavenDomTestCase {
  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();

    importProject("<groupId>test</groupId>" +
                  "<artifactId>module1</artifactId>" +
                  "<version>1</version>");
  }

  public void testFindModelPropertyFromReference() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>project.version}</name>" +
                     "<description>${project.version}</description>");

    assertSearchResults(myProjectPom,
                        findTag("project.name"),
                        findTag("project.description"));
  }

  public void testFindModelPropertyFromReferenceWithDifferentQualifiers() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<version>1</version>" +

                     "<name>${<caret>version}</name>" +
                     "<description>${pom.version}</description>");

    assertSearchResults(myProjectPom,
                        findTag("project.name"),
                        findTag("project.description"));
  }

  public void testFindUsagesFromTag() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<<caret>version>1</version>" +

                     "<name>${project.version}</name>" +
                     "<description>${version}</description>");

    assertSearchResults(myProjectPom,
                        findTag("project.name"),
                        findTag("project.description"));
  }

  public void testFindUsagesFromTagValue() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<version>1<caret>1</version>" +

                     "<name>${project.version}</name>");

    assertSearchResults(myProjectPom, findTag("project.name"));
  }

  public void testFindUsagesFromProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<version>11</version>" +
                     "<name>${foo}</name>" +
                     "<properties>" +
                     "  <f<caret>oo>value</foo>" +
                     "</properties>");

    assertSearchResultsInclude(myProjectPom, findTag("project.name"));
  }

  public void testFindUsagesForEnvProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<version>11</version>" +
                     "<name>${env.<caret>" + getEnvVar() + "}</name>" +
                     "<description>${env." + getEnvVar() + "}</description>");

    assertSearchResultsInclude(myProjectPom, findTag("project.name"), findTag("project.description"));
  }

  public void testFindUsagesForSystemProperty() throws Exception {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<version>11</version>" +
                     "<name>${use<caret>r.home}</name>" +
                     "<description>${user.home}</description>");

    assertSearchResultsInclude(myProjectPom, findTag("project.name"), findTag("project.description"));
  }

  public void testFindUsagesForSystemPropertyInFilteredResources() throws Exception {
    createProjectSubDir("res");

    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>" +
                  "<name>${user.home}</name>" +

                  "<build>" +
                  "  <resources>" +
                  "    <resource>" +
                  "      <directory>res</directory>" +
                  "      <filtering>true</filtering>" +
                  "    </resource>" +
                  "  </resources>" +
                  "</build>");

    VirtualFile f = createProjectSubFile("res/foo.properties",
                                         "foo=abc${user<caret>.home}abc");

    List<PsiElement> result = search(f);
    assertContain(result, findTag("project.name"), MavenDomUtil.findPropertyValue(myProject, f, "foo"));
  }

  public void testHighlightingFromTag() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>module1</artifactId>" +
                     "<<caret>version>1</version>" +

                     "<name>${project.version}</name>" +
                     "<description>${version}</description>");

    assertHighlighted(myProjectPom,
                      new HighlightInfo(findTag("project.name"), "project.version"),
                      new HighlightInfo(findTag("project.description"), "version"));
  }
}
