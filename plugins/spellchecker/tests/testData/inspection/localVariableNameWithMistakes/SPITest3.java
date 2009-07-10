package testData.inspection.localVariableNameWithMistakes.data.java.src;

class SPITest3 {
  public void method() {
    String <TYPO descr="Word 'Ttest' is misspelled">camelCaseTtest</TYPO> = "she is reading";
    String <TYPO descr="Word 'ttest' is misspelled">ttest</TYPO> = "she is reading";
  }
}
