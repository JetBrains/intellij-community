package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * @author nik
 */
public class XBreakpointImpl<T extends XBreakpointProperties> implements XBreakpoint<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xdebugger.impl.breakpoints.XBreakpointImpl");
  private XBreakpointType<T> myType;
  private T myProperties;
  private BreakpointState myState;

  public XBreakpointImpl(final XBreakpointType<T> type, final T properties) {
    myState = new BreakpointState();
    myState.setEnabled(true);
    myState.setTypeId(type.getId());
    myType = type;
    myProperties = properties;
  }

  public XBreakpointImpl(final XBreakpointType<T> type, BreakpointState breakpointState) {
    myState = breakpointState;
    myType = type;
    Class<T> propertiesClass = myType.getPropertiesClass();
    try {
      myProperties = propertiesClass.newInstance();
    }
    catch (Exception e) {
      LOG.error(e);
    }
    myProperties.loadState(XmlSerializer.deserialize(myState.getPropertiesElement(), getStateClass()));
  }

  private Class getStateClass() {
    Type type = resolveVariable(PersistentStateComponent.class.getTypeParameters()[0], myProperties.getClass());
    return ReflectionUtil.getRawType(type);
  }

  private static Type resolveVariable(final TypeVariable variable, final Class aClass) {
    Type type;
    Class current = aClass;
    while ((type = ReflectionUtil.resolveVariable(variable, current, false)) == null) {
      current = current.getSuperclass();
      if (current == null) {
        return null;
      }
    }
    if (type instanceof TypeVariable) {
      return resolveVariable((TypeVariable)type, aClass);
    }
    return type;
  }

  public boolean isEnabled() {
    return myState.isEnabled();
  }

  public void setEnabled(final boolean enabled) {
    myState.setEnabled(true);
  }

  public boolean isValid() {
    return true;
  }

  @NotNull
  public T getProperties() {
    return myProperties;
  }

  @NotNull
  public Icon getIcon() {
    throw new UnsupportedOperationException("'getIcon' not implemented in " + getClass().getName());
  }

  @NotNull
  public XBreakpointType getType() {
    return myType;
  }

  public BreakpointState getState() {
    myState.setPropertiesElement(XmlSerializer.serialize(myProperties.getState()));
    return myState;
  }

  @Tag("breakpoint")
  public static class BreakpointState {
    private boolean myEnabled;
    private String myTypeId;
    private Element myPropertiesElement;

    @Attribute("enabled")
    public boolean isEnabled() {
      return myEnabled;
    }

    public void setEnabled(final boolean enabled) {
      myEnabled = enabled;
    }

    @Attribute("type")
    public String getTypeId() {
      return myTypeId;
    }

    public void setTypeId(final String typeId) {
      myTypeId = typeId;
    }

    @Tag("properties")
    public Element getPropertiesElement() {
      return myPropertiesElement;
    }

    public void setPropertiesElement(final Element propertiesElement) {
      myPropertiesElement = propertiesElement;
    }
  }
}
