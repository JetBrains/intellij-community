package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.folding.impl.CollapseSelectionHandler;

/**
 * Created by IntelliJ IDEA.
 * User: ven
 * Date: Apr 10, 2003
 * Time: 9:59:35 PM
 * To change this template use Options | File Templates.
 */
public class CollapseSelectionAction extends BaseCodeInsightAction {
  protected CodeInsightActionHandler getHandler() {
    return new CollapseSelectionHandler();
  }
}
