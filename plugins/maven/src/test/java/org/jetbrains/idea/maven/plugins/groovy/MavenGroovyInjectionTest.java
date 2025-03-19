package org.jetbrains.idea.maven.plugins.groovy;

import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MavenGroovyInjectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Test
  public void testCompletion() {
    myFixture.configureByText("pom.xml", """
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
      """);

    myFixture.completeBasic();

    List<String> lookups = myFixture.getLookupElementStrings();
    assert lookups.containsAll(new ArrayList<String>(Arrays.asList("String", "StringBuffer", "StringBuilder")));
  }

  @Test
  public void testCompletion2() {
    myFixture.configureByText("pom.xml", """
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
      """);

    myFixture.completeBasic();

    List<String> lookups = myFixture.getLookupElementStrings();
    assert lookups.containsAll(new ArrayList<String>(Arrays.asList("String", "StringBuffer", "StringBuilder")));
  }

  @Test
  public void testCompletion3() {
    myFixture.configureByText("pom.xml", """
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
      """);

    myFixture.completeBasic();

    List<String> lookups = myFixture.getLookupElementStrings();
    assert lookups.containsAll(new ArrayList<String>(Arrays.asList("String", "StringBuffer", "StringBuilder")));
  }

  @Test
  public void testInjectionVariables() {
    myFixture.configureByText("pom.xml", """
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
      """);

    PsiElement element = myFixture.getElementAtCaret();

    assert element instanceof GrVariable;
    assert ((GrVariable)element).getDeclaredType().getPresentableText().equals("MavenProject");
  }

  @Test
  public void testHighlighting() {
    myFixture.configureByText("pom.xml", """
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
      """);

    myFixture.checkHighlighting(true, false, true);
  }
}
