package org.jetbrains.idea.maven.plugins

import com.intellij.openapi.roots.ModuleRootManager
import org.jetbrains.idea.maven.dom.MavenDomTestCase

/**
 * @author Sergey Evdokimov
 */
class MavenIdeaPluginTest extends MavenDomTestCase {

  public void testConfigureJdk() {
    importProject("""
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
""")

    def module = getModule("project")
    assert module != null;

    assert !ModuleRootManager.getInstance(module).sdkInherited
    assert ModuleRootManager.getInstance(module).sdk == null
  }

}
