package org.jetbrains.idea.maven.importing;

import com.intellij.maven.testFramework.MavenDomTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import org.junit.Test;

public class MavenIdeaPluginTest extends MavenDomTestCase {
  @Test
  public void testConfigureJdk() {
    importProject(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
        <build>
          <plugins>
            <plugin>
                <groupId>com.googlecode</groupId>
                <artifactId>maven-idea-plugin</artifactId>
                <version>1.6.1</version>

                <configuration>
                  <jdkName>invalidJdk</jdkName>
                </configuration>
            </plugin>
          </plugins>
        </build>
        """);

    Module module = getModule("project");
    assert module != null;

    assert !ModuleRootManager.getInstance(module).isSdkInherited();
    assert ModuleRootManager.getInstance(module).getSdk() == null;
  }
}
