// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven.configuration

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.runInEdtAndGet
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector.Companion.create
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.maven.AbstractKotlinMavenImporterTest
import org.junit.Test
import java.io.File

private const val KOTLIN_VERSION = "2.2.20"

class KotlinSourceRootDirsMavenTest : AbstractKotlinMavenImporterTest() {

    private val purePom = """
    <groupId>org.example</groupId>
    <artifactId>project</artifactId>
    <version>1.0-SNAPSHOT</version>
            """

    @Test
    fun `test when only java dirs exist`() {
        runBlocking {
            importProjectAsync(purePom)
        }

        assertModules("project")

        val afterFile = """
            <?xml version="1.0"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
              <modelVersion>4.0.0</modelVersion>

                <groupId>org.example</groupId>
                <artifactId>project</artifactId>
                <version>1.0-SNAPSHOT</version>

                <properties>
                    <kotlin.version>2.2.20</kotlin.version>
                </properties>

                <dependencies>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-stdlib</artifactId>
                        <version>${'$'}{kotlin.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-test</artifactId>
                        <version>${'$'}{kotlin.version}</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>

                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>
                            <version>${'$'}{kotlin.version}</version>
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
                    </plugins>
                </build>


            </project>
        """.trimIndent()
        doTest(project, project.modules.first(), afterFile)
    }

    @Test
    fun `test when kotlin dirs also exist`() {
        createProjectSubDirs("src/main/kotlin", "src/test/kotlin")

        runBlocking {
            importProjectAsync(purePom)
        }

        assertModules("project")

        val afterFile = """
<?xml version="1.0"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

    <groupId>org.example</groupId>
    <artifactId>project</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <kotlin.version>2.2.20</kotlin.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-stdlib</artifactId>
            <version>${'$'}{kotlin.version}</version>
        </dependency>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-test</artifactId>
            <version>${'$'}{kotlin.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
                <version>${'$'}{kotlin.version}</version>
                <executions>
                    <execution>
                        <id>compile</id>
                        <phase>compile</phase>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                        <configuration>
                            <sourceDirs>
                                <sourceDir>src/main/java</sourceDir>
                                <sourceDir>src/main/kotlin</sourceDir>
                            </sourceDirs>
                        </configuration>
                    </execution>
                    <execution>
                        <id>test-compile</id>
                        <phase>test-compile</phase>
                        <goals>
                            <goal>test-compile</goal>
                        </goals>
                        <configuration>
                            <sourceDirs>
                                <sourceDir>src/test/java</sourceDir>
                                <sourceDir>src/test/kotlin</sourceDir>
                            </sourceDirs>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>


</project>
        """.trimIndent()
        doTest(project, project.modules.first(), afterFile)
    }

    private val mainPomWithSubmodule =
        """
    <groupId>org.example</groupId>
    <artifactId>project</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <modules>
        <module>myModule1</module>
    </modules>

    <properties>
        <maven.compiler.source>22</maven.compiler.source>
        <maven.compiler.target>22</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
            """

    private val submodulePom =
        """
    <parent>
        <groupId>org.example</groupId>
        <artifactId>project</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>myModule1</artifactId>

    <properties>
        <maven.compiler.source>22</maven.compiler.source>
        <maven.compiler.target>22</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
            """

    /**
     * Uses Kotlin style for defining source directories:
     * <sourceDirectory>src/main/kotlin</sourceDirectory>
     * <testSourceDirectory>src/test/kotlin</testSourceDirectory>
     */
    @Test
    fun `test submodule only with kotlin dir`() = runBlocking {
        createProjectSubDirs("src/main/kotlin", "myModule1/src/main/kotlin", "src/test/kotlin", "myModule1/src/test/kotlin")

        val mainPom = createProjectPom(mainPomWithSubmodule)
        val modulePom1 = createModulePom("myModule1", submodulePom)

        importProjectsAsync(mainPom, modulePom1)
        assertModules("project", "myModule1")

        val module = project.modules.first { it.name == "myModule1" }

        val afterFile = """<?xml version="1.0"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.example</groupId>
        <artifactId>project</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>myModule1</artifactId>

    <properties>
        <maven.compiler.source>22</maven.compiler.source>
        <maven.compiler.target>22</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <kotlin.version>2.2.20</kotlin.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-stdlib</artifactId>
            <version>${'$'}{kotlin.version}</version>
        </dependency>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-test</artifactId>
            <version>${'$'}{kotlin.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <sourceDirectory>src/main/kotlin</sourceDirectory>
        <testSourceDirectory>src/test/kotlin</testSourceDirectory>
        <plugins>
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
                <version>${'$'}{kotlin.version}</version>
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
                <configuration>
                    <jvmTarget>${'$'}{maven.compiler.target}</jvmTarget>
                </configuration>
            </plugin>
        </plugins>
    </build>


</project>"""

        doTest(project, module, afterFile)
    }

    @Test
    fun `test submodule with java and kotlin dirs`() = runBlocking {
        createProjectSubDirs(
            "src/main/kotlin", "myModule1/src/main/kotlin", "src/test/kotlin", "myModule1/src/test/kotlin",
            "myModule1/src/main/java", "myModule1/src/test/java"
        )

        val mainPom = createProjectPom(mainPomWithSubmodule)
        val modulePom1 = createModulePom("myModule1", submodulePom)

        importProjectsAsync(mainPom, modulePom1)
        assertModules("project", "myModule1")

        val module = project.modules.first { it.name == "myModule1" }

        val afterFile = """<?xml version="1.0"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.example</groupId>
        <artifactId>project</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>myModule1</artifactId>

    <properties>
        <maven.compiler.source>22</maven.compiler.source>
        <maven.compiler.target>22</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <kotlin.version>2.2.20</kotlin.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-stdlib</artifactId>
            <version>${'$'}{kotlin.version}</version>
        </dependency>
        <dependency>
            <groupId>org.jetbrains.kotlin</groupId>
            <artifactId>kotlin-test</artifactId>
            <version>${'$'}{kotlin.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
                <version>${'$'}{kotlin.version}</version>
                <executions>
                    <execution>
                        <id>compile</id>
                        <phase>compile</phase>
                        <goals>
                            <goal>compile</goal>
                        </goals>
                        <configuration>
                            <sourceDirs>
                                <sourceDir>src/main/java</sourceDir>
                                <sourceDir>src/main/kotlin</sourceDir>
                            </sourceDirs>
                        </configuration>
                    </execution>
                    <execution>
                        <id>test-compile</id>
                        <phase>test-compile</phase>
                        <goals>
                            <goal>test-compile</goal>
                        </goals>
                        <configuration>
                            <sourceDirs>
                                <sourceDir>src/test/java</sourceDir>
                                <sourceDir>src/test/kotlin</sourceDir>
                            </sourceDirs>
                        </configuration>
                    </execution>
                </executions>
                <configuration>
                    <jvmTarget>${'$'}{maven.compiler.target}</jvmTarget>
                </configuration>
            </plugin>
        </plugins>
    </build>


</project>"""

        doTest(project, module, afterFile)
    }

    private fun doTest(project: Project, module: Module, afterFile: String) {
        return runInEdtAndGet {
            val moduleRootManager = ModuleRootManager.getInstance(module)
            val contentEntry = moduleRootManager.contentEntries.first()

            val contentEntryPath = contentEntry.file?.path
            assertNotNull(contentEntryPath)
            val pom = runReadAction {
                resolveRelativePath(contentEntryPath!!).toPsiFile(project)
            }
            assertNotNull(pom)

            val collector = create(project)
            val version = IdeKotlinVersion.get(KOTLIN_VERSION)
            runConfigurator(module, pom!!, KotlinJavaMavenConfigurator(), version, collector)

            collector.showNotification()

            val pomAfter = resolveRelativePath(contentEntryPath!!).toPsiFile(project)
            assertEquals(afterFile, pomAfter!!.text)
        }
    }

    fun runConfigurator(
        module: Module,
        file: PsiFile,
        configurator: KotlinMavenConfigurator,
        version: IdeKotlinVersion,
        collector: NotificationMessageCollector
    ) {
        WriteCommandAction.runWriteCommandAction(module.project) {
            configurator.configureModule(module, file, version, collector)
        }
    }

    private fun resolveRelativePath(contentEntryPath: String): File {
        return File(contentEntryPath, MavenConstants.POM_XML.replace("/", File.separator))
    }
}