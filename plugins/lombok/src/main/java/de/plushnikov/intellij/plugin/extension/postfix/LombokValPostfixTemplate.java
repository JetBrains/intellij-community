package de.plushnikov.intellij.plugin.extension.postfix;

public class LombokValPostfixTemplate extends LombokVarValPostfixTemplate {

  public LombokValPostfixTemplate() {
    super("val", "lombok.val name = expr", "lombok.val");
  }
}
