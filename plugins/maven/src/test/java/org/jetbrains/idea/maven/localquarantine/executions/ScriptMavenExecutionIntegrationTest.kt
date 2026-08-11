// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.localquarantine.executions

import com.intellij.maven.testFramework.utils.MavenProjectJDKTestFixture
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ThrowableRunnable
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import org.jetbrains.idea.maven.fixtures.checkUpdatingExcludedFoldersAfterExecution
import com.intellij.maven.testFramework.fixtures.createProjectSubFile
import org.jetbrains.idea.maven.fixtures.debugMavenRunConfiguration
import org.jetbrains.idea.maven.fixtures.execute
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.projectPath
import com.intellij.maven.testFramework.fixtures.testRootDisposable
import org.jetbrains.idea.maven.fixtures.toggleScriptsRegistryKey
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class ScriptMavenExecutionIntegrationTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenImportingFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )
  
  private lateinit var jdkFixture: MavenProjectJDKTestFixture

  @BeforeEach
  fun setUp() {
    jdkFixture = MavenProjectJDKTestFixture(maven.project, "MavenExecutionTestJDK")
    EdtTestUtil.runInEdtAndWait<RuntimeException?>(ThrowableRunnable {
      WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { jdkFixture.setUp() })
    })
    maven.toggleScriptsRegistryKey(true)
  }

  @AfterEach
  fun tearDownJdk() {
    EdtTestUtil.runInEdtAndWait<RuntimeException?>(ThrowableRunnable {
      WriteAction.runAndWait<RuntimeException?>(ThrowableRunnable { jdkFixture.tearDown() })
    })
  }

  @Test
  fun testUpdatingExcludedFoldersAfterExecution() = runBlocking {
    maven.checkUpdatingExcludedFoldersAfterExecution()
  }

  @Test
  fun testShouldDebugMavenExec() = runBlocking {
    Registry.get("maven.use.scripts.debug.agent").setValue(false, maven.testRootDisposable)
    maven.importProjectAsync("""
         <groupId>test</groupId>
         <artifactId>project</artifactId>
         <version>1</version>
         
         <build>
          <plugins>
              <plugin>
                  <groupId>org.codehaus.mojo</groupId>
                  <artifactId>exec-maven-plugin</artifactId>
                  <version>3.0.0</version>
                  <executions>
                      <execution>
                          <goals>
                              <goal>java</goal>
                          </goals>
                      </execution>
                  </executions>
                  <configuration>
                      <mainClass>org.example.Main</mainClass>
                  </configuration>
              </plugin>
          </plugins>
      </build>
         """)

    maven.createProjectSubFile("src/main/java/org/example/Main.java",
                         """
package org.example;

import java.lang.management.ManagementFactory;

public class Main {
    public static void main(String[] args) {

        String inputArgs = ManagementFactory.getRuntimeMXBean().
                getInputArguments().toString();
        boolean isDebug = inputArgs.contains("-agentlib:jdwp") || inputArgs.contains("-Xrunjdwp");

        if(isDebug) {
            System.out.println("TEST APPLICATION IS UNDER DEBUG");
        } else {
            System.out.println("TEST APPLICATION IS NOT UNDER DEBUG");
        }
    }
}
""".trimIndent())

    val executionInfo = maven.execute(MavenRunnerParameters(true, maven.projectPath.toCanonicalPath(), null as String?, mutableListOf("compile"), emptyList()))
    assertTrue(executionInfo.stdout.contains("BUILD SUCCESS"), executionInfo.toString())

    val runExecInfo = maven.execute(MavenRunnerParameters(true, maven.projectPath.toCanonicalPath(), null as String?, mutableListOf("exec:java"), emptyList()))
    assertTrue(runExecInfo.stdout.contains("TEST APPLICATION IS NOT UNDER DEBUG"), runExecInfo.toString())

    val debugExecInfo = maven.debugMavenRunConfiguration(MavenRunnerParameters(true, maven.projectPath.toCanonicalPath(), null as String?, mutableListOf("exec:java"), emptyList()))
    assertTrue(debugExecInfo.stdout.contains("TEST APPLICATION IS UNDER DEBUG"), debugExecInfo.toString())
  }

}