package org.jetbrains.idea.maven.inspections.dom;

import org.jetbrains.idea.maven.dom.MavenDomWithIndicesTestCase;
import org.jetbrains.idea.maven.dom.inspections.MavenDuplicateDependenciesInspection;
import org.junit.Test;

public class MavenDuplicatedDependencyInspectionTest extends MavenDomWithIndicesTestCase {
  @Test
  public void testDuplicatedInSameFile() {
    myFixture.enableInspections(MavenDuplicateDependenciesInspection.class);

    createProjectPom("""
                       <groupId>mavenParent</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                         
                       <dependencies>
                         <<warning>dependency</warning>>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>3.8.2</version>
                           <scope>provided</scope>
                         </dependency>
                         <<warning>dependency</warning>>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>3.8.2</version>
                         </dependency>
                       </dependencies>""");

    checkHighlighting();
  }

  @Test
  public void testDuplicatedInSameFileDifferentVersion() {
    myFixture.enableInspections(MavenDuplicateDependenciesInspection.class);

    createProjectPom("""
                       <groupId>mavenParent</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                         
                       <dependencies>
                         <<warning>dependency</warning>>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>3.8.2</version>
                         </dependency>
                         <<warning>dependency</warning>>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>3.8.1</version>
                         </dependency>
                       </dependencies>""");

    checkHighlighting();
  }

  @Test
  public void testDuplicatedInParentDifferentScope() {
    myFixture.enableInspections(MavenDuplicateDependenciesInspection.class);

    createModulePom("child", """
      <groupId>mavenParent</groupId>
      <artifactId>child</artifactId>
      <version>1.0</version>
        
          <parent>
      <groupId>mavenParent</groupId>
      <artifactId>parent</artifactId>
      <version>1.0</version>
          </parent>
        
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>3.8.2</version>
          <scope>runtime</scope>
        </dependency>
      </dependencies>""");

    createProjectPom("""
                       <groupId>mavenParent</groupId>
                       <artifactId>parent</artifactId>
                       <version>1.0</version>
                       <packaging>pom</packaging>
                         
                       <modules>
                         <module>child</module>
                       </modules>
                         
                       <dependencies>
                         <dependency>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>3.8.2</version>
                           <scope>provided</scope>
                         </dependency>
                       </dependencies>""");

    importProject();

    checkHighlighting();
  }

  @Test
  public void testDuplicatedInParentSameScope() {
    myFixture.enableInspections(MavenDuplicateDependenciesInspection.class);

    createModulePom("child", """
      <groupId>mavenParent</groupId>
      <artifactId>child</artifactId>
      <version>1.0</version>
        
          <parent>
      <groupId>mavenParent</groupId>
      <artifactId>parent</artifactId>
      <version>1.0</version>
          </parent>
        
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>3.8.1</version>
          <scope>compile</scope>
        </dependency>
      </dependencies>""");

    createProjectPom("""
                       <groupId>mavenParent</groupId>
                       <artifactId>parent</artifactId>
                       <version>1.0</version>
                       <packaging>pom</packaging>
                         
                       <modules>
                         <module>child</module>
                       </modules>
                         
                       <dependencies>
                         <<warning>dependency</warning>>
                           <groupId>junit</groupId>
                           <artifactId>junit</artifactId>
                           <version>3.8.1</version>
                         </dependency>
                       </dependencies>""");

    importProjectWithErrors();

    checkHighlighting();
  }

  @Test
  public void testDuplicatedInParentDifferentVersion() {
    myFixture.enableInspections(MavenDuplicateDependenciesInspection.class);

    createModulePom("child", """
      <groupId>mavenParent</groupId>
      <artifactId>child</artifactId>
      <version>1.0</version>
        
          <parent>
      <groupId>mavenParent</groupId>
      <artifactId>parent</artifactId>
      <version>1.0</version>
          </parent>
        
      <dependencies>
        <dependency>
          <groupId>junit</groupId>
          <artifactId>junit</artifactId>
          <version>3.8.1</version>
        </dependency>
      </dependencies>""");

    importProject("""
                    <groupId>mavenParent</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0</version>
                    <packaging>pom</packaging>
                      
                    <modules>
                      <module>child</module>
                    </modules>
                      
                    <dependencies>
                      <dependency>
                        <groupId>junit</groupId>
                        <artifactId>junit</artifactId>
                        <version>3.8.2</version>
                      </dependency>
                    </dependencies>""");

    checkHighlighting();
  }

  @Test
  public void testDuplicatedInManagedDependencies() {
    myFixture.enableInspections(MavenDuplicateDependenciesInspection.class);

    createProjectPom("""
                       <groupId>mavenParent</groupId>
                       <artifactId>childA</artifactId>
                       <version>1.0</version>
                         
                       <dependencyManagement>
                         <dependencies>
                           <<warning>dependency</warning>>
                             <groupId>junit</groupId>
                             <artifactId>junit</artifactId>
                             <version>3.8.2</version>
                             <type>jar</type>
                           </dependency>
                         
                           <<warning>dependency</warning>>
                             <groupId>junit</groupId>
                             <artifactId>junit</artifactId>
                             <version>4.0</version>
                           </dependency>
                         
                           <dependency>
                             <groupId>junit</groupId>
                             <artifactId>junit</artifactId>
                             <version>4.0</version>
                             <classifier>sources</classifier>
                           </dependency>
                         </dependencies>
                       </dependencyManagement>""");

    checkHighlighting();
  }
}
