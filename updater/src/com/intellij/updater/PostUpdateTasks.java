// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

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

  static void updateWindowsRegistry(Path targetDir, String nameAndVersion, String buildNumber) {
    try {
      LOG.info("updateWindowsRegistry for: " + targetDir);
      var rootKeys = List.of(WinReg.HKEY_CURRENT_USER, WinReg.HKEY_LOCAL_MACHINE);
      var keyPath = "Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall";
      var targetPath = targetDir.toString();
      for (var rootKey : rootKeys) {
        for (var key : Advapi32Util.registryGetKeys(rootKey, keyPath)) {
          try {
            var location = Advapi32Util.registryGetStringValue(rootKey, keyPath + "\\" + key, "InstallLocation");
            if (targetPath.equalsIgnoreCase(location)) {
              LOG.info("key: " + rootKey + "\\" + keyPath + "\\" + key);
              Advapi32Util.registrySetStringValue(rootKey, keyPath + "\\" + key, "DisplayName", nameAndVersion);
              Advapi32Util.registrySetStringValue(rootKey, keyPath + "\\" + key, "DisplayVersion", buildNumber);
              return;
            }
          }
          catch (Win32Exception ignored) { }
        }
      }
    }
    catch (Throwable t) {
      LOG.log(Level.WARNING, "updateWindowsRegistry failed", t);
    }
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
            @SuppressWarnings("NullableProblems")
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
}
