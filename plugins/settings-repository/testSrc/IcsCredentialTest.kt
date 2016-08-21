package org.jetbrains.settingsRepository

import com.intellij.credentialStore.Credentials
import com.intellij.testFramework.ApplicationRule
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.URIish
import org.jetbrains.settingsRepository.git.JGitCredentialsProvider
import org.junit.ClassRule
import org.junit.Test
import java.io.File

internal class IcsCredentialTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ApplicationRule()
  }

  private fun createProvider(credentialsStore: IcsCredentialsStore): JGitCredentialsProvider {
    return JGitCredentialsProvider(lazyOf(credentialsStore), FileRepositoryBuilder().setBare().setGitDir(File("/tmp/fake")).build())
  }

  private fun createFileStore() = IcsCredentialsStoreWrapper()

  @Test
  fun explicitSpecifiedInURL() {
    val credentialsStore = createFileStore()
    val username = CredentialItem.Username()
    val password = CredentialItem.Password()
    val uri = URIish("https://develar:bike@github.com/develar/settings-repository.git")
    assertThat(createProvider(credentialsStore).get(uri, username, password)).isTrue()
    assertThat(username.value).isEqualTo("develar")
    assertThat(String(password.value!!)).isEqualTo("bike")
    // ensure that credentials store was not used
    assertThat(credentialsStore.get(uri.host)).isNull()
  }

  @Test
  fun gitCredentialHelper() {
    val credentialStore = createFileStore()
    credentialStore.set("bitbucket.org", Credentials("develar", "bike"))

    val username = CredentialItem.Username()
    val password = CredentialItem.Password()
    val uri = URIish("https://develar@bitbucket.org/develar/test-ics.git")
    assertThat(createProvider(credentialStore).get(uri, username, password)).isTrue()
    assertThat(username.value).isEqualTo("develar")
    assertThat(String(password.value!!)).isNotEmpty()
  }
}
