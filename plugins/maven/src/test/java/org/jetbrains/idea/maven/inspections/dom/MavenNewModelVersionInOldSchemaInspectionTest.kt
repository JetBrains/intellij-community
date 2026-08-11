// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.inspections.dom

import com.intellij.maven.testFramework.fixtures.MavenVersionArguments
import com.intellij.maven.testFramework.fixtures.assumeModel_4_0_0
import com.intellij.maven.testFramework.fixtures.assumeModel_4_1_0
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenDomFixture
import com.intellij.maven.testFramework.fixtures.setRawPomFile
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.MavenDomBundle
import org.jetbrains.idea.maven.dom.inspections.MavenNewModelVersionInOldSchemaInspection
import org.jetbrains.idea.maven.fixtures.checkHighlighting
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ArgumentsSource

@TestApplication
@ParameterizedClass
@ArgumentsSource(MavenVersionArguments::class)
class MavenNewModelVersionInOldSchemaInspectionTest(mavenVersion: String, modelVersion: String) {

  private val maven by mavenDomFixture(
    mavenVersion = mavenVersion,
    modelVersion = modelVersion
  )

  @BeforeEach
  fun setUp() {
    maven.fixture.enableInspections(MavenNewModelVersionInOldSchemaInspection::class.java)
    runBlocking {
      maven.importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>test</artifactId>
                       <version>1.0</version>
                       """.trimIndent())
    }
  }

  @Test
  fun testCheckHighlightingWrongModel() = runBlocking{
    maven.assumeModel_4_0_0("testing only for model 4.0.0")
    maven.setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
       <modelVersion><error descr="${MavenDomBundle.message("inspection.new.model.version.in.old.schema")}">4.1.0</error></modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())
    maven.checkHighlighting()
  }

  @Test
  fun testCheckNotHighlightingModel4_0() = runBlocking{
    maven.assumeModel_4_0_0("testing only for model 4.0.0")
    maven.setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())
    maven.checkHighlighting()
  }

  @Test
  fun testCheckNotHighlightingModel4_1() = runBlocking{
    maven.assumeModel_4_1_0("testing only for model 4.1.0")
    maven.setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd">
        <modelVersion>4.1.0</modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())
    maven.checkHighlighting()
  }


  @Test
  fun testCheckNotHighlightingModel4_1_manySpaces() = runBlocking{
    maven.assumeModel_4_1_0("testing only for model 4.1.0")
    maven.setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0      http://maven.apache.org/xsd/maven-4.1.0.xsd">
        <modelVersion>4.1.0</modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())
    maven.checkHighlighting()
  }

  @Test
  fun testCheckNotHighlightingModel4_1_newLine() = runBlocking{
    maven.assumeModel_4_1_0("testing only for model 4.1.0")
    maven.setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0
         http://maven.apache.org/xsd/maven-4.1.0.xsd">
        <modelVersion>4.1.0</modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())
    maven.checkHighlighting()
  }

  @Test
  fun testCheckNotHighlightingModel4_1_underscore() = runBlocking{
    maven.assumeModel_4_1_0("testing only for model 4.1.0")
    maven.setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0  http://maven.apache.org/maven-v4_1_0.xsd">
        <modelVersion>4.1.0</modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())
    maven.checkHighlighting()
  }

  @Test
  fun testCheckQuickFix() = runBlocking{
    maven.assumeModel_4_0_0("testing only for model 4.0.0")
    maven.setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion><error descr="${MavenDomBundle.message("inspection.new.model.version.in.old.schema")}"><caret>4.1.0</error></modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())
    maven.checkHighlighting()

    val intention =  maven.fixture.availableIntentions.singleOrNull{it.text.contains("Update Maven Model and XSD to 4.1.0")}
    assertNotNull(intention, "Cannot find intention")
    maven.fixture.launchAction(intention!!)

    maven.fixture.checkResult("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd">
        <modelVersion>4.1.0</modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())
  }


  @Test
  fun testCheckQuickFixAddsXMLSchemaInstance() = runBlocking{
    maven.assumeModel_4_0_0("testing only for model 4.0.0")
    maven.setRawPomFile("""<?xml version="1.0"?>
      <project>
       <modelVersion><error descr="${MavenDomBundle.message("inspection.new.model.version.in.old.schema")}"><caret>4.1.0</error></modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
      </project>
    """.trimIndent())
    maven.checkHighlighting()

    val intention =  maven.fixture.availableIntentions.singleOrNull{it.text.contains("Update Maven Model and XSD to 4.1.0")}
    assertNotNull(intention, "Cannot find intention")
    maven.fixture.launchAction(intention!!)

    maven.fixture.checkResult("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd">
       <modelVersion>4.1.0</modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
      </project>
    """.trimIndent())
  }


  @Test
  fun testQuickFixLeavesHttpsIfWasDefined() = runBlocking{
    maven.assumeModel_4_0_0("testing only for model 4.0.0")
    maven.setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="https://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="https://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion><error descr="${MavenDomBundle.message("inspection.new.model.version.in.old.schema")}"><caret>4.1.0</error></modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
      </project>
    """.trimIndent())
    maven.checkHighlighting()

    val intention =  maven.fixture.availableIntentions.singleOrNull{it.text.contains("Update Maven Model and XSD to 4.1.0")}
    assertNotNull(intention, "Cannot find intention")
    maven.fixture.launchAction(intention!!)

    maven.fixture.checkResult("""<?xml version="1.0"?>
      <project xmlns="https://maven.apache.org/POM/4.1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="https://maven.apache.org/POM/4.1.0 https://maven.apache.org/xsd/maven-4.1.0.xsd">
        <modelVersion>4.1.0</modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
      </project>
    """.trimIndent())
  }

}