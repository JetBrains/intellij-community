// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.CatchingConsumer;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.RequestBuilder;
import com.intellij.webcore.packaging.RepoPackage;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.repository.PyPackageRepositories;
import com.jetbrains.python.packaging.repository.PyPackageRepositoryUtil;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;


@ApiStatus.Internal
public final class PyPIPackageUtil {
  private static final Logger LOG = Logger.getInstance(PyPIPackageUtil.class);
  private static final Gson GSON = new GsonBuilder().create();

  private static final String PYPI_BASE_URL = "https://pypi.org";
  private static final String PYPI_SIMPLE_REPOSITORY_API = "/simple/";
  private static final String PYPI_DETAILS_API = "/pypi/";
  private static final String PYPI_PROJECT_API = "/project/";

  /**
   * @deprecated Use {@link #buildDetailsUrl(String, String)} instead
   */
  @Deprecated
  public static final String PYPI_URL = PYPI_BASE_URL + "/pypi";
  public static final String PYPI_LIST_URL = PYPI_BASE_URL + PYPI_SIMPLE_REPOSITORY_API;

  public static final PyPIPackageUtil INSTANCE = new PyPIPackageUtil();

  public static class NotSimpleRepositoryApiUrlException extends Exception {
    private NotSimpleRepositoryApiUrlException(String url) {
      super(PyBundle.message("python.packaging.error.not.simple.repository.url", url, PYPI_SIMPLE_REPOSITORY_API));
    }
  }

  private PyPIPackageUtil() {
  }

  /**
   * Contains cached versions of packages from additional repositories.
   *
   * @see #getPackageVersionsFromAdditionalRepositories(String)
   */
  private final LoadingCache<String, List<String>> myAdditionalPackagesReleases = CacheBuilder.newBuilder().build(
    new CacheLoader<>() {
      @Override
      public List<String> load(@NotNull String key) throws Exception {
        LOG.debug("Searching for versions of package '" + key + "' in additional repositories");
        final List<String> repositories = PyPackageService.getInstance().additionalRepositories;
        for (String repository : repositories) {
          try {
            final List<String> versions = parsePackageVersionsFromRepository(repository, key);
            if (!versions.isEmpty()) {
              LOG.debug("Found versions " + versions + "of " + key + " at " + repository);
              return Collections.unmodifiableList(versions);
            }
          }
          catch (HttpRequests.HttpStatusException e) {
            if (e.getStatusCode() != 404) {
              LOG.debug("Cannot access " + e.getUrl() + ": " + e.getMessage());
            }
          }
        }
        return Collections.emptyList();
      }
    });

  /**
   * Contains cached packages taken from additional repositories.
   */
  private final LoadingCache<String, List<RepoPackage>> myAdditionalPackages = CacheBuilder.newBuilder().build(
    new CacheLoader<>() {
      @Override
      public List<RepoPackage> load(@NotNull String key) throws Exception {
        return getPackagesFromAdditionalRepository(key);
      }
    });

  /**
   * Contains cached package information retrieved through PyPI's JSON API.
   *
   * @see #refreshAndGetPackageDetailsFromPyPI(String, boolean)
   */
  private final LoadingCache<String, PackageDetails> myPackageToDetails = CacheBuilder.newBuilder().build(
    new CacheLoader<>() {
      @Override
      public PackageDetails load(@NotNull String key) throws Exception {
        LOG.debug("Fetching details for the package '" + key + "' on PyPI");
        final String detailsUrl = buildDetailsUrl(PYPI_LIST_URL, key);
        return HttpRequests.request(detailsUrl)
          .userAgent(getUserAgent())
          .connect(request -> GSON.fromJson(request.getReader(), PackageDetails.class));
      }
    });

  /**
   * Prevents simultaneous updates of {@link PyPackageService#PYPI_REMOVED}
   * because the corresponding response contains tons of data and multiple
   * queries at the same time can cause memory issues.
   */
  private final Object myPyPIPackageCacheUpdateLock = new Object();

  /**
   * Value for "User Agent" HTTP header in form: PyCharm/2016.2 EAP
   */
  private static @NotNull String getUserAgent() {
    return ApplicationNamesInfo.getInstance().getProductName() + "/" + ApplicationInfo.getInstance().getFullVersion();
  }

  public static boolean isPyPIRepository(@Nullable String repository) {
    return repository != null && repository.startsWith(PYPI_BASE_URL);
  }

  public @NotNull List<RepoPackage> getAdditionalPackages(@NotNull List<String> repositories) {
    return StreamEx.of(myAdditionalPackages.getAllPresent(repositories).values()).flatMap(StreamEx::of).toList();
  }

  public void loadAdditionalPackages(@NotNull List<String> repositories, boolean alwaysRefresh) {
    var failedToConnect = new ArrayList<String>();
    if (alwaysRefresh) {
      for (String url : repositories) {
        try {
          myAdditionalPackages.refresh(url);
        }
        catch (Exception e) {
          LOG.error("Error connecting to " + url, e);
          failedToConnect.add(url);
          ApplicationManager.getApplication().getService(PyPackageRepositories.class).markInvalid(url);
        }
      }
    }
    else {
      for (String url : repositories) {
        try {
          getCachedValueOrRethrowIO(myAdditionalPackages, url);
        }
        catch (Exception e) {
          LOG.warn("Error connecting to " + url, e);
          failedToConnect.add(url);
          ApplicationManager.getApplication().getService(PyPackageRepositories.class).markInvalid(url);
        }
      }
    }
    if (!failedToConnect.isEmpty()) {
      PyPackageService packageService = PyPackageService.getInstance();
      failedToConnect.forEach(repo -> packageService.removeRepository(repo));
    }
  }

  private static @NotNull List<RepoPackage> getPackagesFromAdditionalRepository(@NotNull String url) throws IOException {
    return ContainerUtil.map(parsePyPIListFromWeb(url), s -> new RepoPackage(s, url, null));
  }

  public void fillPackageDetails(@NotNull String packageName, @NotNull CatchingConsumer<PackageDetails.Info, Exception> callback) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        final PackageDetails packageDetails = refreshAndGetPackageDetailsFromPyPI(packageName, false);
        callback.consume(packageDetails.getInfo());
      }
      catch (IOException e) {
        callback.consume(e);
      }
    });
  }

  private @NotNull PackageDetails refreshAndGetPackageDetailsFromPyPI(@NotNull String packageName, boolean alwaysRefresh)
    throws IOException {
    if (alwaysRefresh) {
      myPackageToDetails.invalidate(packageName);
    }
    return getCachedValueOrRethrowIO(myPackageToDetails, packageName);
  }

  public void usePackageReleases(@NotNull String packageName, @NotNull CatchingConsumer<? super List<String>, ? super Exception> callback) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        final List<String> releasesFromSimpleIndex = getPackageVersionsFromAdditionalRepositories(packageName);
        if (releasesFromSimpleIndex.isEmpty()) {
          final List<String> releasesFromPyPI = getPackageVersionsFromPyPI(packageName, true);
          callback.consume(releasesFromPyPI);
        }
        else {
          callback.consume(releasesFromSimpleIndex);
        }
      }
      catch (Exception e) {
        callback.consume(e);
      }
    });
  }

  /**
   * Fetches available package versions using JSON API of PyPI.
   */
  private @NotNull List<String> getPackageVersionsFromPyPI(@NotNull String packageName,
                                                           boolean force) throws IOException {
    final PackageDetails details = refreshAndGetPackageDetailsFromPyPI(packageName, force);
    final List<String> result = details.getReleases();
    result.sort(PyPackageVersionComparator.getSTR_COMPARATOR().reversed());
    return Collections.unmodifiableList(result);
  }

  private @Nullable String getLatestPackageVersionFromPyPI(@NotNull Project project, @NotNull String packageName) throws IOException {
    LOG.debug("Requesting the latest PyPI version for the package " + packageName);
    final List<String> versions = getPackageVersionsFromPyPI(packageName, true);
    if (project.isDisposed()) return null;
    return PyPackagingSettings.getInstance(project).selectLatestVersion(versions);
  }

  /**
   * Fetches available package versions by scrapping the page containing package archives.
   * It's primarily used for additional repositories since, e.g. devpi doesn't provide another way to get this information.
   */
  private @NotNull List<String> getPackageVersionsFromAdditionalRepositories(@NotNull String packageName) throws IOException {
    return getCachedValueOrRethrowIO(myAdditionalPackagesReleases, packageName);
  }

  private static @NotNull <T> T getCachedValueOrRethrowIO(@NotNull LoadingCache<String, ? extends T> cache, @NotNull String key)
    throws IOException {
    try {
      return cache.get(key);
    }
    catch (ExecutionException | UncheckedExecutionException e) {
      final Throwable cause = e.getCause();
      throw (cause instanceof IOException ? (IOException)cause : new IOException("Unexpected non-IO error", cause));
    }
  }

  private @Nullable String getLatestPackageVersionFromAdditionalRepositories(@NotNull Project project, @NotNull String packageName)
    throws IOException {
    final List<String> versions = getPackageVersionsFromAdditionalRepositories(packageName);
    return PyPackagingSettings.getInstance(project).selectLatestVersion(versions);
  }

  public @Nullable String fetchLatestPackageVersion(@NotNull Project project, @NotNull String packageName) throws IOException {
    String version = null;
    // Package is on PyPI, not, say, some system package on Ubuntu
    if (PyPIPackageCache.getInstance().containsPackage(packageName)) {
      version = getLatestPackageVersionFromPyPI(project, packageName);
    }
    if (!PyPackageService.getInstance().additionalRepositories.isEmpty()) {
      final String extraVersion = getLatestPackageVersionFromAdditionalRepositories(project, packageName);
      if (extraVersion != null) {
        version = extraVersion;
      }
    }
    return version;
  }

  private static String normalizeRepositoryUrl(@NotNull String repositoryUrl) throws NotSimpleRepositoryApiUrlException {
    final String normalizedRepositoryUrl = repositoryUrl.endsWith("/") ? repositoryUrl : repositoryUrl + "/";

    if (!normalizedRepositoryUrl.endsWith(PYPI_SIMPLE_REPOSITORY_API)) {
      throw new NotSimpleRepositoryApiUrlException(normalizedRepositoryUrl);
    }

    return normalizedRepositoryUrl;
  }

  /**
   * The following constraints are placed on the API:
   * <ul>
   *   <li> All URLs which respond with an HTML5 page MUST end with a / and
   *        the repository SHOULD redirect the URLs without a / to add a / to the end.
   *   <li> Repositories MAY redirect unnormalized URLs to the canonical normalized URL (e.g. /Foobar/ may redirect to /foobar/),
   *        however clients MUST NOT rely on this redirection and MUST request the normalized URL.
   * </ul>
   *
   * @see <a href="https://packaging.python.org/en/latest/specifications/simple-repository-api/#base-html-api">Base HTML API</a>
   * @see <a href="https://packaging.python.org/en/latest/specifications/simple-repository-api/#normalized-names">Normalized Names</a>
   */
  public static @NotNull String buildPackageUrl(
    @NotNull String repositoryUrl,
    @NotNull String packageName
  ) throws NotSimpleRepositoryApiUrlException {
    final String normalizedPackageName = PyPackageName.Companion.normalizePackageName(packageName);
    final String normalizedRepositoryUrl = normalizeRepositoryUrl(repositoryUrl);
    final String packageUrl = normalizedRepositoryUrl + normalizedPackageName + "/";
    return packageUrl;
  }

  /**
   * Project API uses not normalized names (e.g. /project/Flask is correct, GET /project/flask redirects (HTTP 301) to /project/Flask)
   */
  public static @NotNull String buildProjectUrl(@NotNull String packageName) {
    final String projectUrl = PYPI_BASE_URL + PYPI_PROJECT_API + packageName + "/";
    return projectUrl;
  }

  /**
   * Details API uses normalized names, makes HTTP 301 redirect in case of non-normalized.
   */
  public static @NotNull String buildDetailsUrl(@NotNull String repositoryUrl,
                                                @NotNull String packageName) throws NotSimpleRepositoryApiUrlException {
    final String normalizedRepositoryUrl = normalizeRepositoryUrl(repositoryUrl);
    final String normalizedPackageName = PyPackageName.Companion.normalizePackageName(packageName);

    final String baseUrl = StringUtil.trimEnd(normalizedRepositoryUrl, PYPI_SIMPLE_REPOSITORY_API);
    final String detailsUrl = baseUrl + PYPI_DETAILS_API + normalizedPackageName + "/json";
    return detailsUrl;
  }

  public static @NotNull List<String> parsePackageVersionsFromRepository(@NotNull String repositoryUrl, @NotNull String packageName)
    throws IOException, NotSimpleRepositoryApiUrlException {
    String packageUrl = buildPackageUrl(repositoryUrl, packageName);
    return parsePackageVersionsFromArchives(packageUrl, packageName);
  }

  /**
   * @deprecated Use {@link #parsePackageVersionsFromRepository} instead
   */
  @Deprecated
  public static @NotNull List<String> parsePackageVersionsFromArchives(@NotNull String archivesUrl,
                                                                       @NotNull String packageName) throws IOException {
    return HttpRequests.request(archivesUrl).userAgent(getUserAgent()).connect(request -> {
      final List<String> versions = new ArrayList<>();
      final Reader reader = request.getReader();
      new ParserDelegator().parse(reader, new HTMLEditorKit.ParserCallback() {
        HTML.Tag myTag;

        @Override
        public void handleStartTag(HTML.Tag tag, MutableAttributeSet set, int i) {
          myTag = tag;
        }

        @Override
        public void handleText(char @NotNull [] data, int pos) {
          if (myTag != null && "a".equals(myTag.toString())) {
            final String artifactName = String.valueOf(data);
            final String version = extractVersionFromArtifactName(artifactName, packageName);
            if (version != null) {
              versions.add(version);
            }
            else {
              LOG.debug("Could not extract version from " + artifactName + " at " + archivesUrl);
            }
          }
        }
      }, true);
      versions.sort(PyPackageVersionComparator.getSTR_COMPARATOR().reversed());
      return versions;
    });
  }

  private static @Nullable String extractVersionFromArtifactName(@NotNull String artifactName, @NotNull String packageName) {
    final String withoutExtension;
    // Contains more than one dot and thus should be handled separately
    if (artifactName.endsWith(".tar.gz")) {
      withoutExtension = StringUtil.trimEnd(artifactName, ".tar.gz");
    }
    else {
      withoutExtension = FileUtilRt.getNameWithoutExtension(artifactName);
    }
    final String packageNameWithUnderscores = packageName.replace('-', '_');
    final String suffix;
    if (StringUtil.startsWithIgnoreCase(withoutExtension, packageName)) {
      suffix = withoutExtension.substring(packageName.length());
    }
    else if (StringUtil.startsWithIgnoreCase(withoutExtension, packageNameWithUnderscores)) {
      suffix = withoutExtension.substring(packageNameWithUnderscores.length());
    }
    else {
      return null;
    }
    // StringUtil.split excludes empty parts by default effectively stripping a leading dash
    final String version = ContainerUtil.getFirstItem(StringUtil.split(suffix, "-"));
    if (StringUtil.isNotEmpty(version)) {
      return version;
    }
    return null;
  }

  public void updatePyPICache() throws IOException {
    final PyPackageService service = PyPackageService.getInstance();
    if (service.PYPI_REMOVED) return;
    PyPIPackageCache.reload(parsePyPIListFromWeb(PYPI_LIST_URL));
  }

  @RequiresBackgroundThread
  public static @NotNull List<@NotNull String> parsePyPIListFromWeb(@NotNull String url) throws IOException {
    LOG.info("Fetching index of all packages available on " + url);
    RequestBuilder builder = HttpRequests.request(url).userAgent(getUserAgent());

    PyPackageRepositories service = ApplicationManager.getApplication().getService(PyPackageRepositories.class);
    service.getRepositories().stream()
      .filter(repo -> url.equals(repo.getRepositoryUrl()))
      .findFirst()
      .ifPresent(repository -> PyPackageRepositoryUtil.withBasicAuthorization(builder, repository));

    return builder.connect(request -> {
      final List<String> packages = new ArrayList<>();
      final Reader reader = request.getReader();
      new ParserDelegator().parse(reader, new HTMLEditorKit.ParserCallback() {
        HTML.Tag myTag;

        @Override
        public void handleStartTag(@NotNull HTML.Tag tag, @NotNull MutableAttributeSet set, int i) {
          myTag = tag;
        }

        @Override
        public void handleText(char @NotNull [] data, int pos) {
          if (myTag != null && "a".equals(myTag.toString())) {
            String packageName = String.valueOf(data);
            if (packageName.endsWith("/")) {
              packageName = packageName.substring(0, packageName.indexOf("/"));
            }
            packages.add(packageName);
          }
        }

        @Override
        public void handleEndTag(@NotNull HTML.Tag t, int pos) {
          myTag = null;
        }
      }, true);
      return packages;
    });
  }

  public void loadPackages() throws IOException {
    // This lock is solely to prevent multiple threads from updating
    // the mammoth cache of PyPI packages simultaneously.
    synchronized (myPyPIPackageCacheUpdateLock) {
      final PyPIPackageCache cache = PyPIPackageCache.getInstance();
      if (cache.getPackageNames().isEmpty()) {
        updatePyPICache();
      }
    }
  }

  /**
   * @see PyPIPackageCache#containsPackage(String)
   */
  public boolean isInPyPI(@NotNull String packageName) {
    if (packageName.isEmpty()) return false;
    return PyPIPackageCache.getInstance().containsPackage(packageName);
  }

  @SuppressWarnings("FieldMayBeFinal")
  public static final class PackageDetails {
    public static final class Info {
      // We have to explicitly name each of the fields instead of just using
      // GsonBuilder#setFieldNamingStrategy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES),
      // since otherwise GSON wouldn't be able to deserialize server responses
      // in the professional edition of PyCharm where the names of private fields
      // are obfuscated.
      @SerializedName("version")
      private String version = "";
      @SerializedName("author")
      private String author = "";
      @SerializedName("author_email")
      private String authorEmail = "";
      @SerializedName("home_page")
      private String homePage = "";
      @SerializedName("summary")
      private String summary = "";
      @SerializedName("description")
      private String description = "";
      @SerializedName("description_content_type")
      private String descriptionContentType = "";
      @SerializedName("project_urls")
      private Map<String, String> projectUrls = Collections.emptyMap();

      public @NotNull String getVersion() {
        return StringUtil.notNullize(version);
      }

      public @NotNull String getAuthor() {
        return StringUtil.notNullize(author);
      }

      public @NotNull String getAuthorEmail() {
        return StringUtil.notNullize(authorEmail);
      }

      public @NotNull String getHomePage() {
        return StringUtil.notNullize(homePage);
      }

      public @NotNull String getSummary() {
        return StringUtil.notNullize(summary);
      }

      public @NotNull String getDescription() {
        return StringUtil.notNullize(description);
      }

      public @NotNull String getDescriptionContentType() {
        return StringUtil.notNullize(descriptionContentType);
      }

      public @NotNull Map<String, String> getProjectUrls() {
        return ContainerUtil.notNullize(projectUrls);
      }
    }

    @SerializedName("info")
    private Info info = new Info();
    @SerializedName("releases")
    private Map<String, Object> releases = Collections.emptyMap();

    public @NotNull Info getInfo() {
      return info;
    }

    public @NotNull List<String> getReleases() {
      return EntryStream.of(releases).filterValues(PackageDetails::isNotBrokenRelease).keys().toList();
    }

    private static boolean isNotBrokenRelease(Object o) {
      return !(o instanceof List) || !((List<?>)o).isEmpty();
    }
  }
}
