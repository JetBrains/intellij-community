// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.localquarantine.executions

import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.execution.MavenExecutionTest
import org.jetbrains.idea.maven.execution.MavenRunnerParameters
import org.junit.Test

class ScriptMavenExecutionIntegrationTest : MavenExecutionTest() {
  override fun setUp() {
    super.setUp()
    toggleScriptsRegistryKey(true)
  }

  @Test
  fun testShouldDebugMavenExec() = runBlocking {
    Registry.get("maven.use.scripts.debug.agent").setValue(false, testRootDisposable)
    importProjectAsync("""
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

    createProjectSubFile("src/main/java/org/example/Main.java",
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

    val executionInfo = execute(MavenRunnerParameters(true, projectPath.toCanonicalPath(), null as String?, mutableListOf("compile"), emptyList()))
    assertTrue(executionInfo.toString(), executionInfo.stdout.contains("BUILD SUCCESS"))

    val runExecInfo = execute(MavenRunnerParameters(true, projectPath.toCanonicalPath(), null as String?, mutableListOf("exec:java"), emptyList()))
    assertTrue(runExecInfo.toString(), runExecInfo.stdout.contains("TEST APPLICATION IS NOT UNDER DEBUG"))

    val debugExecInfo = debugMavenRunConfiguration(MavenRunnerParameters(true, projectPath.toCanonicalPath(), null as String?, mutableListOf("exec:java"), emptyList()))
    assertTrue(debugExecInfo.toString(), debugExecInfo.stdout.contains("TEST APPLICATION IS UNDER DEBUG"))
  }

}