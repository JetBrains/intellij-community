package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

/**
 * @author nik
*/
@Tag("dependency")
public class XBreakpointDependencyState {
  private String myId;
  private String myMasterBreakpointId;
  private boolean myLeaveEnabled = false;

  public XBreakpointDependencyState() {
  }

  public XBreakpointDependencyState(final String id) {
    myId = id;
  }

  public XBreakpointDependencyState(final String id, final String masterBreakpointId, final boolean leaveEnabled) {
    myId = id;
    myMasterBreakpointId = masterBreakpointId;
    myLeaveEnabled = leaveEnabled;
  }

  @Attribute("id")
  public String getId() {
    return myId;
  }

  @Attribute("master-id")
  public String getMasterBreakpointId() {
    return myMasterBreakpointId;
  }

  @Attribute("leave-enabled")
  public boolean isLeaveEnabled() {
    return myLeaveEnabled;
  }

  public void setId(final String id) {
    myId = id;
  }

  public void setMasterBreakpointId(final String masterBreakpointId) {
    myMasterBreakpointId = masterBreakpointId;
  }

  public void setLeaveEnabled(final boolean leaveEnabled) {
    myLeaveEnabled = leaveEnabled;
  }
}
