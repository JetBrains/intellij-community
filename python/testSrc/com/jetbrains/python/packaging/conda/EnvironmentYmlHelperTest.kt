// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.conda.environmentYml.format.CondaEnvironmentYmlParser
import com.jetbrains.python.packaging.conda.environmentYml.format.EnvironmentYmlModifier
import org.junit.jupiter.api.Assertions
import java.io.File

class EnvironmentYmlHelperTest : PyTestCase() {
  fun testAddRequirementEmpty() {
    val virtualFile = getVirtualFileByName("$testDataPath/requirement/environmentYmlEmpty/environment.yml")!!

    // Create a temporary copy of the file
    val tempDir = FileUtil.createTempDirectory(getTestName(false), null)
    val tempFile = File(tempDir.path, "environment.yml")
    virtualFile.inputStream.use { input ->
      tempFile.outputStream().use { output ->
        input.copyTo(output)
      }
    }
    val tempVirtualFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl("file://${tempFile.absolutePath}")!!

    // Parse the updated file and check if the package was added
    val requirements = CondaEnvironmentYmlParser.fromFile(tempVirtualFile)!!
    Assertions.assertEquals(emptyList<PyRequirement>(), requirements)

    // Add a new package that doesn't exist in the file
    val newPackageName = "new-test-package"
    EnvironmentYmlModifier.addRequirement(myFixture.project, tempVirtualFile, newPackageName)
    val newPackageRequirement = PyRequirementParser.fromLine(newPackageName)!!

    // Parse the file again and check that the package appears only once
    val updatedRequirements = CondaEnvironmentYmlParser.fromFile(tempVirtualFile)!!
    Assertions.assertEquals(listOf(newPackageRequirement), updatedRequirements)
  }

  fun testAddRequirement() {
    val virtualFile = getVirtualFileByName("$testDataPath/requirement/environmentYml/environment.yml")!!

    // Create a temporary copy of the file
    val tempDir = FileUtil.createTempDirectory(getTestName(false), null)
    val tempFile = File(tempDir.path, "environment.yml")
    virtualFile.inputStream.use { input ->
      tempFile.outputStream().use { output ->
        input.copyTo(output)
      }
    }
    val tempVirtualFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl("file://${tempFile.absolutePath}")!!

    // Add a new package that doesn't exist in the file
    val newPackageName = "new-test-package"
    EnvironmentYmlModifier.addRequirement(myFixture.project, tempVirtualFile, newPackageName)

    // Parse the updated file and check if the package was added
    val requirements = CondaEnvironmentYmlParser.fromFile(tempVirtualFile)!!
    val newPackageRequirement = PyRequirementParser.fromLine(newPackageName)!!

    Assertions.assertTrue(requirements.contains(newPackageRequirement),
                          "The new package should be added to the environment.yml file")

    // Try to add the same package again - it should not be added twice
    EnvironmentYmlModifier.addRequirement(myFixture.project, tempVirtualFile, newPackageName)

    // Parse the file again and check that the package appears only once
    val updatedRequirements = CondaEnvironmentYmlParser.fromFile(tempVirtualFile)!!
    val count = updatedRequirements.count { it.name == newPackageRequirement.name }

    Assertions.assertEquals(1, count,
                            "The package should appear only once in the environment.yml file")
  }
}
