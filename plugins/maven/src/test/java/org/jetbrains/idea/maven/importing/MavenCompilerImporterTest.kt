// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.compiler.impl.javaCompiler.BackendCompiler
import com.intellij.compiler.impl.javaCompiler.eclipse.EclipseCompiler
import junit.framework.TestCase
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.Test

class MavenCompilerImporterTest : MavenMultiVersionImportingTestCase() {
  private lateinit var ideCompilerConfiguration: CompilerConfigurationImpl

  private lateinit var javacCompiler: BackendCompiler
  private lateinit var eclipseCompiler: BackendCompiler

  private val eclipsePom = """
                <groupId>test</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <version>3.6.0</version>
                            <configuration>
                                <compilerId>eclipse</compilerId>
                            </configuration>
                            <dependencies>
                                <dependency>
                                    <groupId>org.codehaus.plexus</groupId>
                                    <artifactId>plexus-compiler-eclipse</artifactId>
                                    <version>2.8.1</version>
                                </dependency>
                            </dependencies>
                        </plugin>
                    </plugins>
                </build>
  """

  private val javacPom = """
                     <groupId>test</groupId>
                     <artifactId>project</artifactId>
                     <version>1</version>
                     """

  override fun setUp() {
    super.setUp()
    ideCompilerConfiguration = CompilerConfiguration.getInstance(myProject) as CompilerConfigurationImpl
    javacCompiler = ideCompilerConfiguration.defaultCompiler
    eclipseCompiler = ideCompilerConfiguration.registeredJavaCompilers.find { it is  EclipseCompiler } as EclipseCompiler;
  }

  @Test fun testShouldResolveJavac() {

    createProjectPom(javacPom)
    importProject();

    TestCase.assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)

  }

  @Test fun testShouldResolveEclipseCompilerOnAutoDetect() {
    MavenProjectsManager.getInstance(myProject).importingSettings.isAutoDetectCompiler = true;

    createProjectPom(eclipsePom)
    importProject();

    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)
  }


  @Test fun testShouldResolveEclipseAndSwitchToJavacCompiler() {
    MavenProjectsManager.getInstance(myProject).importingSettings.isAutoDetectCompiler = true;

    createProjectPom(eclipsePom)
    importProject();
    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)

    createProjectPom(javacPom)
    importProject();

    TestCase.assertEquals("Javac", ideCompilerConfiguration.defaultCompiler.id)
  }

  @Test fun testShouldNotSwitchToJavacCompilerIfAutoDetectDisabled() {
    MavenProjectsManager.getInstance(myProject).importingSettings.isAutoDetectCompiler = true;

    createProjectPom(eclipsePom)
    importProject();
    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)

    createProjectPom(javacPom)
    MavenProjectsManager.getInstance(myProject).importingSettings.isAutoDetectCompiler = false;
    importProject();

    TestCase.assertEquals("Eclipse", ideCompilerConfiguration.defaultCompiler.id)
  }

}
