
package com.intellij.codeInsight.generation.surroundWith;

class JavaWithTryCatchFinallySurrounder extends JavaWithTryCatchSurrounder{
  public String getTemplateDescription() {
    return "try / catch / finally";
  }

  public JavaWithTryCatchFinallySurrounder() {
    myGenerateFinally = true;
  }
}