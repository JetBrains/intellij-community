package com.jetbrains.python.sdk;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.remote.VagrantBasedCredentialsHolder;
import com.intellij.remote.ext.CredentialsCase;
import com.intellij.remote.ext.CredentialsLanguageContribution;
import com.intellij.testFramework.PlatformLiteFixture;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import org.junit.Assert;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Alexander Koshevoy
 */
public class PythonSdkTypeTest extends PlatformLiteFixture {
  @Mock private Sdk sdk;
  @Mock private PyRemoteSdkAdditionalDataBase remoteSdkAdditionalData;
  @Mock private SdkAdditionalData sdkAdditionalData;
  @Mock private VagrantBasedCredentialsHolder vagrantBasedCredentialsHolder;
  @Mock private CredentialsLanguageContribution credentialsLanguageContribution;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    registerExtension(CredentialsLanguageContribution.EP_NAME, credentialsLanguageContribution);
  }

  public void testLocalSdk() {
    when(sdk.getSdkAdditionalData()).thenReturn(sdkAdditionalData);
    Assert.assertFalse(PythonSdkType.hasInvalidRemoteCredentials(sdk));
  }

  public void testAbsentRemoteSdkCredentials() {
    when(sdk.getSdkAdditionalData()).thenReturn(remoteSdkAdditionalData);
    Assert.assertFalse(PythonSdkType.hasInvalidRemoteCredentials(sdk));
  }

  public void testValidRemoteSdkCredentials() {
    when(vagrantBasedCredentialsHolder.getVagrantFolder()).thenReturn("/home/vagrant/box");
    mockSwitchOnConnectionType(remoteSdkAdditionalData, vagrantBasedCredentialsHolder);
    when(sdk.getSdkAdditionalData()).thenReturn(remoteSdkAdditionalData);
    Assert.assertFalse(PythonSdkType.hasInvalidRemoteCredentials(sdk));
  }

  public void testInvalidRemoteSdkCredentials() {
    when(vagrantBasedCredentialsHolder.getVagrantFolder()).thenReturn("");
    mockSwitchOnConnectionType(remoteSdkAdditionalData, vagrantBasedCredentialsHolder);
    when(sdk.getSdkAdditionalData()).thenReturn(remoteSdkAdditionalData);
    Assert.assertTrue(PythonSdkType.hasInvalidRemoteCredentials(sdk));
  }

  private static void mockSwitchOnConnectionType(PyRemoteSdkAdditionalDataBase data, final VagrantBasedCredentialsHolder credentials) {
    Mockito.doAnswer(invocation -> {
      ((CredentialsCase.Vagrant)invocation.getArguments()[0]).process(credentials);
      return null;
    }).when(data).switchOnConnectionType(any(CredentialsCase.Vagrant.class));
  }
}