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
package org.jetbrains.idea.maven.importing

import com.intellij.maven.testFramework.MavenDomTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Test

class MavenJGitBuildNumberTest : MavenDomTestCase() {
  override fun runInDispatchThread() = true

  @Test
  fun testCompletion() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <aaa>${'$'}{}</aaa></properties>
                        <build>
                            <plugins>
                                <plugin>
                                    <groupId>ru.concerteza.buildnumber</groupId>
                                    <artifactId>maven-jgit-buildnumber-plugin</artifactId>
                                </plugin>
                            </plugins>
                        </build>
                    
                    """.trimIndent()
    )

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <aaa>${'$'}{<caret>}</aaa></properties>
                           <build>
                               <plugins>
                                   <plugin>
                                       <groupId>ru.concerteza.buildnumber</groupId>
                                       <artifactId>maven-jgit-buildnumber-plugin</artifactId>
                                   </plugin>
                               </plugins>
                           </build>
                       """.trimIndent()
    )

    val variants = getCompletionVariants(myProjectPom)

    assertContain(variants, "git.commitsCount")
  }

  @Test
  fun testHighlighting() = runBlocking {
    createModulePom("m", """
      <artifactId>m</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      <properties>
        <aaa>${'$'}{git.commitsCount}</aaa>
        <bbb>${'$'}{git.commitsCount__}</bbb>
      </properties>
      """.trimIndent())

    importProjectAsync("""
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
                    """.trimIndent()
    )

    val pom = createModulePom("m", """
      <artifactId>m</artifactId>
      <version>1</version>
      <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
      </parent>
      <properties>
        <aaa>${'$'}{git.commitsCount}</aaa>
        <bbb>${'$'}{<error>git.commitsCount__</error>}</bbb>
      </properties>
      """.trimIndent())

    checkHighlighting(pom)
  }

  @Test
  fun testNoPluginHighlighting() = runBlocking {
    importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <aaa>${'$'}{git.commitsCount}</aaa></properties>
                    """.trimIndent()
    )

    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <aaa>${'$'}{<error>git.commitsCount</error>}</aaa></properties>
                       """.trimIndent())

    checkHighlighting()
  }
}
