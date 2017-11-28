package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;

import java.util.Arrays;

/**
 * @author Sergey Evdokimov
 */
public class MavenDependencySmartCompletionTest extends MavenDomWithIndicesTestCase {

  public void testCompletion() {
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

  public void testInsertDependency() {
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

  public void testInsertManagedDependency() {
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

  public void testInsertManagedDependencyWithTypeAndClassifier() {
    createProjectPom("<groupId>test</groupId>\n" +
                     "<artifactId>project</artifactId>\n" +
                     "<version>1</version>\n" +
                     "<properties>\n" +
                     "  <junitClassifier>sources</junitClassifier>\n" +
                     "  <junitType>test-jar</junitType>\n" +
                     "</properties>\n" +

                     "<dependencyManagement>\n" +
                     "  <dependencies>\n" +
                     "    <dependency>\n" +
                     "      <groupId>junit</groupId>\n" +
                     "      <artifactId>junit</artifactId>\n" +
                     "      <version>4.0</version>\n" +
                     "      <type>${junitType}</type>\n" +
                     "      <classifier>${junitClassifier}</classifier>\n" +
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
                                       "<properties>\n" +
                                       "  <junitClassifier>sources</junitClassifier>\n" +
                                       "  <junitType>test-jar</junitType>\n" +
                                       "</properties>\n" +

                                       "<dependencyManagement>\n" +
                                       "  <dependencies>\n" +
                                       "    <dependency>\n" +
                                       "      <groupId>junit</groupId>\n" +
                                       "      <artifactId>junit</artifactId>\n" +
                                       "      <version>4.0</version>\n" +
                                       "      <type>${junitType}</type>\n" +
                                       "      <classifier>${junitClassifier}</classifier>\n" +
                                       "    </dependency>\n" +
                                       "  </dependencies>\n" +
                                       "</dependencyManagement>\n" +

                                       "<dependencies>\n" +
                                       "    <dependency>\n" +
                                       "        <groupId>junit</groupId>\n" +
                                       "        <artifactId>junit</artifactId>\n" +
                                       "        <type>${junitType}</type>\n" +
                                       "        <classifier>${junitClassifier}</classifier>\n" +
                                       "    </dependency>\n" +
                                       "</dependencies>\n"));
  }

  public void testCompletionArtifactIdThenVersion() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>\n" +

                     "<dependencies>\n" +
                     "  <dependency>\n" +
                     "    <artifactId>juni<caret></artifactId>\n" +
                     "  </dependency>\n" +
                     "</dependencies>\n");

    myFixture.configureFromExistingVirtualFile(myProjectPom);

    LookupElement[] elements = myFixture.completeBasic();
    assertSize(1, elements);

    myFixture.type('\n');

    myFixture.checkResult(createPomXml("<groupId>test</groupId>" +
                                       "<artifactId>project</artifactId>" +
                                       "<version>1</version>\n" +

                                       "<dependencies>\n" +
                                       "  <dependency>\n" +
                                       "      <groupId>junit</groupId>\n" +
                                       "      <artifactId>junit</artifactId>\n" +
                                       "      <version><caret></version>\n" +
                                       "  </dependency>\n" +
                                       "</dependencies>\n"));

    myFixture.getLookupElementStrings().containsAll(Arrays.asList("3.8.1", "4.0"));
  }

  public void testCompletionArtifactIdThenGroupIdThenInsertVersion() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>\n" +

                     "<dependencies>\n" +
                     "  <dependency>\n" +
                     "    <artifactId>as<caret></artifactId>\n" +
                     "  </dependency>\n" +
                     "</dependencies>\n");

    myFixture.configureFromExistingVirtualFile(myProjectPom);

    LookupElement[] elements = myFixture.completeBasic();
    assertSize(2, elements);

    myFixture.type('\n');

    assertUnorderedElementsAreEqual(myFixture.getLookupElementStrings(), "asm", "org.ow2.asm");

    myFixture.type("org\n");

    myFixture.checkResult(createPomXml("<groupId>test</groupId>" +
                                       "<artifactId>project</artifactId>" +
                                       "<version>1</version>\n" +

                                       "<dependencies>\n" +
                                       "  <dependency>\n" +
                                       "      <groupId>org.ow2.asm</groupId>\n" +
                                       "      <artifactId>asm</artifactId>\n" +
                                       "      <version>4.1</version>\n" +
                                       "  </dependency>\n" +
                                       "</dependencies>\n"));
  }

  public void testCompletionArtifactIdThenGroupIdThenCompleteVersion() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>\n" +

                     "<dependencies>\n" +
                     "  <dependency>\n" +
                     "    <artifactId>as<caret></artifactId>\n" +
                     "  </dependency>\n" +
                     "</dependencies>\n");

    myFixture.configureFromExistingVirtualFile(myProjectPom);

    LookupElement[] elements = myFixture.completeBasic();
    assertSize(2, elements);

    myFixture.type('\n');

    assertUnorderedElementsAreEqual(myFixture.getLookupElementStrings(), "asm", "org.ow2.asm");

    myFixture.type("asm\n");

    myFixture.checkResult(createPomXml("<groupId>test</groupId>" +
                                       "<artifactId>project</artifactId>" +
                                       "<version>1</version>\n" +

                                       "<dependencies>\n" +
                                       "  <dependency>\n" +
                                       "      <groupId>asm</groupId>\n" +
                                       "      <artifactId>asm</artifactId>\n" +
                                       "      <version><caret></version>\n" +
                                       "  </dependency>\n" +
                                       "</dependencies>\n"));

    myFixture.getLookupElementStrings().equals(Arrays.asList("3.3", "3.3.1"));
  }

  public void testCompletionArtifactIdWithFullInsert() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>\n" +

                     "<dependencies>\n" +
                     "  <dependency>\n" +
                     "    <artifactId>common-i<caret></artifactId>\n" +
                     "  </dependency>\n" +
                     "</dependencies>\n");

    myFixture.configureFromExistingVirtualFile(myProjectPom);

    LookupElement[] elements = myFixture.completeBasic();
    assertSize(1, elements);

    myFixture.type('\n');

    myFixture.checkResult(createPomXml("<groupId>test</groupId>" +
                                       "<artifactId>project</artifactId>" +
                                       "<version>1</version>\n" +

                                       "<dependencies>\n" +
                                       "  <dependency>\n" +
                                       "      <groupId>commons-io</groupId>\n" +
                                       "      <artifactId>commons-io</artifactId>\n" +
                                       "      <version>2.4</version>\n" +
                                       "  </dependency>\n" +
                                       "</dependencies>\n"));
  }

  public void testCompletionArtifactIdInsideManagedDependency() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>\n" +

                     "<dependencyManagement>\n" +
                     "    <dependencies>\n" +
                     "        <dependency>\n" +
                     "            <artifactId>commons-i<caret></artifactId>\n" +
                     "        </dependency>\n" +
                     "    </dependencies>\n" +
                     "</dependencyManagement>\n");

    myFixture.configureFromExistingVirtualFile(myProjectPom);

    LookupElement[] elements = myFixture.completeBasic();
    assertSize(1, elements);
    myFixture.type('\n');

    myFixture.checkResult(createPomXml("<groupId>test</groupId>" +
                                       "<artifactId>project</artifactId>" +
                                       "<version>1</version>\n" +

                                       "<dependencyManagement>\n" +
                                       "    <dependencies>\n" +
                                       "        <dependency>\n" +
                                       "            <groupId>commons-io</groupId>\n" +
                                       "            <artifactId>commons-io</artifactId>\n" +
                                       "            <version>2.4</version>\n" +
                                       "        </dependency>\n" +
                                       "    </dependencies>\n" +
                                       "</dependencyManagement>\n"));
  }

  public void testCompletionArtifactIdWithManagedDependency() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>\n" +
                  "" +
                  "  <dependencyManagement>\n" +
                  "    <dependencies>\n" +
                  "      <dependency>\n" +
                  "        <groupId>commons-io</groupId>\n" +
                  "        <artifactId>commons-io</artifactId>\n" +
                  "        <version>2.4</version>\n" +
                  "      </dependency>\n" +
                  "    </dependencies>\n" +
                  "  </dependencyManagement>\n");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>\n" +
                     "  <dependencyManagement>\n" +
                     "    <dependencies>\n" +
                     "      <dependency>\n" +
                     "        <groupId>commons-io</groupId>\n" +
                     "        <artifactId>commons-io</artifactId>\n" +
                     "        <version>2.4</version>\n" +
                     "      </dependency>\n" +
                     "    </dependencies>\n" +
                     "  </dependencyManagement>\n" +

                     "<dependencies>\n" +
                     "  <dependency>\n" +
                     "    <artifactId>common-i<caret></artifactId>\n" +
                     "  </dependency>\n" +
                     "</dependencies>\n");

    myFixture.configureFromExistingVirtualFile(myProjectPom);

    LookupElement[] elements = myFixture.completeBasic();
    assertSize(1, elements);
    myFixture.type('\n');

    myFixture.checkResult(createPomXml("<groupId>test</groupId>" +
                                       "<artifactId>project</artifactId>" +
                                       "<version>1</version>\n" +

                                       "  <dependencyManagement>\n" +
                                       "    <dependencies>\n" +
                                       "      <dependency>\n" +
                                       "        <groupId>commons-io</groupId>\n" +
                                       "        <artifactId>commons-io</artifactId>\n" +
                                       "        <version>2.4</version>\n" +
                                       "      </dependency>\n" +
                                       "    </dependencies>\n" +
                                       "  </dependencyManagement>\n" +

                                       "<dependencies>\n" +
                                       "  <dependency>\n" +
                                       "      <groupId>commons-io</groupId>\n" +
                                       "      <artifactId>commons-io</artifactId>\n" +
                                       "  </dependency>\n" +
                                       "</dependencies>\n"
    ));
  }

  public void testCompletionGroupIdWithManagedDependencyWithTypeAndClassifier() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>\n" +
                  "<properties>\n" +
                  "  <ioClassifier>ccc</ioClassifier>" +
                  "  <ioType>ttt</ioType>" +
                  "</properties>\n" +
                  "" +
                  "<dependencyManagement>\n" +
                  "  <dependencies>\n" +
                  "    <dependency>\n" +
                  "      <groupId>commons-io</groupId>\n" +
                  "      <artifactId>commons-io</artifactId>\n" +
                  "      <classifier>${ioClassifier}</classifier>\n" +
                  "      <type>${ioType}</type>\n" +
                  "      <version>2.4</version>\n" +
                  "    </dependency>\n" +
                  "  </dependencies>\n" +
                  "</dependencyManagement>\n");

    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>\n" +
                     "<dependencyManagement>\n" +
                     "  <dependencies>\n" +
                     "    <dependency>\n" +
                     "      <groupId>commons-io</groupId>\n" +
                     "      <artifactId>commons-io</artifactId>\n" +
                     "      <classifier>${ioClassifier}</classifier>\n" +
                     "      <type>${ioType}</type>\n" +
                     "      <version>2.4</version>\n" +
                     "    </dependency>\n" +
                     "  </dependencies>\n" +
                     "</dependencyManagement>\n" +

                     "<dependencies>\n" +
                     "  <dependency>\n" +
                     "      <groupId>commons-i<caret></groupId>\n" +
                     "      <artifactId>commons-io</artifactId>\n" +
                     "  </dependency>\n" +
                     "</dependencies>\n");

    myFixture.configureFromExistingVirtualFile(myProjectPom);

    LookupElement[] elements = myFixture.complete(CompletionType.SMART);
    assertNull(elements);
//    assertSize(1, elements);
//    myFixture.type('\n');

    myFixture.checkResult(createPomXml("<groupId>test</groupId>" +
                                       "<artifactId>project</artifactId>" +
                                       "<version>1</version>\n" +

                                       "<dependencyManagement>\n" +
                                       "  <dependencies>\n" +
                                       "    <dependency>\n" +
                                       "      <groupId>commons-io</groupId>\n" +
                                       "      <artifactId>commons-io</artifactId>\n" +
                                       "      <classifier>${ioClassifier}</classifier>\n" +
                                       "      <type>${ioType}</type>\n" +
                                       "      <version>2.4</version>\n" +
                                       "    </dependency>\n" +
                                       "  </dependencies>\n" +
                                       "</dependencyManagement>\n" +

                                       "<dependencies>\n" +
                                       "  <dependency>\n" +
                                       "      <groupId>commons-io</groupId>\n" +
                                       "      <artifactId>commons-io</artifactId>\n" +
                                       "      <type>${ioType}</type>\n" +
                                       "      <classifier>${ioClassifier}</classifier>\n" +
                                       "  </dependency>\n" +
                                       "</dependencies>\n"
    ));
  }

}
