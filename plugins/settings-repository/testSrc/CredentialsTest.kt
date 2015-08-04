package org.jetbrains.settingsRepository.test

import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.UsefulTestCase
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.URIish
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.text.IsEmptyString.isEmptyString
import org.jetbrains.keychain.CredentialsStore
import org.jetbrains.keychain.FileCredentialsStore
import org.jetbrains.settingsRepository.git.JGitCredentialsProvider
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.File

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
