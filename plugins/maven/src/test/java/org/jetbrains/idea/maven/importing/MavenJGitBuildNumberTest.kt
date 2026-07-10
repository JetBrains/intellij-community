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

import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture.Highlight
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assertContain
import com.intellij.maven.testFramework.fixtures.createModulePom
import com.intellij.maven.testFramework.fixtures.createProjectPom
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.jetbrains.idea.maven.fixtures.getCompletionVariants
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenJGitBuildNumberTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  @Test
  fun testCompletion() = runBlocking {
    maven.importProjectAsync("""
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

    maven.createProjectPom("""
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

    val variants = maven.getCompletionVariants(maven.projectPom)

    assertContain(variants, "git.commitsCount")
  }

  @Test
  fun testHighlighting() = runBlocking {
    maven.createModulePom("m", """
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

    maven.importProjectAsync("""
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
                                    <version>1.2.10</version>
                                </plugin>
                            </plugins>
                        </build>
                    """.trimIndent()
    )

    val pom = maven.createModulePom("m", """
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

    maven.checkHighlighting(pom)
  }

  @Test
  fun testNoPluginHighlighting() = runBlocking {
    maven.importProjectAsync("""
                    <groupId>test</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <properties>
                      <aaa>${'$'}{git.commitsCount}</aaa></properties>
                    """.trimIndent()
    )

    maven.checkHighlighting(maven.projectPom, Highlight(text = "git.commitsCount"))
  }
}
