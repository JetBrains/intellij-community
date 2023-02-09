/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.inspections.dom


import org.jetbrains.idea.maven.dom.MavenDomWithIndicesTestCase
import org.jetbrains.idea.maven.dom.inspections.MavenDuplicateDependenciesInspection
import org.junit.Test

class MavenDuplicatedDependencyInspectionTest extends MavenDomWithIndicesTestCase {

  @Test
  void testDuplicatedInSameFile() {
    myFixture.enableInspections(MavenDuplicateDependenciesInspection)

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
""")

    checkHighlighting()
  }

  @Test 
  void testDuplicatedInSameFileDifferentVersion() {
    myFixture.enableInspections(MavenDuplicateDependenciesInspection)

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
""")

    checkHighlighting()
  }

  @Test 
  void testDuplicatedInParentDifferentScope() {
    myFixture.enableInspections(MavenDuplicateDependenciesInspection)

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
""")

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
""")

    importProject()

    checkHighlighting(myProjectPom, true, false, true)
  }

  @Test 
  void testDuplicatedInParentSameScope() {
    myFixture.enableInspections(MavenDuplicateDependenciesInspection)

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
""")

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
  </dependencies>
""")

    importProjectWithErrors()

    checkHighlighting(myProjectPom, true, false, true)
  }

  @Test 
  void testDuplicatedInParentDifferentVersion() {
    myFixture.enableInspections(MavenDuplicateDependenciesInspection)

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
""")

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
  </dependencies>
""")

    checkHighlighting(myProjectPom, true, false, true)
  }

  @Test 
  void testDuplicatedInManagedDependencies() {
    myFixture.enableInspections(MavenDuplicateDependenciesInspection)

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
""")

    checkHighlighting()
  }

}
