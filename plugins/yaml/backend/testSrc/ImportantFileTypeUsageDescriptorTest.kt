// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ImportantFileTypeUsageDescriptorTest : BasePlatformTestCase() {

  fun testK8s() {
    val podFile = myFixture.addFileToProject("deployment.yaml", """
      apiVersion: v1
      kind: Pod
    """.trimIndent())

    assertTrue("K8s file described by content",
               K8sFileTypeUsageDescriptor().describes(myFixture.project, podFile.virtualFile))
  }

  fun testOpenApi() {
    val podFile = myFixture.addFileToProject("api.yaml", """
      openapi: 3.0.1
      paths:
    """.trimIndent())

    assertTrue("OpenAPI file described by content",
               OpenapiFileTypeUsageDescriptor().describes(myFixture.project, podFile.virtualFile))
  }

  fun testSwagger() {
    val podFile = myFixture.addFileToProject("api.yaml", """
      swagger: 2.0
      paths:
    """.trimIndent())

    assertTrue("Swagger file described by content",
               SwaggerFileTypeUsageDescriptor().describes(myFixture.project, podFile.virtualFile))
  }

  fun testDockerCompose() {
    val podFile = myFixture.addFileToProject("compose.yaml", """
      version: 3.0.7
      services:
    """.trimIndent())

    assertTrue("Docker Compose file described by content",
               DockerComposeFileTypeUsageDescriptor().describes(myFixture.project, podFile.virtualFile))
  }

  fun testCloudFormation() {
    val podFile = myFixture.addFileToProject("cloud.yaml", """
      AWSTemplateFormatVersion: '2010-09-09'
      Resources:
    """.trimIndent())

    assertTrue("CloudFormation file described by content",
               CloudFormationFileTypeUsageDescriptor().describes(myFixture.project, podFile.virtualFile))
  }
}