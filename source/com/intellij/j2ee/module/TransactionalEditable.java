package com.intellij.j2ee.module;

import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.options.ConfigurationException;

public interface TransactionalEditable {
  void startEdit(ModifiableRootModel rootModel);

  J2EEModuleContainer getModifiableModel();

  void commit(ModifiableRootModel model) throws ConfigurationException;

  boolean isModified(ModifiableRootModel model);
}