package com.intellij.diagnostic;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.apache.commons.codec.binary.Base64;
import org.jdom.Element;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Aug 11, 2003
 * Time: 8:59:04 PM
 * To change this template use Options | File Templates.
 */
public class ErrorReportConfigurable implements JDOMExternalizable, ApplicationComponent {
  public String ITN_LOGIN = "";
  public String ITN_PASSWORD_CRYPT = "";
  public boolean KEEP_ITN_PASSWORD = false;

  public String EMAIL = "";

  public static ErrorReportConfigurable getInstance() {
    return ServiceManager.getService(ErrorReportConfigurable.class);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    if (! KEEP_ITN_PASSWORD)
      ITN_PASSWORD_CRYPT = "";
  }

  public void writeExternal(Element element) throws WriteExternalException {
    String itnPassword = ITN_PASSWORD_CRYPT;
    if (! KEEP_ITN_PASSWORD)
      ITN_PASSWORD_CRYPT = "";
    DefaultJDOMExternalizer.writeExternal(this, element);

    ITN_PASSWORD_CRYPT = itnPassword;
  }

  public String getComponentName() {
    return "ErrorReportConfigurable";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public String getPlainItnPassword () {
    return new String(new Base64().decode(ErrorReportConfigurable.getInstance().ITN_PASSWORD_CRYPT.getBytes()));
  }

  public void setPlainItnPassword (String password) {
    ITN_PASSWORD_CRYPT = new String(new Base64().encode(password.getBytes()));
  }
}
