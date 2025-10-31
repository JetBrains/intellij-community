// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import mslinks.ShellLink;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
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
    var targetPath = targetDir.toString();
    updateUninstallerSection(targetPath, nameAndVersion, buildNumber);
    if (united) {
      updateContextMenuEntries(targetPath);
    }
  }

  private static void updateUninstallerSection(String targetPath, String nameAndVersion, String buildNumber) {
    try {
      LOG.info("updateUninstallerSection for: " + targetPath);
      var rootKeys = List.of(WinReg.HKEY_CURRENT_USER, WinReg.HKEY_LOCAL_MACHINE);
      var keyPath = "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall";
      for (var rootKey : rootKeys) {
        for (var key : Advapi32Util.registryGetKeys(rootKey, keyPath)) {
          try {
            var location = Advapi32Util.registryGetStringValue(rootKey, keyPath + '\\' + key, "InstallLocation");
            if (targetPath.equalsIgnoreCase(location)) {
              LOG.info("key: " + rootKeyName(rootKey) + '\\' + keyPath + '\\' + key);
              Advapi32Util.registrySetStringValue(rootKey, keyPath + '\\' + key, "DisplayName", nameAndVersion);
              Advapi32Util.registrySetStringValue(rootKey, keyPath + '\\' + key, "DisplayVersion", buildNumber);
              return;
            }
          }
          catch (Win32Exception ignored) { }
        }
      }
    }
    catch (Throwable t) {
      LOG.log(Level.WARNING, "updateUninstallerSection failed", t);
    }
  }

  private static void updateContextMenuEntries(String targetPath) {
    try {
      LOG.info("updateContextMenuEntries for: " + targetPath);
      var rootKeys = List.of(WinReg.HKEY_CURRENT_USER, WinReg.HKEY_LOCAL_MACHINE);
      for (var rootKey : rootKeys) {
        // file association target
        processContextMenuKey(rootKey, "Software\\Classes", true, targetPath);
        // "edit with" context menu
        processContextMenuKey(rootKey, "Software\\Classes\\*\\shell", false, targetPath);
        // folder context menu
        processContextMenuKey(rootKey, "Software\\Classes\\Directory\\shell", false, targetPath);
        processContextMenuKey(rootKey, "Software\\Classes\\Directory\\Background\\shell", false, targetPath);
      }
    }
    catch (Throwable t) {
      LOG.log(Level.WARNING, "updateContextMenuEntries failed", t);
    }
  }

  private static void processContextMenuKey(WinReg.HKEY rootKey, String baseKey, boolean fileAssociation, String targetPath) {
    for (var subKey : Advapi32Util.registryGetKeys(rootKey, baseKey)) {
      if (fileAssociation && (baseKey.startsWith(".") || baseKey.startsWith("ms-") || baseKey.startsWith("microsoft"))) continue;
      try {
        var key = baseKey + '\\' + subKey;
        var iconPath = fileAssociation
          ? Advapi32Util.registryGetStringValue(rootKey, key + "\\DefaultIcon", "")
          : Advapi32Util.registryGetStringValue(rootKey, key, "Icon");
        if (iconPath.regionMatches(true, 0, targetPath, 0, targetPath.length())) {
          var name = Advapi32Util.registryGetStringValue(rootKey, key, "");
          var newName = stripCeSuffixes(name);
          if (!name.equals(newName)) {
            LOG.info("key: " + rootKeyName(rootKey) + '\\' + baseKey + '\\' + subKey);
            Advapi32Util.registrySetStringValue(rootKey, key, "", newName);
          }
        }
      }
      catch (Win32Exception ignored) { }
    }
  }

  private static String rootKeyName(WinReg.HKEY key) {
    return key == WinReg.HKEY_CURRENT_USER ? "HKCU" :
           key == WinReg.HKEY_LOCAL_MACHINE ? "HKLM" :
           "0x" + Long.toHexString(Pointer.nativeValue(key.getPointer()));
  }

  static void updateWindowsShortcuts(Path targetDir, String nameAndVersion) {
    LOG.info("updateWindowsShortcuts for: " + targetDir);
    try {
      var desktop = getFolderPath(ShlObj.CSIDL_DESKTOPDIRECTORY, () -> Path.of(System.getProperty("user.home"), "Desktop"));
      var commonDesktop = getFolderPath(ShlObj.CSIDL_COMMON_DESKTOPDIRECTORY, () -> Path.of(System.getenv("PUBLIC"), "Desktop"));
      var startMenu = getFolderPath(ShlObj.CSIDL_STARTMENU, () -> Path.of(System.getenv("APPDATA"), "Microsoft\\Windows\\Start Menu"))
        .resolve("Programs\\JetBrains");
      var commonStartMenu = getFolderPath(ShlObj.CSIDL_COMMON_STARTMENU, () -> Path.of(System.getenv("ProgramData"), "Microsoft\\Windows\\Start Menu"))
        .resolve("Programs\\JetBrains");
      var targetPath = targetDir.toString();
      var versionPattern = Pattern.compile("\\d+\\.\\d+");
      for (var folder : List.of(desktop, commonDesktop, startMenu, commonStartMenu)) {
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
                LOG.info("link: " + shortcutFile);
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

  private static Path getFolderPath(int folder, Supplier<Path> fallback) {
    try {
      var path = new char[WinDef.MAX_PATH];
      var res = Shell32.INSTANCE.SHGetFolderPath(null, folder, null, ShlObj.SHGFP_TYPE_CURRENT, path);
      if (WinError.S_OK.equals(res)) {
        var len = 0;
        while (len < path.length && path[len] != 0) len++;
        return Path.of(new String(path, 0, len));
      }
    }
    catch (Exception e) {
      LOG.log(Level.WARNING, "getFolderPath(" + folder + ')', e);
    }
    return fallback.get();
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
