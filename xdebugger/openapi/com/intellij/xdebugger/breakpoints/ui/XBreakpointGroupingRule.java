package com.intellij.xdebugger.breakpoints.ui;

import com.intellij.xdebugger.breakpoints.XBreakpoint;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class XBreakpointGroupingRule<B extends XBreakpoint<?>, G extends XBreakpointGroup> {
  private final String myId;
  private String myPresentableName;

  protected XBreakpointGroupingRule(final @NotNull @NonNls String id, final @NonNls @Nls String presentableName) {
    myId = id;
    myPresentableName = presentableName;
  }

  @NotNull
  public String getPresentableName() {
    return myPresentableName;
  }

  @NotNull 
  public String getId() {
    return myId;
  }

  @Nullable
  public abstract G getGroup(@NotNull B breakpoint, @NotNull Collection<G> groups);
}
