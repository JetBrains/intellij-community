package com.intellij.maven.performanceTesting.dto

data class NewMavenProjectDto(
  val projectName: String,
  val asModule: Boolean,
  val parentModuleName: String?,
  val mavenArchetypeInfo: MavenArchetypeInfo?,
  val sdkObject: SdkObject?
)
