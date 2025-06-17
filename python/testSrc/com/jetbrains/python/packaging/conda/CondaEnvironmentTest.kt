// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.UsefulTestCase.refreshRecursively
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.python.packaging.PyRequirementParser
import com.jetbrains.python.packaging.conda.environmentYml.format.CondaEnvironmentYmlParser
import com.jetbrains.python.testDataPath
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
class CondaEnvironmentTest {
  @Test
  fun testParse() {
    val virtualFile = getVirtualFileByName("$testDataPath/requirement/environmentYml/environment.yml")!!
    val actual = runReadAction {
      CondaEnvironmentYmlParser.fromFile(virtualFile)!!
    }

    // Create expected dependencies based on environment.yml
    val expectedDeps = listOf(
      // Basic package names without versions
      PyRequirementParser.fromLine("numpy")!!,
      PyRequirementParser.fromLine("pandas")!!,
      PyRequirementParser.fromLine("matplotlib")!!,

      // Exact version specifications
      PyRequirementParser.fromLine("scipy==1.9.3")!!,
      PyRequirementParser.fromLine("requests==2.28.1")!!,
      PyRequirementParser.fromLine("flask==2.2.2")!!,

      // Version ranges with operators
      PyRequirementParser.fromLine("django>=4.0")!!,
      PyRequirementParser.fromLine("pillow<=9.2.0")!!,
      PyRequirementParser.fromLine("tensorflow>2.8")!!,
      PyRequirementParser.fromLine("torch<1.13")!!,
      PyRequirementParser.fromLine("scikit-learn!=1.0.0")!!,

      // Complex version constraints
      PyRequirementParser.fromLine("jupyterlab>=3.0,<4.0")!!,
      PyRequirementParser.fromLine("fastapi>=0.68.0,!=0.68.2")!!,
      PyRequirementParser.fromLine("pydantic>1.8,<=1.10.2")!!,
      PyRequirementParser.fromLine("uvicorn>=0.15.0,<0.19.0")!!,

      // Build strings and build numbers (treated as regular packages with versions)
      PyRequirementParser.fromLine("openssl==1.1.1q")!!,
      PyRequirementParser.fromLine("sqlite==3.39.2")!!,

      // Channel-specific packages (channel prefix is removed)
      PyRequirementParser.fromLine("cookiecutter")!!,
      PyRequirementParser.fromLine("biopython")!!,
      PyRequirementParser.fromLine("torchvision")!!,
      PyRequirementParser.fromLine("cudatoolkit==11.7")!!,

      // Channel with exact version and build
      PyRequirementParser.fromLine("jupyter==1.0.0")!!,
      PyRequirementParser.fromLine("samtools==1.15.1")!!,

      // Pip dependencies
      PyRequirementParser.fromLine("requests-oauthlib")!!,
      PyRequirementParser.fromLine("python-dotenv")!!,
      PyRequirementParser.fromLine("black==22.8.0")!!,
      PyRequirementParser.fromLine("pytest==7.1.3")!!,
      PyRequirementParser.fromLine("mypy==0.982")!!,
      PyRequirementParser.fromLine("click>=8.0.0")!!,
      PyRequirementParser.fromLine("rich>=12.0.0,<13.0.0")!!,
      PyRequirementParser.fromLine("typer[all]>=0.6.0")!!,
      PyRequirementParser.fromLine("fastapi[all]==0.85.0")!!,
      PyRequirementParser.fromLine("sqlalchemy[postgresql,mysql]>=1.4.0")!!,
      PyRequirementParser.fromLine("apache-airflow[postgres,redis,celery]==2.4.1")!!,
      PyRequirementParser.fromLine("tqdm==1.2.3")!!
    )

    UsefulTestCase.assertSameElements(actual, expectedDeps)
  }

  private fun getVirtualFileByName(fileName: String): VirtualFile? {
    val path = LocalFileSystem.getInstance().findFileByPath(fileName.replace(File.separatorChar, '/'))
    if (path != null) {
      refreshRecursively(path)
      return path
    }
    return null
  }
}
