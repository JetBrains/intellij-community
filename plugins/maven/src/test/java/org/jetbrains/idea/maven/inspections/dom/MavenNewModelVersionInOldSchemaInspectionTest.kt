// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.inspections.dom

import com.intellij.maven.testFramework.MavenDomTestCase
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.dom.inspections.MavenNewModelVersionInOldSchemaInspection
import org.junit.Test

class MavenNewModelVersionInOldSchemaInspectionTest: MavenDomTestCase()  {
  override fun setUp() {
    super.setUp()


    fixture.enableInspections(MavenNewModelVersionInOldSchemaInspection::class.java)
    runBlocking {
      importProjectAsync("""
                       <groupId>test</groupId>
                       <artifactId>test</artifactId>
                       <version>1.0</version>
                       """.trimIndent())
    }
  }

  @Test
  fun testCheckHighlightingWrongModel() = runBlocking{
    assumeModel_4_0_0("testing only for model 4.0.0")
    setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
       <modelVersion><error descr="Model version 4.1.0 is required for projects with 4.1.0 schema">4.1.0</error></modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())
    checkHighlighting()
  }

  @Test
  fun testCheckNotHighlightingModel4_0() = runBlocking{
    assumeModel_4_0_0("testing only for model 4.0.0")
    setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion>4.0.0</modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())
    checkHighlighting()
  }

  @Test
  fun testCheckNotHighlightingModel4_1() = runBlocking{
    assumeModel_4_1_0("testing only for model 4.1.0")
    setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd">
        <modelVersion>4.1.0</modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())
    checkHighlighting()
  }


  @Test
  fun testCheckNotHighlightingModel4_1_manySpaces() = runBlocking{
    assumeModel_4_1_0("testing only for model 4.1.0")
    setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0      http://maven.apache.org/xsd/maven-4.1.0.xsd">
        <modelVersion>4.1.0</modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())
    checkHighlighting()
  }

  @Test
  fun testCheckNotHighlightingModel4_1_newLine() = runBlocking{
    assumeModel_4_1_0("testing only for model 4.1.0")
    setRawPomFile("""<?xml version="1.0"?>
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
    checkHighlighting()
  }

  @Test
  fun testCheckNotHighlightingModel4_1_underscore() = runBlocking{
    assumeModel_4_1_0("testing only for model 4.1.0")
    setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0  http://maven.apache.org/maven-v4_1_0.xsd">
        <modelVersion>4.1.0</modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())
    checkHighlighting()
  }

  @Test
  fun testCheckQuickFix() = runBlocking{
    assumeModel_4_0_0("testing only for model 4.0.0")
    setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion><error descr="Model version 4.1.0 is required for projects with 4.1.0 schema"><caret>4.1.0</error></modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
        </project>
    """.trimIndent())
    checkHighlighting()

    val intention =  fixture.availableIntentions.singleOrNull{it.text.contains("Update Maven Model and XSD to 4.1.0")}
    assertNotNull("Cannot find intention", intention)
    fixture.launchAction(intention!!)

    fixture.checkResult("""<?xml version="1.0"?>
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
    assumeModel_4_0_0("testing only for model 4.0.0")
    setRawPomFile("""<?xml version="1.0"?>
      <project>
       <modelVersion><error descr="Model version 4.1.0 is required for projects with 4.1.0 schema"><caret>4.1.0</error></modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
      </project>
    """.trimIndent())
    checkHighlighting()

    val intention =  fixture.availableIntentions.singleOrNull{it.text.contains("Update Maven Model and XSD to 4.1.0")}
    assertNotNull("Cannot find intention", intention)
    fixture.launchAction(intention!!)

    fixture.checkResult("""<?xml version="1.0"?>
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
    assumeModel_4_0_0("testing only for model 4.0.0")
    setRawPomFile("""<?xml version="1.0"?>
      <project xmlns="https://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="https://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
        <modelVersion><error descr="Model version 4.1.0 is required for projects with 4.1.0 schema"><caret>4.1.0</error></modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
      </project>
    """.trimIndent())
    checkHighlighting()

    val intention =  fixture.availableIntentions.singleOrNull{it.text.contains("Update Maven Model and XSD to 4.1.0")}
    assertNotNull("Cannot find intention", intention)
    fixture.launchAction(intention!!)

    fixture.checkResult("""<?xml version="1.0"?>
      <project xmlns="https://maven.apache.org/POM/4.1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="https://maven.apache.org/POM/4.1.0 https://maven.apache.org/xsd/maven-4.1.0.xsd">
        <modelVersion>4.1.0</modelVersion>
        <groupId>my.group</groupId>
        <artifactId>artifact</artifactId>
        <version>1.0</version>
      </project>
    """.trimIndent())
  }

}