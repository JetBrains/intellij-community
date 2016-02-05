package com.jetbrains.python.sdk;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.remote.VagrantBasedCredentialsHolder;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import junit.framework.TestCase;
import org.junit.Assert;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.when;

/**
 * @author Alexander Koshevoy
 */
public class PythonSdkTypeTest extends TestCase {
  @Mock
  private Sdk sdk;
  @Mock
  private PyRemoteSdkAdditionalDataBase remoteSdkAdditionalData;
  @Mock
  private SdkAdditionalData sdkAdditionalData;
  @Mock
  private VagrantBasedCredentialsHolder vagrantBasedCredentialsHolder;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);
  }

  public void testLocalSdk() throws Exception {
    when(sdk.getSdkAdditionalData()).thenReturn(sdkAdditionalData);
    Assert.assertFalse(PythonSdkType.hasInvalidRemoteCredentials(sdk));
  }

  public void testAbsentRemoteSdkCredentials() throws Exception {
    when(sdk.getSdkAdditionalData()).thenReturn(remoteSdkAdditionalData);
    Assert.assertFalse(PythonSdkType.hasInvalidRemoteCredentials(sdk));
  }

  public void testValidRemoteSdkCredentials() throws Exception {
    when(vagrantBasedCredentialsHolder.getVagrantFolder()).thenReturn("/home/vagrant/box");
    mockSwitchOnConnectionType(remoteSdkAdditionalData, vagrantBasedCredentialsHolder);
    when(sdk.getSdkAdditionalData()).thenReturn(remoteSdkAdditionalData);
    Assert.assertFalse(PythonSdkType.hasInvalidRemoteCredentials(sdk));
  }

  public void testInvalidRemoteSdkCredentials() throws Exception {
    when(vagrantBasedCredentialsHolder.getVagrantFolder()).thenReturn("");
    mockSwitchOnConnectionType(remoteSdkAdditionalData, vagrantBasedCredentialsHolder);
    when(sdk.getSdkAdditionalData()).thenReturn(remoteSdkAdditionalData);
    Assert.assertTrue(PythonSdkType.hasInvalidRemoteCredentials(sdk));
  }

  private static void mockSwitchOnConnectionType(PyRemoteSdkAdditionalDataBase data, final VagrantBasedCredentialsHolder credentials) {
    // TODO: fix
    //Mockito.doAnswer(new Answer() {
    //  @Override
    //  public Void answer(InvocationOnMock invocation) {
    //    ((RemoteSdkConnectionAcceptor)invocation.getArguments()[0]).vagrant(credentials);
    //    return null;
    //  }
    //}).when(data).switchOnConnectionType(any(RemoteSdkConnectionAcceptor.class));
  }
}