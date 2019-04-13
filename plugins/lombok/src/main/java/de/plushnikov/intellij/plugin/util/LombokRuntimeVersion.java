package de.plushnikov.intellij.plugin.util;

public class LombokRuntimeVersion {
  public String findCurrentLombokVersion() {
    //"1.18.4" -> @FieldNameConstants redesigned
    //"1.18.2" -> @SuperBuilder added
    //"1.18.0" -> @Flogger added
    //"1.16.22" -> lombok.experimental.Builder and lombok.experimental.Value were removed
    //"1.16.16" -> @Builder.Default added
    //"1.16.12" -> @var added
    //"1.16.10" -> JBoss logger added
    //"1.16.6" -> @Helper added

    return "";
  }
}
