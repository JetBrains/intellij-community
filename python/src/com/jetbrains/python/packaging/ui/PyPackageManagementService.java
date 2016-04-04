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
package com.jetbrains.python.packaging.ui;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunCanceledByUserException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.CatchingConsumer;
import com.intellij.webcore.packaging.InstalledPackage;
import com.intellij.webcore.packaging.PackageManagementService;
import com.intellij.webcore.packaging.RepoPackage;
import com.jetbrains.python.packaging.*;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.apache.xmlrpc.AsyncCallback;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
@SuppressWarnings("UseOfObsoleteCollectionType")
public class PyPackageManagementService extends PackageManagementService {
  @NotNull private static final Pattern PATTERN_ERROR_LINE = Pattern.compile(".*error:.*", Pattern.CASE_INSENSITIVE);

  private final Project myProject;
  protected final Sdk mySdk;

  public PyPackageManagementService(@NotNull final Project project, @NotNull final Sdk sdk) {
    myProject = project;
    mySdk = sdk;
  }

  @NotNull
  public Sdk getSdk() {
    return mySdk;
  }

  @Override
  public List<String> getAllRepositories() {
    final PyPackageService packageService = PyPackageService.getInstance();
    List<String> result = new ArrayList<String>();
    if (!packageService.PYPI_REMOVED) result.add(PyPIPackageUtil.PYPI_URL);
    result.addAll(packageService.additionalRepositories);
    return result;
  }

  @Override
  public void addRepository(final String repositoryUrl) {
    PyPackageService.getInstance().addRepository(repositoryUrl);
  }

  @Override
  public void removeRepository(final String repositoryUrl) {
    PyPackageService.getInstance().removeRepository(repositoryUrl);
  }

  @Override
  public List<RepoPackage> getAllPackages() throws IOException {
    final Map<String, String> packageToVersionMap;
    try {
      packageToVersionMap = PyPIPackageUtil.INSTANCE.loadAndGetPackages();
    }
    catch (IOException e) {
      throw new IOException("Could not reach URL " + e.getMessage() + ". Please, check your internet connection.");
    }
    List<RepoPackage> packages = versionMapToPackageList(packageToVersionMap);
    packages.addAll(PyPIPackageUtil.INSTANCE.getAdditionalPackageNames());
    return packages;
  }

  protected static List<RepoPackage> versionMapToPackageList(Map<String, String> packageToVersionMap) {
    final boolean customRepoConfigured = !PyPackageService.getInstance().additionalRepositories.isEmpty();
    String url = customRepoConfigured ? PyPIPackageUtil.PYPI_URL : "";
    List<RepoPackage> packages = new ArrayList<RepoPackage>();
    for (Map.Entry<String, String> entry : packageToVersionMap.entrySet()) {
      packages.add(new RepoPackage(entry.getKey(), url, entry.getValue()));
    }
    return packages;
  }

  @Override
  public List<RepoPackage> reloadAllPackages() throws IOException {
    PyPIPackageUtil.INSTANCE.clearPackagesCache();
    return getAllPackages();
  }

  @Override
  public List<RepoPackage> getAllPackagesCached() {
    return versionMapToPackageList(PyPIPackageUtil.getPyPIPackages());
  }

  @Override
  public boolean canInstallToUser() {
    return !PythonSdkType.isVirtualEnv(mySdk);
  }

  @Override
  public String getInstallToUserText() {
    String userSiteText = "Install to user's site packages directory";
    if (!PythonSdkType.isRemote(mySdk))
      userSiteText += " (" + PySdkUtil.getUserSite() + ")";
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
  public Collection<InstalledPackage> getInstalledPackages() throws IOException {
    List<PyPackage> packages;
    try {
      packages = PyPackageManager.getInstance(mySdk).getPackages(false);
      if (packages != null) {
        Collections.sort(packages, new Comparator<PyPackage>() {
          @Override
          public int compare(@NotNull PyPackage pkg1, @NotNull PyPackage pkg2) {
            return pkg1.getName().compareTo(pkg2.getName());
          }
        });
      }
    }
    catch (ExecutionException e) {
      throw new IOException(e);
    }
    return packages != null ? new ArrayList<InstalledPackage>(packages) : new ArrayList<InstalledPackage>();
  }

  @Override
  public void installPackage(final RepoPackage repoPackage, String version, boolean forceUpgrade, String extraOptions,
                             final Listener listener, boolean installToUser) {
    final String packageName = repoPackage.getName();
    final String repository = PyPIPackageUtil.PYPI_URL.equals(repoPackage.getRepoUrl()) ? null : repoPackage.getRepoUrl();
    final List<String> extraArgs = new ArrayList<String>();
    if (installToUser) {
      extraArgs.add(PyPackageManager.USE_USER_SITE);
    }
    if (extraOptions != null) {
      // TODO: Respect arguments quotation
      Collections.addAll(extraArgs, extraOptions.split(" +"));
    }
    if (!StringUtil.isEmptyOrSpaces(repository)) {
      extraArgs.add("--extra-index-url");
      extraArgs.add(repository);
    }
    if (forceUpgrade) {
      extraArgs.add("-U");
    }
    final PyRequirement req;
    if (version != null) {
      req = new PyRequirement(packageName, version);
    }
    else {
      req = new PyRequirement(packageName);
    }

    final PyPackageManagerUI ui = new PyPackageManagerUI(myProject, mySdk, new PyPackageManagerUI.Listener() {
      @Override
      public void started() {
        listener.operationStarted(packageName);
      }

      @Override
      public void finished(@Nullable List<ExecutionException> exceptions) {
        listener.operationFinished(packageName, toErrorDescription(exceptions, mySdk));
      }
    });
    ui.install(Collections.singletonList(req), extraArgs);
  }

  @Nullable
  public static ErrorDescription toErrorDescription(@Nullable List<ExecutionException> exceptions, @Nullable Sdk sdk) {
    if (exceptions != null && !exceptions.isEmpty() && !isCancelled(exceptions)) {
      return createDescription(exceptions.get(0), sdk);
    }
    return null;
  }

  @Override
  public void uninstallPackages(List<InstalledPackage> installedPackages, final Listener listener) {
    final String packageName = installedPackages.size() == 1 ? installedPackages.get(0).getName() : null;
    PyPackageManagerUI ui = new PyPackageManagerUI(myProject, mySdk, new PyPackageManagerUI.Listener() {
      @Override
      public void started() {
        listener.operationStarted(packageName);
      }

      @Override
      public void finished(final List<ExecutionException> exceptions) {
        listener.operationFinished(packageName, toErrorDescription(exceptions, mySdk));
      }
    });

    List<PyPackage> pyPackages = new ArrayList<PyPackage>();
    for (InstalledPackage aPackage : installedPackages) {
      if (aPackage instanceof PyPackage) {
        pyPackages.add((PyPackage)aPackage);
      }
    }
    ui.uninstall(pyPackages);
  }

  @Override
  public void fetchPackageVersions(final String packageName, final CatchingConsumer<List<String>, Exception> consumer) {
    PyPIPackageUtil.INSTANCE.usePackageReleases(packageName, new AsyncCallback() {
      @Override
      public void handleResult(Object result, URL url, String method) {
        //noinspection unchecked
        final List<String> releases = (List<String>)result;
        if (releases != null) {
          PyPIPackageUtil.INSTANCE.addPackageReleases(packageName, releases);
          consumer.consume(releases);
        }
      }

      @Override
      public void handleError(Exception exception, URL url, String method) {
        consumer.consume(exception);
      }
    });
  }

  @Override
  public void fetchPackageDetails(final String packageName, final CatchingConsumer<String, Exception> consumer) {
    PyPIPackageUtil.INSTANCE.fillPackageDetails(packageName, new AsyncCallback() {
      @Override
      public void handleResult(Object result, URL url, String method) {
        final Hashtable details = (Hashtable)result;
        PyPIPackageUtil.INSTANCE.addPackageDetails(packageName, details);
        consumer.consume(formatPackageDetails(details));
      }

      @Override
      public void handleError(Exception exception, URL url, String method) {
        consumer.consume(exception);
      }
    });
  }

  @NonNls private static final String TEXT_PREFIX = "<html><head>" +
                                                    "    <style type=\"text/css\">" +
                                                    "        p {" +
                                                    "            font-family: Arial,serif; font-size: 12pt; margin: 2px 2px" +
                                                    "        }" +
                                                    "    </style>" +
                                                    "</head><body style=\"font-family: Arial,serif; font-size: 12pt; margin: 5px 5px;\">";
  @NonNls private static final String TEXT_SUFFIX = "</body></html>";

  private static String formatPackageDetails(Hashtable details) {
    Object description = details.get("summary");
    StringBuilder stringBuilder = new StringBuilder(TEXT_PREFIX);
    if (description instanceof String) {
      stringBuilder.append(description).append("<br/>");
    }
    Object version = details.get("version");
    if (version instanceof String && !StringUtil.isEmpty((String)version)) {
      stringBuilder.append("<h4>Version</h4>");
      stringBuilder.append(version);
    }
    Object author = details.get("author");
    if (author instanceof String && !StringUtil.isEmpty((String)author)) {
      stringBuilder.append("<h4>Author</h4>");
      stringBuilder.append(author).append("<br/><br/>");
    }
    Object authorEmail = details.get("author_email");
    if (authorEmail instanceof String && !StringUtil.isEmpty((String)authorEmail)) {
      stringBuilder.append("<br/>");
      stringBuilder.append(composeHref("mailto:" + authorEmail));
    }
    Object homePage = details.get("home_page");
    if (homePage instanceof String && !StringUtil.isEmpty((String)homePage)) {
      stringBuilder.append("<br/>");
      stringBuilder.append(composeHref((String)homePage));
    }
    stringBuilder.append(TEXT_SUFFIX);
    return stringBuilder.toString();
  }

  @NonNls private static final String HTML_PREFIX = "<a href=\"";
  @NonNls private static final String HTML_SUFFIX = "</a>";

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
  private static ErrorDescription createDescription(@NotNull ExecutionException e, @Nullable Sdk sdk) {
    if (e instanceof PyExecutionException) {
      final PyExecutionException ee = (PyExecutionException)e;
      final String stdoutCause = findErrorCause(ee.getStdout());
      final String stderrCause = findErrorCause(ee.getStderr());
      final String cause = stdoutCause != null ? stdoutCause : stderrCause;
      final String message =  cause != null ? cause : ee.getMessage();
      final String command = ee.getCommand() + " " + StringUtil.join(ee.getArgs(), " ");
      return new ErrorDescription(message, command, ee.getStdout() + "\n" + ee.getStderr(), findErrorSolution(ee, cause, sdk));
    }
    else {
      return ErrorDescription.fromMessage(e.getMessage());
    }
  }

  @Nullable
  private static String findErrorSolution(@NotNull PyExecutionException e, @Nullable String cause, @Nullable Sdk sdk) {
    if (cause != null) {
      if (StringUtil.containsIgnoreCase(cause, "SyntaxError")) {
        final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
        return "Make sure that you use a version of Python supported by this package. Currently you are using Python " +
               languageLevel + ".";
      }
    }

    if (SystemInfo.isLinux && (containsInOutput(e, "pyconfig.h") || containsInOutput(e, "Python.h"))) {
      return "Make sure that you have installed Python development packages for your operating system.";
    }

    if ("pip".equals(e.getCommand()) && sdk != null) {
      return "Try to run this command from the system terminal. Make sure that you use the correct version of 'pip' " +
             "installed for your Python interpreter located at '" + sdk.getHomePath() + "'.";
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

  /*@Override
  public void updatePackage(@NotNull InstalledPackage installedPackage,
                            @Nullable String version,
                            @NotNull Listener listener) {
    installPackage(new RepoPackage(installedPackage.getName(), null *//* TODO? *//*), null, true, null, listener, false);
  }

  @Override
  public boolean shouldFetchLatestVersionsForOnlyInstalledPackages() {
    final List<String> repositories = PyPackageService.getInstance().additionalRepositories;
    return repositories.size() > 1  || (repositories.size() == 1 && !repositories.get(0).equals(PyPIPackageUtil.PYPI_LIST_URL));
  }

  @Override
  public void fetchLatestVersion(@NotNull InstalledPackage pkg, @NotNull CatchingConsumer<String, Exception> consumer) {
    final String version;
    try {
      version = PyPackageManager.getInstance(mySdk).fetchLatestVersion(pkg);
      consumer.consume(version);
    }
    catch (ExecutionException ignored) {

    }
  }*/
}