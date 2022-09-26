package org.jetbrains.idea.maven.dom;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import org.junit.Test;

import java.util.Arrays;

/**
 * @author Sergey Evdokimov
 */
public class MavenDependencySmartCompletionTest extends MavenDomWithIndicesTestCase {


  @Test
  public void testCompletion() {
    createProjectPom("<groupId>test</groupId>" +
                     "<artifactId>project</artifactId>" +
                     "<version>1</version>" +

                     "<dependencies>" +
                     "  <dependency>" +
                     "    ju<caret>" +
                     "  </dependency>" +
                     "</dependencies>");

    assertCompletionVariantsInclude(myProjectPom, RENDERING_TEXT, "junit:junit");
  }

  @Test
  public void testInsertDependency() {
    createProjectPom("<groupId>test</groupId>\n" +
                     "<artifactId>project</artifactId>\n" +
                     "<version>1</version>\n" +

                     "<dependencies>\n" +
                     "  <dependency>juni<caret></dependency>\n" +
                     "</dependencies>\n");

    configTest(myProjectPom);
    LookupElement[] elements = myFixture.completeBasic();
    assertCompletionVariants(myFixture, RENDERING_TEXT, "junit:junit");
    assertSize(1, elements);

    myFixture.type('\n');


    myFixture.checkResult(createPomXml("<groupId>test</groupId>\n" +
                                       "<artifactId>project</artifactId>\n" +
                                       "<version>1</version>\n" +

                                       "<dependencies>\n" +
                                       "  <dependency>\n" +
                                       "      <groupId>junit</groupId>\n" +
                                       "      <artifactId>junit</artifactId>\n" +
                                       "      <version><caret></version>\n" +
                                       "      <scope>test</scope>\n" +
                                       "  </dependency>\n" +
                                       "</dependencies>\n"));
  }

  @Test
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
                     "  <dependency>junit:<caret></dependency>\n" +
                     "</dependencies>\n");

    configTest(myProjectPom);
    myFixture.complete(CompletionType.BASIC);
    assertCompletionVariants(myFixture, RENDERING_TEXT, "junit:junit");
    myFixture.type('\n');

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
                                       "  <dependency>\n" +
                                       "      <groupId>junit</groupId>\n" +
                                       "      <artifactId>junit</artifactId>\n" +
                                       "      <scope>test</scope>\n" +
                                       "  </dependency>\n" +
                                       "</dependencies>\n"));
  }

  @Test
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
                     "  <dependency>junit:<caret></dependency>\n" +
                     "</dependencies>\n");

    configTest(myProjectPom);

    LookupElement[] elements = myFixture.completeBasic();
    assertSize(1, elements);

    myFixture.type('\n');


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
                                       "  <dependency>\n" +
                                       "      <groupId>junit</groupId>\n" +
                                       "      <artifactId>junit</artifactId>\n" +
                                       "      <type>${junitType}</type>\n" +
                                       "      <classifier>${junitClassifier}</classifier>\n" +
                                       "      <scope>test</scope>\n" +
                                       "  </dependency>\n" +
                                       "</dependencies>\n"));
  }

  @Test
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

    elements = myFixture.completeBasic();
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
                                       "      <scope>test</scope>\n" +
                                       "  </dependency>\n" +
                                       "</dependencies>\n"));

    myFixture.getLookupElementStrings().containsAll(Arrays.asList("3.8.1", "4.0"));
  }

  @Test
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

    assertCompletionVariants(myFixture, RENDERING_TEXT, "asm-attrs", "asm", "asm-analysis", "asm-parent", "asm-tree", "asm-util");

    myFixture.type('\n');

    assertCompletionVariants(myFixture, RENDERING_TEXT, "asm");

    myFixture.type("\n");

    myFixture.checkResult(createPomXml("<groupId>test</groupId>" +
                                       "<artifactId>project</artifactId>" +
                                       "<version>1</version>\n" +

                                       "<dependencies>\n" +
                                       "  <dependency>\n" +
                                       "      <groupId>asm</groupId>\n" +
                                       "      <artifactId>asm-attrs</artifactId>\n" +
                                       "      <version>2.2.1</version>\n" +
                                       "  </dependency>\n" +
                                       "</dependencies>\n"));
  }

  @Test
  public void testCompletionArtifactIdNonExactmatch() {
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

    assertCompletionVariants(myFixture, RENDERING_TEXT, "commons-io");
  }

  @Test
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

    assertCompletionVariants(myFixture, RENDERING_TEXT, "commons-io");

    myFixture.type('\n');

    assertCompletionVariants(myFixture, RENDERING_TEXT, "2.4", "1.4");

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

  @Test
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

    elements = myFixture.completeBasic();
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

  @Test
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

    LookupElement[] elements = myFixture.complete(CompletionType.BASIC);
    assertSize(1, elements);
    myFixture.type('\n');

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
