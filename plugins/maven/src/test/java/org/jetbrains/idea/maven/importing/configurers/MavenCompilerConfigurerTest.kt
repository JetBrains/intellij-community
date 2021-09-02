// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.configurers

import com.intellij.compiler.CompilerConfiguration
import org.jetbrains.idea.maven.MavenMultiVersionImportingTestCase
import org.junit.Test

class MavenCompilerConfigurerTest : MavenMultiVersionImportingTestCase() {

  @Test
  fun `test plugin configuration release property`() {
    createProjectPom("""
    <groupId>test</groupId>
    <artifactId>project</artifactId>
    <packaging>pom</packaging>
    <version>1</version>
    <modules>
        <module>m1</module>
        <module>m2</module>
        <module>m3</module>
    </modules>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <release>11</release>
                </configuration>
            </plugin>
        </plugins>
    </build>""")

    createModulePom("m1", """
    <artifactId>m1</artifactId>
    <version>1</version>
    <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
    </parent>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration> 
                    <release>15</release>
                </configuration>
            </plugin>
        </plugins>
    </build>""")

    createModulePom("m2", """
    <artifactId>m2</artifactId>
    <version>1</version>
    <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
    </parent>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration> 
                    <release>9</release>
                </configuration>
            </plugin>
        </plugins>
    </build>""")

    createModulePom("m3", """
    <artifactId>m3</artifactId>
    <version>1</version>
    <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
    </parent>""")
    importProject()

    assertEquals("11", CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("project")))
    assertEquals("15", CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("m1")))
    assertEquals("9", CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("m2")))
    assertEquals("11", CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("m3")))
  }

  @Test
  fun `test property configuration release property`() {
    createProjectPom("""
    <groupId>test</groupId>
    <artifactId>project</artifactId>
    <packaging>pom</packaging>
    <version>1</version>
    <modules>
        <module>m1</module>
        <module>m2</module>
        <module>m3</module>
    </modules>
    <properties>
        <maven.compiler.release>11</maven.compiler.release> 
    </properties>""")

    createModulePom("m1", """
    <artifactId>m1</artifactId>
    <version>1</version>
    <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
    </parent>
    <properties>
        <maven.compiler.release>15</maven.compiler.release> 
    </properties>""")

    createModulePom("m2", """
    <artifactId>m2</artifactId>
    <version>1</version>
    <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
    </parent>
    <properties>
        <maven.compiler.release>9</maven.compiler.release> 
    </properties>""")

    createModulePom("m3", """
    <artifactId>m3</artifactId>
    <version>1</version>
    <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
    </parent>""")
    importProject()

    assertEquals("11", CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("project")))
    assertEquals("15", CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("m1")))
    assertEquals("9", CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("m2")))
    assertEquals("11", CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("m3")))
  }

  @Test
  fun `test mixed configuration with old property in parent`() {
    createProjectPom("""
    <groupId>test</groupId>
    <artifactId>project</artifactId>
    <packaging>pom</packaging>
    <version>1</version>
    <modules>
        <module>m1</module> 
    </modules>
    <properties>
        <maven.compiler.source>11</maven.compiler.source> 
        <maven.compiler.target>11</maven.compiler.target>
    </properties>""")

    createModulePom("m1", """
    <artifactId>m1</artifactId>
    <version>1</version>
    <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
    </parent>
    <properties>
        <maven.compiler.release>9</maven.compiler.release> 
    </properties>""")

    importProject()

    assertEquals("11", CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("project")))
    assertEquals("11", CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("m1")))
  }

  @Test
  fun `test mixed configuration with old property in child`() {
    createProjectPom("""
    <groupId>test</groupId>
    <artifactId>project</artifactId>
    <packaging>pom</packaging>
    <version>1</version>
    <modules>
        <module>m1</module> 
    </modules>
    <properties> 
        <maven.compiler.release>9</maven.compiler.release> 
    </properties>""")

    createModulePom("m1", """
    <artifactId>m1</artifactId>
    <version>1</version>
    <parent>
        <groupId>test</groupId>
        <artifactId>project</artifactId>
        <version>1</version>
    </parent>
    <properties>
        <maven.compiler.source>11</maven.compiler.source> 
        <maven.compiler.target>11</maven.compiler.target> 
    </properties>""")

    importProject()

    assertEquals("9", CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("project")))
    assertEquals("11", CompilerConfiguration.getInstance(myProject).getBytecodeTargetLevel(getModule("m1")))
  }

}