package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
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

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * @author nik
 */
public class XBreakpointBase<Self extends XBreakpoint<P>, P extends XBreakpointProperties, S extends XBreakpointBase.BreakpointState> implements XBreakpoint<P> {
  private final XBreakpointType<Self, P> myType;
  private final @Nullable P myProperties;
  private final S myState;
  private final XBreakpointManagerImpl myBreakpointManager;

  public XBreakpointBase(final XBreakpointType<Self, P> type, XBreakpointManagerImpl breakpointManager, final @Nullable P properties, final S state) {
    myState = state;
    myType = type;
    myProperties = properties;
    myBreakpointManager = breakpointManager;
  }

  protected XBreakpointBase(final XBreakpointType<Self, P> type, XBreakpointManagerImpl breakpointManager, S breakpointState) {
    myState = breakpointState;
    myType = type;
    myBreakpointManager = breakpointManager;
    myProperties = type.createProperties();
    if (myProperties != null) {
      Object state = XmlSerializer.deserialize(myState.getPropertiesElement(), getStateClass(myProperties.getClass()));
      myProperties.loadState(state);
    }
  }

  protected final Project getProject() {
    return myBreakpointManager.getProject();
  }

  protected final void fireBreakpointChanged() {
    myBreakpointManager.fireBreakpointChanged(this);
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

  public Navigatable getNavigatable() {
    XSourcePosition position = getSourcePosition();
    return position != null ? new OpenFileDescriptor(getProject(), position.getFile(), position.getOffset()) : null;
  }

  public boolean isEnabled() {
    return myState.isEnabled();
  }

  public void setEnabled(final boolean enabled) {
    myState.setEnabled(enabled);
    fireBreakpointChanged();
  }

  public boolean isValid() {
    return true;
  }

  @Nullable 
  public P getProperties() {
    return myProperties;
  }

  @NotNull
  public XBreakpointType<Self,P> getType() {
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
  public static class BreakpointState<B extends XBreakpoint<P>, P extends XBreakpointProperties, T extends XBreakpointType<B,P>> {
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

    public XBreakpointBase<B,P,?> createBreakpoint(@NotNull T type, @NotNull XBreakpointManagerImpl breakpointManager) {
      return new XBreakpointBase<B, P, BreakpointState<B,P,?>>(type, breakpointManager, this);
    }
  }
}
