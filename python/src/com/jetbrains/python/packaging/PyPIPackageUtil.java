package com.jetbrains.python.packaging;

import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
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
  public static @NonNls String PYPI_URL = "http://pypi.python.org/pypi";
  public static @NonNls String PYPI_LIST_URL = "http://pypi.python.org/pypi?%3Aaction=index";
  private XmlRpcClient myXmlRpcClient;
  public static PyPIPackageUtil INSTANCE = new PyPIPackageUtil();
  private Map<String, Hashtable> packageToDetails = new HashMap<String, Hashtable>();
  private Map<String, List<String>> packageToReleases = new HashMap<String, List<String>>();
  private List<String> errorMessages;
  private Pattern PYPI_PATTERN = Pattern.compile("/pypi/([^/]*)/(.*)");
  private Set<ComparablePair> myAdditionalPackageNames;

  public Set<String> getPackageNames(final String url) throws IOException {
    final TreeSet<String> names = new TreeSet<String>();
    HTMLEditorKit.ParserCallback callback =
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
      URL repositoryUrl = new URL(url);
      InputStream is = repositoryUrl.openStream();
      Reader reader = new InputStreamReader(is);
      try{
        new ParserDelegator().parse(reader, callback, true);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
      finally {
        reader.close();
      }
    }
    catch (MalformedURLException e) {
      LOG.warn(e);
      errorMessages.add("Package list from "+ url + " was not loaded. " + e.getMessage());
    }

    return names;
  }

  public Set<ComparablePair> getAdditionalPackageNames(Project project) {
    if (myAdditionalPackageNames == null) {
      myAdditionalPackageNames = new TreeSet<ComparablePair>();
      for (String url : PyPackageService.getInstance(project).additionalRepositories) {
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

  public XmlRpcClient getXmlRpcClient() {
    return myXmlRpcClient;
  }

  public void addPackageDetails(@NonNls String packageName, Hashtable details) {
    packageToDetails.put(packageName, details);
  }
  @Nullable
  public Hashtable getPackageDetails(@NonNls String packageName) {
    if (packageToDetails.containsKey(packageName)) return packageToDetails.get(packageName);
    return null;
  }

  @NotNull
  public List<String> getPackageReleases(@NonNls String packageName) {
    if (packageToReleases.containsKey(packageName)) return packageToReleases.get(packageName);
    Vector<String> params = new Vector<String>();
    params.add(packageName);
    try {
      List<String> releases = (List<String>)myXmlRpcClient.execute("package_releases", params);
      packageToReleases.put(packageName, releases);
      return releases;

    }
    catch (Exception e) {
      LOG.warn(e);
    }
    return Collections.emptyList();
  }

  private PyPIPackageUtil() {
    try {
      DefaultXmlRpcTransportFactory factory = new DefaultXmlRpcTransportFactory(new URL(PYPI_URL));
      factory.setProperty("timeout", 1000);
      myXmlRpcClient = new XmlRpcClient(new URL(PYPI_URL), factory);
      errorMessages = new ArrayList<String>();
    }
    catch (MalformedURLException e) {
      LOG.warn(e);
    }
  }


  public void updatePyPICache(Project project) throws IOException {
    parsePyPIList(getPyPIListFromWeb(), project);
  }
  
  public void parsePyPIList(List<String> packages, Project project) {
    for (String pyPackage : packages) {
      try {
        Matcher matcher = PYPI_PATTERN.matcher(URLDecoder.decode(pyPackage, "UTF-8"));
        if (matcher.find()) {
          String packageName = matcher.group(1);
          String packageVersion = matcher.group(2);
          if (!packageName.contains(" "))
            PyPackageService.getInstance(project).PY_PACKAGES.put(packageName, packageVersion);
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
        e.printStackTrace();
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

  public Collection<String> getPackageNames(Project project) throws IOException {
    Map<String, String> pyPIPackages = getPyPIPackages(project);
    if (pyPIPackages.isEmpty()) {
      updatePyPICache(project);
      pyPIPackages = getPyPIPackages(project);
    }
    ArrayList<String> list = Lists.newArrayList(pyPIPackages.keySet());
    Collections.sort(list);
    return list;
  }

  public static Map<String, String> getPyPIPackages(Project project) {
    return PyPackageService.getInstance(project).PY_PACKAGES;
  }
  
  
  public static void showError(String title, String message, Project project) {
    final DialogBuilder builder = new DialogBuilder(project);
    builder.setTitle(title);
    final JTextArea textArea = new JTextArea();
    textArea.setEditable(false);
    textArea.setText(message);
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
