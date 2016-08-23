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
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.history.VcsAppendableHistoryPartnerAdapter;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.SequentialUpdatesContext;
import com.intellij.openapi.vcs.update.UpdateSession;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.containers.Convertor;
import com.intellij.vcsUtil.VcsUtil;
import junit.framework.Assert;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.auth.SvnAuthenticationManager;
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
 * Date: 2/11/13
 * Time: 3:48 PM
 */
public class SvnProtocolsTest extends Svn17TestCase {
  // todo correct URL
  private final static String ourSSH_URL = "svn+ssh://unit-069:222/home/irina/svnrepo";

  private final static String ourHTTP_URL = "http://unit-364.labs.intellij.net/svn/forMerge/tmp";
  private final static String ourHTTPS_URL = "https://";
  private final static String ourSVN_URL = "svn://";

  //private final static String[] ourTestURL = {ourSSH_URL, ourHTTP_URL};
  // at the moment
  private final static String[] ourTestURL = {ourHTTP_URL};

  public static final String SSH_USER_NAME = "user";
  public static final String SSH_PASSWORD = "qwerty4321";
  public static final int SSH_PORT_NUMBER = 222;
  private SvnVcs myVcs;


  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    myVcs = SvnVcs.getInstance(myProject);
    // replace authentication provider so that pass credentials without dialogs
    final SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    final SvnAuthenticationManager interactiveManager = configuration.getInteractiveManager(myVcs);
    final SvnTestInteractiveAuthentication authentication = new SvnTestInteractiveAuthentication(interactiveManager) {
      @Override
      public int acceptServerAuthentication(SVNURL url, String realm, Object certificate, boolean resultMayBeStored) {
        return ISVNAuthenticationProvider.ACCEPTED;
      }
    };
    interactiveManager.setAuthenticationProvider(authentication);

    final SvnAuthenticationManager manager = configuration.getAuthenticationManager(myVcs);
    // will be the same as in interactive -> authentication notifier is not used
    manager.setAuthenticationProvider(authentication);

    authentication.addAuthentication(ISVNAuthenticationManager.SSH,
                                     new Convertor<SVNURL, SVNAuthentication>() {
                                       @Override
                                       public SVNAuthentication convert(SVNURL o) {
                                         return new SVNSSHAuthentication(SSH_USER_NAME, SSH_PASSWORD, SSH_PORT_NUMBER, true, o, false);
                                       }
                                     });
    authentication.addAuthentication(ISVNAuthenticationManager.USERNAME, new Convertor<SVNURL, SVNAuthentication>() {
      @Override
      public SVNAuthentication convert(SVNURL o) {
        return new SVNUserNameAuthentication(SSH_USER_NAME, true, o, false);
      }
    });
    authentication.addAuthentication(ISVNAuthenticationManager.PASSWORD,
                                     new Convertor<SVNURL, SVNAuthentication>() {
                                       @Override
                                       public SVNAuthentication convert(SVNURL o) {
                                         return new SVNPasswordAuthentication("sally", "abcde", true, o, false);
                                       }
                                     });
  }

  @Test
  public void testBrowseRepository() throws Exception {
    for (String s : ourTestURL) {
      System.out.println("Testing URL: " + s);
      testBrowseRepositoryImpl(s);
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

    Assert.assertTrue(! list.isEmpty());
  }

  @Test
  public void testCheckout() throws Exception {
    for (String s : ourTestURL) {
      System.out.println("Testing URL: " + s);
      testCheckoutImpl(s);
    }
  }

  @Test
  public void testHistory() throws Exception {
    for (String s : ourTestURL) {
      System.out.println("Testing URL: " + s);
      testHistoryImpl(s);
    }
  }

  private void testHistoryImpl(String s) throws VcsException {
    final VcsHistoryProvider provider = myVcs.getVcsHistoryProvider();
    final VcsAppendableHistoryPartnerAdapter partner = new VcsAppendableHistoryPartnerAdapter() {
      @Override
      public void acceptRevision(VcsFileRevision revision) {
        super.acceptRevision(revision);
        if(getSession().getRevisionList().size() > 1) {
          throw new ProcessCanceledException();
        }
      }
    };
    try {
      provider.reportAppendableHistory(VcsContextFactory.SERVICE.getInstance().createFilePathOnNonLocal(s, true), partner);
    } catch (ProcessCanceledException e) {
      //ok
    }
    final List<VcsFileRevision> list = partner.getSession().getRevisionList();
    Assert.assertTrue(! list.isEmpty());
  }

  // todo this test writes to repository - so it's disabled for now - while admins are preparing a server
  /*
  @Test
  public void testUpdateAndCommit() throws Exception {
    for (String url : ourTestURL) {
      final File wc1 = testCheckoutImpl(url);
      final File wc2 = testCheckoutImpl(url);

      final File file = testCommitImpl(wc1);
      System.out.println("Committed file: " + file.getPath());
      testUpdateImpl(wc2, file);
    }
  }*/

  private void testUpdateImpl(File wc1, final File created) {
    Assert.assertTrue(wc1.isDirectory());
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(wc1);
    final UpdatedFiles files = UpdatedFiles.create();
    final UpdateSession session =
      myVcs.getUpdateEnvironment().updateDirectories(new FilePath[]{VcsUtil.getFilePath(vf)}, files, new EmptyProgressIndicator(),
                                                     new Ref<>());
    Assert.assertTrue(session.getExceptions() == null || session.getExceptions().isEmpty());
    Assert.assertTrue(! session.isCanceled());
    Assert.assertTrue(! files.getGroupById(FileGroup.CREATED_ID).getFiles().isEmpty());
    final String path = files.getGroupById(FileGroup.CREATED_ID).getFiles().iterator().next();
    final String name = path.substring(path.lastIndexOf(File.separator) + 1);
    Assert.assertEquals(created.getName(), name);
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
    return root;
  }

  // disable tests for now
  private @interface Test{}
}
