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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.net.HttpConfigurable;
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

/**
 * User: catherine
 */
@SuppressWarnings("UseOfObsoleteCollectionType")
public class PyPIPackageUtil {
  public static final Logger LOG = Logger.getInstance(PyPIPackageUtil.class.getName());
  public static final String PYPI_HOST = "https://pypi.python.org";
  @NonNls public static final String PYPI_URL = PYPI_HOST + "/pypi";
  @NonNls public static final String PYPI_LIST_URL = PYPI_HOST + "/pypi?%3Aaction=index";

  public static Map<String, String> PACKAGES_TOPLEVEL = new HashMap<String, String>();

  public static final PyPIPackageUtil INSTANCE = new PyPIPackageUtil();

  private XmlRpcClient myXmlRpcClient;
  private Map<String, Hashtable> packageToDetails = new HashMap<String, Hashtable>();
  private static Map<String, List<String>> packageToReleases = new HashMap<String, List<String>>();
  private Pattern PYPI_PATTERN = Pattern.compile("/pypi/([^/]*)/(.*)");
  private static Set<RepoPackage> ourAdditionalPackageNames = new TreeSet<RepoPackage>();
  @Nullable private volatile Set<String> myPackageNames = null;


  static {
    try {
      fillPackages();
    }
    catch (IOException e) {
      LOG.error("Cannot find \"packages\". " + e.getMessage());
    }
  }

  private static void fillPackages() throws IOException {
    FileReader reader = new FileReader(PythonHelpersLocator.getHelperPath("/tools/packages"));
    try {
      final String text = FileUtil.loadTextAndClose(reader);
      final List<String> lines = StringUtil.split(text, "\n");
      for (String line : lines) {
        final List<String> split = StringUtil.split(line, " ");
        PACKAGES_TOPLEVEL.put(split.get(0), split.get(1));
      }
    }
    finally {
      reader.close();
    }
  }

  @NotNull
  private static Pair<String, String> splitNameVersion(@NotNull final String pyPackage) {
    int dashInd = pyPackage.lastIndexOf("-");
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

  public static boolean isPyPIRepository(@Nullable final String repository) {
    return repository != null && repository.startsWith(PYPI_HOST);
  }

  public void fillAdditionalPackages(@NotNull final String url) {
    final boolean simpleIndex = url.endsWith("simple/");
    final List<String> packagesList = parsePyPIListFromWeb(url, simpleIndex);

    for (String pyPackage : packagesList) {
      if (simpleIndex) {
        final Pair<String, String> nameVersion = splitNameVersion(pyPackage);
        ourAdditionalPackageNames.add(new RepoPackage(nameVersion.getFirst(), url, nameVersion.getSecond()));
      }
      else {
        try {
          Pattern repositoryPattern = Pattern.compile(url + "([^/]*)/([^/]*)$");
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

  public Set<RepoPackage> getAdditionalPackageNames() {
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
  public Hashtable getPackageDetails(@NonNls String packageName) {
    if (packageToDetails.containsKey(packageName)) return packageToDetails.get(packageName);
    return null;
  }

  public void fillPackageDetails(@NonNls String packageName, final AsyncCallback callback) {
    final Hashtable details = getPackageDetails(packageName);
    if (details == null) {
      final Vector<String> params = new Vector<String>();
      params.add(packageName);
      try {
        String version = getPyPIPackages().get(packageName);
        if (version == null) {
          final List<String> releases = getPackageReleases(packageName);
          if (releases != null && !releases.isEmpty()) {
            version = releases.get(0);
          }
        }
        params.add(version);
        myXmlRpcClient.executeAsync("release_data", params, callback);
      }
      catch (Exception ignored) {
        LOG.info(ignored);
      }
    }
    else
      callback.handleResult(details, null, "");
  }

  public void addPackageReleases(@NotNull final String packageName, @NotNull final List<String> releases) {
    packageToReleases.put(packageName, releases);
  }

  public void usePackageReleases(@NonNls String packageName, final AsyncCallback callback) {
    final List<String> releases = getPackageReleases(packageName);
    if (releases == null) {
      final Vector<String> params = new Vector<String>();
      params.add(packageName);
      params.add("show_hidden=True");
      myXmlRpcClient.executeAsync("package_releases", params, callback);
    }
    else {
      callback.handleResult(releases, null, "");
    }
  }

  @Nullable
  public static List<String> getPackageReleases(@NonNls String packageName) {
    if (packageToReleases.containsKey(packageName)) return packageToReleases.get(packageName);
    final List<String> repositories = PyPackageService.getInstance().additionalRepositories;
    if (!repositories.isEmpty()) {
      for (String repository : repositories) {
        repository = composeSimpleUrl(packageName, repository);

        final List<String> versions = parsePackageVersions(repository);
        if (!versions.isEmpty()) {
          packageToReleases.put(packageName, versions);
          return versions;
        }
      }
    }
    return null;
  }

  private static List<String> parsePackageVersions(@NotNull final String repository) {
    return HttpRequests.request(repository).connect(new HttpRequests.RequestProcessor<List<String>>() {
      @Override
      public List<String> process(@NotNull HttpRequests.Request request) throws IOException {
        final List<String> versions = new ArrayList<String>();
        Reader reader = request.getReader();
        new ParserDelegator().parse(reader, new HTMLEditorKit.ParserCallback() {
          HTML.Tag myTag;

          @Override
          public void handleStartTag(HTML.Tag tag, MutableAttributeSet set, int i) {
            myTag = tag;
          }

          @Override
          public void handleText(char[] data, int pos) {
            if (myTag != null && "a".equals(myTag.toString())) {
              String packageVersion = String.valueOf(data);
              final String suffix = ".tar.gz";
              if (!packageVersion.endsWith(suffix)) return;
              packageVersion = StringUtil.trimEnd(packageVersion, suffix);
              versions.add(splitNameVersion(packageVersion).second);
            }
          }

        }, true);
        return versions;
      }
    }, Collections.emptyList(), LOG);
  }

  @NotNull
  private static String composeSimpleUrl(@NonNls @NotNull final String packageName, @NotNull final String rep) {
    String suffix = "";
    final String repository = StringUtil.trimEnd(rep, "/");
    if (!repository.endsWith("+simple")) {
      suffix = "/+simple";
    }
    suffix += "/" + packageName;
    return repository + suffix;
  }

  private PyPIPackageUtil() {
    try {
      DefaultXmlRpcTransportFactory factory = new PyPIXmlRpcTransportFactory(new URL(PYPI_URL));
      factory.setProperty("timeout", 1000);
      myXmlRpcClient = new XmlRpcClient(new URL(PYPI_URL), factory);
    }
    catch (MalformedURLException e) {
      LOG.warn(e);
    }
  }

  public void updatePyPICache(final PyPackageService service) throws IOException {
    service.LAST_TIME_CHECKED = System.currentTimeMillis();

    service.PY_PACKAGES.clear();
    if (service.PYPI_REMOVED) return;
    parsePyPIList(parsePyPIListFromWeb(PYPI_LIST_URL, false), service);
  }

  public void parsePyPIList(final List<String> packages, final PyPackageService service) {
    myPackageNames = null;
    for (String pyPackage : packages) {
      try {
        final Matcher matcher = PYPI_PATTERN.matcher(URLDecoder.decode(pyPackage, "UTF-8"));
        if (matcher.find()) {
          final String packageName = matcher.group(1);
          final String packageVersion = matcher.group(2);
          if (!packageName.contains(" "))
            service.PY_PACKAGES.put(packageName, packageVersion);
        }
      }
      catch (UnsupportedEncodingException e) {
        LOG.warn(e.getMessage());
      }
    }
  }

  @NotNull
  public List<String> parsePyPIListFromWeb(@NotNull final String url, final boolean isSimpleIndex) {
    return HttpRequests.request(url).connect(new HttpRequests.RequestProcessor<List<String>>() {
      @Override
      public List<String> process(@NotNull HttpRequests.Request request) throws IOException {
        final List<String> packages = new ArrayList<String>();
        Reader reader = request.getReader();
        new ParserDelegator().parse(reader, new HTMLEditorKit.ParserCallback() {
          boolean inTable = false;
          HTML.Tag myTag;

          @Override
          public void handleStartTag(HTML.Tag tag, MutableAttributeSet set, int i) {
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
          public void handleText(char[] data, int pos) {
            if (isSimpleIndex) {
              if (myTag != null && "a".equals(myTag.toString())) {
                packages.add(String.valueOf(data));
              }
            }
          }

          @Override
          public void handleEndTag(HTML.Tag tag, int i) {
            if (!isSimpleIndex) {
              if ("table".equals(tag.toString())) {
                inTable = !inTable;
              }
            }
          }
        }, true);
        return packages;
      }
    }, Collections.emptyList(), LOG);
  }

  public Collection<String> getPackageNames() {
    Map<String, String> pyPIPackages = getPyPIPackages();
    ArrayList<String> list = Lists.newArrayList(pyPIPackages.keySet());
    Collections.sort(list);
    return list;
  }

  public Map<String, String> loadAndGetPackages() throws IOException {
    Map<String, String> pyPIPackages = getPyPIPackages();
    if (pyPIPackages.isEmpty()) {
      updatePyPICache(PyPackageService.getInstance());
      pyPIPackages = getPyPIPackages();
    }
    return pyPIPackages;
  }

  public static Map<String, String> getPyPIPackages() {
    return PyPackageService.getInstance().PY_PACKAGES;
  }

  public boolean isInPyPI(@NotNull String packageName) {
    if (myPackageNames == null) {
      final Set<String> names = new HashSet<String>();
      for (String name : getPyPIPackages().keySet()) {
        names.add(name.toLowerCase(Locale.ENGLISH));
      }
      myPackageNames = names;
    }
    return myPackageNames != null && myPackageNames.contains(packageName.toLowerCase(Locale.ENGLISH));
  }

  private static class PyPIXmlRpcTransport extends DefaultXmlRpcTransport {
    public PyPIXmlRpcTransport(URL url) {
      super(url);
    }

    @Override
    public InputStream sendXmlRpc(byte[] request) throws IOException {
      // Create a trust manager that does not validate certificate for this connection
      TrustManager[] trustAllCerts = new TrustManager[]{new PyPITrustManager()};

      try {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());

        final HttpConfigurable settings = HttpConfigurable.getInstance();
        con = settings.openConnection(PYPI_LIST_URL);
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
        OutputStream out = con.getOutputStream();
        out.write(request);
        out.flush();
        out.close();
        return con.getInputStream();
      }
      catch (NoSuchAlgorithmException e) {
        LOG.warn(e.getMessage());
      }
      catch (KeyManagementException e) {
        LOG.warn(e.getMessage());
      }
      return super.sendXmlRpc(request);
    }
  }

  private static class PyPIXmlRpcTransportFactory extends DefaultXmlRpcTransportFactory {
    public PyPIXmlRpcTransportFactory(URL url) {
      super(url);
    }

    @Override
    public XmlRpcTransport createTransport() throws XmlRpcClientException {
      return new PyPIXmlRpcTransport(url);
    }
  }

  private static class PyPITrustManager implements X509TrustManager {
    public X509Certificate[] getAcceptedIssuers(){return null;}
    public void checkClientTrusted(X509Certificate[] certs, String authType){}
    public void checkServerTrusted(X509Certificate[] certs, String authType){}
  }
}
