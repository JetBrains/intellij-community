// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.CurrentContentRevision;
import com.intellij.openapi.vcs.history.VcsAppendableHistoryPartnerAdapter;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.update.FileGroup;
import com.intellij.openapi.vcs.update.UpdateSession;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.auth.AcceptResult;
import org.jetbrains.idea.svn.auth.PasswordAuthenticationData;
import org.jetbrains.idea.svn.auth.SvnAuthenticationManager;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.checkout.SvnCheckoutProvider;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.testFramework.UsefulTestCase.assertExists;
import static org.jetbrains.idea.svn.SvnUtil.parseUrl;
import static org.junit.Assert.*;

@Ignore
public class SvnProtocolsTest extends SvnTestCase {
  // todo correct URL
  private final static String ourSSH_URL = "svn+ssh://unit-069:222/home/irina/svnrepo";

  private final static Url ourHTTP_URL = parseUrl("http://unit-364.labs.intellij.net/svn/forMerge/tmp", false);
  private final static String ourHTTPS_URL = "https://";
  private final static String ourSVN_URL = "svn://";

  //private final static String[] ourTestURL = {ourSSH_URL, ourHTTP_URL};
  // at the moment
  private final static Url[] ourTestURL = {ourHTTP_URL};

  public static final String SSH_USER_NAME = "user";
  public static final String SSH_PASSWORD = "qwerty4321";
  public static final int SSH_PORT_NUMBER = 222;

  @Override
  @Before
  public void before() throws Exception {
    super.before();
    // replace authentication provider so that pass credentials without dialogs
    final SvnConfiguration configuration = SvnConfiguration.getInstance(myProject);
    final SvnAuthenticationManager interactiveManager = configuration.getInteractiveManager(vcs);
    final SvnTestInteractiveAuthentication authentication = new SvnTestInteractiveAuthentication() {
      @Override
      public AcceptResult acceptServerAuthentication(Url url, String realm, Object certificate, boolean canCache) {
        return AcceptResult.ACCEPTED_PERMANENTLY;
      }
    };
    interactiveManager.setAuthenticationProvider(authentication);

    final SvnAuthenticationManager manager = configuration.getAuthenticationManager(vcs);
    // will be the same as in interactive -> authentication notifier is not used
    manager.setAuthenticationProvider(authentication);

    authentication
      .addAuthentication(SvnAuthenticationManager.PASSWORD, o -> new PasswordAuthenticationData("sally", "abcde", true));
  }

  @Test
  public void testBrowseRepository() throws Exception {
    for (Url s : ourTestURL) {
      System.out.println("Testing URL: " + s);
      testBrowseRepositoryImpl(s);
    }
  }

  private void testBrowseRepositoryImpl(Url url) throws VcsException {
    List<DirectoryEntry> list = new ArrayList<>();
    vcs.getFactoryFromSettings().createBrowseClient().list(Target.on(url), null, null, list::add);

    assertTrue(!list.isEmpty());
  }

  @Test
  public void testCheckout() throws Exception {
    for (Url s : ourTestURL) {
      System.out.println("Testing URL: " + s);
      testCheckoutImpl(s);
    }
  }

  @Test
  public void testHistory() throws Exception {
    for (Url s : ourTestURL) {
      System.out.println("Testing URL: " + s);
      testHistoryImpl(s);
    }
  }

  private void testHistoryImpl(Url s) throws VcsException {
    final VcsHistoryProvider provider = vcs.getVcsHistoryProvider();
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
      provider
        .reportAppendableHistory(VcsContextFactory.getInstance().createFilePathOnNonLocal(s.toDecodedString(), true), partner);
    } catch (ProcessCanceledException e) {
      //ok
    }
    final List<VcsFileRevision> list = partner.getSession().getRevisionList();
    assertTrue(!list.isEmpty());
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
    assertTrue(wc1.isDirectory());
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(wc1);
    final UpdatedFiles files = UpdatedFiles.create();
    final UpdateSession session =
      vcs.getUpdateEnvironment().updateDirectories(new FilePath[]{VcsUtil.getFilePath(vf)}, files, new EmptyProgressIndicator(),
                                                   new Ref<>());
    assertTrue(session.getExceptions().isEmpty());
    assertFalse(session.isCanceled());
    assertFalse(files.getGroupById(FileGroup.CREATED_ID).getFiles().isEmpty());
    final String path = files.getGroupById(FileGroup.CREATED_ID).getFiles().iterator().next();
    final String name = path.substring(path.lastIndexOf(File.separator) + 1);
    assertEquals(created.getName(), name);
  }

  private File testCommitImpl(File wc1) throws IOException {
    assertTrue(wc1.isDirectory());
    final File file = FileUtil.createTempFile(wc1, "file", ".txt");
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(vf);
    final ArrayList<VirtualFile> files = new ArrayList<>();
    files.add(vf);
    final List<VcsException> exceptions = vcs.getCheckinEnvironment().scheduleUnversionedFilesForAddition(files);
    assertTrue(exceptions.isEmpty());

    final Change change = new Change(null, new CurrentContentRevision(VcsUtil.getFilePath(vf)));
    commit(Collections.singletonList(change), "commit");
    return file;
  }

  private File testCheckoutImpl(Url url) throws IOException {
    final File root = FileUtil.createTempDirectory("checkoutRoot", "");
    root.deleteOnExit();
    assertExists(root);
    SvnCheckoutProvider
      .checkout(myProject, root, url, Revision.HEAD, Depth.INFINITY, false, new CheckoutProvider.Listener() {
        @Override
        public void directoryCheckedOut(File directory, VcsKey vcs) {
        }

        @Override
        public void checkoutCompleted() {
        }
      }, WorkingCopyFormat.ONE_DOT_SEVEN);
    final int[] cnt = {0};
    FileUtil.processFilesRecursively(root, file -> {
      ++ cnt[0];
      return ! (cnt[0] > 1);
    });
    assertTrue(cnt[0] > 1);
    return root;
  }
}
