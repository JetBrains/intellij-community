package com.intellij.settingsSync

import com.intellij.idea.TestFor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.settingsSync.auth.SettingsSyncAuthService
import com.intellij.settingsSync.auth.SettingsSyncDefaultAuthService
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.ui.JBAccountInfoService
import com.jetbrains.cloudconfig.CloudConfigFileClientV2
import com.jetbrains.cloudconfig.Configuration
import com.jetbrains.cloudconfig.FileVersionInfo
import com.jetbrains.cloudconfig.exception.UnauthorizedException
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.*
import java.util.*

@RunWith(JUnit4::class)
internal class SettingsSyncAuthTest : BasePlatformTestCase() {

  private fun dummyFileVersionInfo(): FileVersionInfo {
    return object : FileVersionInfo() {
      override fun getVersionId(): String {
        return "aaaaa"
      }

      override fun getModifiedDate(): Date {
        return Date()
      }

      override fun isLatest(): Boolean {
        return true
      }
    }
  }

  @Test
  @TestFor(issues = ["IDEA-307565"])
  fun `idToken is invalidated after unauthorized exception`() {
    val authServiceSpy = spy<SettingsSyncDefaultAuthService>()
    ApplicationManager.getApplication().replaceService(
      SettingsSyncAuthService::class.java,
      authServiceSpy,
      SettingsSyncMain.getInstance() as SettingsSyncMainImpl
    )

    val accountInfoService = mock<JBAccountInfoService>()
    `when`(accountInfoService.idToken).thenReturn("OLD-ID-TOKEN")
    `when`(authServiceSpy.getAccountInfoService()).thenReturn(accountInfoService)

    val communicator = object : CloudConfigServerCommunicator() {
      override fun createCloudConfigClient(url: String, versionContext: CloudConfigVersionContext): CloudConfigFileClientV2 {
        // we retrieve the token in the parent method
        super.createCloudConfigClient(url, versionContext)

        return object : CloudConfigFileClientV2("http://localhost:7777/cloudconfig", Configuration(),
                                                CloudConfigServerCommunicator.DUMMY_ETAG_STORAGE, CloudConfigVersionContext()) {
          override fun getLatestVersion(file: String?): FileVersionInfo {
            if (_currentIdTokenVar != "NEW-ID-TOKEN") {
              throw UnauthorizedException("Invalid credentials!!!")
            }
            return dummyFileVersionInfo()
          }
        }
      }

    }

    communicator.checkServerState()
    verify(authServiceSpy, times(1)).invalidateJBA("OLD-ID-TOKEN")
    assertFalse(authServiceSpy.isLoggedIn())

    // User updates JBA details
    `when`(accountInfoService.idToken).thenReturn("NEW-ID-TOKEN")
    authServiceSpy.login()

    // Check that userId was updated in communicator
    communicator.checkServerState()
    assertEquals("NEW-ID-TOKEN", communicator._currentIdTokenVar)
  }

  @Test
  @TestFor(issues = ["IDEA-343073"])
  fun `disable setting sync logged out on null idToken`() {
    SettingsSyncSettings.getInstance().syncEnabled = true
    assertTrue(SettingsSyncSettings.getInstance().syncEnabled)

    val authServiceSpy = spy<SettingsSyncDefaultAuthService>()
    ApplicationManager.getApplication().replaceService(
      SettingsSyncAuthService::class.java,
      authServiceSpy,
      SettingsSyncMain.getInstance() as SettingsSyncMainImpl
    )

    val accountInfoService = mock<JBAccountInfoService>()
    `when`(accountInfoService.idToken).thenReturn(null)
    `when`(authServiceSpy.getAccountInfoService()).thenReturn(accountInfoService)

    val communicator = object : CloudConfigServerCommunicator() {
      override fun createCloudConfigClient(url: String, versionContext: CloudConfigVersionContext): CloudConfigFileClientV2 {
        // we retrieve the token in the parent method
        super.createCloudConfigClient(url, versionContext)

        return object : CloudConfigFileClientV2("http://localhost:7777/cloudconfig", Configuration(),
                                                CloudConfigServerCommunicator.DUMMY_ETAG_STORAGE, CloudConfigVersionContext()) {
          override fun getLatestVersion(file: String?): FileVersionInfo {
            if (_currentIdTokenVar != null) {
              fail("current token must be null!")
            }
            throw UnauthorizedException("Invalid credentials!!!")
          }
        }
      }

    }

    communicator.checkServerState()
    assertFalse(authServiceSpy.isLoggedIn())
    assertFalse(SettingsSyncSettings.getInstance().syncEnabled)
  }
}
