
package com.intellij.codeInsight.generation.surroundWith;

class SurroundWithTryCatchFinallyHandler extends SurroundWithTryCatchHandler{
  public SurroundWithTryCatchFinallyHandler() {
    myGenerateFinally = true;
  }
}