
package com.intellij.ide.util.gotoByName;

import javax.swing.*;

public interface ChooseByNameModel {
  String getPromptText();

  String getNotInMessage();
  String getNotFoundMessage();
  String getCheckBoxName();
  char getCheckBoxMnemonic();
  boolean loadInitialCheckBoxState();
  void saveInitialCheckBoxState(boolean state);

  ListCellRenderer getListCellRenderer();

  String[] getNames(boolean checkBoxState);
  Object[] getElementsByName(String name, boolean checkBoxState);
  String getElementName(Object element);
}