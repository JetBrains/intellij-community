// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging.ui;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.DetailedDescription;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.CatchingConsumer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.webcore.packaging.InstalledPackage;
import com.intellij.webcore.packaging.PackageManagementServiceEx;
import com.intellij.webcore.packaging.RepoPackage;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PySdkBundle;
import com.jetbrains.python.packaging.*;
import com.jetbrains.python.packaging.PyPIPackageUtil.PackageDetails;
import com.jetbrains.python.packaging.requirement.PyRequirementRelation;
import com.jetbrains.python.packaging.statistics.PythonPackagesDialogStatisticsCollector;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class PyPackageManagementService extends PackageManagementServiceEx {
  @NotNull private static final Pattern PATTERN_ERROR_LINE = Pattern.compile(".*error:.*", Pattern.CASE_INSENSITIVE);
  @NonNls protected static final String TEXT_PREFIX = buildHtmlStylePrefix();

  @NotNull
  private static String buildHtmlStylePrefix() {
    // Shamelessly copied from Plugin Manager dialog
    final int fontSize = JBUIScale.scale(12);
    final int m1 = JBUIScale.scale(2);
    final int m2 = JBUIScale.scale(5);
    return String.format("<html><head>" +
                         "    <style type=\"text/css\">" +
                         "        p {" +
                         "            font-family: Arial,serif; font-size: %dpt; margin: %dpx %dpx" +
                         "        }" +
                         "    </style>" +
                         "</head><body style=\"font-family: Arial,serif; font-size: %dpt; margin: %dpx %dpx;\">",
                         fontSize, m1, m1, fontSize, m2, m2);
  }

  @NonNls private static final String TEXT_SUFFIX = "</body></html>";

  @NotNull private final Project myProject;
  @NotNull protected final Sdk mySdk;
  protected final ExecutorService myExecutorService;

  public PyPackageManagementService(@NotNull Project project, @NotNull Sdk sdk) {
    myProject = project;
    mySdk = sdk;
    // Dumb heuristic for the size of IO-bound tasks pool: safer than unlimited, snappier than a single thread
    myExecutorService = AppExecutorUtil.createBoundedApplicationPoolExecutor("PyPackageManagementService Pool", 4);
  }

  @NotNull
  public Sdk getSdk() {
    return mySdk;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Nullable
  @Override
  public List<String> getAllRepositories() {
    final List<String> result = new ArrayList<>();
    if (!PyPackageService.getInstance().PYPI_REMOVED) result.add(PyPIPackageUtil.PYPI_LIST_URL);
    result.addAll(getAdditionalRepositories());
    return result;
  }

  @Override
  public void addRepository(String repositoryUrl) {
    PyPackageService.getInstance().addRepository(repositoryUrl);
  }

  @Override
  public void removeRepository(String repositoryUrl) {
    PyPackageService.getInstance().removeRepository(repositoryUrl);
  }

  @NotNull
  @Override
  public List<RepoPackage> getAllPackages() throws IOException {
    PyPIPackageUtil.INSTANCE.loadPackages();
    PyPIPackageUtil.INSTANCE.loadAdditionalPackages(getAdditionalRepositories(), false);
    return getAllPackagesCached();
  }

  @NotNull
  @Override
  public List<RepoPackage> reloadAllPackages() throws IOException {
    PyPIPackageUtil.INSTANCE.updatePyPICache();
    PyPIPackageUtil.INSTANCE.loadAdditionalPackages(getAdditionalRepositories(), true);
    return getAllPackagesCached();
  }

  @NotNull
  @Override
  public List<RepoPackage> getAllPackagesCached() {
    // Make a copy, since ManagePackagesDialog attempts to change the passed in collection directly
    final List<RepoPackage> result = new ArrayList<>();
    if (!PyPackageService.getInstance().PYPI_REMOVED) {
      result.addAll(getCachedPyPIPackages());
    }
    result.addAll(PyPIPackageUtil.INSTANCE.getAdditionalPackages(getAdditionalRepositories()));
    return result;
  }

  public Map<String, List<RepoPackage>> getAllPackagesByRepository() {
    Map<String, List<RepoPackage>> result = new HashMap<>();
    result.put(PyPIPackageUtil.PYPI_LIST_URL, getCachedPyPIPackages());
    List<String> repositories = getAdditionalRepositories();
    for (String repo : repositories) {
      result.put(repo, PyPIPackageUtil.INSTANCE.getAdditionalPackages(List.of(repo)));
    }
    return result;
  }

  @NotNull
  private static List<String> getAdditionalRepositories() {
    return PyPackageService.getInstance().additionalRepositories;
  }

  @NotNull
  private static List<RepoPackage> getCachedPyPIPackages() {
    // Don't show URL next to the package name in "Available Packages" if only PyPI is in use
    final boolean customRepoConfigured = !getAdditionalRepositories().isEmpty();
    final String url = customRepoConfigured ? PyPIPackageUtil.PYPI_LIST_URL : "";
    return ContainerUtil.map(PyPIPackageCache.getInstance().getPackageNames(), name -> new RepoPackage(name, url, null));
  }

  @Override
  public boolean canInstallToUser() {
    return !PythonSdkUtil.isVirtualEnv(mySdk);
  }

  @NotNull
  @Override
  public String getInstallToUserText() {
    String userSiteText = PyBundle.message("button.install.to.user.site.packages.directory");
    if (!PythonSdkUtil.isRemote(mySdk)) {
      userSiteText += " (" + PythonSdkUtil.getUserSite() + ")";
    }
    return userSiteText;
  }

  @Override
  public boolean isInstallToUserSelected() {
    return PyPackageService.getInstance().useUserSite(mySdk.getHomePath());
  }

  @Override
  public void installToUserChanged(boolean newValue) {
    PyPackageService.getInstance().addSdkToUserSite(mySdk.getHomePath(), newValue);
  }

  @Override
  public @NotNull List<? extends InstalledPackage> getInstalledPackagesList() throws ExecutionException {
    List<PyPackage> packages = new ArrayList<>(PyPackageManager.getInstance(mySdk).refreshAndGetPackages(true));
    packages.sort(Comparator.comparing(InstalledPackage::getName));
    return packages;
  }

  @Override
  public void installPackage(@NotNull RepoPackage repoPackage,
                             @Nullable String version,
                             boolean forceUpgrade,
                             @Nullable String extraOptions,
                             @NotNull Listener listener,
                             boolean installToUser) {
    final String packageName = repoPackage.getName();
    final String repository = PyPIPackageUtil.isPyPIRepository(repoPackage.getRepoUrl()) ? null : repoPackage.getRepoUrl();
    final List<String> extraArgs = new ArrayList<>();
    if (installToUser) {
      extraArgs.add(PyPackageManager.USE_USER_SITE);
    }
    if (extraOptions != null) {
      // TODO: Respect arguments quotation
      Collections.addAll(extraArgs, extraOptions.split(" +"));
    }
    if (!StringUtil.isEmptyOrSpaces(repository)) {
      extraArgs.add("--index-url");
      extraArgs.add(repository);
    }
    if (forceUpgrade) {
      extraArgs.add("-U");
    }
    final PyRequirement req = version == null
                              ? PyRequirementsKt.pyRequirement(packageName)
                              : PyRequirementsKt.pyRequirement(packageName, PyRequirementRelation.EQ, version);

    final PyPackageManagerUI ui = new PyPackageManagerUI(myProject, mySdk, new PyPackageManagerUI.Listener() {
      @Override
      public void started() {
        listener.operationStarted(packageName);
      }

      @Override
      public void finished(@Nullable List<ExecutionException> exceptions) {
        listener.operationFinished(packageName, toErrorDescription(exceptions, mySdk, repoPackage.getName()));
      }
    });
    ui.install(Collections.singletonList(req), extraArgs);
  }

  @Nullable
  public static ErrorDescription toErrorDescription(@Nullable List<ExecutionException> exceptions, @Nullable Sdk sdk) {
    return toErrorDescription(exceptions, sdk, null);
  }

  @Nullable
  public static PyPackageInstallationErrorDescription toErrorDescription(@Nullable List<ExecutionException> exceptions,
                                                                         @Nullable Sdk sdk,
                                                                         @Nullable String packageName) {
    if (exceptions != null && !exceptions.isEmpty() && !isCancelled(exceptions)) {
      return createDescription(exceptions.get(0), sdk, packageName);
    }
    return null;
  }

  @Override
  public void uninstallPackages(List<? extends InstalledPackage> installedPackages, @NotNull Listener listener) {
    PythonPackagesDialogStatisticsCollector.getPackageUninstalledEvent().log(myProject);
    final String packageName = installedPackages.size() == 1 ? installedPackages.get(0).getName() : null;
    final PyPackageManagerUI ui = new PyPackageManagerUI(myProject, mySdk, new PyPackageManagerUI.Listener() {
      @Override
      public void started() {
        listener.operationStarted(packageName);
      }

      @Override
      public void finished(List<ExecutionException> exceptions) {
        listener.operationFinished(packageName, toErrorDescription(exceptions, mySdk));
      }
    });

    final List<PyPackage> pyPackages = new ArrayList<>();
    for (InstalledPackage aPackage : installedPackages) {
      if (aPackage instanceof PyPackage) {
        pyPackages.add((PyPackage)aPackage);
      }
    }
    ui.uninstall(pyPackages);
  }

  @Override
  public void fetchPackageVersions(String packageName, CatchingConsumer<? super List<String>, ? super Exception> consumer) {
    PyPIPackageUtil.INSTANCE.usePackageReleases(packageName, consumer);
  }

  @Override
  public void fetchPackageDetails(@NotNull String packageName, CatchingConsumer<? super @Nls String, ? super Exception> consumer) {
    PyPIPackageUtil.INSTANCE.fillPackageDetails(packageName, new CatchingConsumer<>() {
      @Override
      public void consume(PackageDetails.Info details) {
        consumer.consume(formatPackageInfo(details));
      }

      @Override
      public void consume(Exception e) {
        consumer.consume(e);
      }
    });
  }

  private static String formatPackageInfo(@NotNull PackageDetails.Info info) {
    final StringBuilder stringBuilder = new StringBuilder(TEXT_PREFIX);
    final String description = info.getSummary();
    if (StringUtil.isNotEmpty(description)) {
      stringBuilder.append(description).append("<br/>");
    }
    final String version = info.getVersion();
    if (StringUtil.isNotEmpty(version)) {
      stringBuilder.append("<h4>Version</h4>");
      stringBuilder.append(version);
    }
    final String author = info.getAuthor();
    if (StringUtil.isNotEmpty(author)) {
      stringBuilder.append("<h4>Author</h4>");
      stringBuilder.append(author).append("<br/><br/>");
    }
    final String authorEmail = info.getAuthorEmail();
    if (StringUtil.isNotEmpty(authorEmail)) {
      stringBuilder.append("<br/>");
      stringBuilder.append(composeHref("mailto:" + authorEmail));
    }
    final String homePage = info.getHomePage();
    if (StringUtil.isNotEmpty(homePage)) {
      stringBuilder.append("<br/>");
      stringBuilder.append(composeHref(homePage));
    }
    stringBuilder.append(TEXT_SUFFIX);
    return stringBuilder.toString();
  }

  @NonNls private static final String HTML_PREFIX = "<a href=\"";
  @NonNls private static final String HTML_SUFFIX = "</a>";

  @NotNull
  private static String composeHref(String vendorUrl) {
    return HTML_PREFIX + vendorUrl + "\">" + vendorUrl + HTML_SUFFIX;
  }

  private static boolean isCancelled(@NotNull List<ExecutionException> exceptions) {
    for (ExecutionException e : exceptions) {
      if (e instanceof RunCanceledByUserException) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static PyPackageInstallationErrorDescription createDescription(@NotNull ExecutionException e,
                                                                         @Nullable Sdk sdk,
                                                                         @Nullable String packageName) {
    if (e instanceof PyExecutionException ee) {
      final String stdout = ee.getStdout();
      final String stdoutCause = findErrorCause(stdout);
      final String stderrCause = findErrorCause(ee.getStderr());
      final String cause = stdoutCause != null ? stdoutCause : stderrCause;
      final String message = cause != null ? cause : ee.getMessage();
      final String command = ee.getCommand() + " " + StringUtil.join(ee.getArgs(), " ");
      return new PyPackageInstallationErrorDescription(message, command, stdout.isEmpty() ? ee.getStderr() : stdout + "\n" + ee.getStderr(),
                                                       findErrorSolution(ee, cause, sdk), packageName, sdk);
    }
    else {
      return new PyPackageInstallationErrorDescription(e.getMessage(), null, null, null, packageName, sdk);
    }
  }

  @Nullable
  private static @DetailedDescription String findErrorSolution(@NotNull PyExecutionException e, @Nullable String cause, @Nullable Sdk sdk) {
    if (cause != null) {
      if (StringUtil.containsIgnoreCase(cause, "SyntaxError")) {
        final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
        return PySdkBundle.message("python.sdk.use.python.version.supported.by.this.package", languageLevel);
      }
    }

    if (SystemInfo.isLinux && (containsInOutput(e, "pyconfig.h") || containsInOutput(e, "Python.h"))) {
      return PySdkBundle.message("python.sdk.check.python.development.packages.installed");
    }

    if ("pip".equals(e.getCommand()) && sdk != null) {
      return PySdkBundle.message("python.sdk.try.to.run.command.from.system.terminal", sdk.getHomePath());
    }

    return null;
  }

  private static boolean containsInOutput(@NotNull PyExecutionException e, @NotNull String text) {
    return StringUtil.containsIgnoreCase(e.getStdout(), text) || StringUtil.containsIgnoreCase(e.getStderr(), text);
  }

  @Nullable
  private static String findErrorCause(@NotNull String output) {
    final Matcher m = PATTERN_ERROR_LINE.matcher(output);
    if (m.find()) {
      final String result = m.group();
      return result != null ? result.trim() : null;
    }
    return null;
  }

  @Override
  public void updatePackage(@NotNull InstalledPackage installedPackage,
                            @Nullable String version,
                            @NotNull Listener listener) {
    installPackage(new RepoPackage(installedPackage.getName(), null), version, true, null, listener, false);
  }

  /**
   * @return whether the latest version should be requested independently for each package
   */
  @Override
  public boolean shouldFetchLatestVersionsForOnlyInstalledPackages() {
    return true;
  }

  @Override
  public void fetchLatestVersion(@NotNull InstalledPackage pkg, @NotNull CatchingConsumer<? super String, ? super Exception> consumer) {
    myExecutorService.execute(() -> {
      if (myProject.isDisposed()) return;
      try {
        PyPIPackageUtil.INSTANCE.loadPackages();
        final String version = PyPIPackageUtil.INSTANCE.fetchLatestPackageVersion(myProject, pkg.getName());
        consumer.consume(StringUtil.notNullize(version));
      }
      catch (IOException e) {
        consumer.consume(e);
      }
    });
  }

  @Override
  public int compareVersions(@NotNull String version1, @NotNull String version2) {
    return PyPackageVersionComparator.getSTR_COMPARATOR().compare(version1, version2);
  }

  @Nullable
  @Override
  public String getID() {
    return "Python";
  }

  public static class PyPackageInstallationErrorDescription extends ErrorDescription {
    @Nullable private final String myPackageName;
    @Nullable private final String myPythonVersion;
    @Nullable private final String myInterpreterPath;
    @Nullable private final Sdk mySdk;

    public PyPackageInstallationErrorDescription(@NotNull @DetailedDescription String message,
                                                 @Nullable String command,
                                                 @Nullable String output,
                                                 @Nullable @DetailedDescription String solution,
                                                 @Nullable String packageName,
                                                 @Nullable Sdk sdk) {
      super(message, command, output, solution);
      myPackageName = packageName;
      mySdk = sdk;
      myPythonVersion = sdk != null ? sdk.getVersionString() : null;
      myInterpreterPath = sdk != null ? sdk.getHomePath() : null;
    }

    @Nullable
    public static PyPackageInstallationErrorDescription createFromMessage(@Nullable @NlsContexts.DetailedDescription String message) {
      return message != null ? new PyPackageInstallationErrorDescription(message, null, null, null, null, null) : null;
    }

    public @Nullable @NlsSafe String getPackageName() {
      return myPackageName;
    }

    public @Nullable @NlsSafe String getPythonVersion() {
      return myPythonVersion;
    }

    public @Nullable @NlsSafe String getInterpreterPath() {
      return myInterpreterPath;
    }

    public @Nullable Sdk getSdk() {
      return mySdk;
    }
  }

  @Override
  public boolean canManageRepositories() {
    // Package management from ManageRepositoriesDialog is disabled in favor of Packaging toolwindow
    return false;
  }
}
