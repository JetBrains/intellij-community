package de.plushnikov.intellij.plugin.extension.postfix;

public class LombokVarPostfixTemplate extends LombokVarValPostfixTemplate {

  public LombokVarPostfixTemplate() {
    super("varl", "lombok.experimental.var name = expr", "lombok.experimental.var");
  }
}
