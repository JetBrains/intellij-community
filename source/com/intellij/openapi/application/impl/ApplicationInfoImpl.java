
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Document;
import org.jdom.Element;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 *
 */
public class ApplicationInfoImpl extends ApplicationInfoEx implements JDOMExternalizable, ApplicationComponent {
  private final static String BUILD_STUB = "__BUILD_NUMBER__";
  private String myVersionName = null;
  private String myMajorVersion = null;
  private String myMinorVersion = null;
  private String myBuildNumber = null;
  private String myLogoUrl = null;
  private String myAboutLogoUrl = null;
  private Calendar myBuildDate = null;
  private String myPackageCode = null;
  private boolean myShowLicensee = true;

  public void initComponent() { }

  public void disposeComponent() {
  }

  public Calendar getBuildDate() {
    return myBuildDate;
  }

  public String getBuildNumber() {
    return myBuildNumber;
  }

  public String getMajorVersion() {
    return myMajorVersion;
  }

  public String getMinorVersion() {
    return myMinorVersion;
  }

  public String getVersionName() {
    return myVersionName;
  }

  public String getLogoUrl() {
    return myLogoUrl;
  }

  public String getAboutLogoUrl() {
    return myAboutLogoUrl;
  }

  public String getPackageCode() {
    return myPackageCode;
  }

  public String getFullApplicationName() {
    StringBuffer buffer = new StringBuffer();
    buffer.append(getVersionName());
    buffer.append(" ");
    if (getMajorVersion() != null){
      buffer.append(getMajorVersion());
    }
    else {
      String bn = getBuildNumber();
      if (!BUILD_STUB.equals(bn)) {
        buffer.append('#');
        buffer.append(bn);
      }
      else {
        buffer.append("DevVersion");
      }
    }
    if (getMinorVersion() != null && getMinorVersion().length() > 0){
      buffer.append(".");
      buffer.append(getMinorVersion());
    }
    return buffer.toString();
  }

  public boolean showLicenseeInfo() {
    return myShowLicensee;
  }

  public static ApplicationInfoEx getShadowInstance() {
    ApplicationInfoImpl instance = new ApplicationInfoImpl();
    try {
      Document doc = JDOMUtil.loadDocument(ApplicationInfoImpl.class.getResourceAsStream("/idea/" + COMPONENT_NAME + ".xml"));
      instance.readExternal(doc.getRootElement());
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return instance;
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    Element versionElement = parentNode.getChild("version");
    if (versionElement != null) {
      myMajorVersion = versionElement.getAttributeValue("major");
      myMinorVersion = versionElement.getAttributeValue("minor");
      myVersionName = versionElement.getAttributeValue("name");
    }

    Element buildElement = parentNode.getChild("build");
    if (buildElement != null) {
      myBuildNumber = buildElement.getAttributeValue("number");
      String dateString = buildElement.getAttributeValue("date");
      int year = 0;
      int month = 0;
      int day = 0;
      try {
        year = new Integer(dateString.substring(0, 4)).intValue();
        month = new Integer(dateString.substring(4, 6)).intValue();
        day = new Integer(dateString.substring(6, 8)).intValue();
      }
      catch (Exception ex) {
      }
      if (month > 0) {
        month--;
      }
      myBuildDate = new GregorianCalendar(year, month, day);
    }

    Element logoElement = parentNode.getChild("logo");
    if (logoElement != null) {
      myLogoUrl = logoElement.getAttributeValue("url");
    }

    Element aboutLogoElement = parentNode.getChild("about");
    if (aboutLogoElement != null) {
      myAboutLogoUrl = aboutLogoElement.getAttributeValue("url");
    }

    Element packageElement = parentNode.getChild("package");
    if (packageElement != null) {
      myPackageCode = packageElement.getAttributeValue("code");
    }

    Element showLicensee = parentNode.getChild("licensee");
    if (showLicensee != null) {
      myShowLicensee = Boolean.valueOf(showLicensee.getAttributeValue("show")).booleanValue();
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }


  public String getComponentName() {
    return COMPONENT_NAME;
  }

}
