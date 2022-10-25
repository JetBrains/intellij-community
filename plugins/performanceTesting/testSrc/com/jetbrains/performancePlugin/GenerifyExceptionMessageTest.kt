package com.jetbrains.performancePlugin

import org.junit.Assert
import org.junit.Test

class GenerifyExceptionMessageTest {
  companion object {

    fun replaceNumberTestTemplate(generifyErrorMessageFunc: (String) -> String) {
      val exceptionMessage = """
        Check after replaceBySource
        
        Entity source filter: 11111111111111111111111111111111111111111111111111
        
        Version: v27
        Saving store content at: /opt/teamcity-agent/temp/buildTmp/startupPerformanceTests8000405305582635120/perf-startup/tests/IU-installer-from-file/spring-beans/log/workspaceModel/storeDump-20211004-061900
        java.lang.AssertionError: VirtualFileUrl: file:///opt/teamcity-agent/temp/buildTmp/startupPerformanceTests8000405305582635120/perf-startup/cache/projects/unpacked/springbeans-master/.idea/libraries exist in both maps but EntityId: 17179869184 with Property: entitySource absent at other
        """.trimIndent()

      // text1234text => text<NUM>text
      val cleanedMessage = generifyErrorMessageFunc(exceptionMessage)
      Assert.assertEquals("""
  Check after replaceBySource
  
  Entity source filter: <NUM>
  
  Version: v<NUM>
  Saving store content at: /opt/teamcity-agent/temp/buildTmp/startupPerformanceTests<NUM>/perf-startup/tests/IU-installer-from-file/spring-beans/log/workspaceModel/storeDump-<NUM>-<NUM>
  java.lang.AssertionError: VirtualFileUrl: file:///opt/teamcity-agent/temp/buildTmp/startupPerformanceTests<NUM>/perf-startup/cache/projects/unpacked/springbeans-master/.idea/libraries exist in both maps but EntityId: <NUM> with Property: entitySource absent at other
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
        C:\BuildAgent\temp\buildTmp\startupPerformanceTests5482355837770071386\perf-startup\tests\IU-installer-from-file\workspace-model-loading-from-cache-maven\system\projects\java-design-patterns-master.db451f59\project-model-cache\cache.tmp -> C:\BuildAgent\temp\buildTmp\startupPerformanceTests5482355837770071386\perf-startup\tests\IU-installer-from-file\workspace-model-loading-from-cache-maven\system\projects\java-design-patterns-master.db451f59\project-model-cache\cache.data
        java.nio.file.AccessDeniedException: C:\BuildAgent\temp\buildTmp\startupPerformanceTests5482355837770071386\perf-startup\tests\IU-installer-from-file\workspace-model-loading-from-cache-maven\system\projects\java-design-patterns-master.db451f59\project-model-cache\cache.tmp -> C:\BuildAgent\temp\buildTmp\startupPerformanceTests5482355837770071386\perf-startup\tests\IU-installer-from-file\workspace-model-loading-from-cache-maven\system\projects\java-design-patterns-master.db451f59\project-model-cache\cache.data
        """.trimIndent()

      // java-design-patterns-master.db451f59 => java-design-patterns-master.<HASH>
      val cleanedMessage = generifyErrorMessageFunc(exceptionMessage)
      Assert.assertEquals(
        """
            C:\BuildAgent\temp\buildTmp\startupPerformanceTests<NUM>\perf-startup\tests\IU-installer-from-file\workspace-model-loading-from-cache-maven\system\projects\java-design-patterns-master.<HASH>\project-model-cache\cache.tmp -> C:\BuildAgent\temp\buildTmp\startupPerformanceTests<NUM>\perf-startup\tests\IU-installer-from-file\workspace-model-loading-from-cache-maven\system\projects\java-design-patterns-master.<HASH>\project-model-cache\cache.data
            java.nio.file.AccessDeniedException: C:\BuildAgent\temp\buildTmp\startupPerformanceTests<NUM>\perf-startup\tests\IU-installer-from-file\workspace-model-loading-from-cache-maven\system\projects\java-design-patterns-master.<HASH>\project-model-cache\cache.tmp -> C:\BuildAgent\temp\buildTmp\startupPerformanceTests<NUM>\perf-startup\tests\IU-installer-from-file\workspace-model-loading-from-cache-maven\system\projects\java-design-patterns-master.<HASH>\project-model-cache\cache.data
            """.trimIndent(),
        cleanedMessage)
    }
  }

  @Test
  fun replaceNumbersTest() {
    replaceNumberTestTemplate(ProjectLoaded::generifyErrorMessage)
  }

  @Test
  fun replaceIDTest() {
    replaceIDTestTemplate(ProjectLoaded::generifyErrorMessage)
  }

  @Test
  fun replaceHashesTest() {
    replaceHashesTestTemplate(ProjectLoaded::generifyErrorMessage)
  }
}