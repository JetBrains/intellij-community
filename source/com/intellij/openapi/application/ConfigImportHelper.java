/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.application;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.util.PropertyResourceBundle;

/**
 * @author max
 */
public class ConfigImportHelper {
  @NonNls private static final String BUILD_NUMBER_FILE = "build.txt";
  @NonNls private static final String PLUGINS_PATH = "plugins";
  @NonNls private static final String BIN_FOLDER = "bin";

  private ConfigImportHelper() {}

  public static void importConfigsTo(String newConfigPath) {
    do {
      ImportOldConfigsPanel dlg = new ImportOldConfigsPanel();
      dlg.setVisible(true);
      if (dlg.isImportEnabled()) {
        File instHome = dlg.getSelectedFile();
        File oldConfigDir = getOldConfigDir(instHome);
        if (oldConfigDir == null) {
          JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                        ApplicationBundle.message("error.invalid.installation.home", instHome.getAbsolutePath(),
                                                                  ApplicationNamesInfo.getInstance().getFullProductName()));
          continue;
        }

        if (!oldConfigDir.exists()) {
          JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                        ApplicationBundle.message("error.no.settings.path",
                                                                  oldConfigDir.getAbsolutePath()),
                                        ApplicationBundle.message("title.settings.import.failed"), JOptionPane.WARNING_MESSAGE);
          continue;
        }

        try {
          xcopy(oldConfigDir, new File(newConfigPath));
        }
        catch (IOException e) {
          JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                        ApplicationBundle.message("error.unable.to.import.settings", e.getMessage()),
                                        ApplicationBundle.message("title.settings.import.failed"), JOptionPane.WARNING_MESSAGE);
        }
      }

      break;
    }
    while (true);
  }

  public static void xcopy(File src, File dest) throws IOException{
    src = src.getCanonicalFile();
    dest = dest.getCanonicalFile();
    if (!src.isDirectory()){
      throw new IOException(ApplicationBundle.message("config.import.invalid.directory.error", src.getAbsolutePath()));
    }
    if (!dest.isDirectory()){
      throw new IOException(ApplicationBundle.message("config.import.invalid.directory.error", dest.getAbsolutePath()));
    }
    FileUtil.copyDir(src, dest);

    // Delete plugins just imported. They're most probably incompatible with newer idea version.
    File plugins = new File(dest, PLUGINS_PATH);
    if (plugins.exists()) {
      FileUtil.delete(plugins);
    }
  }

  @Nullable
  public static File getOldConfigDir(File oldInstallHome) {
    int oldBuildNumber = getBuildNumber(oldInstallHome);

    if (oldBuildNumber != -1 && oldBuildNumber <= 600) { // Pandora
      //noinspection HardCodedStringLiteral
      return new File(oldInstallHome, "config");
    }

    File[] launchFileCandidates = getLaunchFilesCandidates(oldInstallHome);
    for (File file : launchFileCandidates) {
      if (file.exists()) {
        String configDir = PathManager.substituteVars(getConfigFromLaxFile(file), oldInstallHome.getPath());
        if (configDir != null) {
          File probableConfig = new File(configDir);
          if (probableConfig.exists()) return probableConfig;
        }
      }
    }

    return null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static File[] getLaunchFilesCandidates(File instHome) {
    File bin = new File(instHome, BIN_FOLDER);
    return new File[]{
      new File(bin, "idea.properties"),
      new File(bin, "idea.lax"),
      new File(bin, "idea.bat"),
      new File(bin, "idea.sh"),
      new File(new File(instHome, "Contents"), "Info.plist"),
      new File(new File(new File(bin, "idea.app"), "Contents"), "Info.plist"),
      new File(new File(new File(instHome, "idea.app"), "Contents"), "Info.plist")
    };
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @Nullable
  public static String getConfigFromLaxFile(File file) {
      if (file.getName().endsWith(".properties")) {
          try {
            InputStream fis = new BufferedInputStream(new FileInputStream(file));
            PropertyResourceBundle bundle;
            try {
              bundle = new PropertyResourceBundle(fis);
            }
            finally {
              fis.close();
            }
            return bundle.getString("idea.config.path");
          } catch (IOException e) {
              return null;
          }
      }

      String fileContent = getContent(file);
      String configParam = "idea.config.path=";
      int idx = fileContent.indexOf(configParam);
      if (idx == -1) {
          configParam = "<key>idea.config.path</key>";
          idx = fileContent.indexOf(configParam);
          if (idx == -1) return null;
          idx = fileContent.indexOf("<string>", idx);
          if (idx == -1) return null;
          idx += "<string>".length();
          return fixDirName(fileContent.substring(idx, fileContent.indexOf("</string>", idx)), true);
      } else {
          String configDir = "";
          idx += configParam.length();
          if (fileContent.length() > idx) {
              if (fileContent.charAt(idx) == '"') {
                  idx++;
                  while ((fileContent.length() > idx) && (fileContent.charAt(idx) != '"') && (fileContent.charAt(idx) != '\n') &&
                          (fileContent.charAt(idx) != '\r')) {
                      configDir += fileContent.charAt(idx);
                      idx++;
                  }
              } else {
                  while ((fileContent.length() > idx) && (!Character.isSpaceChar(fileContent.charAt(idx))) &&
                          (fileContent.charAt(idx) != '\n') &&
                          (fileContent.charAt(idx) != '\r')) {
                      configDir += fileContent.charAt(idx);
                      idx++;
                  }
              }
          }
          configDir = fixDirName(configDir, true);
          if (configDir.length() > 0) {
              configDir = (new File(configDir)).getPath();
          }
          return configDir;
      }
  }

  @Nullable
  private static String getContent(File file) {
    try {
      StringBuffer content = new StringBuffer();
      BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
      try {
        do {
          String line = reader.readLine();
          if (line == null) break;
          content.append(line);
          content.append('\n');
        }
        while (true);
      }
      finally {
        reader.close();
      }

      return content.toString();
    }
    catch (Exception e) {
      return null;
    }
  }

  public static String fixDirName(String dir, boolean replaceUserHome) {
    if (StringUtil.startsWithChar(dir, '\"') && StringUtil.endsWithChar(dir, '\"')) {
      dir = dir.substring(1, dir.length() - 1);
    }
    if (replaceUserHome) {
      if ((dir.startsWith("~\\")) || dir.startsWith("~//") || dir.startsWith("~" + File.separatorChar)) {
        dir = SystemProperties.getUserHome() + dir.substring(1);
      }
    }
    return dir;
  }

  public static boolean isInstallationHome(String installationHome) {
    //noinspection HardCodedStringLiteral
    boolean quickTest = new File(new File(installationHome, "lib"), "idea.jar").exists() &&
                        new File(installationHome, BIN_FOLDER).exists();
    if (!quickTest) return false;

    File[] files = getLaunchFilesCandidates(new File(installationHome));
    for (File file : files) {
      if (file.exists()) return true;
    }

    return false;
  }

  private static int getBuildNumber(File installDirectory) {
    installDirectory = installDirectory.getAbsoluteFile();

    File buildTxt = new File(installDirectory, BUILD_NUMBER_FILE);
    if ((!buildTxt.exists()) || (buildTxt.isDirectory())){
      buildTxt = new File(new File(installDirectory, BIN_FOLDER), BUILD_NUMBER_FILE);
    }

    if (buildTxt.exists() && !buildTxt.isDirectory()){
      int buildNumber = -1;
      String buildNumberText = getContent(buildTxt);
      if (buildNumberText != null) {
        try{
          if (buildNumberText.length() > 1){
            buildNumberText = buildNumberText.trim();
            buildNumber = Integer.parseInt(buildNumberText);
          }
        }
        catch (Exception e){
          // OK
        }
      }
      return buildNumber;
    }

    return -1;
  }
}
