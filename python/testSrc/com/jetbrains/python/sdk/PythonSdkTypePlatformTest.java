package com.jetbrains.python.sdk;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.remote.ext.CredentialsLanguageContribution;
import com.intellij.testFramework.PlatformLiteFixture;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import org.junit.Assert;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.when;

/**
 * @author Alexander Koshevoy
 */
public class PythonSdkTypePlatformTest extends PlatformLiteFixture {
  @Mock private Sdk sdk;
  @Mock private PyRemoteSdkAdditionalDataBase remoteSdkAdditionalData;
  @Mock private SdkAdditionalData sdkAdditionalData;
  @Mock private CredentialsLanguageContribution credentialsLanguageContribution;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initApplication();
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
}