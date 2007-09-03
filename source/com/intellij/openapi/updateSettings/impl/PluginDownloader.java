/*
 * User: anna
 * Date: 10-Aug-2007
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.RepositoryHelper;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.io.UrlConnectionUtil;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class PluginDownloader {

  private static final Logger LOG = Logger.getInstance("#" + PluginDownloader.class.getName());

  @NonNls private static final String FILENAME = "filename=";

  private String myPluginId;
  private String myPluginUrl;
  private final String myPluginVersion;

  private String myFileName;
  private String myPluginName;

  public PluginDownloader(final String pluginId, final String pluginUrl, final String pluginVersion) {
    myPluginId = pluginId;
    myPluginUrl = pluginUrl;
    myPluginVersion = pluginVersion;
  }

  public PluginDownloader(final String pluginId,
                          final String pluginUrl,
                          final String pluginVersion,
                          final String fileName,
                          final String pluginName) {
    myPluginId = pluginId;
    myPluginUrl = pluginUrl;
    myPluginVersion = pluginVersion;
    myFileName = fileName;
    myPluginName = pluginName;
  }

  public boolean prepareToInstall() throws IOException {
    return prepareToInstall(new ProgressIndicatorBase());
  }

  public boolean prepareToInstall(ProgressIndicator pi) throws IOException {
    File oldFile = null;
    if (PluginManager.isPluginInstalled(PluginId.getId(myPluginId))) {
      //store old plugins file
      final IdeaPluginDescriptor ideaPluginDescriptor = PluginManager.getPlugin(PluginId.getId(myPluginId));
      LOG.assertTrue(ideaPluginDescriptor != null);
      if (myPluginVersion != null && IdeaPluginDescriptorImpl.compareVersion(ideaPluginDescriptor.getVersion(), myPluginVersion) >= 0) return false;
      oldFile = ideaPluginDescriptor.getPath();
    }
    // download plugin
    File file;
    String errorMessage = IdeBundle.message("unknown.error");
    try {
      file = downloadPlugin(pi);
    }
    catch (IOException ex) {
      file = null;
      errorMessage = ex.getMessage();
    }
    if (file == null) {
      final String errorMessage1 = errorMessage;
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Messages.showErrorDialog(IdeBundle.message("error.plugin.was.not.installed", getPluginName(), errorMessage1),
                                   IdeBundle.message("title.failed.to.download"));
        }
      });
      return false;
    }

    if (oldFile != null) {
      // add command to delete the 'action script' file
      StartupActionScriptManager.ActionCommand deleteOld = new StartupActionScriptManager.DeleteCommand(oldFile);
      StartupActionScriptManager.addActionCommand(deleteOld);
    }

    //noinspection HardCodedStringLiteral
    if (file.getName().endsWith(".jar")) {
      // add command to copy file to the IDEA/plugins path
      StartupActionScriptManager.ActionCommand copyPlugin =
        new StartupActionScriptManager.CopyCommand(file, new File(PathManager.getPluginsPath() + File.separator + file.getName()));
      StartupActionScriptManager.addActionCommand(copyPlugin);
    }
    else {
      // add command to unzip file to the IDEA/plugins path
      String unzipPath;
      if (ZipUtil.isZipContainsFolder(file)) {
        unzipPath = PathManager.getPluginsPath();
      }
      else {
        unzipPath = PathManager.getPluginsPath() + File.separator + getPluginName();
      }

      StartupActionScriptManager.ActionCommand unzip = new StartupActionScriptManager.UnzipCommand(file, new File(unzipPath));
      StartupActionScriptManager.addActionCommand(unzip);
    }

    // add command to remove temp plugin file
    StartupActionScriptManager.ActionCommand deleteTemp = new StartupActionScriptManager.DeleteCommand(file);
    StartupActionScriptManager.addActionCommand(deleteTemp);
    return true;
  }

  private File downloadPlugin(ProgressIndicator pi) throws IOException {
    HttpURLConnection connection = (HttpURLConnection)new URL(myPluginUrl).openConnection();
    try
    {
      pi.setText(IdeBundle.message("progress.connecting"));

      InputStream is = UrlConnectionUtil.getConnectionInputStream(connection, pi);

      if (is == null) {
        throw new IOException("Failed to open connection");
      }

      pi.setText(IdeBundle.message("progress.downloading.plugin", getPluginName()));

      final File pluginsTemp = new File(PathManagerEx.getPluginTempPath());

      if (!pluginsTemp.exists()) {
        pluginsTemp.mkdirs();
      }

      File file = File.createTempFile("plugin", "download", pluginsTemp);

      int responseCode = connection.getResponseCode();
      switch (responseCode) {
        case HttpURLConnection.HTTP_OK:
          break;
        default:
          // some problems
          throw new IOException(IdeBundle.message("error.connection.failed.with.http.code.N", responseCode));
      }

      pi.setIndeterminate(connection.getContentLength() == -1);

      OutputStream fos = null;
      try {
        fos = new BufferedOutputStream(new FileOutputStream(file, false));
        StreamUtil.copyStreamContent(is, fos);
      }
      finally {
        if (fos != null) {
          fos.close();
        }
        is.close();
      }
      if (myFileName == null) {
        String contentDisposition = connection.getHeaderField("Content-Disposition");
        if (contentDisposition == null || contentDisposition.indexOf(FILENAME) < 0) {
          // try to find filename in URL
          String usedURL = connection.getURL().toString();
          int startPos = usedURL.lastIndexOf("/");

          myFileName = usedURL.substring(startPos + 1);
          if (myFileName.length() == 0 || myFileName.contains("?")) {
            myFileName = myPluginUrl.substring(myPluginUrl.lastIndexOf("/") + 1);
          }
        }
        else {
          int startIdx = contentDisposition.indexOf(FILENAME);
          myFileName = contentDisposition.substring(startIdx + FILENAME.length(), contentDisposition.length());
          // according to the HTTP spec, the filename is a quoted string, but some servers don't quote it
          // for example: http://www.jspformat.com/Download.do?formAction=d&id=8
          if (myFileName.startsWith("\"") && myFileName.endsWith("\"")) {
            myFileName = myFileName.substring(1, myFileName.length()-1);
          }
          if (myFileName.indexOf('\\') >= 0 || myFileName.indexOf('/') >= 0 || myFileName.indexOf(File.separatorChar) >= 0 ||
              myFileName.indexOf('\"') >= 0) {
            // invalid path name passed by the server - fail to download
            FileUtil.delete(file);
            throw new IOException("Invalid filename returned by server");
          }
        }
      }

      File newFile = new File (file.getParentFile(), myFileName);
      FileUtil.rename(file, newFile);
      return newFile;
    }
    finally {
      connection.disconnect();
    }
  }

  public String getFileName() {
    if (myFileName == null) {
      myFileName = myPluginUrl.substring(myPluginUrl.lastIndexOf("/") + 1);
    }
    return myFileName;
  }

  public String getPluginName() {
    if (myPluginName == null) {
      myPluginName = FileUtil.getNameWithoutExtension(getFileName());
    }
    return myPluginName;
  }

  /**
   * Updates given plugin from Repository
   * @param pluginId given plugin id
   * @param pluginVersion available version or null if plugin must be uploaded even if current version is greater than uploading
   * @throws IOException
   */
  public static void updateFromRepository(final String pluginId, final @Nullable String pluginVersion) throws IOException {
    @NonNls final String url =
      RepositoryHelper.DOWNLOAD_URL + URLEncoder.encode(pluginId, "UTF8") + "&build=" + ApplicationInfo.getInstance().getBuildNumber();
    new PluginDownloader(pluginId, url, pluginVersion).prepareToInstall();
  }
}