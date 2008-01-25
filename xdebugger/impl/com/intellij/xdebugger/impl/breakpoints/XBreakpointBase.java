package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.pom.Navigatable;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
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
public class XBreakpointBase<Self extends XBreakpoint<P>, P extends XBreakpointProperties, S extends XBreakpointBase.BreakpointState> extends UserDataHolderBase implements XBreakpoint<P> {
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

  @NotNull
  public SuspendPolicy getSuspendPolicy() {
    return myState.mySuspendPolicy;
  }

  public void setSuspendPolicy(@NotNull SuspendPolicy policy) {
    myState.mySuspendPolicy = policy;
    fireBreakpointChanged();
  }

  public boolean isLogMessage() {
    return myState.isLogMessage();
  }

  public void setLogMessage(final boolean logMessage) {
    myState.setLogMessage(logMessage);
    fireBreakpointChanged();
  }

  public String getLogExpression() {
    return myState.getLogExpression();
  }

  public void setLogExpression(@Nullable final String expression) {
    myState.setLogExpression(expression);
    fireBreakpointChanged();
  }

  public String getCondition() {
    return myState.getCondition();
  }

  public void setCondition(@Nullable final String condition) {
    myState.setCondition(condition);
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
    private String myTypeId;
    private boolean myEnabled;
    private Element myPropertiesElement;
    private SuspendPolicy mySuspendPolicy = SuspendPolicy.ALL;
    private boolean myLogMessage;
    private String myLogExpression;
    private String myCondition;

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

    @Attribute("suspend")
    public String getSuspendPolicy() {
      return mySuspendPolicy.name();
    }

    public void setSuspendPolicy(final String suspendPolicy) {
      mySuspendPolicy = SuspendPolicy.valueOf(suspendPolicy);
    }

    @Attribute("log-message")
    public boolean isLogMessage() {
      return myLogMessage;
    }

    public void setLogMessage(final boolean logMessage) {
      myLogMessage = logMessage;
    }

    @Tag("log-expression")
    public String getLogExpression() {
      return myLogExpression;
    }

    public void setLogExpression(final String logExpression) {
      myLogExpression = logExpression;
    }

    @Tag("condition")
    public String getCondition() {
      return myCondition;
    }

    public void setCondition(final String condition) {
      myCondition = condition;
    }

    public XBreakpointBase<B,P,?> createBreakpoint(@NotNull T type, @NotNull XBreakpointManagerImpl breakpointManager) {
      return new XBreakpointBase<B, P, BreakpointState<B,P,?>>(type, breakpointManager, this);
    }
  }
}
