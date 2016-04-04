package org.jetbrains.settingsRepository.test

import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.UsefulTestCase
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.URIish
import org.jetbrains.keychain.CredentialsStore
import org.jetbrains.keychain.FileCredentialsStore
import org.jetbrains.settingsRepository.git.JGitCredentialsProvider
import org.junit.After
import org.junit.Test
import java.io.File

class CredentialsTest {
  private var storeFile: File? = null

  private fun createProvider(credentialsStore: CredentialsStore): JGitCredentialsProvider {
    return JGitCredentialsProvider(NotNullLazyValue.createConstantValue(credentialsStore), FileRepositoryBuilder().setBare().setGitDir(File("/tmp/fake")).build())
  }

  private fun createFileStore(): FileCredentialsStore {
    return FileCredentialsStore(FileUtil.generateRandomTemporaryPath().toPath())
  }

  @After fun tearDown() {
    storeFile?.delete()
    storeFile = null
  }

  @Test fun explicitSpecifiedInURL() {
    val credentialsStore = createFileStore()
    val username = CredentialItem.Username()
    val password = CredentialItem.Password()
    val uri = URIish("https://develar:bike@github.com/develar/settings-repository.git")
    assertThat(createProvider(credentialsStore).get(uri, username, password)).isTrue()
    assertThat(username.value).isEqualTo("develar")
    assertThat(String(password.value!!)).isEqualTo("bike")
    // ensure that credentials store was not used
    assertThat(credentialsStore.get(uri.host)).isNull()
    assertThat(storeFile?.exists()).isFalse()
  }

  @Test fun gitCredentialHelper() {
    // we don't yet setup test environment for this test and use host environment
    if (UsefulTestCase.IS_UNDER_TEAMCITY) {
      return
    }

    val credentialsStore = createFileStore()
    val username = CredentialItem.Username()
    val password = CredentialItem.Password()
    val uri = URIish("https://develar@bitbucket.org/develar/test-ics.git")
    assertThat(createProvider(credentialsStore).get(uri, username, password)).isTrue()
    assertThat(username.value).isEqualTo("develar")
    assertThat(String(password.value!!)).isNotEmpty()
  }
}
