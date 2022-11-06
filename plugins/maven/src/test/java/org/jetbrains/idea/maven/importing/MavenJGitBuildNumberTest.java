/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.importing;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.maven.testFramework.MavenDomTestCase;
import org.junit.Test;

import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class MavenJGitBuildNumberTest extends MavenDomTestCase {

  @Test
  public void testCompletion() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <aaa>${}</aaa></properties>
                        <build>
                            <plugins>
                                <plugin>
                                    <groupId>ru.concerteza.buildnumber</groupId>
                                    <artifactId>maven-jgit-buildnumber-plugin</artifactId>
                                </plugin>
                            </plugins>
                        </build>
                    """
    );

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <aaa>${<caret>}</aaa></properties>
                           <build>
                               <plugins>
                                   <plugin>
                                       <groupId>ru.concerteza.buildnumber</groupId>
                                       <artifactId>maven-jgit-buildnumber-plugin</artifactId>
                                   </plugin>
                               </plugins>
                           </build>
                       """
    );

    List<String> variants = getCompletionVariants(myProjectPom);

    assertContain(variants, "git.commitsCount");
  }

  @Test
  public void testHighlighting() {
    createModulePom("m", """
      <artifactId>m</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      <properties>
        <aaa>${git.commitsCount}</aaa>
        <bbb>${git.commitsCount__}</bbb>
      </properties>
      """);

    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                      <module>m</module>
                    </modules>
                        <build>
                            <plugins>
                                <plugin>
                                    <groupId>ru.concerteza.buildnumber</groupId>
                                    <artifactId>maven-jgit-buildnumber-plugin</artifactId>
                                </plugin>
                            </plugins>
                        </build>
                    """
    );

    VirtualFile pom = createModulePom("m", """
      <artifactId>m</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      <properties>
        <aaa>${git.commitsCount}</aaa>
        <bbb>${<error>git.commitsCount__</error>}</bbb>
      </properties>
      """);

    checkHighlighting(pom, true, false, true);
  }

  @Test
  public void testNoPluginHighlighting() {
    importProject("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <aaa>${git.commitsCount}</aaa></properties>
                    """
    );

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <aaa>${<error>git.commitsCount</error>}</aaa></properties>
                       """);

    checkHighlighting(myProjectPom);
  }


}
