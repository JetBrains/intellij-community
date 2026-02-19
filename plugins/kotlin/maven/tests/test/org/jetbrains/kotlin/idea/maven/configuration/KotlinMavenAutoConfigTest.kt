// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven.configuration

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.runInEdtAndGet
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.base.test.AndroidStudioTestUtils
import org.jetbrains.kotlin.idea.maven.AbstractKotlinMavenImporterTest
import org.junit.Test

class KotlinMavenAutoConfigTest() : AbstractKotlinMavenImporterTest() {

    override fun shouldRunTest(): Boolean {
        return super.shouldRunTest() && !AndroidStudioTestUtils.skipIncompatibleTestAgainstAndroidStudio()
    }

    private fun testConfigure(moduleName: String, expectedSuccess: Boolean) {
        return runInEdtAndGet {
            val registryValue = Registry.get("kotlin.configuration.maven.autoConfig.enabled")
            val oldValue = registryValue.asBoolean()
            registryValue.setValue(true, testRootDisposable)
            try {
                val module = runReadAction {
                    ModuleManager.getInstance(project).findModuleByName(moduleName)!!
                }
                val settings = runBlocking { KotlinJavaMavenConfigurator().calculateAutoConfigSettings(module) }
                if (expectedSuccess) {
                    assertEquals(module, settings?.module)
                } else {
                    assertNull(settings)
                }
            } finally {
                registryValue.setValue(oldValue)
            }
        }
    }

    @Test
    fun testSingleModuleNoKotlin() {
        val moduleName = "module"
        runBlocking {
            importProjectAsync(
                """
    <groupId>org.example</groupId>
    <artifactId>$moduleName</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
  """
            )
        }
        testConfigure(moduleName, expectedSuccess = true)
    }

    @Test
    fun testKotlinAlreadyConfigured() {
        val moduleName = "module"
        runBlocking {
            importProjectAsync(
                """
    <groupId>org.example</groupId>
    <artifactId>$moduleName</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <kotlin.code.style>official</kotlin.code.style>
        <kotlin.compiler.jvmTarget>1.8</kotlin.compiler.jvmTarget>
    </properties>

    <repositories>
        <repository>
            <id>mavenCentral</id>
            <url>https://repo1.maven.org/maven2/</url>
        </repository>
    </repositories>

    <build>
        <sourceDirectory>src/main/kotlin</sourceDirectory>
        <testSourceDirectory>src/test/kotlin</testSourceDirectory>
        <plugins>
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
                <version>2.2.20</version>
                <executions>
                    <execution>
                        <id>compile</id>
                        <phase>compile</phase>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>test-compile</id>
                        <phase>test-compile</phase>
                        <goals>
                            <goal>test-compile</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>2.22.2</version>
            </plugin>
            <plugin>
                <artifactId>maven-failsafe-plugin</artifactId>
                <version>2.22.2</version>
            </plugin>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>1.6.0</version>
                <configuration>
                    <mainClass>MainKt</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <dependencies>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-test-junit5</artifactId>
            <version>2.2.20</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.0</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-stdlib</artifactId>
            <version>2.2.20</version>
        </dependency>
    </dependencies>
  """
            )
        }
        testConfigure(moduleName, expectedSuccess = false)
    }
}