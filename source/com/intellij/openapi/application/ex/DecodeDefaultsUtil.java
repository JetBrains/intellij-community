package com.intellij.openapi.application.ex;

import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.util.text.StringUtil;

import java.io.BufferedInputStream;
import java.io.InputStream;

public class DecodeDefaultsUtil {
  public static InputStream getDefaultsInputStream(BaseComponent component) {
    InputStream stream = getDefaultsInputStream(component, component.getComponentName());
    if (stream != null) {
      return new BufferedInputStream(stream);
    }

    return null;
  }

  public static InputStream getDefaultsInputStream(Object requestor, final String componentResourcePath) {
    boolean isPathAbsoulte = StringUtil.startsWithChar(componentResourcePath, '/');
    if (isPathAbsoulte) {
      return requestor.getClass().getResourceAsStream(componentResourcePath + ".xml");
    }
    else {
      return getResourceStreamByRelativePath(requestor, componentResourcePath, ".xml");
    }
  }

  private static InputStream getResourceStreamByRelativePath(Object requestor, final String componentResourcePath, String resourceExtension) {
    String appName = ApplicationManagerEx.getApplicationEx().getName();
    InputStream resultStream = requestor.getClass().getResourceAsStream("/" + appName + "/" + componentResourcePath + resourceExtension);
    if (resultStream == null) {
      resultStream = requestor.getClass().getResourceAsStream("/" + componentResourcePath + resourceExtension);
    }
    return resultStream;
  }
}
