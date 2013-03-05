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
import junit.framework.Assert;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.junit.Before;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
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

  private String outHttpUser = "sally";
  private String outHttpPassword = "abcde";

  private final static String ourHTTP_URL = "http://unit-364.labs.intellij.net/svn/forMerge/tmp";

  private int myCertificateAskedInteractivelyCount = 0;
  private int myCredentialsAskedInteractivelyCount = 0;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
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
    myCertificateAskedInteractivelyCount = 0;
    myCredentialsAskedInteractivelyCount = 0;
  }

  @Test
  public void testTmpHttpUpdate() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTP_URL);
    Assert.assertEquals(1, myCredentialsAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    instance.clearAuthenticationDirectory(myProject);
    instance.clearRuntimeStorage();

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.myUseAcceleration);
    mySaveCredentials = false;

    updateSimple(wc1);

    Assert.assertEquals(2, myCredentialsAskedInteractivelyCount);

    // credentials are cached now only
    updateSimple(wc1);

    Assert.assertEquals(2, myCredentialsAskedInteractivelyCount);
  }

  @Test
  public void testPermanentHttpUpdate() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTP_URL);
    Assert.assertEquals(1, myCredentialsAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    instance.clearAuthenticationDirectory(myProject);
    instance.clearRuntimeStorage();

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.myUseAcceleration);
    mySaveCredentials = true;

    updateSimple(wc1);

    Assert.assertEquals(2, myCredentialsAskedInteractivelyCount);

    // credentials are cached now only
    updateSimple(wc1);

    Assert.assertEquals(2, myCredentialsAskedInteractivelyCount);
  }

  @Test
  public void testTmpHttpCommit() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTP_URL);
    Assert.assertEquals(1, myCredentialsAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    instance.clearAuthenticationDirectory(myProject);
    instance.clearRuntimeStorage();

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.myUseAcceleration);
    mySaveCredentials = false;

    testCommitImpl(wc1);

    Assert.assertEquals(2, myCredentialsAskedInteractivelyCount);

    // credentials are cached now only
    testCommitImpl(wc1);

    Assert.assertEquals(2, myCredentialsAskedInteractivelyCount);
  }

  @Test
  public void testPermanentHttpCommit() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTP_URL);
    Assert.assertEquals(1, myCredentialsAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    instance.clearAuthenticationDirectory(myProject);
    instance.clearRuntimeStorage();

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.myUseAcceleration);
    mySaveCredentials = true;

    testCommitImpl(wc1);

    Assert.assertEquals(2, myCredentialsAskedInteractivelyCount);

    // credentials are cached now only
    testCommitImpl(wc1);

    Assert.assertEquals(2, myCredentialsAskedInteractivelyCount);
  }

  @Test
  public void testFailedThenSuccessTmpHttpUpdate() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTP_URL);
    Assert.assertEquals(1, myCredentialsAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    instance.clearAuthenticationDirectory(myProject);
    instance.clearRuntimeStorage();

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.myUseAcceleration);
    mySaveCredentials = false;
    myCredentialsCorrect = false;

    updateSimple(wc1);

    Assert.assertEquals(3, myCredentialsAskedInteractivelyCount);

    // credentials are cached now only
    updateSimple(wc1);

    Assert.assertEquals(3, myCredentialsAskedInteractivelyCount);
  }

  @Test
  public void testCanceledThenFailedThenSuccessTmpHttpUpdate() throws Exception {
    final File wc1 = testCheckoutImpl(ourHTTP_URL);
    Assert.assertEquals(1, myCredentialsAskedInteractivelyCount);

    final SvnConfiguration instance = SvnConfiguration.getInstance(myProject);
    instance.clearAuthenticationDirectory(myProject);
    instance.clearRuntimeStorage();

    Assert.assertEquals(SvnConfiguration.UseAcceleration.commandLine, instance.myUseAcceleration);
    mySaveCredentials = false;
    myCredentialsCorrect = false;
    myCancelAuth = true;
    updateExpectAuthCanceled(wc1);
    myCancelAuth = false;

    updateSimple(wc1);

    Assert.assertEquals(3, myCredentialsAskedInteractivelyCount);

    // credentials are cached now only
    updateSimple(wc1);

    Assert.assertEquals(3, myCredentialsAskedInteractivelyCount);
  }

  private File testCommitImpl(File wc1) throws IOException {
    Assert.assertTrue(wc1.isDirectory());
    final File file = FileUtil.createTempFile(wc1, "file", ".txt");
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    Assert.assertNotNull(vf);
    final ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
    files.add(vf);
    final List<VcsException> exceptions = myVcs.getCheckinEnvironment().scheduleUnversionedFilesForAddition(files);
    Assert.assertTrue(exceptions.isEmpty());

    final Change change = new Change(null, new CurrentContentRevision(new FilePathImpl(vf)));
    final List<VcsException> commit = myVcs.getCheckinEnvironment().commit(Collections.singletonList(change), "commit");
    Assert.assertTrue(commit.isEmpty());
    return file;
  }

  private File testCheckoutImpl(final String url) throws IOException {
    final File root = FileUtil.createTempDirectory("checkoutRoot", "");
    root.deleteOnExit();
    Assert.assertTrue(root.exists());
    SvnCheckoutProvider
      .checkout(myProject, root, url, SVNRevision.HEAD, SVNDepth.INFINITY, false, new CheckoutProvider.Listener() {
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
    return root;
  }

  private void updateExpectAuthCanceled(File wc1) {
    Assert.assertTrue(wc1.isDirectory());
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(wc1);
    final UpdatedFiles files = UpdatedFiles.create();
    final UpdateSession session =
      myVcs.getUpdateEnvironment().updateDirectories(new FilePath[]{new FilePathImpl(vf)}, files, new EmptyProgressIndicator(),
                                                     new Ref<SequentialUpdatesContext>());
    Assert.assertTrue(session.getExceptions() != null && ! session.getExceptions().isEmpty());
    Assert.assertTrue(!session.isCanceled());
    Assert.assertTrue(session.getExceptions().get(0).getMessage().contains("Authentication canceled"));
  }

  private void updateSimple(File wc1) {
    Assert.assertTrue(wc1.isDirectory());
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(wc1);
    final UpdatedFiles files = UpdatedFiles.create();
    final UpdateSession session =
      myVcs.getUpdateEnvironment().updateDirectories(new FilePath[]{new FilePathImpl(vf)}, files, new EmptyProgressIndicator(),
                                                     new Ref<SequentialUpdatesContext>());
    Assert.assertTrue(session.getExceptions() == null || session.getExceptions().isEmpty());
    Assert.assertTrue(!session.isCanceled());
  }

  private void testBrowseRepositoryImpl(final String url) throws SVNException {
    final List<SVNDirEntry> list = new ArrayList<SVNDirEntry>();
    final SVNRepository repository = myVcs.createRepository(url);
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
