package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.project.Project;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.XSourcePosition;
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
public class XBreakpointBase<Self extends XBreakpoint<P>, P extends XBreakpointProperties, S extends XBreakpointBase.BreakpointState> implements XBreakpoint<P> {
  private final XBreakpointType<Self, P> myType;
  private final @Nullable P myProperties;
  private final S myState;
  protected final Project myProject;

  public XBreakpointBase(final XBreakpointType<Self, P> type, final Project project, final @Nullable P properties, final S state) {
    myState = state;
    myType = type;
    myProperties = properties;
    myProject = project;
  }

  protected XBreakpointBase(final XBreakpointType<Self, P> type, final Project project, S breakpointState) {
    myState = breakpointState;
    myType = type;
    myProject = project;
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

  public XSourcePosition getSourcePosition() {
    return null;
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

  @Nullable 
  public P getProperties() {
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

  public void dispose() {
  }

  @Tag("breakpoint")
  public static class BreakpointState<B extends XBreakpoint<P>, P extends XBreakpointProperties> {
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

    public XBreakpointBase<B,P,?> createBreakpoint(@NotNull XBreakpointType<B,P> type, final Project project) {
      return new XBreakpointBase<B,P, BreakpointState>(type, project, this);
    }
  }
}
