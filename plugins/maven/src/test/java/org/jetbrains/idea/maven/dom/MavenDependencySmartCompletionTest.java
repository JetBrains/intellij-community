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
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencies>
                         <dependency>juni<caret></dependency>
                       </dependencies>
                       """);

    configTest(myProjectPom);
    LookupElement[] elements = myFixture.completeBasic();
    assertCompletionVariants(myFixture, RENDERING_TEXT, "junit:junit");
    assertSize(1, elements);

    myFixture.type('\n');


    myFixture.checkResult(createPomXml("""
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                         <version>1</version>
                                         <dependencies>
                                           <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <version><caret></version>
                                               <scope>test</scope>
                                           </dependency>
                                         </dependencies>
                                         """));
  }

  @Test
  public void testInsertManagedDependency() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <dependencyManagement>
                         <dependencies>
                           <dependency>
                             <groupId>junit</groupId>
                             <artifactId>junit</artifactId>
                             <version>4.0</version>
                           </dependency>
                         </dependencies>
                       </dependencyManagement>
                       <dependencies>
                         <dependency>junit:<caret></dependency>
                       </dependencies>
                       """);

    configTest(myProjectPom);
    myFixture.complete(CompletionType.BASIC);
    assertCompletionVariants(myFixture, RENDERING_TEXT, "junit:junit");
    myFixture.type('\n');

    myFixture.checkResult(createPomXml("""
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                         <version>1</version>
                                         <dependencyManagement>
                                           <dependencies>
                                             <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <version>4.0</version>
                                             </dependency>
                                           </dependencies>
                                         </dependencyManagement>
                                         <dependencies>
                                           <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <scope>test</scope>
                                           </dependency>
                                         </dependencies>
                                         """));
  }

  @Test
  public void testInsertManagedDependencyWithTypeAndClassifier() {
    createProjectPom("""
                       <groupId>test</groupId>
                       <artifactId>project</artifactId>
                       <version>1</version>
                       <properties>
                         <junitClassifier>sources</junitClassifier>
                         <junitType>test-jar</junitType>
                       </properties>
                       <dependencyManagement>
                         <dependencies>
                           <dependency>
                             <groupId>junit</groupId>
                             <artifactId>junit</artifactId>
                             <version>4.0</version>
                             <type>${junitType}</type>
                             <classifier>${junitClassifier}</classifier>
                           </dependency>
                         </dependencies>
                       </dependencyManagement>
                       <dependencies>
                         <dependency>junit:<caret></dependency>
                       </dependencies>
                       """);

    configTest(myProjectPom);

    LookupElement[] elements = myFixture.completeBasic();
    assertSize(1, elements);

    myFixture.type('\n');


    myFixture.checkResult(createPomXml("""
                                         <groupId>test</groupId>
                                         <artifactId>project</artifactId>
                                         <version>1</version>
                                         <properties>
                                           <junitClassifier>sources</junitClassifier>
                                           <junitType>test-jar</junitType>
                                         </properties>
                                         <dependencyManagement>
                                           <dependencies>
                                             <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <version>4.0</version>
                                               <type>${junitType}</type>
                                               <classifier>${junitClassifier}</classifier>
                                             </dependency>
                                           </dependencies>
                                         </dependencyManagement>
                                         <dependencies>
                                           <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <type>${junitType}</type>
                                               <classifier>${junitClassifier}</classifier>
                                               <scope>test</scope>
                                           </dependency>
                                         </dependencies>
                                         """));
  }

  @Test
  public void testCompletionArtifactIdThenVersion() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                       <dependencies>
                         <dependency>
                           <artifactId>juni<caret></artifactId>
                         </dependency>
                       </dependencies>
                       """);

    myFixture.configureFromExistingVirtualFile(myProjectPom);

    LookupElement[] elements = myFixture.completeBasic();
    assertSize(1, elements);

    myFixture.type('\n');

    elements = myFixture.completeBasic();
    assertSize(1, elements);
    myFixture.type('\n');

    myFixture.checkResult(createPomXml("""
                                         <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                                         <dependencies>
                                           <dependency>
                                               <groupId>junit</groupId>
                                               <artifactId>junit</artifactId>
                                               <version><caret></version>
                                               <scope>test</scope>
                                           </dependency>
                                         </dependencies>
                                         """));

    myFixture.getLookupElementStrings().containsAll(Arrays.asList("3.8.1", "4.0"));
  }

  @Test
  public void testCompletionArtifactIdThenGroupIdThenInsertVersion() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                       <dependencies>
                         <dependency>
                           <artifactId>as<caret></artifactId>
                         </dependency>
                       </dependencies>
                       """);

    myFixture.configureFromExistingVirtualFile(myProjectPom);

    LookupElement[] elements = myFixture.completeBasic();

    assertCompletionVariants(myFixture, RENDERING_TEXT, "asm-attrs", "asm", "asm-analysis", "asm-parent", "asm-tree", "asm-util");

    myFixture.type('\n');

    assertCompletionVariants(myFixture, RENDERING_TEXT, "asm");

    myFixture.type("\n");

    myFixture.checkResult(createPomXml("""
                                         <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                                         <dependencies>
                                           <dependency>
                                               <groupId>asm</groupId>
                                               <artifactId>asm-attrs</artifactId>
                                               <version>2.2.1</version>
                                           </dependency>
                                         </dependencies>
                                         """));
  }

  @Test
  public void testCompletionArtifactIdNonExactmatch() {
    importProject("<groupId>test</groupId>" +
                  "<artifactId>project</artifactId>" +
                  "<version>1</version>");

    createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                       <dependencies>
                         <dependency>
                           <artifactId>common-i<caret></artifactId>
                         </dependency>
                       </dependencies>
                       """);

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

    createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                       <dependencyManagement>
                           <dependencies>
                               <dependency>
                                   <artifactId>commons-i<caret></artifactId>
                               </dependency>
                           </dependencies>
                       </dependencyManagement>
                       """);

    myFixture.configureFromExistingVirtualFile(myProjectPom);

    LookupElement[] elements = myFixture.completeBasic();
    assertSize(1, elements);
    myFixture.type('\n');

    assertCompletionVariants(myFixture, RENDERING_TEXT, "commons-io");

    myFixture.type('\n');

    assertCompletionVariants(myFixture, RENDERING_TEXT, "2.4", "1.4");

    myFixture.type('\n');

    myFixture.checkResult(createPomXml("""
                                         <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                                         <dependencyManagement>
                                             <dependencies>
                                                 <dependency>
                                                     <groupId>commons-io</groupId>
                                                     <artifactId>commons-io</artifactId>
                                                     <version>2.4</version>
                                                 </dependency>
                                             </dependencies>
                                         </dependencyManagement>
                                         """));
  }

  @Test
  public void testCompletionArtifactIdWithManagedDependency() {
    importProject("""
                    <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                      <dependencyManagement>
                        <dependencies>
                          <dependency>
                            <groupId>commons-io</groupId>
                            <artifactId>commons-io</artifactId>
                            <version>2.4</version>
                          </dependency>
                        </dependencies>
                      </dependencyManagement>
                    """);

    createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                         <dependencyManagement>
                           <dependencies>
                             <dependency>
                               <groupId>commons-io</groupId>
                               <artifactId>commons-io</artifactId>
                               <version>2.4</version>
                             </dependency>
                           </dependencies>
                         </dependencyManagement>
                       <dependencies>
                         <dependency>
                           <artifactId>common-i<caret></artifactId>
                         </dependency>
                       </dependencies>
                       """);

    myFixture.configureFromExistingVirtualFile(myProjectPom);

    LookupElement[] elements = myFixture.completeBasic();
    assertSize(1, elements);
    myFixture.type('\n');

    elements = myFixture.completeBasic();
    assertSize(1, elements);
    myFixture.type('\n');

    myFixture.checkResult(createPomXml("""
                                         <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                                           <dependencyManagement>
                                             <dependencies>
                                               <dependency>
                                                 <groupId>commons-io</groupId>
                                                 <artifactId>commons-io</artifactId>
                                                 <version>2.4</version>
                                               </dependency>
                                             </dependencies>
                                           </dependencyManagement>
                                         <dependencies>
                                           <dependency>
                                               <groupId>commons-io</groupId>
                                               <artifactId>commons-io</artifactId>
                                           </dependency>
                                         </dependencies>
                                         """
    ));
  }

  @Test
  public void testCompletionGroupIdWithManagedDependencyWithTypeAndClassifier() {
    importProject("""
                    <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                    <properties>
                      <ioClassifier>ccc</ioClassifier>  <ioType>ttt</ioType></properties>
                    <dependencyManagement>
                      <dependencies>
                        <dependency>
                          <groupId>commons-io</groupId>
                          <artifactId>commons-io</artifactId>
                          <classifier>${ioClassifier}</classifier>
                          <type>${ioType}</type>
                          <version>2.4</version>
                        </dependency>
                      </dependencies>
                    </dependencyManagement>
                    """);

    createProjectPom("""
                       <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                       <dependencyManagement>
                         <dependencies>
                           <dependency>
                             <groupId>commons-io</groupId>
                             <artifactId>commons-io</artifactId>
                             <classifier>${ioClassifier}</classifier>
                             <type>${ioType}</type>
                             <version>2.4</version>
                           </dependency>
                         </dependencies>
                       </dependencyManagement>
                       <dependencies>
                         <dependency>
                             <groupId>commons-i<caret></groupId>
                             <artifactId>commons-io</artifactId>
                         </dependency>
                       </dependencies>
                       """);

    myFixture.configureFromExistingVirtualFile(myProjectPom);

    LookupElement[] elements = myFixture.complete(CompletionType.BASIC);
    assertSize(1, elements);
    myFixture.type('\n');

    myFixture.checkResult(createPomXml("""
                                         <groupId>test</groupId><artifactId>project</artifactId><version>1</version>
                                         <dependencyManagement>
                                           <dependencies>
                                             <dependency>
                                               <groupId>commons-io</groupId>
                                               <artifactId>commons-io</artifactId>
                                               <classifier>${ioClassifier}</classifier>
                                               <type>${ioType}</type>
                                               <version>2.4</version>
                                             </dependency>
                                           </dependencies>
                                         </dependencyManagement>
                                         <dependencies>
                                           <dependency>
                                               <groupId>commons-io</groupId>
                                               <artifactId>commons-io</artifactId>
                                               <type>${ioType}</type>
                                               <classifier>${ioClassifier}</classifier>
                                           </dependency>
                                         </dependencies>
                                         """
    ));
  }
}
