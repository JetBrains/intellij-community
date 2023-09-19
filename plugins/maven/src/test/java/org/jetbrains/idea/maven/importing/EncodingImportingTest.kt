package org.jetbrains.idea.maven.importing;

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import groovy.lang.Closure;
import org.junit.Test;

import java.io.IOException;

public class EncodingImportingTest extends MavenMultiVersionImportingTestCase {
  @Test
  public void testEncodingDefinedByProperty() throws IOException {
    final byte[] text = new byte[]{-12, -59, -53, -45, -44};// Russian text in koi8-r encoding.

    final VirtualFile file = createProjectSubFile("src/main/resources/A.txt");
    ApplicationManager.getApplication().runWriteAction(new Closure(this, this) {
      public void doCall(Object it) throws IOException { file.setBinaryContent(text); }

      public void doCall() throws IOException {
        doCall(null);
      }
    });

    importProject(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>

        <properties>
        <project.build.sourceEncoding>koi8-r</project.build.sourceEncoding>
        </properties>
        """);

    String loadedText = VfsUtil.loadText(file);

    assert loadedText.equals(new String(text, "koi8-r"));
  }

  @Test
  public void testEncodingDefinedByPluginConfig() throws IOException {
    final byte[] text = new byte[]{-12, -59, -53, 45, -44};// Russian text in koi8-r encoding.

    final VirtualFile file = createProjectSubFile("src/main/resources/A.txt");
    ApplicationManager.getApplication().runWriteAction(new Closure(this, this) {
      public void doCall(Object it) throws IOException { file.setBinaryContent(text); }

      public void doCall() throws IOException {
        doCall(null);
      }
    });

    importProject(
      """
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>

          <build>
            <plugins>
              <plugin>
                <artifactId>maven-resources-plugin</artifactId>
                <configuration>
                  <encoding>koi8-r</encoding>
                </configuration>
              </plugin>
            </plugins>
          </build>
        """);

    String loadedText = VfsUtil.loadText(file);

    assert loadedText.equals(new String(text, "koi8-r"));
  }
}
