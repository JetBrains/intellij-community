package org.jetbrains.idea.maven.inspections.dom;

import com.intellij.maven.testFramework.MavenDomTestCase;
import org.jetbrains.idea.maven.dom.inspections.MavenDuplicatePluginInspection;
import org.junit.Test;

public class MavenDuplicatedPluginInspectionTest extends MavenDomTestCase {
  @Override
  public boolean runInDispatchThread() { return true; }

  @Test
  public void testDuplicatedPlugin() {
    getFixture().enableInspections(MavenDuplicatePluginInspection.class);

    createProjectPom("""
                       <groupId>mavenParent</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                         
                       <build>
                         <plugins>
                           <<warning>plugin</warning>>
                             <groupId>org.apache.maven.plugins</groupId>
                             <artifactId>maven-jar-plugin</artifactId>
                             <version>2.2</version>
                           </plugin>
                           <<warning>plugin</warning>>
                             <groupId>org.apache.maven.plugins</groupId>
                             <artifactId>maven-jar-plugin</artifactId>
                             <version>2.2</version>
                           </plugin>
                         </plugins>
                       </build>""");

    checkHighlighting();
  }
}
