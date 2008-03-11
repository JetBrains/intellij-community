package com.intellij.xdebugger;

import java.util.EventListener;

/**
 * @author nik
 */
public interface XDebugSessionListener extends EventListener {

  void sessionPaused();

  void sessionResumed();

}
