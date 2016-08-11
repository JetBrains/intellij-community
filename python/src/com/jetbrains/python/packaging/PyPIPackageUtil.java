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
package com.jetbrains.python.packaging;

import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.CatchingConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.webcore.packaging.PackageVersionComparator;
import com.intellij.webcore.packaging.RepoPackage;
import com.jetbrains.python.PythonHelpersLocator;
import org.apache.xmlrpc.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * User: catherine
 */
@SuppressWarnings("UseOfObsoleteCollectionType")
public class PyPIPackageUtil {
  private static final Logger LOG = Logger.getInstance(PyPIPackageUtil.class);
  
  private static final String PYPI_HOST = "https://pypi.python.org";
  public static final String PYPI_URL = PYPI_HOST + "/pypi";
  public static final String PYPI_LIST_URL = PYPI_HOST + "/simple";

  public static final Map<String, String> PACKAGES_TOPLEVEL = new HashMap<>();

  private static final Map<String, List<String>> ourPackageToReleases = new HashMap<>();
  private static final Set<RepoPackage> ourAdditionalPackageNames = new TreeSet<>();

  public static final PyPIPackageUtil INSTANCE = new PyPIPackageUtil();
  private XmlRpcClient myXmlRpcClient;
  private final Map<String, Hashtable> packageToDetails = new HashMap<>();
  @Nullable private volatile Set<String> myPackageNames = null;


  static {
    try {
      fillPackages();
    }
    catch (IOException e) {
      LOG.error("Cannot find \"packages\". " + e.getMessage());
    }
  }

  /**
   * Prevents simultaneous updates of {@link PyPackageService#PY_PACKAGES}
   * because the corresponding response contains tons of data and multiple
   * queries at the same time can cause memory issues. 
   */
  private final Object myPyPIPackageCacheUpdateLock = new Object();
  

  private PyPIPackageUtil() {
    try {
      final DefaultXmlRpcTransportFactory factory = new PyPIXmlRpcTransportFactory(new URL(PYPI_URL));
      factory.setProperty("timeout", 1000);
      myXmlRpcClient = new XmlRpcClient(new URL(PYPI_URL), factory);
    }
    catch (MalformedURLException e) {
      LOG.warn(e);
    }
  }

  /**
   * Value for "User Agent" HTTP header in form: PyCharm/2016.2 EAP
   */
  @NotNull
  private static String getUserAgent() {
    return ApplicationNamesInfo.getInstance().getProductName() + "/" + ApplicationInfo.getInstance().getFullVersion();
  }

  private static void fillPackages() throws IOException {
    try (FileReader reader = new FileReader(PythonHelpersLocator.getHelperPath("/tools/packages"))) {
      final String text = FileUtil.loadTextAndClose(reader);
      final List<String> lines = StringUtil.split(text, "\n");
      for (String line : lines) {
        final List<String> split = StringUtil.split(line, " ");
        PACKAGES_TOPLEVEL.put(split.get(0), split.get(1));
      }
    }
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

  private static void fillAdditionalPackages(@NotNull String url) throws IOException {
    final boolean simpleIndex = url.endsWith("simple/");
    final List<String> packagesList = parsePyPIListFromWeb(url, simpleIndex);

    for (String pyPackage : packagesList) {
      if (simpleIndex) {
        final Pair<String, String> nameVersion = splitNameVersion(StringUtil.trimTrailing(pyPackage, '/'));
        ourAdditionalPackageNames.add(new RepoPackage(nameVersion.getFirst(), url, nameVersion.getSecond()));
      }
      else {
        try {
          final Pattern repositoryPattern = Pattern.compile(url + "([^/]*)/([^/]*)$");
          final Matcher matcher = repositoryPattern.matcher(URLDecoder.decode(pyPackage, "UTF-8"));
          if (matcher.find()) {
            final String packageName = matcher.group(1);
            final String packageVersion = matcher.group(2);
            if (!packageName.contains(" "))
              ourAdditionalPackageNames.add(new RepoPackage(packageName, url, packageVersion));
          }
        }
        catch (UnsupportedEncodingException e) {
          LOG.warn(e.getMessage());
        }
      }
    }
  }

  @NotNull
  public Set<RepoPackage> getAdditionalPackageNames() throws IOException {
    if (ourAdditionalPackageNames.isEmpty()) {
      for (String url : PyPackageService.getInstance().additionalRepositories) {
        fillAdditionalPackages(url);
      }
    }
    return ourAdditionalPackageNames;
  }

  public void clearPackagesCache() {
    PyPackageService.getInstance().PY_PACKAGES.clear();
    ourAdditionalPackageNames.clear();
  }

  public void addPackageDetails(@NonNls String packageName, Hashtable details) {
    packageToDetails.put(packageName, details);
  }

  @Nullable
  private Hashtable getPackageDetails(@NotNull String packageName) {
    return packageToDetails.get(packageName);
  }

  public void fillPackageDetails(@NotNull String packageName, @NotNull CatchingConsumer<Hashtable, Exception> callback) {
    final Hashtable details = getPackageDetails(packageName);
    if (details != null) {
      callback.consume(details);
      return;
    }
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      final Vector<String> params = new Vector<>();
      params.add(packageName);
      try {
        final String version = fetchLatestPackageVersion(packageName);
        if (version != null) {
          params.add(version);
          final Object result = myXmlRpcClient.execute("release_data", params);
          if (result != null) {
            callback.consume((Hashtable)result);
          }
        }
      }
      catch (Exception e) {
        callback.consume(e);
      }
    });
  }

  public void addPackageReleases(@NotNull String packageName, @NotNull List<String> releases) {
    ourPackageToReleases.put(packageName, releases);
  }

  public void usePackageReleases(@NotNull String packageName, @NotNull CatchingConsumer<List<String>, Exception> callback) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        final List<String> releases = getPackageVersionsFromAdditionalRepositories(packageName);
        if (releases == null) {
          final Vector<String> params = new Vector<>();
          params.add(packageName);
          params.add("show_hidden=True");
          final Object result = myXmlRpcClient.execute("package_releases", params);
          if (result != null) {
            //noinspection unchecked
            callback.consume((List<String>)result);
          }
        }
        else {
          callback.consume(releases);
        }
      }
      catch (Exception e) {
        callback.consume(e);
      }
    });
  }

  @Nullable
  private static List<String> getPackageVersionsFromAdditionalRepositories(@NotNull @NonNls String packageName) throws IOException {
    if (ourPackageToReleases.containsKey(packageName)) {
      return ourPackageToReleases.get(packageName);
    }
    final List<String> repositories = PyPackageService.getInstance().additionalRepositories;
    for (String repository : repositories) {
      final List<String> versions = getVersionsFromRepository(packageName, repository);
      if (!versions.isEmpty()) {
        ourPackageToReleases.put(packageName, versions);
        return versions;
      }
    }
    return null;
  }

  @Nullable
  private static String getLatestPackageVersionFromAdditionalRepositories(@NotNull String packageName) throws IOException {
    final List<String> versions = getPackageVersionsFromAdditionalRepositories(packageName);
    return ContainerUtil.getFirstItem(versions);
  }

  @NotNull
  private static List<String> getPackageVersionsFromPyPI(@NotNull String packageName) throws IOException {
    return getVersionsFromRepository(packageName, PYPI_LIST_URL);
  }

  @Nullable
  private static String getLatestPackageVersionFromPyPI(@NotNull String packageName) throws IOException {
    LOG.debug("Requesting the latest PyPI version for the package " + packageName);
    final List<String> versions = getPackageVersionsFromPyPI(packageName);
    final String latest = ContainerUtil.getFirstItem(versions);
    getPyPIPackages().put(packageName, StringUtil.notNullize(latest));
    return latest;
  }

  @NotNull
  private static List<String> getVersionsFromRepository(@NotNull String packageName, @NotNull String repository) throws IOException {
    final String packageArchivesSimpleUrl = composeSimpleUrl(packageName, repository);
    return parsePackageVersionsFromArchives(packageArchivesSimpleUrl);
  }

  @Nullable
  public static String fetchLatestPackageVersion(@NotNull String packageName) throws IOException {
    String version = getPyPIPackages().get(packageName);
    // Package is on PyPI but it's version is unknown
    if (version != null && version.isEmpty()) {
      version = getLatestPackageVersionFromPyPI(packageName);
    }
    final String extraVersion = getLatestPackageVersionFromAdditionalRepositories(packageName);
    if (extraVersion != null) {
      version = extraVersion;
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
    Map<String, String> pyPIPackages = getPyPIPackages();
    synchronized (myPyPIPackageCacheUpdateLock) {
      if (pyPIPackages.isEmpty()) {
        updatePyPICache(PyPackageService.getInstance());
        pyPIPackages = getPyPIPackages();
      }
    }
    return pyPIPackages;
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

  private static class PyPIXmlRpcTransport extends DefaultXmlRpcTransport {
    public PyPIXmlRpcTransport(URL url) {
      super(url);
    }

    @Override
    public InputStream sendXmlRpc(@NotNull byte[] request) throws IOException {
      // Create a trust manager that does not validate certificate for this connection
      final TrustManager[] trustAllCerts = new TrustManager[]{new PyPITrustManager()};

      try {
        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());

        final HttpConfigurable settings = HttpConfigurable.getInstance();
        con = settings.openConnection(PYPI_HOST + "/pypi?%3Aaction=index");
        if (con instanceof HttpsURLConnection) {
          ((HttpsURLConnection)con).setSSLSocketFactory(sslContext.getSocketFactory());
        }
        con.setDoInput(true);
        con.setDoOutput(true);
        con.setUseCaches(false);
        con.setAllowUserInteraction(false);
        con.setRequestProperty("Content-Length",
                               Integer.toString(request.length));
        con.setRequestProperty("Content-Type", "text/xml");
        if (auth != null)
        {
          con.setRequestProperty("Authorization", "Basic " + auth);
        }
        final OutputStream out = con.getOutputStream();
        out.write(request);
        out.flush();
        out.close();
        return con.getInputStream();
      }
      catch (@NotNull NoSuchAlgorithmException | KeyManagementException e) {
        LOG.warn(e.getMessage());
      }
      return super.sendXmlRpc(request);
    }
  }

  private static class PyPIXmlRpcTransportFactory extends DefaultXmlRpcTransportFactory {
    public PyPIXmlRpcTransportFactory(URL url) {
      super(url);
    }

    @NotNull
    @Override
    public XmlRpcTransport createTransport() throws XmlRpcClientException {
      return new PyPIXmlRpcTransport(url);
    }
  }

  private static class PyPITrustManager implements X509TrustManager {
    @Override
    public X509Certificate[] getAcceptedIssuers(){return null;}
    @Override
    public void checkClientTrusted(X509Certificate[] certs, String authType){}
    @Override
    public void checkServerTrusted(X509Certificate[] certs, String authType){}
  }
}
