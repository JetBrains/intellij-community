package com.intellij.facet.impl.ui.libraries.versions;

import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LibrariesConfigurationManager implements Disposable {

  private static final String REQUIRED_CLASSES_DELIMITER = ",";
  private static final String RI_TEMPLATE = "$RI$";
  private static final String VERSION_TEMPLATE = "$VERSION$";

  @NotNull
  public static Map<LibraryVersionInfo, List<LibraryInfo>> getLibraries(final String... urls) {
    final Map<LibraryVersionInfo, List<LibraryInfo>> versionLibs = new HashMap<LibraryVersionInfo, List<LibraryInfo>>();

    for (String url : urls) {
      final LibrariesConfigurationInfo libs =
        XmlSerializer.deserialize(LibrariesConfigurationInfo.class.getResource(url), LibrariesConfigurationInfo.class);

      assert libs != null;
      assert libs.getLibraryConfigurationInfos() != null;

      final String defaultVersion = libs.getDefaultVersion();
      final String defaultRI = libs.getDefaultRI();
      final String defaultDownloadUrl = libs.getDefaultDownloadUrl();
      final String defaultPresentationUrl = libs.getDefaultPresentationUrl();


      for (LibraryConfigurationInfo libInfo : libs.getLibraryConfigurationInfos()) {
        final String version = choose(libInfo.getVersion(), defaultVersion);

        assert !StringUtil.isEmptyOrSpaces(version);

        final String ri = choose(libInfo.getRI(), defaultRI);
        final String downloadUrl = choose(libInfo.getDownloadUrl(), defaultDownloadUrl);
        final String presentationdUrl = choose(libInfo.getPresentationdUrl(), defaultPresentationUrl);


        final LibraryVersionInfo versionInfo = new LibraryVersionInfo(version, ri);
        final LibraryInfo info = createLibraryInfo(downloadUrl, presentationdUrl, version, ri, libInfo);

        if (versionLibs.get(versionInfo) == null) versionLibs.put(versionInfo, new ArrayList<LibraryInfo>());

        versionLibs.get(versionInfo).add(info);
      }
    }
    return versionLibs;
  }

  @Nullable
  private static String choose(@Nullable String str, @Nullable String defaultStr) {
    return StringUtil.isEmptyOrSpaces(str) ? defaultStr : str;
  }

  private static LibraryInfo createLibraryInfo(String downloadUrl, String presentationdUrl, String version, String ri,
                                               LibraryConfigurationInfo libInfo) {

    downloadUrl = downloadUrl.replace(VERSION_TEMPLATE, version);
    if (ri != null) {
      downloadUrl = downloadUrl.replace(RI_TEMPLATE, ri);
    }

    String jarName = libInfo.getJarName();
    return new LibraryInfo(jarName, version, downloadUrl + jarName, presentationdUrl,
                           getRequredClasses(libInfo.getRequiredClasses()));
  }

  private static String[] getRequredClasses(final String requiredClasses) {
    final List<String> strings = StringUtil.split(requiredClasses, REQUIRED_CLASSES_DELIMITER);
    return ArrayUtil.toStringArray(strings);
  }

  public void dispose() {

  }
}