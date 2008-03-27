package com.intellij.slicer;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;

/**
 * @author cdr
 */
public class SliceBackwardAction extends CodeInsightAction{
  private final SliceBackwardHandler myHandler = new SliceBackwardHandler();

  protected CodeInsightActionHandler getHandler() {
    return myHandler;
  }
}
