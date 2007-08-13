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

  public boolean prepareToInstall() throws IOException {
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
      file = downloadPlugin();
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

  private File downloadPlugin() throws IOException {
    HttpURLConnection connection = (HttpURLConnection)new URL(myPluginUrl).openConnection();
    try
    {
      final ProgressIndicator pi = new ProgressIndicatorBase();//ProgressManager.getInstance().getProgressIndicator();
      pi.setText(IdeBundle.message("progress.connecting"));

      InputStream is = UrlConnectionUtil.getConnectionInputStream(connection, pi);

      if (is == null) {
        throw new IOException("Failed to open connection");
      }

      pi.setText(IdeBundle.message("progress.downloading.plugin", getPluginName()));

      File file =  new File (PathManagerEx.getPluginTempPath(), getFileName());
      file.createNewFile();

      int responseCode = connection.getResponseCode();
      switch (responseCode) {
        case HttpURLConnection.HTTP_OK:
          break;
        default:
          // some problems
          throw new IOException(IdeBundle.message("error.connection.failed.with.http.code.N", responseCode));
      }

      pi.setIndeterminate(true);

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
      return file;
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