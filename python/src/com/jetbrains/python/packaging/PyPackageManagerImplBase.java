// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.python.community.helpersLocator.PythonHelpersLocator;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.net.HttpConfigurable;
import com.jetbrains.python.PyPsiPackageUtil;
import com.jetbrains.python.PySdkBundle;
import com.jetbrains.python.errorProcessing.ExecErrorImpl;
import com.jetbrains.python.errorProcessing.ExecErrorReason;
import com.jetbrains.python.packaging.common.PythonPackage;
import com.jetbrains.python.packaging.pip.PipParseUtils;
import com.jetbrains.python.packaging.repository.PyPackageRepositoryUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PyDetectedSdk;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static com.intellij.webcore.packaging.PackageVersionComparator.VERSION_COMPARATOR;

/**
 * @deprecated TODO: explain
 */
@Deprecated(forRemoval = true)
public abstract class PyPackageManagerImplBase extends PyPackageManager {
  protected static final String SETUPTOOLS_VERSION = "44.1.1";
  protected static final String PIP_VERSION = "24.3.1";

  protected static final String SETUPTOOLS_WHEEL_NAME = "setuptools-" + SETUPTOOLS_VERSION + "-py2.py3-none-any.whl";
  protected static final String PIP_WHEEL_NAME = "pip-" + PIP_VERSION + "-py2.py3-none-any.whl";

  protected static final int ERROR_NO_SETUPTOOLS = 3;

  private static final Logger LOG = Logger.getInstance(PyPackageManagerImplBase.class);

  protected static final String PACKAGING_TOOL = "packaging_tool.py";
  protected static final int TIMEOUT = 10 * 60 * 1000;

  protected static final String INSTALL = "install";
  protected static final String UNINSTALL = "uninstall";
  protected String mySeparator = File.separator;

  protected volatile @Nullable List<PyPackage> myPackagesCache = null;
  private final AtomicBoolean myUpdatingCache = new AtomicBoolean(false);

  @Override
  public void refresh() {
    LOG.debug("Refreshing SDK roots and packages cache");
    final Application application = ApplicationManager.getApplication();
    application.invokeLater(() -> {
      final Sdk sdk = getSdk();
      application.runWriteAction(() -> {
        final VirtualFile[] files = sdk.getRootProvider().getFiles(OrderRootType.CLASSES);
        VfsUtil.markDirtyAndRefresh(true, true, true, files);
      });
      PythonSdkType.getInstance().setupSdkPaths(sdk);
    });
  }

  @Override
  public void installManagement() throws ExecutionException {
    final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(getSdk());
    if (languageLevel.isOlderThan(LanguageLevel.PYTHON27)) {
      throw new ExecutionException(PySdkBundle.message("python.sdk.packaging.package.management.for.python.not.supported",
                                                       languageLevel, LanguageLevel.PYTHON27));
    }

    boolean success = updatePackagingTools();
    if (success) {
      return;
    }

    if (languageLevel.isOlderThan(LanguageLevel.PYTHON312)) { // Python 3.12 doesn't require setuptools to list packages anymore
      final PyPackage installedSetuptools = refreshAndCheckForSetuptools();
      if (installedSetuptools == null || VERSION_COMPARATOR.compare(installedSetuptools.getVersion(), SETUPTOOLS_VERSION) < 0) {
        installManagement(Objects.requireNonNull(getHelperPath(SETUPTOOLS_WHEEL_NAME)));
      }
    }

    final PyPackage installedPip = PyPsiPackageUtil.findPackage(refreshAndGetPackages(false), PyPackageUtil.PIP);
    if (installedPip == null || VERSION_COMPARATOR.compare(installedPip.getVersion(), PIP_VERSION) < 0) {
      installManagement(Objects.requireNonNull(getHelperPath(PIP_WHEEL_NAME)));
    }
  }

  protected final boolean updatePackagingTools() {
    try {
      installUsingPipWheel("--upgrade", "--force-reinstall", PyPackageUtil.SETUPTOOLS, PyPackageUtil.PIP);
      return true;
    }
    catch (ExecutionException e) {
      LOG.info(e);
      return false;
    }
    finally {
      refreshPackagesSynchronously();
    }
  }

  @Override
  public boolean hasManagement() throws ExecutionException {
    final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(getSdk());
    final Boolean hasSetuptools = languageLevel.isAtLeast(LanguageLevel.PYTHON312) || refreshAndCheckForSetuptools() != null;
    final Boolean hasPip = PyPsiPackageUtil.findPackage(refreshAndGetPackages(false), PyPackageUtil.PIP) != null;
    return hasSetuptools && hasPip;
  }

  protected final @Nullable PyPackage refreshAndCheckForSetuptools() throws ExecutionException {
    try {
      final List<PyPackage> packages = refreshAndGetPackages(false);
      final PyPackage setuptoolsPackage = PyPsiPackageUtil.findPackage(packages, PyPackageUtil.SETUPTOOLS);
      return setuptoolsPackage != null ? setuptoolsPackage : PyPsiPackageUtil.findPackage(packages, PyPackageUtil.DISTRIBUTE);
    }
    catch (PyExecutionException e) {
      var pyError = e.getPyError();
      if (pyError instanceof ExecErrorImpl<?> error) {
        var errorReason = error.getErrorReason();
        if (errorReason instanceof ExecErrorReason.UnexpectedProcessTermination unexpectedProcessTermination) {
          int exitCode = unexpectedProcessTermination.getExitCode();
          if (exitCode == ERROR_NO_SETUPTOOLS) {
            return null;
          }
        }
      }
      throw e;
    }
  }

  protected void installManagement(@NotNull String name) throws ExecutionException {
    installUsingPipWheel("--no-index", name);
  }

  protected abstract void installUsingPipWheel(String @NotNull ... pipArgs) throws ExecutionException;

  protected PyPackageManagerImplBase(@NotNull Sdk sdk) { super(sdk); }

  @RequiresReadLock(generateAssertion = false)
  protected static @NotNull LanguageLevel getOrRequestLanguageLevelForSdk(@NotNull Sdk sdk) throws ExecutionException {
    if (sdk instanceof PyDetectedSdk) {
      final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
      if (flavor != null && sdk.getHomePath() != null) {
        return flavor.getLanguageLevel(sdk.getHomePath());
      }
      throw new ExecutionException(PySdkBundle.message("python.sdk.packaging.cannot.retrieve.version", sdk.getHomePath()));
    }
    // Use the cached version for an already configured SDK
    return PythonSdkType.getLanguageLevelForSdk(sdk);
  }

  @Override
  public @NotNull List<PyPackage> refreshAndGetPackages(boolean alwaysRefresh) throws ExecutionException {
    final List<PyPackage> currentPackages = myPackagesCache;
    if (alwaysRefresh || currentPackages == null) {
      myPackagesCache = null;
      try {
        final List<PyPackage> packages = collectPackages();
        LOG.debug("Packages installed in " + getSdk().getName() + ": " + packages);
        myPackagesCache = packages;
        ApplicationManager.getApplication().getMessageBus().syncPublisher(PACKAGE_MANAGER_TOPIC).packagesRefreshed(getSdk());
        return Collections.unmodifiableList(packages);
      }
      catch (ExecutionException e) {
        myPackagesCache = Collections.emptyList();
        throw e;
      }
    }
    return Collections.unmodifiableList(currentPackages);
  }

  protected abstract @NotNull List<PyPackage> collectPackages() throws ExecutionException;

  protected final void refreshPackagesSynchronously() {
    PyPackageUtil.updatePackagesSynchronouslyWithGuard(this, myUpdatingCache);
  }

  protected static @Nullable String getProxyString() {
    final HttpConfigurable settings = HttpConfigurable.getInstance();
    if (settings != null && settings.USE_HTTP_PROXY) {
      final String credentials;
      if (settings.PROXY_AUTHENTICATION) {
        credentials = String.format("%s:%s@", settings.getProxyLogin(), settings.getPlainProxyPassword());
      }
      else {
        credentials = "";
      }
      return "http://" + credentials + String.format("%s:%d", settings.PROXY_HOST, settings.PROXY_PORT);
    }
    return null;
  }

  protected @Nullable String getHelperPath(final @NotNull String helper) throws ExecutionException {
    return PythonHelpersLocator.findPathStringInHelpers(helper);
  }

  protected static @NotNull List<String> makeSafeToDisplayCommand(@NotNull List<String> cmdline) {
    final List<String> safeCommand = new ArrayList<>(cmdline);
    for (int i = 0; i < safeCommand.size(); i++) {
      if (cmdline.get(i).equals("--proxy") && i + 1 < cmdline.size()) {
        safeCommand.set(i + 1, makeSafeUrlArgument(cmdline.get(i + 1)));
      }
      if (cmdline.get(i).equals("--index-url") && i + 1 < cmdline.size()) {
        safeCommand.set(i + 1, makeSafeUrlArgument(cmdline.get(i + 1)));
      }
    }
    return safeCommand;
  }

  private static @NotNull String makeSafeUrlArgument(@NotNull String urlArgument) {
    try {
      final URI proxyUri = new URI(urlArgument);
      final String credentials = proxyUri.getUserInfo();
      if (credentials != null) {
        final int colonIndex = credentials.indexOf(":");
        if (colonIndex >= 0) {
          final String login = credentials.substring(0, colonIndex);
          final String password = credentials.substring(colonIndex + 1);
          final String maskedPassword = StringUtil.repeatSymbol('*', password.length());
          final String maskedCredentials = login + ":" + maskedPassword;
          if (urlArgument.contains(credentials)) {
            return urlArgument.replaceFirst(Pattern.quote(credentials), maskedCredentials);
          }
          else {
            final String encodedCredentials = PyPackageRepositoryUtil.encodeCredentialsForUrl(login, password);
            return urlArgument.replaceFirst(Pattern.quote(encodedCredentials), maskedCredentials);
          }
        }
      }
    }
    catch (URISyntaxException ignored) {
    }
    return urlArgument;
  }

  protected static @NotNull List<PyPackage> parsePackagingToolOutput(@NotNull String output) {
    List<@NotNull PythonPackage> packageList = PipParseUtils.parseListResult(output);
    List<PyPackage> packages = new ArrayList<>();
    for (PythonPackage pythonPackage : packageList) {
      PyPackage pkg = new PyPackage(pythonPackage.getName(), pythonPackage.getVersion());
      packages.add(pkg);
    }
    return packages;
  }
}
