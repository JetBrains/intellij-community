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
package com.jetbrains.python.packaging;

import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.webcore.packaging.RepoPackage;
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
import java.net.URLConnection;
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
  public static Logger LOG = Logger.getInstance(PyPIPackageUtil.class.getName());
  @NonNls public static String PYPI_URL = "https://pypi.python.org/pypi";
  @NonNls public static String PYPI_LIST_URL = "https://pypi.python.org/pypi?%3Aaction=index";
  private XmlRpcClient myXmlRpcClient;
  public static PyPIPackageUtil INSTANCE = new PyPIPackageUtil();
  private Map<String, Hashtable> packageToDetails = new HashMap<String, Hashtable>();
  private Map<String, List<String>> packageToReleases = new HashMap<String, List<String>>();
  private Pattern PYPI_PATTERN = Pattern.compile("/pypi/([^/]*)/(.*)");
  private Set<RepoPackage> myAdditionalPackageNames;
  @Nullable private volatile Set<String> myPackageNames = null;

  public static Set<String> getPackageNames(final String url) throws IOException {
    final TreeSet<String> names = new TreeSet<String>();
    final HTMLEditorKit.ParserCallback callback =
        new HTMLEditorKit.ParserCallback() {
          HTML.Tag myTag;
          @Override
          public void handleStartTag(HTML.Tag tag,
                                     MutableAttributeSet set,
                                     int i) {
            myTag = tag;
          }

          public void handleText(char[] data, int pos) {
            if (myTag != null && "a".equals(myTag.toString())) {
              names.add(String.valueOf(data));
            }
          }
        };

    try {
      final URL repositoryUrl = new URL(url);
      final InputStream is = repositoryUrl.openStream();
      final Reader reader = new InputStreamReader(is);
      try{
        new ParserDelegator().parse(reader, callback, true);
      }
      catch (IOException e) {
        LOG.warn(e);
      }
      finally {
        reader.close();
      }
    }
    catch (MalformedURLException e) {
      LOG.warn(e);
    }

    return names;
  }

  public Set<RepoPackage> getAdditionalPackageNames() {
    if (myAdditionalPackageNames == null) {
      myAdditionalPackageNames = new TreeSet<RepoPackage>();
      for (String url : PyPackageService.getInstance().additionalRepositories) {
        try {
          for (String pyPackage : getPackageNames(url)) {
            if (!pyPackage.contains(" "))
              myAdditionalPackageNames.add(new RepoPackage(pyPackage, url));
          }
        }
        catch (IOException e) {
          LOG.warn(e);
        }
      }
    }
    return myAdditionalPackageNames;
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
        params.add(getPyPIPackages().get(packageName));
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
      myXmlRpcClient.executeAsync("package_releases", params, callback);
    }
    else {
      callback.handleResult(releases, null, "");
    }
  }

  @Nullable
  public List<String> getPackageReleases(@NonNls String packageName) {
    if (packageToReleases.containsKey(packageName)) return packageToReleases.get(packageName);
    return null;
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
    parsePyPIList(getPyPIListFromWeb(), service);
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

  @Nullable
  public List<String> getPyPIListFromWeb() throws IOException {
    final List<String> packages = new ArrayList<String>();
    HTMLEditorKit.ParserCallback callback =
        new HTMLEditorKit.ParserCallback() {
          HTML.Tag myTag;
          boolean inTable = false;
          @Override
          public void handleStartTag(HTML.Tag tag, MutableAttributeSet set, int i) {
            if ("table".equals(tag.toString()))
              inTable = !inTable;

            if (inTable && "a".equals(tag.toString())) {
              packages.add(String.valueOf(set.getAttribute(HTML.Attribute.HREF)));
            }
          }

          @Override
          public void handleEndTag(HTML.Tag tag, int i) {
            if ("table".equals(tag.toString()))
              inTable = !inTable;
          }
        };


    // Create a trust manager that does not validate certificate
    TrustManager[] trustAllCerts = new TrustManager[]{new PyPITrustManager()};

    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustAllCerts, new SecureRandom());

      final HttpConfigurable settings = HttpConfigurable.getInstance();
      final URLConnection connection = settings.openConnection(PYPI_LIST_URL);

      if (connection instanceof HttpsURLConnection) {
        ((HttpsURLConnection)connection).setSSLSocketFactory(sslContext.getSocketFactory());
      }
      InputStream is = connection.getInputStream();
      Reader reader = new InputStreamReader(is);
      try{
        new ParserDelegator().parse(reader, callback, true);
      }
      catch (IOException e) {
        LOG.warn(e);
      }
      finally {
        reader.close();
      }
    }
    catch (Exception e) {
      LOG.warn(e);
    }
    return packages;
  }

  public Collection<String> getPackageNames() throws IOException {
    Map<String, String> pyPIPackages = loadAndGetPackages();
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
        names.add(name.toLowerCase());
      }
      myPackageNames = names;
    }
    return myPackageNames != null && myPackageNames.contains(packageName.toLowerCase());
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
