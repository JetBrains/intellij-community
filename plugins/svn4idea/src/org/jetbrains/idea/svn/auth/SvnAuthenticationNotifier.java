// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.auth;

import com.intellij.concurrency.JobScheduler;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NamedRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.impl.GenericNotifierImpl;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ThreeState;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.proxy.CommonProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.api.ClientFactory;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;
import org.jetbrains.idea.svn.info.InfoClient;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.jetbrains.idea.svn.SvnUtil.isAuthError;

public class SvnAuthenticationNotifier extends GenericNotifierImpl<SvnAuthenticationNotifier.AuthenticationRequest, Url> {
  private static final Logger LOG = Logger.getInstance(SvnAuthenticationNotifier.class);

  private static final List<String> ourAuthKinds = Arrays
    .asList(SvnAuthenticationManager.PASSWORD, "svn.ssh", SvnAuthenticationManager.SSL, "svn.username", "svn.ssl.server", "svn.ssh.server");

  private final SvnVcs myVcs;
  private final RootsToWorkingCopies myRootsToWorkingCopies;
  private final Map<Url, Boolean> myCopiesPassiveResults;
  private ScheduledFuture<?> myTimer;
  private volatile boolean myVerificationInProgress;

  public SvnAuthenticationNotifier(final SvnVcs svnVcs) {
    super(svnVcs.getProject(), svnVcs.getDisplayName(), "Not Logged In to Subversion", NotificationType.ERROR);
    myVcs = svnVcs;
    myRootsToWorkingCopies = myVcs.getRootsToWorkingCopies();
    myCopiesPassiveResults = Collections.synchronizedMap(new HashMap<Url, Boolean>());
    myVerificationInProgress = false;
  }

  public void init() {
    if (myTimer != null) {
      stop();
    }
    myTimer =
    // every 10 minutes
    JobScheduler.getScheduler().scheduleWithFixedDelay(myCopiesPassiveResults::clear, 10, 10 * 60, TimeUnit.SECONDS);
  }

  public void stop() {
    myTimer.cancel(false);
    myTimer = null;
  }

  @Override
  protected boolean ask(final AuthenticationRequest obj, String description) {
    if (myVerificationInProgress) {
      return showAlreadyChecking();
    }
    myVerificationInProgress = true;

    final Ref<Boolean> resultRef = new Ref<>();

    final Runnable checker = () -> {
      try {
        final boolean result =
          interactiveValidation(obj.myProject, obj.getUrl(), obj.getRealm(), obj.getKind());
        log("ask result for: " + obj.getUrl() + " is: " + result);
        resultRef.set(result);
        if (result) {
          onStateChangedToSuccess(obj);
        }
      }
      finally {
        myVerificationInProgress = false;
      }
    };
    final Application application = ApplicationManager.getApplication();
    // also do not show auth if thread does not have progress indicator
    if (application.isReadAccessAllowed() || !ProgressManager.getInstance().hasProgressIndicator()) {
      application.executeOnPooledThread(checker);
    }
    else {
      checker.run();
      return resultRef.get();
    }
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
    myVcs.invokeRefreshSvnRoots();

    final List<Url> outdatedRequests = new LinkedList<>();
    final Collection<Url> keys = getAllCurrentKeys();
    for (Url key : keys) {
      final Url commonURLAncestor = key.commonAncestorWith(obj.getUrl());
      if ((commonURLAncestor != null) && (! StringUtil.isEmptyOrSpaces(commonURLAncestor.getHost())) &&
          (! StringUtil.isEmptyOrSpaces(commonURLAncestor.getPath()))) {
        //final AuthenticationRequest currObj = getObj(key);
        //if ((currObj != null) && passiveValidation(myVcs.getProject(), key, true, currObj.getRealm(), currObj.getKind())) {
          outdatedRequests.add(key);
        //}
      }
    }
    log("on state changed ");
    ApplicationManager.getApplication().invokeLater(() -> {
      for (Url key : outdatedRequests) {
        removeLazyNotificationByKey(key);
      }
    }, ModalityState.NON_MODAL);
  }

  @Override
  public boolean ensureNotify(AuthenticationRequest obj) {
    final Url key = getKey(obj);
    myCopiesPassiveResults.remove(key);
    /*VcsBalloonProblemNotifier.showOverChangesView(myVcs.getProject(), "You are not authenticated to '" + obj.getRealm() + "'." +
      "To login, see pending notifications.", MessageType.ERROR);*/
    return super.ensureNotify(obj);
  }

  @Override
  protected boolean onFirstNotification(AuthenticationRequest obj) {
    if (ProgressManager.getInstance().hasProgressIndicator()) {
      return ask(obj, null);  // TODO
    } else {
      return false;
    }
  }

  @NotNull
  @Override
  public Url getKey(final AuthenticationRequest obj) {
    // !!! wc's URL
    return obj.getWcUrl();
  }

  @Nullable
  public Url getWcUrl(final AuthenticationRequest obj) {
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
  public ThreeState isAuthenticatedFor(@NotNull VirtualFile vf, @Nullable ClientFactory factory) {
    final WorkingCopy wcCopy = myRootsToWorkingCopies.getWcRoot(vf);
    if (wcCopy == null) return ThreeState.UNSURE;

    // check there's no cancellation yet
    final boolean haveCancellation = getStateFor(wcCopy.getUrl());
    if (haveCancellation) return ThreeState.NO;

    final Boolean keptResult = myCopiesPassiveResults.get(wcCopy.getUrl());
    if (Boolean.TRUE.equals(keptResult)) return ThreeState.YES;
    if (Boolean.FALSE.equals(keptResult)) return ThreeState.NO;

    // check have credentials
    boolean calculatedResult = factory == null ? passiveValidation(myVcs, wcCopy.getUrl()) : passiveValidation(factory, wcCopy.getUrl());
    myCopiesPassiveResults.put(wcCopy.getUrl(), calculatedResult);
    return calculatedResult ? ThreeState.YES : ThreeState.NO;
  }

  // TODO: Looks like passive authentication for command line integration could show dialogs for proxy errors. So, it could make sense to
  // TODO: reuse some logic from validationImpl().
  // TODO: Also SvnAuthenticationNotifier is not called for command line integration (ensureNotify() is called only in SVNKit lifecycle).
  // TODO: Though its logic with notifications seems rather convenient. Fix this.
  private static boolean passiveValidation(@NotNull ClientFactory factory, @NotNull Url url) {
    Info info = null;

    try {
      info = factory.create(InfoClient.class, false).doInfo(Target.on(url), Revision.UNDEFINED);
    }
    catch (SvnBindException ignore) {
    }

    return info != null;
  }

  @NotNull
  @Override
  protected String getNotificationContent(AuthenticationRequest obj) {
    return "<a href=\"\">Click to fix.</a> Not logged In to Subversion '" + obj.getRealm() + "' (" + obj.getUrl().toDecodedString() + ")";
  }

  public static class AuthenticationRequest {
    private final Project myProject;
    private final String myKind;
    private final Url myUrl;
    private final String myRealm;

    private Url myWcUrl;
    private boolean myOutsideCopies;
    private boolean myForceSaving;

    public AuthenticationRequest(Project project, String kind, Url url, String realm) {
      myProject = project;
      myKind = kind;
      myUrl = url;
      myRealm = realm;
    }

    public boolean isForceSaving() {
      return myForceSaving;
    }

    public void setForceSaving(boolean forceSaving) {
      myForceSaving = forceSaving;
    }

    public boolean isOutsideCopies() {
      return myOutsideCopies;
    }

    public void setOutsideCopies(boolean outsideCopies) {
      myOutsideCopies = outsideCopies;
    }

    public Url getWcUrl() {
      return myWcUrl;
    }

    public void setWcUrl(Url wcUrl) {
      myWcUrl = wcUrl;
    }

    public String getKind() {
      return myKind;
    }

    public Url getUrl() {
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

  public static boolean passiveValidation(@NotNull SvnVcs vcs, final Url url) {
    SvnConfiguration configuration = vcs.getSvnConfiguration();
    SvnAuthenticationManager passiveManager = configuration.getPassiveAuthenticationManager(vcs);
    return validationImpl(vcs.getProject(), url, configuration, passiveManager, false, null, null, false);
  }

  public static boolean interactiveValidation(final Project project, final Url url, final String realm, final String kind) {
    final SvnConfiguration configuration = SvnConfiguration.getInstance(project);
    final SvnAuthenticationManager passiveManager = configuration.getInteractiveManager(SvnVcs.getInstance(project));
    return validationImpl(project, url, configuration, passiveManager, true, realm, kind, true);
  }

  private static boolean validationImpl(final Project project, final Url url,
                                        final SvnConfiguration configuration, final SvnAuthenticationManager manager,
                                        final boolean checkWrite,
                                        final String realm,
                                        final String kind, boolean interactive) {
    // we should also NOT show proxy credentials dialog if at least fixed proxy was used, so
    Proxy proxyToRelease = null;
    if (! interactive && configuration.isIsUseDefaultProxy()) {
      final HttpConfigurable instance = HttpConfigurable.getInstance();
      if (instance.USE_HTTP_PROXY && instance.PROXY_AUTHENTICATION && (StringUtil.isEmptyOrSpaces(instance.getProxyLogin()) ||
                                                                       StringUtil.isEmptyOrSpaces(instance.getPlainProxyPassword()))) {
        return false;
      }
      if (instance.USE_PROXY_PAC) {
        final List<Proxy> select;
        try {
          select = CommonProxy.getInstance().select(new URI(url.toString()));
        }
        catch (URISyntaxException e) {
          LOG.info("wrong URL: " + url.toString());
          return false;
        }
        if (select != null && ! select.isEmpty()) {
          for (Proxy proxy : select) {
            if (HttpConfigurable.isRealProxy(proxy) && Proxy.Type.HTTP.equals(proxy.type())) {
              final InetSocketAddress address = (InetSocketAddress)proxy.address();
              final PasswordAuthentication password =
                HttpConfigurable.getInstance().getGenericPassword(address.getHostName(), address.getPort());
              if (password == null) {
                CommonProxy.getInstance().noAuthentication("http", address.getHostName(), address.getPort());
                proxyToRelease = proxy;
              }
            }
          }
        }
      }
    }
    SvnInteractiveAuthenticationProvider.clearCallState();
    Target target = Target.on(url);
    try {
      SvnVcs.getInstance(project).getFactory(target).create(InfoClient.class, interactive).doInfo(target, Revision.HEAD);
    }
    catch (ProcessCanceledException e) {
      return false;
    }
    catch (SvnBindException e) {
      if (isAuthError(e)) {
        log(e);
        return false;
      }
      LOG.info("some other exc", e);
      if (interactive) {
        showAuthenticationFailedWithHotFixes(project, configuration, e);
      }
      return false; /// !!!! any exception means user should be notified that authorization failed
    } finally {
      if (! interactive && configuration.isIsUseDefaultProxy() && proxyToRelease != null) {
        final InetSocketAddress address = (InetSocketAddress)proxyToRelease.address();
        CommonProxy.getInstance().noAuthentication("http", address.getHostName(), address.getPort());
      }
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
    final AuthenticationData svnAuthentication = provider.requestClientAuthentication(kind, url, realm, true);
    if (svnAuthentication != null) {
      configuration.acknowledge(kind, realm, svnAuthentication);
      configuration.getAuthenticationManager(svnVcs).acknowledgeAuthentication(kind, url, realm, svnAuthentication);
      return true;
    }
    return false;
  }

  private static void showAuthenticationFailedWithHotFixes(final Project project,
                                                           final SvnConfiguration configuration,
                                                           final SvnBindException e) {
    ApplicationManager.getApplication().invokeLater(() -> VcsBalloonProblemNotifier
      .showOverChangesView(project, "Authentication failed: " + e.getMessage(), MessageType.ERROR, new NamedRunnable(
                             SvnBundle.message("confirmation.title.clear.authentication.cache")) {
                             @Override
                             public void run() {
                               clearAuthenticationCache(project, null, configuration
                                 .getConfigurationDirectory());
                             }
                           }, new NamedRunnable(
                             SvnBundle.message("action.title.select.configuration.directory")) {
                             @Override
                             public void run() {
                               SvnConfigurable.selectConfigurationDirectory(configuration.getConfigurationDirectory(),
                                                                            s -> configuration.setConfigurationDirParameters(false, s), project, null);
                             }
                           }
      ), ModalityState.NON_MODAL, project.getDisposed());
  }

  public static void clearAuthenticationCache(@NotNull final Project project, final Component component, final String configDirPath) {
    if (configDirPath != null) {
      int result;
      if (component == null) {
        result = Messages.showYesNoDialog(project, SvnBundle.message("confirmation.text.delete.stored.authentication.information"),
                                          SvnBundle.message("confirmation.title.clear.authentication.cache"),
                                          Messages.getWarningIcon());
      } else {
        result = Messages.showYesNoDialog(component, SvnBundle.message("confirmation.text.delete.stored.authentication.information"),
                                          SvnBundle.message("confirmation.title.clear.authentication.cache"),
                                          Messages.getWarningIcon());
      }
      if (result == Messages.YES) {
        SvnConfiguration.RUNTIME_AUTH_CACHE.clear();
        clearAuthenticationDirectory(SvnConfiguration.getInstance(project));
      }
    }
  }

  public static void clearAuthenticationDirectory(@NotNull SvnConfiguration configuration) {
    final File authDir = new File(configuration.getConfigurationDirectory(), "auth");
    if (authDir.exists()) {
      final Runnable process = () -> {
        final ProgressIndicator ind = ProgressManager.getInstance().getProgressIndicator();
        if (ind != null) {
          ind.setIndeterminate(true);
          ind.setText("Clearing stored credentials in " + authDir.getAbsolutePath());
        }
        final File[] files = authDir.listFiles((dir, name) -> ourAuthKinds.contains(name));

        for (File dir : files) {
          if (ind != null) {
            ind.setText("Deleting " + dir.getAbsolutePath());
          }
          FileUtil.delete(dir);
        }
      };
      final Application application = ApplicationManager.getApplication();
      if (application.isUnitTestMode() || !application.isDispatchThread()) {
        process.run();
      }
      else {
        ProgressManager.getInstance()
          .runProcessWithProgressSynchronously(process, "button.text.clear.authentication.cache", false, configuration.getProject());
      }
    }
  }
}
