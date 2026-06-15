package com.intellij.maven.groovy

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.junit.Test

class MavenGroovyInjectionTest : LightJavaCodeInsightFixtureTestCase() {
    @Test
    fun testCompletion() {
        myFixture.configureByText(
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

        myFixture.completeBasic()

        val lookups = myFixture.getLookupElementStrings()
        assert(lookups!!.containsAll(ArrayList<String?>(mutableListOf<String?>("String", "StringBuffer", "StringBuilder"))))
    }

    @Test
    fun testCompletion2() {
        myFixture.configureByText(
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

        myFixture.completeBasic()

        val lookups = myFixture.getLookupElementStrings()
        assert(lookups!!.containsAll(ArrayList<String?>(mutableListOf<String?>("String", "StringBuffer", "StringBuilder"))))
    }

    @Test
    fun testCompletion3() {
        myFixture.configureByText(
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

        myFixture.completeBasic()

        val lookups = myFixture.getLookupElementStrings()
        assert(lookups!!.containsAll(ArrayList<String?>(mutableListOf<String?>("String", "StringBuffer", "StringBuilder"))))
    }

    @Test
    fun testInjectionVariables() {
        myFixture.configureByText(
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

        val element = myFixture.getElementAtCaret()

        assert(element is GrVariable)
        assert((element as GrVariable).getDeclaredType()!!.getPresentableText() == "MavenProject")
    }

    @Test
    fun testHighlighting() {
        myFixture.configureByText(
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

        myFixture.checkHighlighting(true, false, true)
    }
}
