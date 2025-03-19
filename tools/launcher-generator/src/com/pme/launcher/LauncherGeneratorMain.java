// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.pme.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@SuppressWarnings({"CallToPrintStackTrace", "UseOfSystemOutOrSystemErr"})
public final class LauncherGeneratorMain {
  public static void main(String... args) {
    if (args.length != 5) {
      System.err.println(
        "Usage: LauncherGeneratorMain <template .exe file> <resource.h file> <properties file> <.ico file> <output .exe file>");
      System.exit(1);
    }

    var templateFile = Path.of(args[0]);
    if (!Files.isRegularFile(templateFile)) {
      System.err.println("Launcher template .exe file '" + args[0] + "' not found");
      System.exit(2);
    }

    var resourceHeaderFile = Path.of(args[1]);
    if (!Files.isRegularFile(templateFile)) {
      System.err.println("Resource header file '" + args[1] + "' not found");
      System.exit(3);
    }
    var resourceIDs = new HashMap<String, Integer>();
    try (var lines = Files.lines(resourceHeaderFile)) {
      var pattern = Pattern.compile("#define (\\w+)\\s+(\\d+)");
      lines.map(pattern::matcher).filter(Matcher::matches).forEach(m -> {
        resourceIDs.put(m.group(1), Integer.parseInt(m.group(2)));
      });
    }
    catch (IOException | NumberFormatException e) {
      System.err.println("Error loading '" + resourceHeaderFile + "': " + e.getMessage());
      System.exit(3);
    }

    if (resourceIDs.get("IDI_WINLAUNCHER") == null) {
      System.err.println("Key 'IDI_WINLAUNCHER' is missing from '" + resourceHeaderFile);
      System.exit(3);
    }

    var propertiesFile = Path.of(args[2]);
    if (!Files.isRegularFile(propertiesFile)) {
      System.err.println("Properties file '" + args[2] + "' not found");
      System.exit(4);
    }
    var properties = new Properties();
    try {
      try (var inputStream = Files.newInputStream(propertiesFile)) {
        properties.load(inputStream);
      }
    }
    catch (IOException e) {
      System.err.println("Error loading '" + propertiesFile + "': " + e.getMessage());
      System.exit(4);
    }

    for (var key : properties.stringPropertyNames()) {
      if (key.startsWith("IDS_") && resourceIDs.get(key) == null) {
        System.err.println("Key '" + key + "' is missing from '" + resourceHeaderFile);
        System.exit(3);
      }
    }

    var fileVersion = properties.getProperty("FileVersion");
    var productVersion = properties.getProperty("ProductVersion");
    if (productVersion == null) {
      System.err.println("Key 'ProductVersion' is missing from '" + propertiesFile);
      System.exit(4);
    }
    var productCodeSeparator = productVersion.indexOf('-');
    if (productCodeSeparator > 0) {
      productVersion = productVersion.substring(0, productCodeSeparator);
    }

    var icoFile = Path.of(args[3]);
    if (!Files.isRegularFile(icoFile)) {
      System.err.println("Icon file '" + args[3] + "' not found");
      System.exit(5);
    }

    var outputFile = Path.of(args[4]);
    var generator = new LauncherGenerator(templateFile, outputFile);
    try {
      generator.load();

      for (var key : properties.stringPropertyNames()) {
        if (key.startsWith("IDS_")) {
          generator.setResourceString(resourceIDs.get(key), properties.getProperty(key));
        }
        else {
          generator.setVersionInfoString(key, properties.getProperty(key));
        }
      }
      generator.setVersionInfoString("OriginalFilename", outputFile.getFileName().toString());

      try (var iconStream = Files.newInputStream(icoFile)) {
        generator.injectIcon(resourceIDs.get("IDI_WINLAUNCHER"), iconStream);
      }

      if (fileVersion != null) {
        generator.setFileVersionNumber(splitVersion(fileVersion));
      }
      generator.setProductVersionNumber(splitVersion(productVersion));

      generator.generate();
    }
    catch (IOException e) {
      e.printStackTrace();
      System.exit(6);
    }
  }

  private static int[] splitVersion(String version) {
    try {
      var parts = Stream.of(version.split("\\.")).mapToInt(Integer::parseInt).toArray();
      if (parts.length != 4) throw new IllegalArgumentException("Invalid version format: " + version);
      return parts;
    }
    catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid version format: " + version);
    }
  }
}
