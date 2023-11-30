package com.intellij.settingsSync

import com.intellij.idea.TestFor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.settingsSync.auth.SettingsSyncDefaultAuthService
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.ui.JBAccountInfoService
import com.intellij.util.resettableLazy
import com.jetbrains.cloudconfig.CloudConfigFileClientV2
import com.jetbrains.cloudconfig.exception.UnauthorizedException
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.*

@RunWith(JUnit4::class)
internal class SettingsSyncAuthTest : BasePlatformTestCase() {
  @Test
  @TestFor(issues = ["IDEA-307565"])
  fun `user id is invalidated after unauthorized exception`() {
    val authServiceSpy = spy<SettingsSyncDefaultAuthService>()
    ApplicationManager.getApplication().replaceService(SettingsSyncAuthService::class.java, authServiceSpy, SettingsSyncMain.getInstance())

    val accountInfoService = mock<JBAccountInfoService>()
    `when`(accountInfoService.userData).thenReturn(JBAccountInfoService.JBAData("userId", "loginName", "email@jebrains.com"))
    `when`(authServiceSpy.getAccountInfoService()).thenReturn(accountInfoService)

    val exceptionThrowingClient = mock<CloudConfigFileClientV2>()
    `when`(exceptionThrowingClient.getLatestVersion(any())).thenThrow(UnauthorizedException("Invalid credentials"))
    val communicator = SettingsSyncMain.getInstance().getRemoteCommunicator() as CloudConfigServerCommunicator
    communicator._client = resettableLazy {
      exceptionThrowingClient
    }

    communicator.checkServerState()
    verify(authServiceSpy, times(1)).invalidateJBA("userId")
    assertFalse(authServiceSpy.isLoggedIn())

    // User updates JBA details
    `when`(accountInfoService.userData).thenReturn(JBAccountInfoService.JBAData("new_userId", "new_loginName", "new_email@jebrains.com"))
    authServiceSpy.login()

    // Check that userId was updated in communicator
    assertEquals("new_userId", communicator.userId)
  }
}
