package com.intellij.ide.plugins;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressStream;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Mar 28, 2003
 * Time: 12:56:26 AM
 * To change this template use Options | File Templates.
 */
public class RepositoryHelper {
  //private static final Logger LOG = com.intellij.openapi.diagnostic.Logger.getInstance("#com.intellij.ide.plugins.RepositoryHelper");
  public static final String REPOSITORY_HOST = "http://plugins.intellij.net";
  //public static final String REPOSITORY_HOST = "http://unit-038:8080/plug";
  public static final String REPOSITORY_LIST_URL = REPOSITORY_HOST + "/plugins/list/";
  public static final String DOWNLOAD_URL = REPOSITORY_HOST + "/pluginManager?action=download&id=";
  public static final String REPOSITORY_LIST_SYSTEM_ID = REPOSITORY_HOST + "/plugin-repository.dtd";

  private static final String FILENAME = "filename=\"";

  private static class InputStreamGetter implements Runnable {
    private InputStream is;
    private URLConnection urlConnection;

    public InputStream getIs() {
      return is;
    }

    public InputStreamGetter(URLConnection urlConnection) {
      this.urlConnection = urlConnection;
    }

    public void run() {
      try {
        is = urlConnection.getInputStream();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static CategoryNode makeCategoryTree (String url)
    throws SAXException, ParserConfigurationException, IOException {
    final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();

    if (pi.isCanceled())
      return null;

    RepositoryContentHandler handler = new RepositoryContentHandler();
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    HttpURLConnection connection = (HttpURLConnection)new URL (url).openConnection();
    try {
      if (pi.isCanceled())
        return null;

      pi.setText("Waiting for reply from " + RepositoryHelper.REPOSITORY_HOST);

      InputStreamGetter getter = new InputStreamGetter(connection);
      final Thread thread = new Thread (getter, "InputStreamGetter");
      thread.start();

      while (! pi.isCanceled()) {
        try {
          thread.join(50);
          pi.setFraction(System.currentTimeMillis());
          if (! thread.isAlive())
            break;
        }
        catch (InterruptedException e) {
          return null;
        }
      }

      InputStream is = getter.getIs();
      if (is == null)
        return null;

      pi.setText("Downloading List of Plugins");
      File temp = File.createTempFile("temp", "", new File (PathManagerEx.getPluginTempPath()));
      try {
        FileOutputStream fos = new FileOutputStream(temp, false);
        ProgressStream ps = new ProgressStream(is,
                                               ProgressManager.getInstance().getProgressIndicator());
        byte [] buffer = new byte [1024];
        do {
          int size = ps.read(buffer);
          if (size == -1)
            break;
          fos.write(buffer, 0, size);
        } while (true);
        fos.close();

        parser.parse(temp, handler);
      } finally {
        temp.delete();
      }
    } catch (RuntimeException e) {
      if (e.getCause() != null && e.getCause() instanceof InterruptedException)
        return null;
      else
        throw e;
    }

    return handler.getRoot();
  }

  private static InputStream getConnectionInputStream (URLConnection connection) {
    final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();

    pi.setText("Connecting...");
    InputStreamGetter getter = new InputStreamGetter(connection);
    final Thread thread = new Thread (getter, "InputStreamGetter");
    thread.start();

    while (! pi.isCanceled()) {
      try {
        thread.join(50);

        if (! thread.isAlive())
          break;
      }
      catch (InterruptedException e) {
        return null;
      }
    }

    return getter.getIs();
  }

  public static File downloadPlugin (PluginNode pluginNode, boolean packet, long count, long available) throws IOException {
    ApplicationInfoEx ideInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();
    String buildNumber = "";
    try {
      buildNumber = Integer.valueOf(ideInfo.getBuildNumber()).toString();
    } catch (NumberFormatException e) {
      buildNumber = "3000";
    }

    String url = DOWNLOAD_URL +
                 URLEncoder.encode(pluginNode.getName(), "UTF8") +
                 "&build=" + buildNumber;
    HttpURLConnection connection = (HttpURLConnection)new URL (url).openConnection();
    final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();

    InputStream is = getConnectionInputStream(connection);

    if (is == null)
      return null;

    pi.setText("Downloading plugin '" + pluginNode.getName() + "'");
    File file = File.createTempFile("plugin", "download",
                                    new File (PathManagerEx.getPluginTempPath()));
    FileOutputStream fos = new FileOutputStream(file, false);

    int responseCode = connection.getResponseCode();
    switch (responseCode) {
      case HttpURLConnection.HTTP_OK:
        break;
      default:
        // some problems
        throw new IOException("Connection failed with HTTP code " + responseCode);
    }

    if (pluginNode.getSize().equals("-1")) {
      if (connection.getContentLength() == -1)
        pi.setIndeterminate(true);
      else
        pluginNode.setSize(Integer.toString(connection.getContentLength()));
    }

    boolean cleanFile = true;

    try {
      is = new ProgressStream(packet ? count : 0, packet ? available : Integer.valueOf(pluginNode.getSize()).intValue(),
                                              is, pi);
      int c;
      while ((c = is.read()) != -1) {
        if (pi.isCanceled())
          throw new RuntimeException(new InterruptedException());

        fos.write(c);
      }

      cleanFile = false;
    } catch (RuntimeException e) {
      if (e.getCause() != null && e.getCause() instanceof InterruptedException)
        return null;
      else
        throw e;
    } finally {
      fos.close();
      if (cleanFile)
        file.delete();
    }

    String fileName = null;
    String contentDisposition = connection.getHeaderField("Content-Disposition");
    if (contentDisposition == null) {
      // try to find filename in URL
      String usedURL = connection.getURL().toString();
      int startPos = usedURL.lastIndexOf("/");

      fileName = usedURL.substring(startPos + 1);

    } else {
      int startIdx = contentDisposition.indexOf(FILENAME);
      if (startIdx != -1)
        fileName = contentDisposition.substring(startIdx + FILENAME.length(), contentDisposition.length() - 1);
      else
        throw new IllegalArgumentException(contentDisposition);
    }

    connection.disconnect();

    File newFile = new File (file.getParentFile(), fileName);
    file.renameTo(newFile);

    return newFile;
  }
}
