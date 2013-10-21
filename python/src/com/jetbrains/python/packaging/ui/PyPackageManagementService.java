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
package com.jetbrains.python.packaging.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.CatchingConsumer;
import com.intellij.webcore.packaging.InstalledPackage;
import com.intellij.webcore.packaging.PackageManagementService;
import com.intellij.webcore.packaging.RepoPackage;
import com.jetbrains.python.packaging.*;
import com.jetbrains.python.sdk.PythonSdkType;
import org.apache.xmlrpc.AsyncCallback;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * @author yole
 */
public class PyPackageManagementService extends PackageManagementService {
  private final Project myProject;
  private final Sdk mySdk;

  public PyPackageManagementService(Project project, Sdk sdk) {
    myProject = project;
    mySdk = sdk;
  }

  public Sdk getSdk() {
    return mySdk;
  }

  @Override
  public List<String> getAllRepositories() {
    List<String> result = new ArrayList<String>();
    result.add(PyPIPackageUtil.PYPI_URL);
    result.addAll(PyPackageService.getInstance().additionalRepositories);
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

  private static List<RepoPackage> versionMapToPackageList(Map<String, String> packageToVersionMap) {
    final boolean customRepoConfigured = !PyPackageService.getInstance().additionalRepositories.isEmpty();
    String url = customRepoConfigured? PyPIPackageUtil.PYPI_URL : "";
    List<RepoPackage> packages = new ArrayList<RepoPackage>();
    for (Map.Entry<String, String> entry : packageToVersionMap.entrySet()) {
      packages.add(new RepoPackage(entry.getKey(), url, entry.getValue()));
    }
    return packages;
  }

  @Override
  public List<RepoPackage> reloadAllPackages() throws IOException {
    final PyPackageService service = PyPackageService.getInstance();
    PyPIPackageUtil.INSTANCE.updatePyPICache(service);
    service.LAST_TIME_CHECKED = System.currentTimeMillis();
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
      userSiteText += " (" + PyPackageManagerImpl.getUserSite() + ")";
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
      packages = ((PyPackageManagerImpl)PyPackageManager.getInstance(mySdk)).getPackages();
    }
    catch (PyExternalProcessException e) {
      throw new IOException(e);
    }
    return new ArrayList<InstalledPackage>(packages);
  }

  @Override
  public void installPackage(final RepoPackage repoPackage,String version, boolean forceUpgrade, String extraOptions,
                             final Listener listener, boolean installToUser) {
    final String packageName = repoPackage.getName();
    final String repository = PyPIPackageUtil.PYPI_URL.equals(repoPackage.getRepoUrl()) ? null : repoPackage.getRepoUrl();
    final List<String> extraArgs = new ArrayList<String>();
    if (installToUser) {
      extraArgs.add(PyPackageManagerImpl.USE_USER_SITE);
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

    final PyPackageManagerImpl.UI ui = new PyPackageManagerImpl.UI(myProject, mySdk, new PyPackageManagerImpl.UI.Listener() {
      @Override
      public void started() {
        listener.operationStarted(packageName);
      }

      @Override
      public void finished(@Nullable List<PyExternalProcessException> exceptions) {
        listener.operationFinished(packageName, toErrorDescription(exceptions));
      }
    });
    ui.install(Collections.singletonList(req), extraArgs);
  }

  private String toErrorDescription(List<PyExternalProcessException> exceptions) {
    String errorDescription = null;
    if (exceptions != null && exceptions.size() > 0) {
      errorDescription = PyPackageManagerImpl.UI.createDescription(exceptions, "");
    }
    return errorDescription;
  }

  @Override
  public void uninstallPackages(List<InstalledPackage> installedPackages, final Listener listener) {
    final String packageName = installedPackages.size() == 1 ? installedPackages.get(0).getName() : null;
    PyPackageManagerImpl.UI ui = new PyPackageManagerImpl.UI(myProject, mySdk, new PyPackageManagerImpl.UI.Listener() {
      @Override
      public void started() {
        listener.operationStarted(packageName);
      }

      @Override
      public void finished(final List<PyExternalProcessException> exceptions) {
        listener.operationFinished(packageName, toErrorDescription(exceptions));
      }
    });

    List<PyPackage> pyPackages = new ArrayList<PyPackage>();
    for (InstalledPackage aPackage : installedPackages) {
      if (aPackage instanceof PyPackage) {
       pyPackages.add((PyPackage) aPackage);
      }
    }
    ui.uninstall(pyPackages);
  }

  @Override
  public void fetchPackageVersions(final String packageName, final CatchingConsumer<List<String>, Exception> consumer) {
    PyPIPackageUtil.INSTANCE.usePackageReleases(packageName, new AsyncCallback() {
      @Override
      public void handleResult(Object result, URL url, String method) {
        final List<String> releases = (List<String>)result;
        PyPIPackageUtil.INSTANCE.addPackageReleases(packageName, releases);
        consumer.consume(releases);
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
}
