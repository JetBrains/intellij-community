// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import mslinks.ShellLink;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;

import static com.intellij.updater.Runner.LOG;
import static java.util.Objects.requireNonNullElse;

final class PostUpdateTasks {
  private static final String[] EMPTY_ARRAY = {};
  private static final int ERROR_FILE_NOT_FOUND = 2;

  private static final int CSIDL_STARTMENU = 0x000B;
  private static final int CSIDL_DESKTOPDIRECTORY = 0x0010;
  private static final int CSIDL_COMMON_STARTMENU = 0x0016;
  private static final int CSIDL_COMMON_DESKTOPDIRECTORY = 0x0019;

  private static final Supplier<WindowsNative> NATIVE = WindowsNative.supplier();

  static void refreshAppBundleIcon(Path targetDir) {
    try {
      var applicationPath = "Contents".equals(targetDir.getFileName().toString()) ? targetDir.getParent() : targetDir;
      LOG.info("refreshApplicationIcon for: " + applicationPath);
      Files.setLastModifiedTime(applicationPath, FileTime.from(Instant.now()));
    }
    catch (IOException e) {
      LOG.log(Level.WARNING, "refreshApplicationIcon failed", e);
    }
  }

  static void updateWindowsRegistry(Path targetDir, String nameAndVersion, String buildNumber, boolean united) {
    var nativeApi = NATIVE.get();
    if (nativeApi == null) {
      LOG.info("updateWindowsRegistry skipped: Windows native helpers are not available");
      return;
    }
    var targetPath = targetDir.toString();
    LOG.info("path: " + targetPath + "; name/version: " + nameAndVersion + "; build: " + buildNumber + "; united: " + united);
    updateUninstallerSection(nativeApi, targetPath, nameAndVersion, buildNumber);
    updateManufacturerSection(nativeApi, targetPath, buildNumber, united);
    if (united) {
      updateContextMenuEntries(nativeApi, targetPath);
    }
  }

  private static void updateUninstallerSection(WindowsNative nativeApi, String targetPath, String nameAndVersion, String buildNumber) {
    try {
      var rootKeys = List.of(WindowsNative.HKEY_CURRENT_USER, WindowsNative.HKEY_LOCAL_MACHINE);
      var nodes = List.of("Software", "Software\\WOW6432Node");
      for (var rootKey : rootKeys) {
        for (var node : nodes) {
          var baseKey = node + "\\Microsoft\\Windows\\CurrentVersion\\Uninstall";
          LOG.info("scanning: " + formatKey(rootKey, baseKey));
          for (var key : getRegistrySubKeys(nativeApi, rootKey, baseKey)) {
            try {
              var location = nativeApi.registryGetString(rootKey, baseKey + '\\' + key, "InstallLocation");
              if (location != null && targetPath.equalsIgnoreCase(location)) {
                LOG.info("found: " + formatKey(rootKey, baseKey, key));
                nativeApi.registrySetString(rootKey, baseKey + '\\' + key, "DisplayName", nameAndVersion);
                nativeApi.registrySetString(rootKey, baseKey + '\\' + key, "DisplayVersion", buildNumber);
                return;
              }
            }
            catch (WindowsNative.NativeException e) {
              if (e.errorCode != ERROR_FILE_NOT_FOUND) {
                LOG.log(Level.FINE, e, () -> "updateUninstallerSection: " + formatKey(rootKey, baseKey, key));
              }
            }
          }
        }
      }
    }
    catch (Throwable t) {
      LOG.log(Level.WARNING, "updateUninstallerSection failed", t);
    }
  }

  private static void updateManufacturerSection(WindowsNative nativeApi, String targetPath, String buildNumber, boolean united) {
    try {
      var rootKeys = List.of(WindowsNative.HKEY_CURRENT_USER, WindowsNative.HKEY_LOCAL_MACHINE);
      var nodes = List.of("Software", "Software\\WOW6432Node");
      for (var rootKey : rootKeys) {
        for (var node : nodes) {
          var baseKey = node + "\\JetBrains";
          LOG.info("scanning: " + formatKey(rootKey, baseKey));
          for (var productKey : getRegistrySubKeys(nativeApi, rootKey, baseKey)) {
            for (var buildKey : getRegistrySubKeys(nativeApi, rootKey, baseKey + '\\' + productKey)) {
              try {
                var oldKey = baseKey + '\\' + productKey + '\\' + buildKey;
                var location = nativeApi.registryGetString(rootKey, oldKey, "");
                if (location != null && targetPath.equalsIgnoreCase(location)) {
                  var newKey = baseKey + '\\' + (united ? stripCeSuffixes(productKey) : productKey) + '\\' + buildNumber;
                  LOG.info("found: " + formatKey(rootKey, oldKey) + "; moving to: " + formatKey(rootKey, newKey));
                  nativeApi.registryCreateKey(rootKey, newKey);
                  nativeApi.registryCopyValues(rootKey, oldKey, newKey);
                  nativeApi.registryDeleteKey(rootKey, oldKey);
                  return;
                }
              }
              catch (WindowsNative.NativeException e) {
                if (e.errorCode != ERROR_FILE_NOT_FOUND) {
                  LOG.log(Level.FINE, e, () -> "updateManufacturerSection: " + formatKey(rootKey, baseKey, productKey, buildKey));
                }
              }
            }
          }
        }
      }
    }
    catch (Throwable t) {
      LOG.log(Level.WARNING, "updateManufacturerSection failed", t);
    }
  }

  private static void updateContextMenuEntries(WindowsNative nativeApi, String targetPath) {
    try {
      var rootKeys = List.of(WindowsNative.HKEY_CURRENT_USER, WindowsNative.HKEY_LOCAL_MACHINE);
      var updated = false;
      for (var rootKey : rootKeys) {
        // file association target
        updated |= processContextMenuKey(nativeApi, rootKey, "Software\\Classes", true, targetPath);
        // "edit with" context menu
        updated |= processContextMenuKey(nativeApi, rootKey, "Software\\Classes\\*\\shell", false, targetPath);
        // folder context menu
        updated |= processContextMenuKey(nativeApi, rootKey, "Software\\Classes\\Directory\\shell", false, targetPath);
        updated |= processContextMenuKey(nativeApi, rootKey, "Software\\Classes\\Directory\\Background\\shell", false, targetPath);
      }
      if (updated) {
        notifyShellAboutChangedAssociations(nativeApi);
      }
    }
    catch (Throwable t) {
      LOG.log(Level.WARNING, "updateContextMenuEntries failed", t);
    }
  }

  private static boolean processContextMenuKey(
    WindowsNative nativeApi, long rootKey, String baseKey, boolean fileAssociation, String targetPath
  ) {
    var updated = false;
    LOG.info("scanning: " + formatKey(rootKey, baseKey));
    for (var subKey : getRegistrySubKeys(nativeApi, rootKey, baseKey)) {
      if (fileAssociation && (baseKey.startsWith(".") || baseKey.startsWith("ms-") || baseKey.startsWith("microsoft"))) continue;
      try {
        var key = baseKey + '\\' + subKey;
        var iconPath = fileAssociation
                       ? nativeApi.registryGetString(rootKey, key + "\\DefaultIcon", "")
                       : nativeApi.registryGetString(rootKey, key, "Icon");
        if (iconPath != null && iconPath.regionMatches(true, 0, targetPath, 0, targetPath.length())) {
          LOG.info("found: " + formatKey(rootKey, key));
          var name = nativeApi.registryGetString(rootKey, key, "");
          if (name != null) {
            var newName = stripCeSuffixes(name);
            if (!name.equals(newName)) {
              LOG.info("renaming '" + name + "' to '" + newName + "'");
              nativeApi.registrySetString(rootKey, key, "", newName);
              updated = true;
            }
          }
        }
      }
      catch (WindowsNative.NativeException e) {
        if (e.errorCode != ERROR_FILE_NOT_FOUND) {
          LOG.log(Level.FINE, e, () -> "processContextMenuKey: " + formatKey(rootKey, baseKey, subKey));
        }
      }
    }
    return updated;
  }

  private static String[] getRegistrySubKeys(WindowsNative nativeApi, long rootKey, String key) {
    try {
      return nativeApi.registrySubKeys(rootKey, key);
    }
    catch (WindowsNative.NativeException e) {
      if (e.errorCode != ERROR_FILE_NOT_FOUND) {
        LOG.log(Level.FINE, e, () -> "registrySubKeys(" + formatKey(rootKey, key) + ')');
      }
      return EMPTY_ARRAY;
    }
  }

  private static String formatKey(long rootKey, String... subKeys) {
    var sb = new StringBuilder().append(
      rootKey == WindowsNative.HKEY_CURRENT_USER ? "HKCU" :
      rootKey == WindowsNative.HKEY_LOCAL_MACHINE ? "HKLM" :
      "0x" + Long.toHexString(rootKey)
    );
    for (var subKey : subKeys) sb.append('\\').append(subKey);
    return sb.toString();
  }

  private static void notifyShellAboutChangedAssociations(WindowsNative nativeApi) {
    try {
      nativeApi.notifyShellAssociationsChanged();
    }
    catch (Throwable t) {
      LOG.log(Level.WARNING, "notifyShellAboutChangedAssociations failed", t);
    }
  }

  static void updateWindowsShortcuts(Path targetDir, String nameAndVersion) {
    LOG.info("path: " + targetDir + "; name/version: " + nameAndVersion);
    try {
      var nativeApi = NATIVE.get();
      var desktop = getFolderPath(nativeApi, CSIDL_DESKTOPDIRECTORY, () -> Path.of(System.getProperty("user.home"), "Desktop"));
      var commonDesktop = getFolderPath(nativeApi, CSIDL_COMMON_DESKTOPDIRECTORY, () -> Path.of(System.getenv("PUBLIC"), "Desktop"));
      var startMenu = getFolderPath(
        nativeApi, CSIDL_STARTMENU, () -> Path.of(System.getenv("APPDATA"), "Microsoft\\Windows\\Start Menu")
      ).resolve("Programs\\JetBrains");
      var commonStartMenu = getFolderPath(
        nativeApi, CSIDL_COMMON_STARTMENU, () -> Path.of(System.getenv("ProgramData"), "Microsoft\\Windows\\Start Menu")
      ).resolve("Programs\\JetBrains");
      var targetPath = targetDir.toString();
      var versionPattern = Pattern.compile("\\d+\\.\\d+");
      for (var folder : List.of(desktop, commonDesktop, startMenu, commonStartMenu)) {
        LOG.info("scanning: " + folder);
        if (Files.isDirectory(folder)) {
          Files.walkFileTree(folder, Set.of(), 1, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path shortcutFile, BasicFileAttributes attrs) {
              var shortcutName = shortcutFile.getFileName().toString();
              if (
                shortcutName.endsWith(".lnk") &&
                versionPattern.matcher(shortcutName).find() &&
                targetPath.equalsIgnoreCase(getLinkTarget(shortcutFile))
              ) {
                LOG.info("found: " + shortcutFile);
                var newShortcutFile = shortcutFile.resolveSibling(nameAndVersion + ".lnk");
                if (!Files.exists(newShortcutFile)) {
                  try {
                    Files.move(shortcutFile, newShortcutFile, StandardCopyOption.ATOMIC_MOVE);
                  }
                  catch (IOException e) {
                    LOG.log(Level.WARNING, "renaming to " + newShortcutFile + " failed", e);
                  }
                }
              }
              return FileVisitResult.CONTINUE;
            }
          });
        }
      }
    }
    catch (Throwable t) {
      LOG.log(Level.WARNING, "updateWindowsShortcuts failed", t);
    }
  }

  private static Path getFolderPath(@Nullable WindowsNative nativeApi, int csidl, Supplier<Path> fallback) {
    var path = nativeApi != null ? nativeApi.folderPath(csidl) : null;
    return path != null ? path : fallback.get();
  }

  private static @Nullable String getLinkTarget(Path shortcutFile) {
    try {
      var target = Path.of(new ShellLink(shortcutFile).resolveTarget());
      if (target.getFileName().toString().endsWith(".exe")) {
        target = target.getParent();
        if ("bin".equals(target.getFileName().toString())) {
          return target.getParent().toString();
        }
      }
    }
    catch (Exception e) {
      LOG.log(Level.WARNING, "getLinkTarget(" + shortcutFile + ')', e);
    }
    return null;
  }

  static void updateDesktopEntries(Path targetDir) {
    try {
      LOG.info("updateDesktopEntries for: " + targetDir);
      var userHome = System.getProperty("user.home");
      var dataDirectories =
        requireNonNullElse(System.getenv("XDG_DATA_HOME"), userHome + "/.local/share") + ':' +
        requireNonNullElse(System.getenv("XDG_DATA_DIRS"), "/usr/local/share:/usr/share");
      var targetPrefix = "Exec=\"" + targetDir + "/bin/";
      outer:
      for (var path : dataDirectories.split(":")) {
        try {
          var dir = Path.of(path, "applications");
          try (var stream = Files.newDirectoryStream(dir, "*.desktop")) {
            LOG.info("visiting " + dir);
            for (var entry : stream) {
              var content = Files.readAllLines(entry);
              if (content.stream().anyMatch(line -> line.startsWith(targetPrefix))) {
                var updated = updateEntry(content);
                if (updated) {
                  LOG.info("entry: " + entry);
                  Files.write(entry, content);
                  refreshMenu(path.startsWith(userHome + '/'));
                  break outer;
                }
              }
            }
          }
        }
        catch (InvalidPathException | NotDirectoryException ignored) { }
      }
    }
    catch (Throwable t) {
      LOG.log(Level.WARNING, "updateDesktopEntries failed", t);
    }
  }

  private static boolean updateEntry(List<String> content) {
    var updated = false;
    for (int i = 0; i < content.size(); i++) {
      var line = content.get(i);
      if (line.startsWith("Name=")) {
        var newLine = stripCeSuffixes(line);
        if (!newLine.equals(line)) {
          content.set(i, newLine);
          updated = true;
        }
        break;
      }
    }
    if (updated) {
      for (int i = 0; i < content.size(); i++) {
        var line = content.get(i);
        if (line.startsWith("StartupWMClass=")) {
          content.set(i, line.replace("-ce", ""));
          break;
        }
      }
    }
    return updated;
  }

  private static void refreshMenu(boolean userMode) {
    try {
      var ec = new ProcessBuilder("xdg-desktop-menu", "forceupdate", "--mode", userMode ? "user" : "system")
        .inheritIO()
        .start()
        .waitFor();
      LOG.info("refreshMenu: ec=" + ec);
    }
    catch (Exception e) {
      LOG.log(Level.WARNING, "refreshMenu failed", e);
    }
  }

  private static String stripCeSuffixes(String line) {
    return line.replace(" CE", "").replace(" Community Edition", "");
  }
}
