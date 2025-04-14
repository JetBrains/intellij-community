package com.intellij.settingsSync

import com.intellij.idea.TestFor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.settingsSync.core.SettingsSyncMain
import com.intellij.settingsSync.core.SettingsSyncSettings
import com.intellij.settingsSync.core.SettingsSyncStatusTracker
import com.intellij.settingsSync.core.auth.SettingsSyncAuthService
import com.intellij.settingsSync.jba.CloudConfigServerCommunicator
import com.intellij.settingsSync.jba.CloudConfigVersionContext
import com.intellij.settingsSync.jba.auth.JBAAuthService
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
import java.util.Date

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
    val authServiceSpy = spy<JBAAuthService>()
    ApplicationManager.getApplication().replaceService(SettingsSyncAuthService::class.java, authServiceSpy, SettingsSyncMain.getInstance())

    val accountInfoService = mock<JBAccountInfoService>()
    `when`(accountInfoService.idToken).thenReturn("OLD-ID-TOKEN")
    `when`(authServiceSpy.getAccountInfoService()).thenReturn(accountInfoService)

    val communicator = object : CloudConfigServerCommunicator("http://localhost:7777/cloudconfig", authServiceSpy) {
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

    //TODO("assertFalse(authServiceSpy.isLoggedIn())")

    // User updates JBA details
    `when`(accountInfoService.idToken).thenReturn("NEW-ID-TOKEN")
    /*
    runBlockingCancellable {
      doReturn(null).`when`(authServiceSpy).login(null)
      authServiceSpy.login(null)
    }
    */

    // Check that userId was updated in communicator
    communicator.checkServerState()
    assertEquals("NEW-ID-TOKEN", communicator._currentIdTokenVar)
  }

  @Test
  @TestFor(issues = ["IDEA-343073"])
  fun `setting sync set action required on invalid idToken`() {
    SettingsSyncSettings.getInstance().syncEnabled = true
    assertTrue(SettingsSyncSettings.getInstance().syncEnabled)

    val authServiceSpy = spy<JBAAuthService>()
    ApplicationManager.getApplication().replaceService(SettingsSyncAuthService::class.java, authServiceSpy, SettingsSyncMain.getInstance())

    val accountInfoService = mock<JBAccountInfoService>()
    val invalidToken = "INVALID_TOKEN"
    `when`(accountInfoService.idToken).thenReturn(invalidToken)
    `when`(authServiceSpy.getAccountInfoService()).thenReturn(accountInfoService)

    val communicator = object : CloudConfigServerCommunicator("http://localhost:7777/cloudconfig", authServiceSpy) {
      override fun createCloudConfigClient(url: String, versionContext: CloudConfigVersionContext): CloudConfigFileClientV2 {
        // we retrieve the token in the parent method
        super.createCloudConfigClient(url, versionContext)

        return object : CloudConfigFileClientV2("http://localhost:7777/cloudconfig", Configuration(),
                                                CloudConfigServerCommunicator.DUMMY_ETAG_STORAGE, CloudConfigVersionContext()) {
          override fun getLatestVersion(file: String?): FileVersionInfo {
            if (_currentIdTokenVar != invalidToken) {
              fail("current token must be invalid!!")
            }
            throw UnauthorizedException("Invalid credentials!!!")
          }
        }
      }
    }

    communicator.checkServerState()
    //assertFalse(authServiceSpy.isLoggedIn())
    assertTrue(SettingsSyncStatusTracker.getInstance().currentStatus is SettingsSyncStatusTracker.SyncStatus.ActionRequired)
  }

  @Test
  @TestFor(issues = ["IJPL-13365"])
  fun `don't disable setting sync on missing idToken`() {
    SettingsSyncSettings.getInstance().syncEnabled = true
    assertTrue(SettingsSyncSettings.getInstance().syncEnabled)

    val authServiceSpy = spy<JBAAuthService>()
    ApplicationManager.getApplication().replaceService(SettingsSyncAuthService::class.java, authServiceSpy, SettingsSyncMain.getInstance())

    val accountInfoService = mock<JBAccountInfoService>()
    `when`(accountInfoService.idToken).thenReturn(null)
    `when`(authServiceSpy.getAccountInfoService()).thenReturn(accountInfoService)

    val communicator = object : CloudConfigServerCommunicator("http://localhost:7777/cloudconfig", authServiceSpy) {
      override fun createCloudConfigClient(url: String, versionContext: CloudConfigVersionContext): CloudConfigFileClientV2? {
        return null
      }
    }

    communicator.checkServerState()
    //assertFalse(authServiceSpy.isLoggedIn())
    assertTrue(SettingsSyncSettings.getInstance().syncEnabled)
  }
}