package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.ui.navigation.History;

public interface HistoryAware {


  interface Configurable {

    void setHistoryFacade(Facade facade);

  }

  interface Facade {

    boolean isHistoryNavigatedNow();

    History getHistory();

    ActionCallback selectInTree(NamedConfigurable configurable);

  }

}
