package org.jetbrains.idea.maven.plugins

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Sergey Evdokimov
 */
class MavenParameterFixedValueTest extends LightCodeInsightFixtureTestCase {

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

  <build>
    <plugins>
      <plugin>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
          <compilerReuseStrategy><caret></compilerReuseStrategy>
        </configuration>
      </plugin>
    </plugins>
  </build>

</project>
""")

    myFixture.completeBasic()

    assertSameElements(['reuseCreated', 'reuseSame', 'alwaysNew'], myFixture.lookupElementStrings)
  }

}
