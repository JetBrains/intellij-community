// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.net.HttpConfigurable;
import com.jetbrains.python.PyPsiPackageUtil;
import com.jetbrains.python.PySdkBundle;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.packaging.repository.PyPackageRepositoryUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PyDetectedSdk;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static com.intellij.webcore.packaging.PackageVersionComparator.VERSION_COMPARATOR;

public abstract class PyPackageManagerImplBase extends PyPackageManager {
  protected static final String SETUPTOOLS_VERSION = "44.1.1";
  protected static final String PIP_VERSION = "20.3.4";

  protected static final String SETUPTOOLS_WHEEL_NAME = "setuptools-" + SETUPTOOLS_VERSION + "-py2.py3-none-any.whl";
  protected static final String PIP_WHEEL_NAME = "pip-" + PIP_VERSION + "-py2.py3-none-any.whl";

  protected static final int ERROR_NO_SETUPTOOLS = 3;

  private static final Logger LOG = Logger.getInstance(PyPackageManagerImplBase.class);

  protected static final String PACKAGING_TOOL = "packaging_tool.py";
  protected static final int TIMEOUT = 10 * 60 * 1000;

  protected static final String INSTALL = "install";
  protected static final String UNINSTALL = "uninstall";
  protected String mySeparator = File.separator;

  @Nullable protected volatile List<PyPackage> myPackagesCache = null;
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

    final PyPackage installedSetuptools = refreshAndCheckForSetuptools();
    final PyPackage installedPip = PyPsiPackageUtil.findPackage(refreshAndGetPackages(false), PyPackageUtil.PIP);
    if (installedSetuptools == null || VERSION_COMPARATOR.compare(installedSetuptools.getVersion(), SETUPTOOLS_VERSION) < 0) {
      installManagement(Objects.requireNonNull(getHelperPath(SETUPTOOLS_WHEEL_NAME)));
    }
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
    return refreshAndCheckForSetuptools() != null &&
           PyPsiPackageUtil.findPackage(refreshAndGetPackages(false), PyPackageUtil.PIP) != null;
  }

  @Nullable
  protected final PyPackage refreshAndCheckForSetuptools() throws ExecutionException {
    try {
      final List<PyPackage> packages = refreshAndGetPackages(false);
      final PyPackage setuptoolsPackage = PyPsiPackageUtil.findPackage(packages, PyPackageUtil.SETUPTOOLS);
      return setuptoolsPackage != null ? setuptoolsPackage : PyPsiPackageUtil.findPackage(packages, PyPackageUtil.DISTRIBUTE);
    }
    catch (PyExecutionException e) {
      if (e.getExitCode() == ERROR_NO_SETUPTOOLS) {
        return null;
      }
      throw e;
    }
  }

  protected void installManagement(@NotNull String name) throws ExecutionException {
    installUsingPipWheel("--no-index", name);
  }

  protected abstract void installUsingPipWheel(String @NotNull ... pipArgs) throws ExecutionException;

  protected PyPackageManagerImplBase(@NotNull Sdk sdk) { super(sdk); }

  @Override
  @NotNull
  public Set<PyPackage> getDependents(@NotNull PyPackage pkg) throws ExecutionException {
    final List<PyPackage> packages = refreshAndGetPackages(false);
    final Set<PyPackage> dependents = new HashSet<>();
    for (PyPackage p : packages) {
      final List<PyRequirement> requirements = p.getRequirements();
      for (PyRequirement requirement : requirements) {
        if (requirement.getName().equals(pkg.getName())) {
          dependents.add(p);
        }
      }
    }
    return dependents;
  }

  @NotNull
  protected static LanguageLevel getOrRequestLanguageLevelForSdk(@NotNull Sdk sdk) throws ExecutionException {
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
  @Nullable
  public List<PyRequirement> getRequirements(@NotNull Module module) {
    return Optional
      .ofNullable(PyPackageUtil.getRequirementsFromTxt(module))
      .orElseGet(() -> PyPackageUtil.findSetupPyRequires(module));
  }

  @Nullable
  @Override
  public PyRequirement parseRequirement(@NotNull String line) {
    return PyRequirementParser.fromLine(line);
  }

  @NotNull
  @Override
  public List<PyRequirement> parseRequirements(@NotNull String text) {
    return PyRequirementParser.fromText(text);
  }

  @NotNull
  @Override
  public List<PyRequirement> parseRequirements(@NotNull VirtualFile file) {
    return PyRequirementParser.fromFile(file);
  }

  @Override
  @NotNull
  public final List<PyPackage> refreshAndGetPackages(boolean alwaysRefresh) throws ExecutionException {
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

  @Nullable
  protected static String getProxyString() {
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

  @Nullable
  protected String getHelperPath(@NotNull final String helper) throws ExecutionException {
    return PythonHelpersLocator.getHelperPath(helper);
  }

  @NotNull
  protected static List<String> makeSafeToDisplayCommand(@NotNull List<String> cmdline) {
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

  @NotNull
  private static String makeSafeUrlArgument(@NotNull String urlArgument) {
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

  protected final @NotNull List<PyPackage> parsePackagingToolOutput(@NotNull String output) throws PyExecutionException {
    List<PyPackage> packages = new ArrayList<>();
    for (String line : StringUtil.splitByLines(output)) {
      PyPackage pkg = parsePackaging(line,
                                     "\t",
                                     true,
                                     PySdkBundle.message("python.sdk.packaging.invalid.output.format"),
                                     PACKAGING_TOOL);

      if (pkg != null) {
        packages.add(pkg);
      }
    }
    return packages;
  }

  protected final @Nullable PyPackage parsePackaging(@NotNull @NonNls String line,
                                                     @NotNull @NonNls String separator,
                                                     boolean useLocation,
                                                     @NotNull @Nls String errorMessage,
                                                     @NotNull @NonNls String command) throws PyExecutionException {
    List<String> fields = StringUtil.split(line, separator);
    if (fields.size() < 3) {
      throw new PyExecutionException(errorMessage, command, List.of());
    }

    final String name = fields.get(0);

    // TODO does it has to be parsed regardless the name?
    List<PyRequirement> requirements = fields.size() >= 4 ?
                                       parseRequirements(toMultilineString(fields.get(3))) :
                                       List.of();

    return "Python".equals(name) ?
           null :
           new PyPackage(name,
                         fields.get(1),
                         useLocation ? fields.get(2) : "",
                         requirements);
  }

  private static @NotNull String toMultilineString(@NotNull String string) {
    return StringUtil.join(StringUtil.split(string, ":"), "\n");
  }
}
