package com.intellij.diagnostic;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.apache.xmlrpc.Base64;
import org.jdom.Element;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Aug 11, 2003
 * Time: 8:59:04 PM
 * To change this template use Options | File Templates.
 */
public class ErrorReportConfigurable implements JDOMExternalizable, ApplicationComponent {
  private static final String CLOSED = "closed";
  private static final String HASH = "hash";
  private static final String ID = "id";
  private static final String THREAD = "thread";

  public String ITN_LOGIN = "";
  public String ITN_PASSWORD_CRYPT = "";
  public boolean KEEP_ITN_PASSWORD = false;

  public String EMAIL = "";

  private Map<String, String> closedExceptions = new HashMap<String, String>();

  public static ErrorReportConfigurable getInstance() {
    return ApplicationManager.getApplication().getComponent(ErrorReportConfigurable.class);
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    if (! KEEP_ITN_PASSWORD)
      ITN_PASSWORD_CRYPT = "";

    Element closed = element.getChild(CLOSED);
    if (closed != null) {
      List hashElements = closed.getChildren(HASH);

      if (hashElements != null)
        for (int i = 0; i < hashElements.size(); i++) {
          Element exceptionId = (Element)hashElements.get(i);

          closedExceptions.put(exceptionId.getAttributeValue(ID), exceptionId.getAttributeValue(THREAD));
        }
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    String itnPassword = ITN_PASSWORD_CRYPT;
    if (! KEEP_ITN_PASSWORD)
      ITN_PASSWORD_CRYPT = "";

    Element closedHash = new Element (CLOSED);
    Iterator <String> it = closedExceptions.keySet().iterator();
    while (it.hasNext()) {
      String id = it.next();
      String thread = closedExceptions.get(id);

      Element hash = new Element (HASH);
      hash.setAttribute(ID, id);
      hash.setAttribute(THREAD, thread);

      closedHash.addContent(hash);
    }

    element.addContent(closedHash);

    DefaultJDOMExternalizer.writeExternal(this, element);

    ITN_PASSWORD_CRYPT = itnPassword;
  }

  public boolean isClosed (String hashId) {
    return closedExceptions.containsKey(hashId);
  }

  public String getClosedThread (String hashId) {
    return closedExceptions.get(hashId);
  }

  public void addClosed (String hashId, String thread) {
    closedExceptions.put(hashId, thread);
  }

  public String getComponentName() {
    return "ErrorReportConfigurable";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public String getPlainItnPassword () {
    return new String(Base64.decode(ErrorReportConfigurable.getInstance().ITN_PASSWORD_CRYPT.getBytes()));
  }

  public void setPlainItnPassword (String password) {
    ITN_PASSWORD_CRYPT = new String(Base64.encode(new String(password).getBytes()));
  }
}
