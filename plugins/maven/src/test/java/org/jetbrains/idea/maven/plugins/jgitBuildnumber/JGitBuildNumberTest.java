package org.jetbrains.idea.maven.plugins.jgitBuildnumber;

import org.jetbrains.idea.maven.dom.MavenDomTestCase;

import java.io.IOException;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class JGitBuildNumberTest extends MavenDomTestCase {

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
    importProject("<groupId>test</groupId>\n" +
                  "<artifactId>project</artifactId>\n" +
                  "<version>1</version>\n" +
                  "<properties>\n" +
                  "  <aaa>${git.commitsCount}</aaa>" +
                  "  <bbb>${git.commitsCount__}</bbb>" +
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
                     "  <aaa>${git.commitsCount}</aaa>" +
                     "  <bbb>${<error>git.commitsCount__</error>}</bbb>" +
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

    checkHighlighting(myProjectPom);
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
