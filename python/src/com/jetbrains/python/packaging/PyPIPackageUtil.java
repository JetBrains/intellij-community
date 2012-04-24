package com.jetbrains.python.packaging;

import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import org.apache.xmlrpc.AsyncCallback;
import org.apache.xmlrpc.DefaultXmlRpcTransportFactory;
import org.apache.xmlrpc.XmlRpcClient;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.awt.*;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: catherine
 */
@SuppressWarnings("UseOfObsoleteCollectionType")
public class PyPIPackageUtil {
  public static Logger LOG = Logger.getInstance(PyPIPackageUtil.class.getName());
  @NonNls public static String PYPI_URL = "http://pypi.python.org/pypi";
  @NonNls public static String PYPI_LIST_URL = "http://pypi.python.org/pypi?%3Aaction=index";
  private XmlRpcClient myXmlRpcClient;
  public static PyPIPackageUtil INSTANCE = new PyPIPackageUtil();
  private Map<String, Hashtable> packageToDetails = new HashMap<String, Hashtable>();
  private Map<String, List<String>> packageToReleases = new HashMap<String, List<String>>();
  private Pattern PYPI_PATTERN = Pattern.compile("/pypi/([^/]*)/(.*)");
  private Set<ComparablePair> myAdditionalPackageNames;
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

  public Set<ComparablePair> getAdditionalPackageNames() {
    if (myAdditionalPackageNames == null) {
      myAdditionalPackageNames = new TreeSet<ComparablePair>();
      for (String url : PyPackageService.getInstance().additionalRepositories) {
        try {
          for (String pyPackage : getPackageNames(url)) {
            if (!pyPackage.contains(" "))
              myAdditionalPackageNames.add(new ComparablePair(pyPackage, url));
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
      DefaultXmlRpcTransportFactory factory = new DefaultXmlRpcTransportFactory(new URL(PYPI_URL));
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

    try {
      URL repositoryUrl = new URL(PYPI_LIST_URL);
      InputStream is = repositoryUrl.openStream();
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
    catch (MalformedURLException e) {
      LOG.warn(e);
    }
    return packages;
  }

  public Collection<String> getPackageNames() throws IOException {
    Map<String, String> pyPIPackages = getPyPIPackages();
    if (pyPIPackages.isEmpty()) {
      updatePyPICache(PyPackageService.getInstance());
      pyPIPackages = getPyPIPackages();
    }
    ArrayList<String> list = Lists.newArrayList(pyPIPackages.keySet());
    Collections.sort(list);
    return list;
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

  public static void showError(@NotNull Project project, @NotNull String title, @NotNull String description) {
    final DialogBuilder builder = new DialogBuilder(project);
    builder.setTitle(title);
    final JTextArea textArea = new JTextArea();
    textArea.setEditable(false);
    textArea.setText(description);
    textArea.setWrapStyleWord(false);
    textArea.setLineWrap(true);
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(textArea);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    final JPanel panel = new JPanel(new BorderLayout(10, 0));
    panel.setPreferredSize(new Dimension(600, 400));
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.add(new JBLabel("Details:", Messages.getErrorIcon(), SwingConstants.LEFT), BorderLayout.NORTH);
    builder.setCenterPanel(panel);
    builder.setButtonsAlignment(SwingConstants.CENTER);
    builder.addOkAction();
    builder.show();
  }
}
