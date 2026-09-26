package com.intellij.lambda.testFramework.project

import com.intellij.ide.starter.project.ProjectInfoSpec
import com.intellij.ide.starter.project.RemoteArchiveProjectInfo
import com.intellij.ide.starter.project.ReusableLocalProjectInfo
import com.intellij.ide.starter.utils.JarUtils
import java.nio.file.Files

object TestAppProject : ProjectInfoSpec by ReusableLocalProjectInfo(
  projectDir = JarUtils.extractResource("projects/TestApp", Files.createTempDirectory("ui-test-resource-"))
)

object HelloWorldProject : ProjectInfoSpec by ReusableLocalProjectInfo(
projectDir = JarUtils.extractResource("projects/hello-world", Files.createTempDirectory("ui-test-resource-"))
)