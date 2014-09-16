package org.jetbrains.plugins.settingsRepository

import org.junit.Test
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.CredentialItem
import com.intellij.openapi.util.NotNullLazyValue
import org.jetbrains.plugins.settingsRepository.git.JGitCredentialsProvider
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

import org.hamcrest.CoreMatchers.*
import org.hamcrest.text.IsEmptyString.*
import org.junit.Assert.assertThat
import java.io.File
import org.junit.After
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.UsefulTestCase

class CredentialsTest {
  private var storeFile: File? = null

  private fun createProvider(credentialsStore: CredentialsStore): JGitCredentialsProvider {
    return JGitCredentialsProvider(NotNullLazyValue.createConstantValue<CredentialsStore>(credentialsStore), FileRepositoryBuilder().setBare().setGitDir(File("/tmp/fake")).build())
  }

  private fun createFileStore(): FileCredentialsStore {
    storeFile = FileUtil.generateRandomTemporaryPath()
    return FileCredentialsStore(storeFile!!)
  }

  public After fun tearDown() {
    storeFile?.delete()
    storeFile = null
  }

  public Test fun explicitSpecifiedInURL() {
    val credentialsStore = createFileStore()
    val username = CredentialItem.Username()
    val password = CredentialItem.Password()
    val uri = URIish("https://develar:bike@github.com/develar/settings-repository.git")
    assertThat(createProvider(credentialsStore).get(uri, username, password), equalTo(true))
    assertThat(username.getValue(), equalTo("develar"))
    assertThat(String(password.getValue()!!), equalTo("bike"))
    // ensure that credentials store was not used
    assertThat(credentialsStore.get(uri.getHost()), nullValue())
    assertThat(storeFile?.exists(), equalTo(false))
  }

  public Test fun gitCredentialHelper() {
    // we don't yet setup test environment for this test and use host environment
    if (UsefulTestCase.IS_UNDER_TEAMCITY) {
      return
    }

    val credentialsStore = createFileStore()
    val username = CredentialItem.Username()
    val password = CredentialItem.Password()
    val uri = URIish("https://develar@bitbucket.org/develar/test-ics.git")
    assertThat(createProvider(credentialsStore).get(uri, username, password), equalTo(true))
    assertThat(username.getValue(), equalTo("develar"))
    assertThat(String(password.getValue()!!), not(isEmptyString()))
  }
}
