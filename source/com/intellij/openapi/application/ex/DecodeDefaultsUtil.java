package com.intellij.openapi.application.ex;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class DecodeDefaultsUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.ex.DecodeDefaultsUtil");

  @NonNls private static final String XML_EXTENSION = ".xml";

  @Nullable
  public static InputStream getDefaultsInputStream(BaseComponent component) {
    InputStream stream = getDefaultsInputStream(component, component.getComponentName());
    if (stream != null) {
      return new BufferedInputStream(stream);
    }

    return null;
  }

  public static URL getDefaults(Object requestor, final String componentResourcePath) {
    boolean isPathAbsoulte = StringUtil.startsWithChar(componentResourcePath, '/');
    if (isPathAbsoulte) {
      return requestor.getClass().getResource(componentResourcePath + XML_EXTENSION);
    }
    else {
      return getResourceByRelativePath(requestor, componentResourcePath, XML_EXTENSION);
    }
  }


  @Nullable
  public static InputStream getDefaultsInputStream(Object requestor, final String componentResourcePath) {
    try {
      final URL defaults = getDefaults(requestor, componentResourcePath);
      return defaults != null ? defaults.openStream() : null;
    }
    catch (IOException e) {
      LOG.error(e);
      return null;
    }
  }

  private static URL getResourceByRelativePath(Object requestor, final String componentResourcePath, String resourceExtension) {
    String appName = ApplicationManagerEx.getApplicationEx().getName();
    URL result = requestor.getClass().getResource("/" + appName + "/" + componentResourcePath + resourceExtension);
    if (result == null) {
      result = requestor.getClass().getResource("/" + componentResourcePath + resourceExtension);
    }
    return result;
  }
}
