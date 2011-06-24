/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.impl.GenericNotifierImpl;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.dialogs.SvnInteractiveAuthenticationProvider;
import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Timer;

public class SvnAuthenticationNotifier extends GenericNotifierImpl<SvnAuthenticationNotifier.AuthenticationRequest, SVNURL> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.SvnAuthenticationNotifier");

  private final SvnVcs myVcs;
  private final RootsToWorkingCopies myRootsToWorkingCopies;
  private final Map<SVNURL, Boolean> myCopiesPassiveResults;
  private Timer myTimer;
  private volatile boolean myVerificationInProgress;

  public SvnAuthenticationNotifier(final SvnVcs svnVcs) {
    super(svnVcs.getProject(), svnVcs.getDisplayName(), "Not Logged In to Subversion", NotificationType.ERROR);
    myVcs = svnVcs;
    myRootsToWorkingCopies = myVcs.getRootsToWorkingCopies();
    myCopiesPassiveResults = Collections.synchronizedMap(new HashMap<SVNURL, Boolean>());
    myVerificationInProgress = false;
  }

  public void init() {
    if (myTimer != null) {
      stop();
    }
    myTimer = new Timer("SVN authentication timer");
    // every 10 minutes
    myTimer.schedule(new TimerTask() {
      @Override
      public void run() {
        myCopiesPassiveResults.clear();
      }
    }, 10000, 10 * 60 * 1000);
  }

  public void stop() {
    myTimer.cancel();
    myTimer = null;
  }

  @Override
  protected boolean ask(final AuthenticationRequest obj, String description) {
    if (myVerificationInProgress) {
      return showAlreadyChecking();
    }
    myVerificationInProgress = true;

    final Ref<Boolean> resultRef = new Ref<Boolean>();

    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          final boolean result =
            interactiveValidation(obj.myProject, obj.getUrl(), obj.getRealm(), obj.getKind());
          log("ask result for: " + obj.getUrl() + " is: " + result);
          resultRef.set(result);
          if (result) {
            onStateChangedToSuccess(obj);
          }
        } finally {
          myVerificationInProgress = false;
        }
      }
    });
    return false;
  }

  private boolean showAlreadyChecking() {
    final IdeFrame frameFor = WindowManagerEx.getInstanceEx().findFrameFor(myProject);
    if (frameFor != null) {
      final JComponent component = frameFor.getComponent();
      Point point = component.getMousePosition();
      if (point == null) {
        point = new Point((int)(component.getWidth() * 0.7), 0);
      }
      SwingUtilities.convertPointToScreen(point, component);
      JBPopupFactory.getInstance().createHtmlTextBalloonBuilder("Already checking...", MessageType.WARNING, null).
        createBalloon().show(new RelativePoint(point), Balloon.Position.below);
    }
    return false;
  }

  private void onStateChangedToSuccess(final AuthenticationRequest obj) {
    myCopiesPassiveResults.put(getKey(obj), true);
    myVcs.invokeRefreshSvnRoots(false);

    final List<SVNURL> outdatedRequests = new LinkedList<SVNURL>();
    final Collection<SVNURL> keys = getAllCurrentKeys();
    for (SVNURL key : keys) {
      final SVNURL commonURLAncestor = SVNURLUtil.getCommonURLAncestor(key, obj.getUrl());
      if ((commonURLAncestor != null) && (! StringUtil.isEmptyOrSpaces(commonURLAncestor.getHost())) &&
          (! StringUtil.isEmptyOrSpaces(commonURLAncestor.getPath()))) {
        //final AuthenticationRequest currObj = getObj(key);
        //if ((currObj != null) && passiveValidation(myVcs.getProject(), key, true, currObj.getRealm(), currObj.getKind())) {
          outdatedRequests.add(key);
        //}
      }
    }
    log("on state changed ");
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        for (SVNURL key : outdatedRequests) {
          removeLazyNotificationByKey(key);
        }
      }
    }, ModalityState.NON_MODAL);
  }

  @Override
  public void ensureNotify(AuthenticationRequest obj) {
    final SVNURL key = getKey(obj);
    myCopiesPassiveResults.remove(key);
    /*VcsBalloonProblemNotifier.showOverChangesView(myVcs.getProject(), "You are not authenticated to '" + obj.getRealm() + "'." +
      "To login, see pending notifications.", MessageType.ERROR);*/
    super.ensureNotify(obj);
  }

  @NotNull
  @Override
  public SVNURL getKey(final AuthenticationRequest obj) {
    // !!! wc's URL
    return obj.getWcUrl();
  }

  @Nullable
  public SVNURL getWcUrl(final AuthenticationRequest obj) {
    if (obj.isOutsideCopies()) return null;
    if (obj.getWcUrl() != null) return obj.getWcUrl();

    final WorkingCopy copy = myRootsToWorkingCopies.getMatchingCopy(obj.getUrl());
    if (copy != null) {
      obj.setOutsideCopies(false);
      obj.setWcUrl(copy.getUrl());
    } else {
      obj.setOutsideCopies(true);
    }
    return copy == null ? null : copy.getUrl();
  }

  /**
   * Bases on presence of notifications!
   */
  public ThreeState isAuthenticatedFor(final VirtualFile vf) {
    final WorkingCopy wcCopy = myRootsToWorkingCopies.getWcRoot(vf);
    if (wcCopy == null) return ThreeState.UNSURE;

    // check there's no cancellation yet
    final boolean haveCancellation = getStateFor(wcCopy.getUrl());
    if (haveCancellation) return ThreeState.NO;

    final Boolean keptResult = myCopiesPassiveResults.get(wcCopy.getUrl());
    if (Boolean.TRUE.equals(keptResult)) return ThreeState.YES;
    if (Boolean.FALSE.equals(keptResult)) return ThreeState.NO;
    
    // check have credentials
    final boolean calculatedResult = passiveValidation(myVcs.getProject(), wcCopy.getUrl());
    myCopiesPassiveResults.put(wcCopy.getUrl(), calculatedResult);
    return calculatedResult ? ThreeState.YES : ThreeState.NO;
  }

  @NotNull
  @Override
  protected String getNotificationContent(AuthenticationRequest obj) {
    return "<a href=\"\">Click to fix.</a> Not logged In to Subversion '" + obj.getRealm() + "' (" + obj.getUrl().toDecodedString() + ")";
  }

  @NotNull
  @Override
  protected String getToString(AuthenticationRequest obj) {
    return "Click to fix. Not logged In to Subversion '" + obj.getRealm() + "' (" + obj.getUrl().toDecodedString() + ")";
  }

  public static class AuthenticationRequest {
    private final Project myProject;
    private final String myKind;
    private final SVNURL myUrl;
    private final String myRealm;

    private SVNURL myWcUrl;
    private boolean myOutsideCopies;

    public AuthenticationRequest(Project project, String kind, SVNURL url, String realm) {
      myProject = project;
      myKind = kind;
      myUrl = url;
      myRealm = realm;
    }

    public boolean isOutsideCopies() {
      return myOutsideCopies;
    }

    public void setOutsideCopies(boolean outsideCopies) {
      myOutsideCopies = outsideCopies;
    }

    public SVNURL getWcUrl() {
      return myWcUrl;
    }

    public void setWcUrl(SVNURL wcUrl) {
      myWcUrl = wcUrl;
    }

    public String getKind() {
      return myKind;
    }

    public SVNURL getUrl() {
      return myUrl;
    }

    public String getRealm() {
      return myRealm;
    }
  }
  
  static void log(final Throwable t) {
    LOG.debug(t);
  }

  static void log(final String s) {
    LOG.debug(s);
  }

  public static boolean passiveValidation(final Project project, final SVNURL url) {
    final SvnConfiguration configuration = SvnConfiguration.getInstance(project);
    final SvnAuthenticationManager passiveManager = configuration.getPassiveAuthenticationManager();
    return validationImpl(project, url, configuration, passiveManager, false, null, null, false);
  }

  public static boolean interactiveValidation(final Project project, final SVNURL url, final String realm, final String kind) {
    final SvnConfiguration configuration = SvnConfiguration.getInstance(project);
    final SvnAuthenticationManager passiveManager = configuration.getInteractiveManager(SvnVcs.getInstance(project));
    return validationImpl(project, url, configuration, passiveManager, true, realm, kind, true);
  }

  private static boolean validationImpl(final Project project, final SVNURL url,
                                        final SvnConfiguration configuration, final SvnAuthenticationManager manager,
                                        final boolean checkWrite,
                                        final String realm,
                                        final String kind, boolean interactive) {
    SvnInteractiveAuthenticationProvider.clearCallState();
    try {
      new SVNWCClient(manager, configuration.getOptions(project)).doInfo(url, SVNRevision.UNDEFINED, SVNRevision.HEAD);
    } catch (SVNAuthenticationException e) {
      log(e);
      return false;
    } catch (SVNCancelException e) {
      log(e); // auth canceled
      return false;
    } catch (SVNException e) {
      if (e.getErrorMessage().getErrorCode().isAuthentication()) {
        log(e);
        return false;
      }
      LOG.info("some other exc", e);
      if (interactive) {
        VcsBalloonProblemNotifier.showOverChangesView(project, "Authentication failed: " + e.getMessage(), MessageType.ERROR);
      }
      return false; /// !!!! any exception means user should be notified that authorization failed
    }

    if (! checkWrite) {
      return true;
    }
    /*if (passive) {
      return SvnInteractiveAuthenticationProvider.wasCalled();
    }*/

    if (SvnInteractiveAuthenticationProvider.wasCalled() && SvnInteractiveAuthenticationProvider.wasCancelled()) return false;
    if (SvnInteractiveAuthenticationProvider.wasCalled()) return true;

    final SvnVcs svnVcs = SvnVcs.getInstance(project);

    final SvnInteractiveAuthenticationProvider provider = new SvnInteractiveAuthenticationProvider(svnVcs, manager);
    final SVNAuthentication svnAuthentication = provider.requestClientAuthentication(kind, url, realm, null, null, true);
    if (svnAuthentication != null) {
      configuration.acknowledge(kind, realm, svnAuthentication);
      try {
        configuration.getAuthenticationManager(svnVcs).acknowledgeAuthentication(true, kind, realm, null, svnAuthentication);
      }
      catch (SVNException e) {
        LOG.info(e);
      }
      return true;
    }
    return false;
  }
}
