// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.maven.testFramework.MavenTestCase
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory


class MavenDomUtilTest : MavenTestCase() {

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

  fun  createXmlFile(text: String) : PsiFile {
    return PsiFileFactory.getInstance(project).createFileFromText("pom.xml", XmlFileType.INSTANCE, text);
  }
}