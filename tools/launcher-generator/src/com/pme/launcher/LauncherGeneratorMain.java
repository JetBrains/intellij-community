// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.pme.launcher;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@SuppressWarnings({"CallToPrintStackTrace", "UseOfSystemOutOrSystemErr"})
public class LauncherGeneratorMain {
  public static void main(String[] args) {
    if (args.length != 6) {
      System.err.println("Usage: LauncherGeneratorMain <template EXE file> <app info file> <resource.h file> <properties> <path to ico> <output>");
      System.exit(1);
    }

    File template = new File(args[0]);
    if (!template.exists()) {
      System.err.println("Launcher template EXE file " + args[0] + " not found");
      System.exit(2);
    }

    String appInfoFileName = args[1];
    InputStream appInfoStream;
    try {
      appInfoStream = new FileInputStream(appInfoFileName);
    }
    catch (FileNotFoundException e) {
      appInfoStream = LauncherGeneratorMain.class.getClassLoader().getResourceAsStream(appInfoFileName);
    }

    if (appInfoStream == null) {
      System.err.println("Application info file " + appInfoFileName + " not found");
      System.exit(3);
    }
    Document appInfo;
    try {
      appInfo = new SAXBuilder().build(appInfoStream);
    } catch (Exception e) {
      System.err.println("Error loading application info file " + appInfoFileName + ": " + e.getMessage());
      System.exit(4);
      return;
    }

    Element appInfoRoot = appInfo.getRootElement();

    String icoUrl = args[4];
    if (icoUrl == null || icoUrl.isBlank()) {
      icoUrl = getChild(appInfoRoot, "icon").getAttributeValue("ico");
    }
    if (icoUrl == null) {
      System.err.println(".ico file URL not specified in application info file " + appInfoFileName);
      System.exit(11);
    }
    InputStream iconStream = LauncherGeneratorMain.class.getClassLoader().getResourceAsStream(icoUrl);
    if (iconStream == null) {
      System.err.println(".ico file " + icoUrl + " not found");
      System.exit(12);
    }

    Map<String, Integer> resourceIDs;
    try {
      resourceIDs = loadResourceIDs(args[2]);
    }
    catch (Exception e) {
      System.err.println("Error loading resource.h: " + e.getMessage());
      System.exit(7);
      return;
    }

    Properties properties = new Properties();
    try {
      try (FileInputStream fis = new FileInputStream(args[3])) {
        properties.load(fis);
      }
    }
    catch (IOException e) {
      System.err.println("Error loading launcher properties: " + e.getMessage());
      System.exit(8);
    }

    String companyName = getChild(appInfoRoot, "company").getAttributeValue("name");
    Element names = getChild(appInfoRoot, "names");
    String productShortName = names.getAttributeValue("product");
    String productFullName = names.getAttributeValue("fullname", productShortName);
    Element versionElement = getChild(appInfoRoot, "version");
    int majorVersion = Integer.parseInt(versionElement.getAttributeValue("major"));
    String minorVersionString = versionElement.getAttributeValue("minor");
    Pattern p = Pattern.compile("(\\d+)(\\.(\\d+))?");
    Matcher matcher = p.matcher(minorVersionString);
    if (!matcher.matches()) {
      System.err.println("Unexpected minor version format: " + minorVersionString);
    }
    int minorVersion = Integer.parseInt(matcher.group(1));
    int bugfixVersion = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
    String buildNumber = getChild(appInfoRoot, "build").getAttributeValue("number");
    String buildDate = getChild(appInfoRoot, "build").getAttributeValue("date");
    String versionString = majorVersion + "." + minorVersion + "." + bugfixVersion + "." + buildNumber;


    String copyrightStart = getChild(appInfoRoot, "company").getAttributeValue("copyrightStart");
    if (copyrightStart == null) {
      copyrightStart = "2000";
    }
    String copyrightEnd = buildDate.substring(0, 4);

    File out = new File(args[5]);
    LauncherGenerator generator = new LauncherGenerator(template, out);
    try {
      generator.load();

      for (Map.Entry<Object, Object> pair : properties.entrySet()) {
        String key = (String)pair.getKey();
        Integer id = resourceIDs.get(key);
        if (id == null) {
          //noinspection SpellCheckingInspection
          System.err.println("Invalid STRINGTABLE ID, missing in '" + args[2] + "': " + key);
          System.exit(9);
        }
        generator.setResourceString(id, (String)pair.getValue());
      }

      //noinspection SpellCheckingInspection
      generator.injectIcon(resourceIDs.get("IDI_WINLAUNCHER"), iconStream);

      generator.setVersionInfoString("CompanyName", companyName);
      generator.setVersionInfoString("LegalCopyright", "Copyright (C) " + copyrightStart + "-" + copyrightEnd + " " + companyName);
      generator.setVersionInfoString("ProductName", productFullName);
      generator.setVersionInfoString("FileVersion", versionString);
      generator.setVersionInfoString("FileDescription", productFullName);
      generator.setVersionInfoString("ProductVersion", versionString);
      generator.setVersionInfoString("InternalName", out.getName());
      generator.setVersionInfoString("OriginalFilename", out.getName());
      generator.setVersionNumber(majorVersion, minorVersion, bugfixVersion);

      generator.generate();
    } catch (IOException e) {
      e.printStackTrace();
      System.exit(10);
    }
  }

  private static Element getChild(Element appInfoRoot, String logo) {
    return appInfoRoot.getChild(logo, appInfoRoot.getNamespace());
  }

  private static Map<String, Integer> loadResourceIDs(String arg) throws IOException {
    Map<String, Integer> result = new HashMap<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(arg), StandardCharsets.UTF_8))) {
      Pattern pattern = Pattern.compile("#define (\\w+)\\s+(\\d+)");
      while (true) {
        String line = reader.readLine();
        if (line == null) break;
        Matcher m = pattern.matcher(line);
        if (m.matches()) {
          result.put(m.group(1), Integer.parseInt(m.group(2)));
        }
      }
    }
    return result;
  }
}
