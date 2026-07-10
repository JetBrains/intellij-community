// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils

import com.intellij.idea.TestFor
import com.intellij.maven.testFramework.fixtures.mavenFixture
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.maven.MavenCustomNioRepositoryHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet
import java.util.jar.Attributes
import kotlin.io.path.setPosixFilePermissions


@TestApplication
class JarUtilsTest {
  private val maven by mavenFixture()

  @Test
  fun testShouldReadPropertiesFromFile() {
    val jarPath = getPathToJar()
    val properties = JarUtils.loadProperties(jarPath, getEntryName())
    assertNotNull(properties)
    assertEquals("1.0", properties!!.getProperty("version"))
    assertEquals("1.0", JarUtils.getJarAttribute(jarPath, null, Attributes.Name.MANIFEST_VERSION))

  }

  @Test
  fun testShouldReadPropertiesFromSymlink() {

    assumeTrue(SystemInfo.isLinux || SystemInfo.isMac)

    val jarPath = getPathToJar()
    val linkPath = maven.dir.resolve("mySymlink.jar")
    Files.createSymbolicLink(linkPath, jarPath)
    val properties = JarUtils.loadProperties(linkPath, getEntryName())
    assertNotNull(properties)
    assertEquals("1.0", properties!!.getProperty("version"))
    assertEquals("1.0", JarUtils.getJarAttribute(linkPath, null, Attributes.Name.MANIFEST_VERSION))

  }

  @TestFor(issues = ["IDEA-371006"])
  @Test
  fun testShouldReadPropertiesFromRWSymlinkOnROFile() {
    assumeTrue(SystemInfo.isLinux || SystemInfo.isMac)

    val jarPath = maven.dir.resolve("myjar.jar")
    Files.copy(getPathToJar(), jarPath)
    jarPath.setPosixFilePermissions(EnumSet.of(PosixFilePermission.OWNER_READ))
    val linkPath = maven.dir.resolve("mySymlink.jar")
    Files.createSymbolicLink(linkPath, jarPath)
    jarPath.setPosixFilePermissions(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OTHERS_WRITE))
    val properties = JarUtils.loadProperties(linkPath, getEntryName())
    assertNotNull(properties)
    assertEquals("1.0", properties!!.getProperty("version"))
    assertEquals("1.0", JarUtils.getJarAttribute(linkPath, null, Attributes.Name.MANIFEST_VERSION))
  }

  private fun getEntryName() = "META-INF/maven/intellij.test/maven-extension/pom.properties"

  private fun getPathToJar(): Path {
    val originalTestDataPath = MavenCustomNioRepositoryHelper.originalTestDataPath
    val jarPath = originalTestDataPath.resolve("plugins/intellij/test/maven-extension/1.0/maven-extension-1.0.jar");
    return jarPath
  }
}
