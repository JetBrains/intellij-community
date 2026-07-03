// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.groovy

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenGroovyInjectionTest(mavenVersion: String, modelVersion: String)  {
  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )

    @Test
    fun testCompletion() {
        maven.fixture.configureByText(
            "pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>

        <groupId>simpleMaven</groupId>
        <artifactId>simpleMaven</artifactId>
        <version>1.0</version>

        <packaging>jar</packaging>

        <build>
          <plugins>
            <plugin>
              <groupId>org.codehaus.groovy.maven</groupId>
              <artifactId>gmaven-plugin</artifactId>
              <version>1.0</version>
              <executions>
                <execution>
                  <id>groovy-magic</id>
                  <phase>package</phase>
                  <goals>
                    <goal>execute</goal>
                  </goals>
                  <configuration>
                    <source>
                      String<caret>
                    </source>
                  </configuration>
                </execution>
              </executions>
              <dependencies>
                <dependency>
                  <groupId>org.apache.ant</groupId>
                  <artifactId>ant-nodeps</artifactId>
                  <version>1.8.0</version>
                </dependency>
              </dependencies>
            </plugin>
          </plugins>
        </build>

      </project>
      
      """.trimIndent()
        )

        maven.fixture.completeBasic()

        val lookups = maven.fixture.getLookupElementStrings()
        assert(lookups!!.containsAll(listOf("String", "StringBuffer", "StringBuilder")))
    }

    @Test
    fun testCompletion2() {
        maven.fixture.configureByText(
            "pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>

        <groupId>simpleMaven</groupId>
        <artifactId>simpleMaven</artifactId>
        <version>1.0</version>

        <packaging>jar</packaging>

        <build>
          <plugins>
                  <plugin>
                      <groupId>org.codehaus.gmaven</groupId>
                      <artifactId>gmaven-plugin</artifactId>
                      <version>1.3</version>
                      <configuration>
                          <!-- http://groovy.codehaus.org/The+groovydoc+Ant+task -->
                          <source>
                              String<caret>
                          </source>
                      </configuration>
                  </plugin>
          </plugins>
        </build>

      </project>
      
      """.trimIndent()
        )

        maven.fixture.completeBasic()

        val lookups = maven.fixture.getLookupElementStrings()
        assert(lookups!!.containsAll(listOf("String", "StringBuffer", "StringBuilder")))
    }

    @Test
    fun testCompletion3() {
        maven.fixture.configureByText(
            "pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>

        <groupId>simpleMaven</groupId>
        <artifactId>simpleMaven</artifactId>
        <version>1.0</version>

        <packaging>jar</packaging>

        <build>
          <plugins>
                  <plugin>
                      <groupId>org.codehaus.gmaven</groupId>
                      <artifactId>groovy-maven-plugin</artifactId>
                      <version>1.3</version>
                      <configuration>
                          <!-- http://groovy.codehaus.org/The+groovydoc+Ant+task -->
                          <source>
                              String<caret>
                          </source>
                      </configuration>
                  </plugin>
          </plugins>
        </build>

      </project>
      
      """.trimIndent()
        )

        maven.fixture.completeBasic()

        val lookups = maven.fixture.getLookupElementStrings()
        assert(lookups!!.containsAll(listOf("String", "StringBuffer", "StringBuilder")))
    }

    @Test
    fun testInjectionVariables() = runBlocking {
        maven.fixture.configureByText(
            "pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>

        <groupId>simpleMaven</groupId>
        <artifactId>simpleMaven</artifactId>
        <version>1.0</version>

        <packaging>jar</packaging>

        <build>
          <plugins>
                  <plugin>
                      <groupId>org.codehaus.gmaven</groupId>
                      <artifactId>gmaven-plugin</artifactId>
                      <version>1.3</version>
                      <configuration>
                          <!-- http://groovy.codehaus.org/The+groovydoc+Ant+task -->
                          <source>
                              println project<caret>
                          </source>
                      </configuration>
                  </plugin>
          </plugins>
        </build>

      </project>
      
      """.trimIndent()
        )

      withContext(Dispatchers.EDT) {
        val element = maven.fixture.elementAtCaret
        assert(element is GrVariable)
        assert((element as GrVariable).declaredType!!.presentableText == "MavenProject")
      }
    }

    @Test
    fun testHighlighting() {
        maven.fixture.configureByText(
            "pom.xml", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>

        <groupId>simpleMaven</groupId>
        <artifactId>simpleMaven</artifactId>
        <version>1.0</version>

        <packaging>jar</packaging>

        <build>
          <plugins>
                  <plugin>
                      <groupId>org.codehaus.gmaven</groupId>
                      <artifactId>gmaven-plugin</artifactId>
                      <version>1.3</version>
                      <configuration>
                          <!-- http://groovy.codehaus.org/The+groovydoc+Ant+task -->
                          <source>
                              import java.lang.String;

                              class SomeClass { public static String buildHi() { return "Hi 2!" } }
                              println SomeClass.buildHi()
                          </source>
                      </configuration>
                  </plugin>
          </plugins>
        </build>

      </project>
      
      """.trimIndent()
        )

        maven.fixture.checkHighlighting(true, false, true)
    }
}
