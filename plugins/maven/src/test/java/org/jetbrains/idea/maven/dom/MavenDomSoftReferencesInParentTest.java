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
package org.jetbrains.idea.maven.dom;

/**
 * @author Sergey Evdokimov
 */
public class MavenDomSoftReferencesInParentTest extends MavenDomTestCase {

  public void testDoNotHighlightSourceDirectoryInParentPom() {
    importProject("<groupId>test</groupId>\n" +
                  "<artifactId>project</artifactId>\n" +
                  "<version>1</version>\n" +

                  "<packaging>pom</packaging>\n" +
                  "<build>\n" +
                  "<sourceDirectory>dsfsfd/sdfsdf</sourceDirectory>\n" +
                  "<testSourceDirectory>qwqwq/weqweqw</testSourceDirectory>\n" +
                  "<scriptSourceDirectory>dfsdf/fsdf</scriptSourceDirectory>\n" +
                  "</build>\n" +
                  "");

    checkHighlighting();
  }

  public void testHighlightSourceDirectory() {
    importProject("<groupId>test</groupId>\n" +
                  "<artifactId>project</artifactId>\n" +
                  "<version>1</version>\n" +

                  "<packaging>jar</packaging>\n" +

                  "<build>\n" +
                  "<sourceDirectory>foo1</sourceDirectory>\n" +
                  "<testSourceDirectory>foo2</testSourceDirectory>\n" +
                  "<scriptSourceDirectory>foo3</scriptSourceDirectory>\n" +
                  "</build>\n" +
                  "");

    createProjectPom("<groupId>test</groupId>\n" +
                 "<artifactId>project</artifactId>\n" +
                 "<version>1</version>\n" +

                 "<packaging>jar</packaging>\n" +

                 "<build>\n" +
                 //"<sourceDirectory><error descr=\"Cannot resolve file 'foo1'\">foo1</error></sourceDirectory>\n" +
                 //"<testSourceDirectory><error descr=\"Cannot resolve file 'foo2'\">foo2</error></testSourceDirectory>\n" +
                 //"<scriptSourceDirectory><error descr=\"Cannot resolve file 'foo3'\">foo3</error></scriptSourceDirectory>\n" +

                 "<sourceDirectory><error>foo1</error></sourceDirectory>\n" +
                 "<testSourceDirectory><error>foo2</error></testSourceDirectory>\n" +
                 "<scriptSourceDirectory><error>foo3</error></scriptSourceDirectory>\n" +
                 "</build>\n" +
                 "");

    checkHighlighting();
  }


}
