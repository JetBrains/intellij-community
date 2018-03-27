// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.update.UpdateSession;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.auth.*;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.jetbrains.idea.svn.SvnUtil.parseUrl;

public class SvnNativeClientAuthTest extends Svn17TestCase {
  private SvnVcs myVcs;
  private AcceptResult myCertificateAnswer = AcceptResult.ACCEPTED_TEMPORARILY;
  private boolean myCredentialsCorrect = true;
  private boolean mySaveCredentials = false;
  private boolean myCancelAuth = false;

  private String outHttpUser = "test";
  private String outHttpPassword = "test";

  private final static Url ourHTTP_URL = parseUrl("http://svnsrvtest/stuff/autoTest", false);
  private final static Url ourHTTPS_URL = parseUrl("https://svnsrvtest:443/TestSSL/autoTest", false);

  private int myCertificateAskedInteractivelyCount = 0;
  private int myCredentialsAskedInteractivelyCount = 0;

  private int myExpectedCreds = 0;
  private int myExpectedCert = 0;
  private boolean myIsSecure;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    final File certFile = new File(myPluginRoot, getTestDataDir() + "/svn/____.pfx");
    setNativeAcceleration(true);
    myVcs = SvnVcs.getInstance(myProject);
    // replace authentication provider so that pass credentials without dialogs
    final SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    final File svnconfig = FileUtil.createTempDirectory("svnconfig", "");
    configuration.setConfigurationDirParameters(false, svnconfig.getPath());

    final SvnAuthenticationManager interactiveManager = configuration.getInteractiveManager(myVcs);
    final SvnTestInteractiveAuthentication authentication = new SvnTestInteractiveAuthentication() {
      @Override
      public AcceptResult acceptServerAuthentication(Url url, String realm, Object certificate, boolean canCache) {
        ++ myCertificateAskedInteractivelyCount;
        return myCertificateAnswer;
      }

      @Override
      public AuthenticationData requestClientAuthentication(String kind, Url url, String realm, boolean canCache) {
        if (myCancelAuth) return null;
        return super.requestClientAuthentication(kind, url, realm, canCache);
      }
    };
    interactiveManager.setAuthenticationProvider(authentication);

    final SvnAuthenticationManager manager = configuration.getAuthenticationManager(myVcs);
    // will be the same as in interactive -> authentication notifier is not used
    manager.setAuthenticationProvider(authentication);

    authentication.addAuthentication(SvnAuthenticationManager.PASSWORD,
                                     o -> {
                                       ++ myCredentialsAskedInteractivelyCount;
                                       if (myCancelAuth) return null;
                                       if (myCredentialsCorrect) {
                                         return new PasswordAuthenticationData(outHttpUser, outHttpPassword, mySaveCredentials);
                                       } else {
                                         myCredentialsCorrect = true;// only once
                                         return new PasswordAuthenticationData("1234214 23 4234", "324324", mySaveCredentials);
                                       }
                                     });
    authentication.addAuthentication(SvnAuthenticationManager.SSL,
                                     o -> {
                                       ++ myCredentialsAskedInteractivelyCount;
                                       if (myCancelAuth) return null;
                                       if (myCredentialsCorrect) {
                                         return new CertificateAuthenticationData(certFile.getAbsolutePath(), "12345".toCharArray(),
                                                                                  mySaveCredentials);
                                       } else {
                                         myCredentialsCorrect = true;// only once
                                         return new CertificateAuthenticationData("1232432423", "3245321532534235445".toCharArray(),
                                                                                  mySaveCredentials);
                                       }
                                     });
    myCertificateAskedInteractivelyCount = 0;
    myCredentialsAskedInteractivelyCount = 0;
  }

  @Test
  public void testTmpHttpUpdate() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTP_URL);
    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    clearAuthCache(instance);

    mySaveCredentials = false;

    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);

    // credentials are cached now only
    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
  }

  @Test
  public void testTmpSSLUpdate() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTPS_URL);
    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    clearAuthCache(instance);

    mySaveCredentials = false;

    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    // credentials are cached now only
    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);
  }

  @Test
  public void testPermanentSSLUpdate() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTPS_URL);
    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    clearAuthCache(instance);

    mySaveCredentials = true;

    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    // credentials are cached now only
    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);
  }

  @Test
  public void testPermanentHttpUpdate() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTP_URL);
    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    clearAuthCache(instance);

    mySaveCredentials = true;

    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);

    // credentials are cached now only
    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
  }

  @Test
  public void testTmpHttpCommit() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTP_URL);
    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    clearAuthCache(instance);

    mySaveCredentials = false;

    testCommitImpl(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);

    // credentials are cached now only
    testCommitImpl(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
  }

  @Test
  public void testTmpSSLCommit() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTPS_URL);
    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    clearAuthCache(instance);

    mySaveCredentials = false;

    testCommitImpl(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    // credentials are cached now only
    testCommitImpl(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);
  }

  @Test
  public void testPermanentSSLCommit() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTPS_URL);
    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    clearAuthCache(instance);

    mySaveCredentials = true;

    testCommitImpl(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    // credentials are cached now only
    testCommitImpl(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);
  }

  @Test
  public void test2PermanentSSLCommit() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTPS_URL);
    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    clearAuthCache(instance);

    mySaveCredentials = true;
    myCertificateAnswer = AcceptResult.ACCEPTED_PERMANENTLY;

    testCommitImpl(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    // credentials are cached now only
    testCommitImpl(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);
  }

  private static void clearAuthCache(@NotNull SvnConfiguration instance) {
    SvnAuthenticationNotifier.clearAuthenticationDirectory(instance);
    instance.clearRuntimeStorage();
  }

  @Test
  public void testMixedSSLCommit() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTPS_URL);
    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    clearAuthCache(instance);

    mySaveCredentials = false;
    myCertificateAnswer = AcceptResult.ACCEPTED_PERMANENTLY;

    testCommitImpl(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    // credentials are cached now only
    testCommitImpl(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);
    //------------
    clearAuthCache(instance);
    mySaveCredentials = true;
    myCertificateAnswer = AcceptResult.ACCEPTED_TEMPORARILY;

    testCommitImpl(wc1);
    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);
    testCommitImpl(wc1);
    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);
  }

  @Test
  public void testPermanentHttpCommit() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTP_URL);
    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    clearAuthCache(instance);

    mySaveCredentials = true;

    testCommitImpl(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);

    // credentials are cached now only
    testCommitImpl(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
  }

  @Test
  public void testFailedThenSuccessTmpHttpUpdate() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTP_URL);
    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    clearAuthCache(instance);

    mySaveCredentials = false;
    myCredentialsCorrect = false;

    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);

    // credentials are cached now only
    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
  }

  @Test
  public void testFailedThenSuccessTmpSSLUpdate() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTPS_URL);
    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    clearAuthCache(instance);

    mySaveCredentials = false;
    myCredentialsCorrect = false;

    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    // credentials wrong, but certificate was ok accepted
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    // credentials are cached now only
    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);
  }

  @Test
  public void testCertificateRejectedThenCredentialsFailedThenSuccessTmpSSLUpdate() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTPS_URL);
    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    clearAuthCache(instance);

    mySaveCredentials = false;
    myCertificateAnswer = AcceptResult.REJECTED;

    updateExpectAuthCanceled(wc1, "Authentication canceled");

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    myCertificateAnswer = AcceptResult.ACCEPTED_TEMPORARILY;
    myCredentialsCorrect = false;

    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    // credentials wrong, but certificate was ok accepted
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    // credentials are cached now only
    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);
  }

  @Test
  public void testCanceledThenFailedThenSuccessTmpHttpUpdate() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTP_URL);
    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    clearAuthCache(instance);

    mySaveCredentials = false;
    myCredentialsCorrect = false;
    myCancelAuth = true;
    updateExpectAuthCanceled(wc1, "Authentication canceled");
    myCancelAuth = false;

    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);

    // credentials are cached now only
    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
  }

  @Test
  public void testCanceledThenFailedThenSuccessTmpSSLUpdate() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTPS_URL);
    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    clearAuthCache(instance);

    mySaveCredentials = false;
    myCredentialsCorrect = false;
    myCancelAuth = true;
    updateExpectAuthCanceled(wc1, "Authentication canceled");
    myCancelAuth = false;

    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    // credentials are cached now only
    updateSimple(wc1);

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);
  }

  private File testCommitImpl(File wc1) throws IOException {
    Assert.assertTrue(wc1.isDirectory());
    final File file = FileUtil.createTempFile(wc1, "file", ".txt");
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    Assert.assertNotNull(vf);
    final ArrayList<VirtualFile> files = new ArrayList<>();
    files.add(vf);
    final List<VcsException> exceptions = myVcs.getCheckinEnvironment().scheduleUnversionedFilesForAddition(files);
    Assert.assertTrue(exceptions.isEmpty());

    final Change change = new Change(null, new CurrentContentRevision(VcsUtil.getFilePath(vf)));
    final List<VcsException> commit = myVcs.getCheckinEnvironment().commit(Collections.singletonList(change), "commit");
    Assert.assertTrue(commit.isEmpty());
    ++ myExpectedCreds;
    ++ myExpectedCert;
    return file;
  }

  private File testCheckoutImpl(@NotNull Url url) throws IOException {
    final File root = FileUtil.createTempDirectory("checkoutRoot", "");
    root.deleteOnExit();
    Assert.assertTrue(root.exists());
    SvnCheckoutProvider
      .checkout(myProject, root, url, Revision.HEAD, Depth.INFINITY, false, new CheckoutProvider.Listener() {
        @Override
        public void directoryCheckedOut(File directory, VcsKey vcs) {
        }

        @Override
        public void checkoutCompleted() {
        }
      }, WorkingCopyFormat.ONE_DOT_SEVEN);
    final int[] cnt = new int[1];
    cnt[0] = 0;
    FileUtil.processFilesRecursively(root, file -> {
      ++ cnt[0];
      return ! (cnt[0] > 1);
    });
    Assert.assertTrue(cnt[0] > 1);
    myIsSecure = "https".equals(url.getProtocol());
    if (myIsSecure) {
      ++ myExpectedCreds;
      ++ myExpectedCert;
    }
    ProjectLevelVcsManager.getInstance(myProject).setDirectoryMapping(root.getPath(), SvnVcs.VCS_NAME);
    refreshSvnMappingsSynchronously();
    return root;
  }

  private void updateExpectAuthCanceled(File wc1, String expectedText) {
    Assert.assertTrue(wc1.isDirectory());
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(wc1);
    final UpdatedFiles files = UpdatedFiles.create();
    final UpdateSession session =
      myVcs.getUpdateEnvironment().updateDirectories(new FilePath[]{VcsUtil.getFilePath(vf)}, files, new EmptyProgressIndicator(),
                                                     new Ref<>());
    Assert.assertTrue(session.getExceptions() != null && ! session.getExceptions().isEmpty());
    Assert.assertTrue(!session.isCanceled());
    Assert.assertTrue(session.getExceptions().get(0).getMessage().contains(expectedText));

    if (myIsSecure) {
      ++ myExpectedCreds;
      ++ myExpectedCert;
    }
  }

  private void updateSimple(File wc1) {
    Assert.assertTrue(wc1.isDirectory());
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(wc1);
    final UpdatedFiles files = UpdatedFiles.create();
    final UpdateSession session =
      myVcs.getUpdateEnvironment().updateDirectories(new FilePath[]{VcsUtil.getFilePath(vf)}, files, new EmptyProgressIndicator(),
                                                     new Ref<>());
    Assert.assertTrue(session.getExceptions() == null || session.getExceptions().isEmpty());
    Assert.assertTrue(!session.isCanceled());
    if (myIsSecure) {
      ++ myExpectedCreds;
      ++ myExpectedCert;
    }
  }

  private static @interface Test {}
}
