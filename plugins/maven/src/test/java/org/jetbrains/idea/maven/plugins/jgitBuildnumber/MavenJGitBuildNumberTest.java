package org.jetbrains.idea.maven.plugins.jgitBuildnumber;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.dom.MavenDomTestCase;

import java.io.IOException;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class MavenJGitBuildNumberTest extends MavenDomTestCase {

  public void testCompletion() throws Exception {
    importProject("<groupId>test</groupId>\n" +
                  "<artifactId>project</artifactId>\n" +
                  "<version>1</version>\n" +
                  "<properties>\n" +
                  "  <aaa>${}</aaa>" +
                  "</properties>\n" +
                  "    <build>\n" +
                  "        <plugins>\n" +
                  "            <plugin>\n" +
                  "                <groupId>ru.concerteza.buildnumber</groupId>\n" +
                  "                <artifactId>maven-jgit-buildnumber-plugin</artifactId>\n" +
                  "            </plugin>\n" +
                  "        </plugins>\n" +
                  "    </build>\n"
       );

    createProjectPom("<groupId>test</groupId>\n" +
                     "<artifactId>project</artifactId>\n" +
                     "<version>1</version>\n" +
                     "<properties>\n" +
                     "  <aaa>${<caret>}</aaa>" +
                     "</properties>\n" +
                     "    <build>\n" +
                     "        <plugins>\n" +
                     "            <plugin>\n" +
                     "                <groupId>ru.concerteza.buildnumber</groupId>\n" +
                     "                <artifactId>maven-jgit-buildnumber-plugin</artifactId>\n" +
                     "            </plugin>\n" +
                     "        </plugins>\n" +
                     "    </build>\n"
    );

    List<String> variants = getCompletionVariants(myProjectPom);

    assertContain(variants, "git.commitsCount");
  }

  public void testHighlighting() throws Exception {
    createModulePom("m", "<artifactId>m</artifactId>\n" +
                         "<version>1</version>\n" +
                         "<parent>\n" +
                         "  <groupId>test</groupId>\n" +
                         "  <artifactId>project</artifactId>\n" +
                         "  <version>1</version>\n" +
                         "</parent>\n" +
                         "<properties>\n" +
                         "  <aaa>${git.commitsCount}</aaa>\n" +
                         "  <bbb>${git.commitsCount__}</bbb>\n" +
                         "</properties>\n");

    importProject("<groupId>test</groupId>\n" +
                  "<artifactId>project</artifactId>\n" +
                  "<version>1</version>\n" +
                  "<packaging>pom</packaging>\n" +
                  "<modules>\n" +
                  "  <module>m</module>\n" +
                  "</modules>\n" +

                  "    <build>\n" +
                  "        <plugins>\n" +
                  "            <plugin>\n" +
                  "                <groupId>ru.concerteza.buildnumber</groupId>\n" +
                  "                <artifactId>maven-jgit-buildnumber-plugin</artifactId>\n" +
                  "            </plugin>\n" +
                  "        </plugins>\n" +
                  "    </build>\n"
    );

    VirtualFile pom = createModulePom("m", "<artifactId>m</artifactId>\n" +
                         "<version>1</version>\n" +
                         "<parent>\n" +
                         "  <groupId>test</groupId>\n" +
                         "  <artifactId>project</artifactId>\n" +
                         "  <version>1</version>\n" +
                         "</parent>\n" +
                         "<properties>\n" +
                         "  <aaa>${git.commitsCount}</aaa>\n" +
                         "  <bbb>${<error>git.commitsCount__</error>}</bbb>\n" +
                         "</properties>\n");

    checkHighlighting(pom, true, false, true);
  }

  public void testNoPluginHighlighting() throws Exception {
    importProject("<groupId>test</groupId>\n" +
                  "<artifactId>project</artifactId>\n" +
                  "<version>1</version>\n" +
                  "<properties>\n" +
                  "  <aaa>${git.commitsCount}</aaa>" +
                  "</properties>\n"
       );

    createProjectPom("<groupId>test</groupId>\n" +
                     "<artifactId>project</artifactId>\n" +
                     "<version>1</version>\n" +
                     "<properties>\n" +
                     "  <aaa>${<error>git.commitsCount</error>}</aaa>" +
                     "</properties>\n");

    checkHighlighting(myProjectPom);
  }


}
