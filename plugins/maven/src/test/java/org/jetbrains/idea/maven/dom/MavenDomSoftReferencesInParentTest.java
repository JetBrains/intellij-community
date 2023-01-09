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

import com.intellij.maven.testFramework.MavenDomTestCase;
import org.junit.Test;

/**
 * @author Sergey Evdokimov
 */
public class MavenDomSoftReferencesInParentTest extends MavenDomTestCase {

  @Test
  public void testDoNotHighlightSourceDirectoryInParentPom() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <build>
                    <sourceDirectory>dsfsfd/sdfsdf</sourceDirectory>
                    <testSourceDirectory>qwqwq/weqweqw</testSourceDirectory>
                    <scriptSourceDirectory>dfsdf/fsdf</scriptSourceDirectory>
                    </build>
                    """);

    checkHighlighting();
  }

  @Test 
  public void testHighlightSourceDirectory() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>jar</packaging>
                    <build>
                    <sourceDirectory>foo1</sourceDirectory>
                    <testSourceDirectory>foo2</testSourceDirectory>
                    <scriptSourceDirectory>foo3</scriptSourceDirectory>
                    </build>
                    """);

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <packaging>jar</packaging>
                       <build>
                       <sourceDirectory><error>foo1</error></sourceDirectory>
                       <testSourceDirectory><error>foo2</error></testSourceDirectory>
                       <scriptSourceDirectory><error>foo3</error></scriptSourceDirectory>
                       </build>
                       """);

    checkHighlighting();
  }


}
