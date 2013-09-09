package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.completion.CompletionType;

import java.io.IOException;

/**
 * @author Sergey Evdokimov
 */
public class MavenDependencySmartCompletionTest extends MavenDomWithIndicesTestCase {

  public void testCompletion() throws IOException {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    <caret>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariantsInclude(myProjectPom, "junit:junit");
  }

  public void testInsertDependency() throws IOException {
    createProjectPom("<groupId>test</groupId>\n" +
                     "<artifactId>project</artifactId>\n" +
                     "<version>1</version>\n" +

                     "<dependencies>\n" +
                     "  <dependency>ju<caret></dependency>\n" +
                     "</dependencies>\n");

    configTest(myProjectPom);
    myFixture.complete(CompletionType.SMART);
    assertContain(myFixture.getLookupElementStrings(), "4.0", "3.8.2");

    myFixture.checkResult(createPomXml("<groupId>test</groupId>\n" +
                                       "<artifactId>project</artifactId>\n" +
                                       "<version>1</version>\n" +

                                       "<dependencies>\n" +
                                       "    <dependency>\n" +
                                       "        <groupId>junit</groupId>\n" +
                                       "        <artifactId>junit</artifactId>\n" +
                                       "        <version><caret></version>\n" +
                                       "    </dependency>\n" +
                                       "</dependencies>\n"));
  }

  public void testInsertManagedDependency() throws IOException {
    createProjectPom("<groupId>test</groupId>\n" +
                     "<artifactId>project</artifactId>\n" +
                     "<version>1</version>\n" +

                     "<dependencyManagement>\n" +
                     "  <dependencies>\n" +
                     "    <dependency>\n" +
                     "      <groupId>junit</groupId>\n" +
                     "      <artifactId>junit</artifactId>\n" +
                     "      <version>4.0</version>\n" +
                     "    </dependency>\n" +
                     "  </dependencies>\n" +
                     "</dependencyManagement>\n" +

                     "<dependencies>\n" +
                     "  <dependency>ju<caret></dependency>\n" +
                     "</dependencies>\n");

    configTest(myProjectPom);
    myFixture.complete(CompletionType.SMART);

    myFixture.checkResult(createPomXml("<groupId>test</groupId>\n" +
                                       "<artifactId>project</artifactId>\n" +
                                       "<version>1</version>\n" +
                                       "<dependencyManagement>\n" +
                                       "  <dependencies>\n" +
                                       "    <dependency>\n" +
                                       "      <groupId>junit</groupId>\n" +
                                       "      <artifactId>junit</artifactId>\n" +
                                       "      <version>4.0</version>\n" +
                                       "    </dependency>\n" +
                                       "  </dependencies>\n" +
                                       "</dependencyManagement>\n" +
                                       "<dependencies>\n" +
                                       "    <dependency>\n" +
                                       "        <groupId>junit</groupId>\n" +
                                       "        <artifactId>junit</artifactId>\n" +
                                       "    </dependency>\n" +
                                       "</dependencies>\n"));
  }

  public void testInsertManagedDependencyWithTypeAndClassifier() throws IOException {
    createProjectPom("<groupId>test</groupId>\n" +
                     "<artifactId>project</artifactId>\n" +
                     "<version>1</version>\n" +

                     "<dependencyManagement>\n" +
                     "  <dependencies>\n" +
                     "    <dependency>\n" +
                     "      <groupId>junit</groupId>\n" +
                     "      <artifactId>junit</artifactId>\n" +
                     "      <version>4.0</version>\n" +
                     "      <type>test-jar</type>\n" +
                     "      <classifier>sources</classifier>\n" +
                     "    </dependency>\n" +
                     "  </dependencies>\n" +
                     "</dependencyManagement>\n" +

                     "<dependencies>\n" +
                     "  <dependency>ju<caret></dependency>\n" +
                     "</dependencies>\n");

    configTest(myProjectPom);
    myFixture.complete(CompletionType.SMART);

    myFixture.checkResult(createPomXml("<groupId>test</groupId>\n" +
                                       "<artifactId>project</artifactId>\n" +
                                       "<version>1</version>\n" +

                                       "<dependencyManagement>\n" +
                                       "  <dependencies>\n" +
                                       "    <dependency>\n" +
                                       "      <groupId>junit</groupId>\n" +
                                       "      <artifactId>junit</artifactId>\n" +
                                       "      <version>4.0</version>\n" +
                                       "      <type>test-jar</type>\n" +
                                       "      <classifier>sources</classifier>\n" +
                                       "    </dependency>\n" +
                                       "  </dependencies>\n" +
                                       "</dependencyManagement>\n" +

                                       "<dependencies>\n" +
                                       "    <dependency>\n" +
                                       "        <groupId>junit</groupId>\n" +
                                       "        <artifactId>junit</artifactId>\n" +
                                       "        <type>test-jar</type>\n" +
                                       "        <classifier>sources</classifier>\n" +
                                       "    </dependency>\n" +
                                       "</dependencies>\n"));
  }

}
