/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.update.SequentialUpdatesContext;
import com.intellij.openapi.vcs.update.UpdateSession;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.Convertor;
import com.intellij.vcsUtil.VcsUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.auth.SvnAuthenticationManager;
import org.jetbrains.idea.svn.auth.SvnAuthenticationNotifier;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.junit.Before;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.*;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 3/4/13
 * Time: 9:55 PM
 */
public class SvnNativeClientAuthTest extends Svn17TestCase {
  private SvnVcs myVcs;
  private int myCertificateAnswer = ISVNAuthenticationProvider.ACCEPTED_TEMPORARY;
  private boolean myCredentialsCorrect = true;
  private boolean mySaveCredentials = false;
  private boolean myCancelAuth = false;

  private String outHttpUser = "test";
  private String outHttpPassword = "test";

  private final static String ourHTTP_URL = "http://svnsrvtest/stuff/autoTest";
  private final static String ourHTTPS_URL = "https://svnsrvtest:443/TestSSL/autoTest";

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
    final SvnTestInteractiveAuthentication authentication = new SvnTestInteractiveAuthentication(interactiveManager) {
      @Override
      public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
        ++ myCertificateAskedInteractivelyCount;
        return myCertificateAnswer;
      }

      @Override
      public SVNAuthentication requestClientAuthentication(String kind,
                                                           SVNURL url,
                                                           String realm,
                                                           SVNErrorMessage errorMessage,
                                                           SVNAuthentication previousAuth,
                                                           boolean authMayBeStored) {
        if (myCancelAuth) return null;
        return super.requestClientAuthentication(kind, url, realm, errorMessage, previousAuth, authMayBeStored);
      }
    };
    interactiveManager.setAuthenticationProvider(authentication);

    final SvnAuthenticationManager manager = configuration.getAuthenticationManager(myVcs);
    // will be the same as in interactive -> authentication notifier is not used
    manager.setAuthenticationProvider(authentication);

    authentication.addAuthentication(ISVNAuthenticationManager.PASSWORD,
                                     new Convertor<SVNURL, SVNAuthentication>() {
                                       @Override
                                       public SVNAuthentication convert(SVNURL o) {
                                         ++ myCredentialsAskedInteractivelyCount;
                                         if (myCancelAuth) return null;
                                         if (myCredentialsCorrect) {
                                           return new SVNPasswordAuthentication(outHttpUser, outHttpPassword, mySaveCredentials, o, false);
                                         } else {
                                           myCredentialsCorrect = true;// only once
                                           return new SVNPasswordAuthentication("1234214 23 4234", "324324", mySaveCredentials, o, false);
                                         }
                                       }
                                     });
    authentication.addAuthentication(ISVNAuthenticationManager.SSL,
                                     new Convertor<SVNURL, SVNAuthentication>() {
                                       @Override
                                       public SVNAuthentication convert(SVNURL o) {
                                         ++ myCredentialsAskedInteractivelyCount;
                                         if (myCancelAuth) return null;
                                         if (myCredentialsCorrect) {
                                           return new SVNSSLAuthentication(certFile, "12345", mySaveCredentials, o, false);
                                         } else {
                                           myCredentialsCorrect = true;// only once
                                           return new SVNSSLAuthentication(new File("1232432423"), "3245321532534235445", mySaveCredentials, o, false);
                                         }
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

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.getUseAcceleration());
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

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.getUseAcceleration());
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

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.getUseAcceleration());
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

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.getUseAcceleration());
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

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.getUseAcceleration());
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

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.getUseAcceleration());
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

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.getUseAcceleration());
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

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.getUseAcceleration());
    mySaveCredentials = true;
    myCertificateAnswer = ISVNAuthenticationProvider.ACCEPTED;

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

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.getUseAcceleration());
    mySaveCredentials = false;
    myCertificateAnswer = ISVNAuthenticationProvider.ACCEPTED;

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
    myCertificateAnswer = ISVNAuthenticationProvider.ACCEPTED_TEMPORARY;

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

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.getUseAcceleration());
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

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.getUseAcceleration());
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

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.getUseAcceleration());
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

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.getUseAcceleration());
    mySaveCredentials = false;
    myCertificateAnswer = ISVNAuthenticationProvider.REJECTED;

    updateExpectAuthCanceled(wc1, "Authentication canceled");

    //Assert.assertEquals(myExpectedCreds, myCredentialsAskedInteractivelyCount);
    //Assert.assertEquals(myExpectedCert, myCertificateAskedInteractivelyCount);

    myCertificateAnswer = ISVNAuthenticationProvider.ACCEPTED_TEMPORARY;
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

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.getUseAcceleration());
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

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.getUseAcceleration());
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

  private File testCheckoutImpl(final String url) throws IOException {
    final File root = FileUtil.createTempDirectory("checkoutRoot", "");
    root.deleteOnExit();
    Assert.assertTrue(root.exists());
    SvnCheckoutProvider
      .checkout(myProject, root, url, SVNRevision.HEAD, Depth.INFINITY, false, new CheckoutProvider.Listener() {
        @Override
        public void directoryCheckedOut(File directory, VcsKey vcs) {
        }

        @Override
        public void checkoutCompleted() {
        }
      }, WorkingCopyFormat.ONE_DOT_SEVEN);
    final int[] cnt = new int[1];
    cnt[0] = 0;
    FileUtil.processFilesRecursively(root, new Processor<File>() {
      @Override
      public boolean process(File file) {
        ++ cnt[0];
        return ! (cnt[0] > 1);
      }
    });
    Assert.assertTrue(cnt[0] > 1);
    myIsSecure = url.contains("https:");
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

  private void testBrowseRepositoryImpl(final String url) throws SVNException {
    final List<SVNDirEntry> list = new ArrayList<>();
    final SVNRepository repository = myVcs.getSvnKitManager().createRepository(url);
    repository.getDir(".", -1, null, new ISVNDirEntryHandler() {
      @Override
      public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
        list.add(dirEntry);
      }
    });

    Assert.assertTrue(!list.isEmpty());
  }

  private static @interface Test {}
}
