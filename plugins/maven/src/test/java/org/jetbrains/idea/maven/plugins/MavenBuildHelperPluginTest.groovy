package org.jetbrains.idea.maven.plugins

import org.jetbrains.idea.maven.dom.MavenDomTestCase

/**
 * @author Sergey Evdokimov
 */
class MavenBuildHelperPluginTest extends MavenDomTestCase {

  public void testCompletion() {
    importProject("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>

  <build>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>build-helper-maven-plugin</artifactId>
        <executions>
          <execution>
            <id>yyy</id>
            <goals>
              <goal>reserve-network-port</goal>
            </goals>
            <configuration>
              <portNames>
                <portName>someNewProperty1</portName>
                <portName>someNewProperty2</portName>
              </portNames>
            </configuration>
          </execution>
          <execution>
            <id>xxx</id>
            <goals>
              <goal>foo</goal>
            </goals>
            <configuration>
              <portNames>
                <portName>someNewProperty3</portName>
              </portNames>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>

  <properties>
    <aaa>\${someNew}</aaa>
  </properties>
""")

    createProjectPom("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>

  <build>
    <plugins>
      <plugin>
        <groupId>org.codehaus.mojo</groupId>
        <artifactId>build-helper-maven-plugin</artifactId>
        <executions>
          <execution>
            <id>yyy</id>
            <goals>
              <goal>reserve-network-port</goal>
            </goals>
            <configuration>
              <portNames>
                <portName>someNewProperty1</portName>
                <portName>someNewProperty2</portName>
              </portNames>
            </configuration>
          </execution>
          <execution>
            <id>xxx</id>
            <goals>
              <goal>foo</goal>
            </goals>
            <configuration>
              <portNames>
                <portName>someNewProperty3</portName>
              </portNames>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>

  <properties>
    <aaa>\${someNew<caret>}</aaa>
  </properties>
""")

    assertCompletionVariants(myProjectPom, "someNewProperty1", "someNewProperty2")
  }

}
