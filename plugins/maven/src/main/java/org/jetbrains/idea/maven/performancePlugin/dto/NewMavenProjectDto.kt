package org.jetbrains.idea.maven.performancePlugin.dto

data class NewMavenProjectDto(
  val projectName: String,
  val asModule: Boolean,
  val parentModuleName: String?,
  val mavenArchetypeInfo: MavenArchetypeInfo?,
  val sdkObject: SdkObject?
)
