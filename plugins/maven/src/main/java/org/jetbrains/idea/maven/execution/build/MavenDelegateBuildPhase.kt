// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution.build

/** Maven default lifecycle phases shown in the delegated-build Advanced Setting dropdown. */
@Suppress("unused") // enum constants are loaded reflectively by AdvancedSettingsImpl
enum class MavenDelegateBuildPhase(val phaseName: String) {
  VALIDATE("validate"),
  INITIALIZE("initialize"),
  GENERATE_SOURCES("generate-sources"),
  PROCESS_SOURCES("process-sources"),
  GENERATE_RESOURCES("generate-resources"),
  PROCESS_RESOURCES("process-resources"),
  COMPILE("compile"),
  PROCESS_CLASSES("process-classes"),
  GENERATE_TEST_SOURCES("generate-test-sources"),
  PROCESS_TEST_SOURCES("process-test-sources"),
  GENERATE_TEST_RESOURCES("generate-test-resources"),
  PROCESS_TEST_RESOURCES("process-test-resources"),
  TEST_COMPILE("test-compile"),
  PROCESS_TEST_CLASSES("process-test-classes"),
  TEST("test"),
  PREPARE_PACKAGE("prepare-package"),
  PACKAGE("package"),
  PRE_INTEGRATION_TEST("pre-integration-test"),
  INTEGRATION_TEST("integration-test"),
  POST_INTEGRATION_TEST("post-integration-test"),
  VERIFY("verify"),
  INSTALL("install"),
  DEPLOY("deploy");

  override fun toString(): String = phaseName
}
