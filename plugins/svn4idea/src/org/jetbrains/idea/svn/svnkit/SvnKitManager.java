/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.svnkit;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnHttpAuthMethodsDefaultChecker;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.auth.SvnAuthenticationManager;
import org.jetbrains.idea.svn.svnkit.lowLevel.PrimitivePool;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.util.jna.SVNJNAUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea14;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnUpgrade;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitManager {

  private static final Logger LOG = SvnVcs.wrapLogger(Logger.getInstance(SvnKitManager.class));

  @NonNls public static final String LOG_PARAMETER_NAME = "javasvn.log";
  @NonNls public static final String TRACE_NATIVE_CALLS = "javasvn.log.native";
  @NonNls public static final String SVNKIT_HTTP_SSL_PROTOCOLS = "svnkit.http.sslProtocols";

  @Nullable private static String ourExplicitlySetSslProtocols;

  @NotNull private final SvnVcs myVcs;
  @NotNull private final Project myProject;
  @NotNull private final SvnConfiguration myConfiguration;

  static {
    System.setProperty("svnkit.log.native.calls", "true");

    final SvnKitDebugLogger
      logger = new SvnKitDebugLogger(Boolean.getBoolean(LOG_PARAMETER_NAME), Boolean.getBoolean(TRACE_NATIVE_CALLS), LOG);
    SVNDebugLog.setDefaultLog(logger);

    SVNJNAUtil.setJNAEnabled(true);
    SvnHttpAuthMethodsDefaultChecker.check();

    SVNAdminAreaFactory.setSelector(new SvnKitAdminAreaFactorySelector());

    DAVRepositoryFactory.setup();
    SVNRepositoryFactoryImpl.setup();
    FSRepositoryFactory.setup();

    // non-optimized writing is fast enough on Linux/MacOS, and somewhat more reliable
    if (SystemInfo.isWindows) {
      SVNAdminArea14.setOptimizedWritingEnabled(true);
    }

    if (!SVNJNAUtil.isJNAPresent()) {
      LOG.warn("JNA is not found by svnkit library");
    }

    ourExplicitlySetSslProtocols = System.getProperty(SVNKIT_HTTP_SSL_PROTOCOLS);
  }

  public SvnKitManager(@NotNull SvnVcs vcs) {
    myVcs = vcs;
    myProject = myVcs.getProject();
    myConfiguration = myVcs.getSvnConfiguration();

    refreshSSLProperty();
  }

  @Nullable
  public static String getExplicitlySetSslProtocols() {
    return ourExplicitlySetSslProtocols;
  }

  public static boolean isSSLProtocolExplicitlySet() {
    return ourExplicitlySetSslProtocols != null;
  }

  public void refreshSSLProperty() {
    if (isSSLProtocolExplicitlySet()) return;

    if (SvnConfiguration.SSLProtocols.all.equals(myConfiguration.getSslProtocols())) {
      System.clearProperty(SVNKIT_HTTP_SSL_PROTOCOLS);
    }
    else if (SvnConfiguration.SSLProtocols.sslv3.equals(myConfiguration.getSslProtocols())) {
      System.setProperty(SVNKIT_HTTP_SSL_PROTOCOLS, "SSLv3");
    }
    else if (SvnConfiguration.SSLProtocols.tlsv1.equals(myConfiguration.getSslProtocols())) {
      System.setProperty(SVNKIT_HTTP_SSL_PROTOCOLS, "TLSv1");
    }
  }

  public void activate() {
    if (SystemInfo.isWindows) {
      if (!SVNJNAUtil.isJNAPresent()) {
        Notifications.Bus.notify(new Notification(myVcs.getDisplayName(), "Subversion plugin: no JNA",
                                                  "A problem with JNA initialization for SVNKit library. Encryption is not available.",
                                                  NotificationType.WARNING), myProject);
      }
      else if (!SVNJNAUtil.isWinCryptEnabled()) {
        Notifications.Bus.notify(new Notification(myVcs.getDisplayName(), "Subversion plugin: no encryption",
                                                  "A problem with encryption module (Crypt32.dll) initialization for SVNKit library. " +
                                                  "Encryption is not available.",
                                                  NotificationType.WARNING
        ), myProject);
      }
    }
  }

  @NotNull
  public ISVNOptions getSvnOptions() {
    return myConfiguration.getOptions();
  }

  @NotNull
  public SVNRepository createRepository(String url) throws SVNException {
    return createRepository(SVNURL.parseURIEncoded(url));
  }

  @NotNull
  public SVNRepository createRepository(@NotNull SVNURL url) throws SVNException {
    SVNRepository repository = SVNRepositoryFactory.create(url);
    repository.setAuthenticationManager(getAuthenticationManager());
    repository.setTunnelProvider(getSvnOptions());

    return repository;
  }

  @NotNull
  private ISVNRepositoryPool getPool() {
    return getPool(getAuthenticationManager());
  }

  @NotNull
  private ISVNRepositoryPool getPool(@NotNull ISVNAuthenticationManager manager) {
    if (myProject.isDisposed()) {
      throw new ProcessCanceledException();
    }
    return new PrimitivePool(manager, getSvnOptions());
  }

  @NotNull
  public SVNUpdateClient createUpdateClient() {
    return setupClient(new SVNUpdateClient(getPool(), getSvnOptions()));
  }

  @NotNull
  public SVNUpdateClient createUpdateClient(@NotNull ISVNAuthenticationManager manager) {
    return setupClient(new SVNUpdateClient(getPool(manager), getSvnOptions()), manager);
  }

  @NotNull
  public SVNStatusClient createStatusClient() {
    SVNStatusClient client = new SVNStatusClient(getPool(), getSvnOptions());
    client.setIgnoreExternals(false);

    return setupClient(client);
  }

  @NotNull
  public SVNWCClient createWCClient() {
    return setupClient(new SVNWCClient(getPool(), getSvnOptions()));
  }

  @NotNull
  public SVNWCClient createUpgradeClient() {
    return new SVNWCClient(createOperationFactory());
  }

  @NotNull
  public SVNWCClient createWCClient(@NotNull ISVNAuthenticationManager manager) {
    return setupClient(new SVNWCClient(getPool(manager), getSvnOptions()), manager);
  }

  @NotNull
  public SVNCopyClient createCopyClient() {
    return setupClient(new SVNCopyClient(getPool(), getSvnOptions()));
  }

  @NotNull
  public SVNMoveClient createMoveClient() {
    return setupClient(new SVNMoveClient(getPool(), getSvnOptions()));
  }

  @NotNull
  public SVNLogClient createLogClient() {
    return setupClient(new SVNLogClient(getPool(), getSvnOptions()));
  }

  @NotNull
  public SVNLogClient createLogClient(@NotNull ISVNAuthenticationManager manager) {
    return setupClient(new SVNLogClient(getPool(manager), getSvnOptions()), manager);
  }

  @NotNull
  public SVNCommitClient createCommitClient() {
    return setupClient(new SVNCommitClient(getPool(), getSvnOptions()));
  }

  @NotNull
  public SVNDiffClient createDiffClient() {
    return setupClient(new SVNDiffClient(getPool(), getSvnOptions()));
  }

  @NotNull
  public SVNChangelistClient createChangelistClient() {
    return setupClient(new SVNChangelistClient(getPool(), getSvnOptions()));
  }

  @NotNull
  public SvnOperationFactory createOperationFactory() {
    SvnOperationFactory result = new OperationFactory();

    result.setOptions(getSvnOptions());
    result.setRepositoryPool(getPool());
    result.setAuthenticationManager(getAuthenticationManager());

    return result;
  }

  @NotNull
  private SvnAuthenticationManager getAuthenticationManager() {
    return myConfiguration.getAuthenticationManager(myVcs);
  }

  @NotNull
  private <T extends SVNBasicClient> T setupClient(@NotNull T client) {
    return setupClient(client, getAuthenticationManager());
  }

  @NotNull
  private static <T extends SVNBasicClient> T setupClient(@NotNull T client, @NotNull ISVNAuthenticationManager manager) {
    client.getOperationsFactory().setAuthenticationManager(manager);

    return client;
  }

  private static class OperationFactory extends SvnOperationFactory {
    /**
     * We could not utilize {@link SvnUpgrade} explicitly to perform working copy upgrade from 1.6 to 1.7 as working copy externals would
     * still be upgraded to 1.8 by internal SVNKit logic - {@link SvnUpgrade} instances will be obtained from {@link SvnOperationFactory},
     * but default "targetWorkingCopyFormat" will be used.
     * <p/>
     * So we just override default "targetWorkingCopyFormat" for {@link SvnUpgrade} instances.
     */
    @Override
    @NotNull
    public SvnUpgrade createUpgrade() {
      SvnUpgrade result = super.createUpgrade();
      result.setTargetWorkingCopyFormat(ISVNWCDb.WC_FORMAT_17);

      return result;
    }
  }
}
