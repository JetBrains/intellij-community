/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.plugins.groovy

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable

/**
 * @author Sergey Evdokimov
 */
class MavenGroovyInjectionTest extends LightCodeInsightFixtureTestCase {

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
""")

    myFixture.completeBasic()

    def lookups = myFixture.lookupElementStrings
    assert lookups.containsAll(["String", "StringBuffer", "StringBuilder"])
  }

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
""")

    myFixture.completeBasic()

    def lookups = myFixture.lookupElementStrings
    assert lookups.containsAll(["String", "StringBuffer", "StringBuilder"])
  }

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
""")

    myFixture.completeBasic()

    def lookups = myFixture.lookupElementStrings
    assert lookups.containsAll(["String", "StringBuffer", "StringBuilder"])
  }

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
""")

    def element = myFixture.getElementAtCaret()

    assert element instanceof GrVariable
    assert element.getDeclaredType().getPresentableText() == "MavenProject"
  }

  public void testHighlighting() {
    myFixture.configureByText("pom.xml", """<?xml version="1.0" encoding="UTF-8"?>
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
""")

    myFixture.checkHighlighting(true, false, true)
  }

}
