package com.jetbrains.performancePlugin

import org.junit.Assert
import org.junit.Test

class GenerifyExceptionMessageTest {
  companion object {

    fun replaceFileTestTemplate(generifyErrorMessageFunc: (String) -> String) {
      val exceptionMessage = """
        Saving store content at: /opt/teamcity-agent/temp/buildTmp/startupPerformanceTests8000405305582635120/perf-startup/tests/IU-installer-from-file/spring-beans/log/workspaceModel/storeDump-20211004-061900
        java.lang.AssertionError: VirtualFileUrl: file:///opt/teamcity-agent/temp/buildTmp/startupPerformanceTests8000405305582635120/perf-startup/cache/projects/unpacked/springbeans-master/.idea/libraries exist in both maps but EntityId: 17179869184 with Property: entitySource absent at other
        java.nio.file.AccessDeniedException: C:\BuildAgent\temp\buildTmp\startupPerformanceTests5482355837770071386\perf-startup\tests\IU-installer-from-file\workspace-model-loading-from-cache-maven\system\projects\java-design-patterns-master.db451f59\project-model-cache\cache.tmp -> C:\BuildAgent\temp\buildTmp\startupPerformanceTests5482355837770071386\perf-startup\tests\IU-installer-from-file\workspace-model-loading-from-cache-maven\system\projects\java-design-patterns-master.db451f59\project-model-cache\cache.data
        Trying to get PSI for an alien project. VirtualFile=file:///mnt/agent/temp/buildTmp/unitTest__navigation_from_implicit_definition_to_type_declaration_2hjbpHmCvN1iUbECrVvmn4aKScz/unitTest553683424042165279/test.cy.ts; project=Project(containerState=disposed temporarily) (disposed) (/mnt/agent/temp/buildTmp/unitTest_transitiveParentClassDependency_2hjbRcDyTtR2Ctiw2utT94bWy2J); but the file actually belongs to Project(containerState=COMPONENT_CREATED) (/mnt/agent/temp/buildTmp/unitTest__navigation_from_implicit_definition_to_type_declaration_2hjbpHmCvN1iUbECrVvmn4aKScz/com_intellij_aqua_frameworks_cypress_codeInsight_commands_CypressCommandNavigationTest_test navigation from implicit definition to type declaration_2hjbpFHroOHk8cQfELhVywNiMwL)
        """.trimIndent()

      val cleanedMessage = generifyErrorMessageFunc(exceptionMessage)
      Assert.assertEquals("""
        Saving store content at: <FILE>
        java.lang.AssertionError: VirtualFileUrl: file://<FILE>: <NUM> with Property: entitySource absent at other
        java.nio.file.AccessDeniedException: <FILE>> <FILE>
        Trying to get PSI for an alien project. VirtualFile=file://<FILE>; project=Project(containerState=disposed temporarily) (disposed) (<FILE>); but the file actually belongs to Project(containerState=COMPONENT_CREATED) (<FILE>)
""".trimIndent(), cleanedMessage)
    }

    fun replaceNumberTestTemplate(generifyErrorMessageFunc: (String) -> String) {
      val exceptionMessage = """
        Check after replaceBySource
        
        Entity source filter: 11111111111111111111111111111111111111111111111111
        
        Version: v27
        Saving store content at: <FILE>
        java.lang.AssertionError: VirtualFileUrl: file://<FILE>: 17179869184 with Property: entitySource absent at other
        """.trimIndent()

      // text1234text => text<NUM>text
      val cleanedMessage = generifyErrorMessageFunc(exceptionMessage)
      Assert.assertEquals("""
  Check after replaceBySource
  
  Entity source filter: <NUM>
  
  Version: v<NUM>
  Saving store content at: <FILE>
  java.lang.AssertionError: VirtualFileUrl: file://<FILE>: <NUM> with Property: entitySource absent at other
  """.trimIndent(),
                          cleanedMessage)
    }


    fun replaceIDTestTemplate(generifyErrorMessageFunc: (String) -> String) {
      val exceptionMessage = """
        Unhandled exception in [CoroutineName(ProjectLifecycleScopeService), StandaloneCoroutine{Cancelling}@3ba-_5aac, Dispatchers.Default]
        com.intellij.serviceContainer.AlreadyDisposedException: Cannot create com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager because container is already disposed (container=Module: 'resource-acquisition-is-initialization' (disposed))
        """.trimIndent()

      // text@3ba5aac, text => text<ID>, text
      val cleanedMessage = generifyErrorMessageFunc(exceptionMessage)
      Assert.assertEquals(
        """
            Unhandled exception in [CoroutineName(ProjectLifecycleScopeService), StandaloneCoroutine{Cancelling}<ID>, Dispatchers.Default]
            com.intellij.serviceContainer.AlreadyDisposedException: Cannot create com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager because container is already disposed (container=Module: 'resource-acquisition-is-initialization' (disposed))
            """.trimIndent(),
        cleanedMessage)
    }

    fun replaceHashesTestTemplate(generifyErrorMessageFunc: (String) -> String) {
      val exceptionMessage = """
        startupPerformanceTests5482355837770071386
        java-design-patterns-master.db451f59
        """.trimIndent()

      // java-design-patterns-master.db451f59 => java-design-patterns-master.<HASH>
      val cleanedMessage = generifyErrorMessageFunc(exceptionMessage)
      Assert.assertEquals(
        """
            startupPerformanceTests<NUM>
            java-design-patterns-master.<HASH>
            """.trimIndent(),
        cleanedMessage)
    }
  }

  @Test
  fun replaceNumbersTest() {
    replaceNumberTestTemplate(::generifyErrorMessage)
  }

  @Test
  fun replaceIDTest() {
    replaceIDTestTemplate(::generifyErrorMessage)
  }

  @Test
  fun replaceHashesTest() {
    replaceHashesTestTemplate(::generifyErrorMessage)
  }
}