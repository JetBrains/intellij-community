package org.jetbrains.idea.maven.dom

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.containers.ContainerUtil

/**
 * @author Sergey Evdokimov
 */
class MavenDomAnnotatorTest extends MavenDomTestCase {

  public void testAnnotatePlugin() {
    def modulePom = createModulePom("m", """
<parent>
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
</parent>
    <artifactId>m</artifactId>

    <build>
        <plugins>
            <plugin>
                <artifactId>maven-compiler-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
""")

    importProject("""
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1</version>

<modules>
  <module>m</module>
</modules>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
""")

    checkGutters(PsiManager.getInstance(myProject).findFile(modulePom), "<artifactId>maven-compiler-plugin</artifactId>", """<parent>
  <groupId>test</groupId>
  <artifactId>project</artifactId>
  <version>1</version>
</parent>""")
  }

  protected void checkGutters(PsiFile file, String ... expectedProperties) {
    myFixture.configureFromExistingVirtualFile(file.virtualFile);

    String text = file.getText();

    Set<String> actualProperties = new HashSet<String>();

    for (com.intellij.codeInsight.daemon.impl.HighlightInfo h : myFixture.doHighlighting()) {
      if (h.getGutterIconRenderer() != null) {
        String s = text.substring(h.getStartOffset(), h.getEndOffset());
        actualProperties.add(s);
      }
    }

    assertEquals(ContainerUtil.newHashSet(expectedProperties), actualProperties);
  }

}
