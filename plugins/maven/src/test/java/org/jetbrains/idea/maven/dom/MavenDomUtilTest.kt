// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.maven.testFramework.fixtures.mavenFixture
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test


@TestApplication
class MavenDomUtilTest {
  private val maven by mavenFixture()

  @Test
  fun testIsProjectFileWithModel400() {
    assertFalse(MavenDomUtil.isProjectFileWithModel410(
      createXmlFile("""
        <?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>test</groupId>
    <artifactId>test</artifactId>
    <version>1</version>
    </project>
      """.trimIndent())
    ))
  }

  @Test
  fun testIsProjectFileWithModel410() {
    assertTrue(MavenDomUtil.isProjectFileWithModel410(
      createXmlFile("""
        <?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd">
    <modelVersion>4.1.0</modelVersion>

    <groupId>test</groupId>
    <artifactId>test</artifactId>
    <version>1</version>
    </project>
      """.trimIndent())
    ))
  }

  @Test
  fun testIsProjectFileWithModel410Incomplete() {
    assertFalse(MavenDomUtil.isProjectFileWithModel410(
      createXmlFile("""
        <?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>test</groupId>
    <artifactId>test</artifactId>
    <version>1</version>
    </project>
      """.trimIndent())
    ))
  }


  @Test
  fun testIsProjectFileWithModel410Incomplete2() {
    assertFalse(MavenDomUtil.isProjectFileWithModel410(
      createXmlFile("""
        <?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.1.0</modelVersion>

    <groupId>test</groupId>
    <artifactId>test</artifactId>
    <version>1</version>
    </project>
      """.trimIndent())
    ))
  }

  @Test
  fun testIsProjectFileWithModel410NoModel() {
    assertTrue(MavenDomUtil.isProjectFileWithModel410(
      createXmlFile("""
        <?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd">

    <groupId>test</groupId>
    <artifactId>test</artifactId>
    <version>1</version>
    </project>
      """.trimIndent())
    ))
  }

  @Test
  fun testIsProjectFileWithModel400NoModelVersionInferred() {
    // 4.0.0 namespace without modelVersion tag - should infer 4.0.0 and return false
    assertFalse(MavenDomUtil.isProjectFileWithModel410(
      createXmlFile("""
        <?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

    <groupId>test</groupId>
    <artifactId>test</artifactId>
    <version>1</version>
    </project>
      """.trimIndent())
    ))
  }

  @Test
  fun testIsProjectFileWithModel410HttpsNoModelVersion() {
    // https namespace without modelVersion tag - should infer 4.1.0 and return true
    assertTrue(MavenDomUtil.isProjectFileWithModel410(
      createXmlFile("""
        <?xml version="1.0" encoding="UTF-8"?>
<project xmlns="https://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://maven.apache.org/POM/4.1.0 https://maven.apache.org/xsd/maven-4.1.0.xsd">

    <groupId>test</groupId>
    <artifactId>test</artifactId>
    <version>1</version>
    </project>
      """.trimIndent())
    ))
  }

  @Test
  fun testGetXmlProjectModelVersionInferredFromNamespace410() {
    // When modelVersion tag is missing, should infer from namespace
    assertEquals("4.1.0", MavenDomUtil.getXmlProjectModelVersion(
      createXmlFile("""
        <?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd">

    <groupId>test</groupId>
    <artifactId>test</artifactId>
    <version>1</version>
    </project>
      """.trimIndent())
    ))
  }

  @Test
  fun testGetXmlProjectModelVersionInferredFromNamespace400() {
    // When modelVersion tag is missing, should infer from namespace
    assertEquals("4.0.0", MavenDomUtil.getXmlProjectModelVersion(
      createXmlFile("""
        <?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">

    <groupId>test</groupId>
    <artifactId>test</artifactId>
    <version>1</version>
    </project>
      """.trimIndent())
    ))
  }

  @Test
  fun testGetXmlProjectModelVersionExplicitTakesPrecedence() {
    // When modelVersion tag is present, use it even if namespace differs
    assertEquals("4.0.0", MavenDomUtil.getXmlProjectModelVersion(
      createXmlFile("""
        <?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.1.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.1.0 http://maven.apache.org/xsd/maven-4.1.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>test</groupId>
    <artifactId>test</artifactId>
    <version>1</version>
    </project>
      """.trimIndent())
    ))
  }

  @Test
  fun testGetXmlProjectModelVersionNoNamespace() {
    // When no namespace and no modelVersion tag, return null
    assertNull(MavenDomUtil.getXmlProjectModelVersion(
      createXmlFile("""
        <?xml version="1.0" encoding="UTF-8"?>
<project>
    <groupId>test</groupId>
    <artifactId>test</artifactId>
    <version>1</version>
    </project>
      """.trimIndent())
    ))
  }

  fun createXmlFile(text: String): PsiFile {
    return PsiFileFactory.getInstance(maven.project).createFileFromText("pom.xml", XmlFileType.INSTANCE, text);
  }
}
