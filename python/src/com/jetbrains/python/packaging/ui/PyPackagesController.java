package com.jetbrains.python.packaging.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.CatchingConsumer;
import com.intellij.webcore.packaging.PackageManagerController;
import com.intellij.webcore.packaging.RepoPackage;
import com.jetbrains.python.packaging.*;
import org.apache.xmlrpc.AsyncCallback;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * @author yole
 */
public class PyPackagesController implements PackageManagerController {
  private final Project myProject;
  private final Sdk mySdk;

  public PyPackagesController(Project project, Sdk sdk) {
    myProject = project;
    mySdk = sdk;
  }

  @Override
  public List<String> getAllRepositories() {
    List<String> result = new ArrayList<String>();
    result.add(PyPIPackageUtil.PYPI_URL);
    result.addAll(PyPackageService.getInstance().additionalRepositories);
    return result;
  }

  @Override
  public boolean canModifyRepository(String repositoryUrl) {
    return !PyPIPackageUtil.PYPI_URL.equals(repositoryUrl);
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
    List<RepoPackage> packages = new ArrayList<RepoPackage>();
    final Collection<String> packageNames = PyPIPackageUtil.INSTANCE.getPackageNames();
    final boolean customRepoConfigured = !PyPackageService.getInstance().additionalRepositories.isEmpty();
    String url = customRepoConfigured? PyPIPackageUtil.PYPI_URL : "";
    for (String name : packageNames) {
      packages.add(new RepoPackage(name, url));
    }
    packages.addAll(PyPIPackageUtil.INSTANCE.getAdditionalPackageNames());
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
  public void installPackage(String packageName,
                             String repositoryUrl,
                             String version,
                             boolean installToUser,
                             String extraOptions,
                             final Listener listener) {
    final String repository = repositoryUrl.equals(PyPIPackageUtil.PYPI_URL) ? null : repositoryUrl;
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
        listener.installationStarted();
      }

      @Override
      public void finished(@Nullable List<PyExternalProcessException> exceptions) {
        listener.installationFinished(PyPackageManagerImpl.UI.createDescription(exceptions, ""));
      }
    });
    ui.install(Collections.singletonList(req), extraArgs);
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
