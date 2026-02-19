package org.jetbrains.idea.maven.inspections.dom

import com.intellij.lang.annotation.HighlightSeverity
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.MavenDomWithIndicesTestCase
import org.jetbrains.idea.maven.dom.inspections.MavenDuplicateDependenciesInspection
import org.junit.Test

class MavenDuplicatedDependencyInspectionTest : MavenDomWithIndicesTestCase() {
  @Test
  fun testDuplicatedInSameFile() = runBlocking {
    fixture.enableInspections(MavenDuplicateDependenciesInspection::class.java)

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
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testDuplicatedInSameFileDifferentVersion() = runBlocking {
    fixture.enableInspections(MavenDuplicateDependenciesInspection::class.java)

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
                       </dependencies>
                       """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testDuplicatedInParentDifferentScope() = runBlocking {
    fixture.enableInspections(MavenDuplicateDependenciesInspection::class.java)

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
      </dependencies>
      """.trimIndent())

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
                       </dependencies>
                       """.trimIndent())

    importProjectAsync()

    checkHighlighting()
  }

  @Test
  fun testDuplicatedInParentSameScope() = runBlocking {
    fixture.enableInspections(MavenDuplicateDependenciesInspection::class.java)

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
      </dependencies>
      """.trimIndent())

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
                           <version>3.8.1</version>
                         </dependency>
                       </dependencies>
                       """.trimIndent())

    importProjectAsync()

    checkHighlighting(projectPom, Highlight(severity = HighlightSeverity.WARNING, text = "dependency", description = "Dependency is duplicated in file(s): child "))
  }

  @Test
  fun testDuplicatedInParentDifferentVersion() = runBlocking {
    fixture.enableInspections(MavenDuplicateDependenciesInspection::class.java)

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
      </dependencies>
      """.trimIndent())

    importProjectAsync("""
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
                    </dependencies>
                    """.trimIndent())

    checkHighlighting()
  }

  @Test
  fun testDuplicatedInManagedDependencies() = runBlocking {
    fixture.enableInspections(MavenDuplicateDependenciesInspection::class.java)

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
                       </dependencyManagement>
                       """.trimIndent())

    checkHighlighting()
  }
}
