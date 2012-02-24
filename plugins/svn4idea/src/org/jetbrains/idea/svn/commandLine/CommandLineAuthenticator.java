/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import org.jetbrains.idea.svn.SvnAuthenticationManager;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.auth.ProviderType;
import org.jetbrains.idea.svn.auth.SvnAuthenticationListener;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.SVNAuthentication;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/1/12
 * Time: 12:28 PM
 */
public class CommandLineAuthenticator {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.commandLine.CommandLineAuthenticator");
  private final Project myProject;
  private final AuthenticationRequiringCommand myCommand;
  private final SvnConfiguration myConfiguration17;
  private final File myConfigDir;

  public CommandLineAuthenticator(Project project, AuthenticationRequiringCommand command) {
    myProject = project;
    myCommand = command;
    myConfiguration17 = SvnConfiguration.getInstance(project);
    final String configurationDirectory = myConfiguration17.getConfigurationDirectory();
    myConfigDir = new File(configurationDirectory);
  }

  public void doWithAuthentication() throws SVNException {
    try {
      myCommand.run(myConfigDir);
      return;
    } catch (SVNException e) {
      if (! e.getErrorMessage().getErrorCode().isAuthentication()) throw e;
    }
    File tempDirectory = null;
    try {
      tempDirectory = FileUtil.createTempDirectory("tmp", "Subversion");
      final SvnAuthenticationManager authenticationManager = SvnConfiguration.createForTmpDir(myProject, tempDirectory);
      //authenticationManager.setAuthenticationForced(true);
      authenticationManager.setArtificialSaving(true);
      myCommand.cleanup();
      tryGetCredentials(authenticationManager, tempDirectory);

      myCommand.cleanup();
      myCommand.run(tempDirectory);
    }
    catch (IOException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
    } finally {
      if (tempDirectory != null) {
        FileUtil.delete(tempDirectory);
      }
    }
  }

  private void tryGetCredentials(SvnAuthenticationManager manager, final File tempDirectory) throws SVNException {
    final StoreListener storeListener = new StoreListener();
    manager.addListener(storeListener);
    final SVNURL svnurl = myCommand.sampleUrl();
    try {
      myCommand.runWithSvnkitClient(tempDirectory, manager);
      LOG.assertTrue(false, "Credentials not asked"); // todo?
    } catch (SvnAuthenticationManager.CredentialsSavedException e) {
      // ok, check result?
      if (e.isSuccess()) {
        final SvnAuthenticationManager realManager = myConfiguration17.getAuthenticationManager(SvnVcs.getInstance(myProject));
        storeListener.reStore(myProject, realManager, svnurl);
      }
    }
    //final SVNWCClient client = new SVNWCClient(manager, myConfiguration17.getOptions(myProject));
    //client.doInfo(svnurl, SVNRevision.UNDEFINED, SVNRevision.UNDEFINED);
  }

  public interface AuthenticationRequiringCommand {
    void run(final File configDir) throws SVNException;
    void runWithSvnkitClient(final File configDir, SvnAuthenticationManager manager) throws SVNException;
    SVNURL sampleUrl();
    void cleanup() throws SVNException;
  }
  
  private static class StoreListener implements SvnAuthenticationListener {
    private final Set<StoreData> myData;
    private final Set<Trinity<String, String, SVNURL>> myAuthRequested;

    private StoreListener() {
      myData = new HashSet<StoreData>();
      myAuthRequested = new HashSet<Trinity<String, String, SVNURL>>();
    }

    @Override
    public void requested(ProviderType type, SVNURL url, String realm, String kind, boolean canceled) {
      if (ProviderType.interactive.equals(type)) {
        myAuthRequested.add(create(kind, realm, url));
      }
    }

    private Trinity<String, String, SVNURL> create(String kind, String realm, SVNURL url) {
      return new Trinity<String, String, SVNURL>(kind, realm, url);
    }

    @Override
    public void actualSaveWillBeTried(ProviderType type, SVNURL url, String realm, String kind) {
    }
    @Override
    public void saveAttemptStarted(ProviderType type, SVNURL url, String realm, String kind) {
    }
    @Override
    public void saveAttemptFinished(ProviderType type, SVNURL url, String realm, String kind) {
    }
    @Override
    public void acknowledge(boolean accepted, String kind, String realm, SVNErrorMessage message, SVNAuthentication authentication) {
      if (accepted && authentication.isStorageAllowed()) {
        final Trinity<String, String, SVNURL> trinity = create(kind, realm, authentication.getURL());
        if (myAuthRequested.contains(trinity)) {
          myData.add(new StoreData(kind, realm, authentication));
        }
      }
    }

    public void reStore(final Project project, final SvnAuthenticationManager realManager, final SVNURL svnurl) {
      for (StoreData data : myData) {
        if (data.myAuthentication == null) continue;
        realManager.requested(ProviderType.interactive, svnurl, data.myRealm, data.myKind, false);
        try {
          realManager.acknowledgeAuthentication(true, data.myKind, data.myRealm, null, data.myAuthentication);
        }
        catch (SVNException e) {
          VcsBalloonProblemNotifier.showOverChangesView(project, "Wasn't able to store credentials: " + e.getMessage(), MessageType.ERROR);
        }
      }
    }
  }

  private static class StoreData {
    public String myKind;
    public String myRealm;
    public SVNAuthentication myAuthentication;

    private StoreData(String kind, String realm, SVNAuthentication authentication) {
      myKind = kind;
      myRealm = realm;
      myAuthentication = authentication;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      StoreData data = (StoreData)o;

      if (myKind != null ? !myKind.equals(data.myKind) : data.myKind != null) return false;
      if (myRealm != null ? !myRealm.equals(data.myRealm) : data.myRealm != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myKind != null ? myKind.hashCode() : 0;
      result = 31 * result + (myRealm != null ? myRealm.hashCode() : 0);
      return result;
    }
  }
}
