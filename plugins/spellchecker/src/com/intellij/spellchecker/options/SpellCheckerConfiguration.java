package com.intellij.spellchecker.options;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(
  name = "spellchecker-configuration",
  storages = {
    @Storage(
    id = "other",
    file = "$PROJECT_FILE$"),
    @Storage(
    id = "dir",
    file = "$PROJECT_CONFIG_DIR$/spellchecker.xml",
    scheme = StorageScheme.DIRECTORY_BASED)})
public final class SpellCheckerConfiguration implements PersistentStateComponent<SpellCheckerConfiguration> {

 
  public SpellCheckerConfiguration getState() {
    return this;
  }

  public void loadState(SpellCheckerConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }

}
