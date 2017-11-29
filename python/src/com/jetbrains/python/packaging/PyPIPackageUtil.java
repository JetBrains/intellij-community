/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.packaging;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.CatchingConsumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.webcore.packaging.PackageVersionComparator;
import com.intellij.webcore.packaging.RepoPackage;
import com.jetbrains.python.PythonHelpersLocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PyPIPackageUtil {
  private static final Logger LOG = Logger.getInstance(PyPIPackageUtil.class);
  private static final Gson GSON = new GsonBuilder().create();
  
  private static final String PYPI_HOST = "https://pypi.python.org";
  public static final String PYPI_URL = PYPI_HOST + "/pypi";
  public static final String PYPI_LIST_URL = PYPI_HOST + "/simple";

  /**
   * Contains mapping "importable top-level package" -> "package names on PyPI".
   */
  public static final ImmutableMap<String, List<String>> PACKAGES_TOPLEVEL = loadPackageAliases();

  public static final PyPIPackageUtil INSTANCE = new PyPIPackageUtil();

  /**
   * Contains cached versions of packages from additional repositories.
   *
   * @see #getPackageVersionsFromAdditionalRepositories(String)
   */
  private final LoadingCache<String, List<String>> myAdditionalPackagesReleases = CacheBuilder.newBuilder().build(
    new CacheLoader<String, List<String>>() {
      @Override
      public List<String> load(@NotNull String key) throws Exception {
        LOG.debug("Searching for versions of package '" + key + "' in additional repositories");
        final List<String> repositories = PyPackageService.getInstance().additionalRepositories;
        for (String repository : repositories) {
          final List<String> versions = parsePackageVersionsFromArchives(composeSimpleUrl(key, repository));
          if (!versions.isEmpty()) {
            LOG.debug("Found versions " + versions + " in " + repository);
            return Collections.unmodifiableList(versions);
          }
        }
        return Collections.emptyList();
      }
    });

  /**
   * Contains cached packages taken from additional repositories.
   * 
   * @see #getAdditionalPackages() 
   */
  private volatile Set<RepoPackage> myAdditionalPackages = null;

  /**
   * Contains cached package information retrieved through PyPI's JSON API.
   *
   * @see #refreshAndGetPackageDetailsFromPyPI(String, boolean)
   */
  private final LoadingCache<String, PackageDetails> myPackageToDetails = CacheBuilder.newBuilder().build(
    new CacheLoader<String, PackageDetails>() {
      @Override
      public PackageDetails load(@NotNull String key) throws Exception {
        LOG.debug("Fetching details for the package '" + key + "' on PyPI");
        return HttpRequests.request(PYPI_URL + "/" + key + "/json")
          .userAgent(getUserAgent())
          .connect(request -> GSON.fromJson(request.getReader(), PackageDetails.class));
      }
    });
  
  /**
   * Lowercased package names for fast check that some package is available in PyPI.
   * TODO find the way to get rid of it, it's not a good idea to store 85k+ entries in memory twice
   */
  @Nullable private volatile Set<String> myPackageNames = null;


  /**
   * Prevents simultaneous updates of {@link PyPackageService#PY_PACKAGES}
   * because the corresponding response contains tons of data and multiple
   * queries at the same time can cause memory issues. 
   */
  private final Object myPyPIPackageCacheUpdateLock = new Object();
  
  /**
   * Value for "User Agent" HTTP header in form: PyCharm/2016.2 EAP
   */
  @NotNull
  private static String getUserAgent() {
    return ApplicationNamesInfo.getInstance().getProductName() + "/" + ApplicationInfo.getInstance().getFullVersion();
  }

  @NotNull
  private static ImmutableMap<String, List<String>> loadPackageAliases() {
    final ImmutableMap.Builder<String, List<String>> builder = ImmutableMap.builder();
    try {
      Files
        .lines(Paths.get(PythonHelpersLocator.getHelperPath("/tools/packages")))
        .forEach(
          line -> {
            final List<String> split = StringUtil.split(line, " ");
            builder.put(split.get(0), new SmartList<>(ContainerUtil.subList(split, 1)));
          }
        );
    }
    catch (IOException e) {
      LOG.error("Cannot find \"packages\". " + e.getMessage());
    }
    return builder.build(); 
  }

  @NotNull
  private static Pair<String, String> splitNameVersion(@NotNull String pyPackage) {
    final int dashInd = pyPackage.lastIndexOf("-");
    if (dashInd >= 0 && dashInd+1 < pyPackage.length()) {
      final String name = pyPackage.substring(0, dashInd);
      final String version = pyPackage.substring(dashInd+1);
      if (StringUtil.containsAlphaCharacters(version)) {
        return Pair.create(pyPackage, null);
      }
      return Pair.create(name, version);
    }
    return Pair.create(pyPackage, null);
  }

  public static boolean isPyPIRepository(@Nullable String repository) {
    return repository != null && repository.startsWith(PYPI_HOST);
  }

  @NotNull
  public Set<RepoPackage> getAdditionalPackages() throws IOException {
    if (myAdditionalPackages == null) {
      final Set<RepoPackage> packages = new TreeSet<>();
      for (String url : PyPackageService.getInstance().additionalRepositories) {
        packages.addAll(getPackagesFromAdditionalRepository(url));
      }
      myAdditionalPackages = packages;
    }
    return Collections.unmodifiableSet(myAdditionalPackages);
  }

  @NotNull
  private static List<RepoPackage> getPackagesFromAdditionalRepository(@NotNull String url) throws IOException {
    final List<RepoPackage> result = new ArrayList<>();
    final boolean simpleIndex = url.endsWith("simple/");
    final List<String> packagesList = parsePyPIListFromWeb(url, simpleIndex);

    for (String pyPackage : packagesList) {
      if (simpleIndex) {
        final Pair<String, String> nameVersion = splitNameVersion(StringUtil.trimTrailing(pyPackage, '/'));
        result.add(new RepoPackage(nameVersion.getFirst(), url, nameVersion.getSecond()));
      }
      else {
        try {
          final Pattern repositoryPattern = Pattern.compile(url + "([^/]*)/([^/]*)$");
          final Matcher matcher = repositoryPattern.matcher(URLDecoder.decode(pyPackage, "UTF-8"));
          if (matcher.find()) {
            final String packageName = matcher.group(1);
            final String packageVersion = matcher.group(2);
            if (!packageName.contains(" ")) {
              result.add(new RepoPackage(packageName, url, packageVersion));
            }
          }
        }
        catch (UnsupportedEncodingException e) {
          LOG.warn(e.getMessage());
        }
      }
    }
    return result;
  }

  public void clearPackagesCache() {
    PyPackageService.getInstance().PY_PACKAGES.clear();
    myAdditionalPackages = null;
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

  @NotNull
  private PackageDetails refreshAndGetPackageDetailsFromPyPI(@NotNull String packageName, boolean alwaysRefresh) throws IOException {
    if (alwaysRefresh) {
      myPackageToDetails.invalidate(packageName);
    }
    return getCachedValueOrRethrowIO(myPackageToDetails, packageName);
  }

  public void usePackageReleases(@NotNull String packageName, @NotNull CatchingConsumer<List<String>, Exception> callback) {
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
  @NotNull
  private List<String> getPackageVersionsFromPyPI(@NotNull String packageName, 
                                                  boolean force) throws IOException {
    final PackageDetails details = refreshAndGetPackageDetailsFromPyPI(packageName, force);
    final List<String> result = details.getReleases();
    result.sort(PackageVersionComparator.VERSION_COMPARATOR.reversed());
    return Collections.unmodifiableList(result);
  }

  @Nullable
  private String getLatestPackageVersionFromPyPI(@NotNull String packageName) throws IOException {
    LOG.debug("Requesting the latest PyPI version for the package " + packageName);
    final List<String> versions = getPackageVersionsFromPyPI(packageName, true);
    final String latest = ContainerUtil.getFirstItem(versions);
    getPyPIPackages().put(packageName, StringUtil.notNullize(latest));
    return latest;
  }

  /**
   * Fetches available package versions by scrapping the page containing package archives. 
   * It's primarily used for additional repositories since, e.g. devpi doesn't provide another way to get this information.
   */
  @NotNull
  private List<String> getPackageVersionsFromAdditionalRepositories(@NotNull String packageName) throws IOException {
    return getCachedValueOrRethrowIO(myAdditionalPackagesReleases, packageName);
  }

  @NotNull
  private static <T> T getCachedValueOrRethrowIO(@NotNull LoadingCache<String, ? extends T> cache, @NotNull String key) throws IOException {
    try {
      return cache.get(key);
    }
    catch (ExecutionException e) {
      final Throwable cause = e.getCause();
      throw (cause instanceof IOException ? (IOException)cause: new IOException("Unexpected non-IO error", cause));
    }
  }

  @Nullable
  private String getLatestPackageVersionFromAdditionalRepositories(@NotNull String packageName) throws IOException {
    final List<String> versions = getPackageVersionsFromAdditionalRepositories(packageName);
    return ContainerUtil.getFirstItem(versions);
  }

  @Nullable
  public String fetchLatestPackageVersion(@NotNull String packageName) throws IOException {
    String version = getPyPIPackages().get(packageName);
    // Package is on PyPI but it's version is unknown
    if (version != null && version.isEmpty()) {
      version = getLatestPackageVersionFromPyPI(packageName);
    }
    if (!PyPackageService.getInstance().additionalRepositories.isEmpty()) {
      final String extraVersion = getLatestPackageVersionFromAdditionalRepositories(packageName);
      if (extraVersion != null) {
        version = extraVersion;
      }
    }
    return version;
  }

  @NotNull
  private static List<String> parsePackageVersionsFromArchives(@NotNull String archivesUrl) throws IOException {
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
        public void handleText(@NotNull char[] data, int pos) {
          if (myTag != null && "a".equals(myTag.toString())) {
            String packageVersion = String.valueOf(data);
            final String suffix = ".tar.gz";
            if (!packageVersion.endsWith(suffix)) return;
            packageVersion = StringUtil.trimEnd(packageVersion, suffix);
            versions.add(splitNameVersion(packageVersion).second);
          }
        }
      }, true);
      versions.sort(PackageVersionComparator.VERSION_COMPARATOR.reversed());
      return versions;
    });
  }

  @NotNull
  private static String composeSimpleUrl(@NonNls @NotNull String packageName, @NotNull String rep) {
    String suffix = "";
    final String repository = StringUtil.trimEnd(rep, "/");
    if (!repository.endsWith("+simple") && !repository.endsWith("/simple")) {
      suffix = "/+simple";
    }
    suffix += "/" + packageName;
    return repository + suffix;
  }

  public void updatePyPICache(@NotNull PyPackageService service) throws IOException {
    service.LAST_TIME_CHECKED = System.currentTimeMillis();

    service.PY_PACKAGES.clear();
    if (service.PYPI_REMOVED) return;
    parsePyPIList(parsePyPIListFromWeb(PYPI_LIST_URL, true), service);
  }

  private void parsePyPIList(@NotNull List<String> packages, @NotNull PyPackageService service) {
    myPackageNames = null;
    for (String pyPackage : packages) {
      try {
        final String packageName = URLDecoder.decode(pyPackage, "UTF-8");
        if (!packageName.contains(" ")) {
          service.PY_PACKAGES.put(packageName, "");
        }
      }
      catch (UnsupportedEncodingException e) {
        LOG.warn(e.getMessage());
      }
    }
  }

  @NotNull
  private static List<String> parsePyPIListFromWeb(@NotNull String url, boolean isSimpleIndex) throws IOException {
    LOG.debug("Fetching index of all packages available on " + url);
    return HttpRequests.request(url).userAgent(getUserAgent()).connect(request -> {
      final List<String> packages = new ArrayList<>();
      final Reader reader = request.getReader();
      new ParserDelegator().parse(reader, new HTMLEditorKit.ParserCallback() {
        boolean inTable = false;
        HTML.Tag myTag;

        @Override
        public void handleStartTag(@NotNull HTML.Tag tag, @NotNull MutableAttributeSet set, int i) {
          myTag = tag;
          if (!isSimpleIndex) {
            if ("table".equals(tag.toString())) {
              inTable = !inTable;
            }

            if (inTable && "a".equals(tag.toString())) {
              packages.add(String.valueOf(set.getAttribute(HTML.Attribute.HREF)));
            }
          }
        }

        @Override
        public void handleText(@NotNull char[] data, int pos) {
          if (isSimpleIndex) {
            if (myTag != null && "a".equals(myTag.toString())) {
              packages.add(String.valueOf(data));
            }
          }
        }

        @Override
        public void handleEndTag(@NotNull HTML.Tag tag, int i) {
          if (!isSimpleIndex) {
            if ("table".equals(tag.toString())) {
              inTable = !inTable;
            }
          }
        }
      }, true);
      return packages;
    });
  }

  @NotNull
  public Collection<String> getPackageNames() {
    final Map<String, String> pyPIPackages = getPyPIPackages();
    final ArrayList<String> list = Lists.newArrayList(pyPIPackages.keySet());
    Collections.sort(list);
    return list;
  }

  @NotNull
  public Map<String, String> loadAndGetPackages() throws IOException {
    // The map returned by getPyPIPackages() is already thread-safe;
    // this lock is solely to prevent multiple threads from updating
    // the mammoth cache of PyPI packages simultaneously.
    synchronized (myPyPIPackageCacheUpdateLock) {
      if (getPyPIPackages().isEmpty()) {
        updatePyPICache(PyPackageService.getInstance());
      }
      return getPyPIPackages();
    }
  }

  @NotNull
  public static Map<String, String> getPyPIPackages() {
    return PyPackageService.getInstance().PY_PACKAGES;
  }

  public boolean isInPyPI(@NotNull String packageName) {
    if (myPackageNames == null) {
      myPackageNames = getPyPIPackages().keySet().stream().map(name -> name.toLowerCase(Locale.ENGLISH)).collect(Collectors.toSet());
    }
    return myPackageNames != null && myPackageNames.contains(packageName.toLowerCase(Locale.ENGLISH));
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

      
      @NotNull
      public String getVersion() {
        return StringUtil.notNullize(version);
      }

      @NotNull
      public String getAuthor() {
        return StringUtil.notNullize(author);
      }

      @NotNull
      public String getAuthorEmail() {
        return StringUtil.notNullize(authorEmail);
      }

      @NotNull
      public String getHomePage() {
        return StringUtil.notNullize(homePage);
      }

      @NotNull
      public String getSummary() {
        return StringUtil.notNullize(summary);
      }
    }

    @SerializedName("info")
    private Info info = new Info();
    @SerializedName("releases")
    private Map<String, Object> releases = Collections.emptyMap();

    @NotNull
    public Info getInfo() {
      return info;
    }

    @NotNull
    public List<String> getReleases() {
      return new ArrayList<>(releases.keySet());
    }
  }
}
