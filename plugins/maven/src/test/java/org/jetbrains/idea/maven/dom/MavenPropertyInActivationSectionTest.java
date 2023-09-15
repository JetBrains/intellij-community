package org.jetbrains.idea.maven.dom;

import com.intellij.maven.testFramework.MavenDomTestCase;
import org.junit.Test;

import java.io.IOException;

public class MavenPropertyInActivationSectionTest extends MavenDomTestCase {
  @Test
  public void testResolvePropertyFromActivationSection() throws IOException {
    importProject(
      """
          <groupId>example</groupId>
          <artifactId>parent</artifactId>
          <packaging>jar</packaging>
          <version>1.0</version>
          <name>example</name>

          <profiles>
            <profile>
              <id>glassfish-env-path</id>
              <activation>
                <property>
                  <name>env.GLASSFISH_HOME_123</name>
                </property>
              </activation>

              <properties>
                <glassfish.home.path>${env.GLASSFISH_HOME_123}</glassfish.home.path>
              </properties>
            </profile>

          </profiles>

          <properties>
            <aaa>\\${env.GLASSFISH_HOME_123}</aaa>
          </properties>
        """);


    assert getReference(myProjectPom, "env.GLASSFISH_HOME_123", 1).resolve() != null;
    assert getReference(myProjectPom, "env.GLASSFISH_HOME_123", 2).resolve() == null;
  }
}
