package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * @author nik
 */
public class XBreakpointBase<T extends XBreakpointProperties, S extends XBreakpointBase.BreakpointState> implements XBreakpoint<T> {
  private final XBreakpointType<T> myType;
  private final @Nullable T myProperties;
  private final S myState;

  public XBreakpointBase(final XBreakpointType<T> type, final @Nullable T properties, final S state) {
    myState = state;
    myType = type;
    myProperties = properties;
  }

  protected XBreakpointBase(final XBreakpointType<T> type, S breakpointState) {
    myState = breakpointState;
    myType = type;
    myProperties = type.createProperties();
    if (myProperties != null) {
      Object state = XmlSerializer.deserialize(myState.getPropertiesElement(), getStateClass(myProperties.getClass()));
      myProperties.loadState(state);
    }
  }

  private static Class getStateClass(final Class<? extends XBreakpointProperties> propertiesClass) {
    Type type = resolveVariable(PersistentStateComponent.class.getTypeParameters()[0], propertiesClass);
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

  public S getState() {
    Element propertiesElement = myProperties != null ? XmlSerializer.serialize(myProperties.getState()) : null;
    myState.setPropertiesElement(propertiesElement);
    return myState;
  }

  @Tag("breakpoint")
  public static class BreakpointState {
    private boolean myEnabled;
    private String myTypeId;
    private Element myPropertiesElement;

    public BreakpointState() {
    }

    public BreakpointState(final boolean enabled, final String typeId) {
      myEnabled = enabled;
      myTypeId = typeId;
    }

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

    public <T extends XBreakpointProperties> XBreakpointBase<T,?> createBreakpoint(@NotNull XBreakpointType<T> type) {
      return new XBreakpointBase<T, BreakpointState>(type, this);
    }
  }
}
