// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.configuration

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.settings.RunConfigurationImporter
import com.intellij.openapi.project.Project
import com.intellij.util.ObjectUtils.consumeIfCast
import com.theoryinpractice.testng.model.TestType
import java.util.*

class TestNGRunConfigurationImporter: RunConfigurationImporter {
  override fun process(project: Project,
                       runConfiguration: RunConfiguration,
                       cfg: MutableMap<String, Any>,
                       modelsProvider: IdeModifiableModelsProvider) {
    if (runConfiguration !is TestNGConfiguration) {
      throw IllegalArgumentException("Unexpected type of run configuration: ${runConfiguration::class.java}")
    }

    val allowedTypes = listOf("package", "class", "method", "group", "suite", "pattern")
    val testKind = cfg.keys.firstOrNull { it in allowedTypes && cfg[it] != null }

    val data = runConfiguration.data

    if (testKind != null) {
      consumeIfCast(cfg[testKind], String::class.java) { testKindValue ->
        data.TEST_OBJECT = when (testKind) {
          "package" -> TestType.PACKAGE.type.also { data.PACKAGE_NAME = testKindValue }
          "class" -> TestType.CLASS.type.also { data.MAIN_CLASS_NAME = testKindValue }
          "method" -> TestType.METHOD.type.also {
            val className = testKindValue.substringBefore('#')
            val methodName = testKindValue.substringAfter('#')
            data.MAIN_CLASS_NAME = className
            data.METHOD_NAME = methodName
          }
          "group" -> TestType.GROUP.type.also { data.GROUP_NAME = testKindValue }
          "suite" -> TestType.SUITE.type.also { data.SUITE_NAME = testKindValue }
          "pattern" -> TestType.PATTERN.type.also { data.setPatterns(LinkedHashSet(testKindValue.split(delimiters = ','))) }
          else -> data.TEST_OBJECT
        }
      }
    }

    consumeIfCast(cfg["vmParameters"], String::class.java) { runConfiguration.vmParameters = it }
    consumeIfCast(cfg["workingDirectory"], String::class.java) { runConfiguration.workingDirectory = it }
    consumeIfCast(cfg["passParentEnvs"], Boolean::class.java) { runConfiguration.isPassParentEnvs = it }
    consumeIfCast(cfg["envs"], Map::class.java) { runConfiguration.envs = it as Map<String, String> }

    consumeIfCast(cfg["moduleName"], String::class.java) {
      val module = modelsProvider.modifiableModuleModel.findModuleByName(it)
      if (module != null) {
        runConfiguration.setModule(module)
      }
    }


  }

  override fun canImport(typeName: String): Boolean = "testng" == typeName

  override fun getConfigurationFactory(): ConfigurationFactory = ConfigurationTypeUtil
    .findConfigurationType<TestNGConfigurationType>(TestNGConfigurationType::class.java)
    .configurationFactories[0]
}