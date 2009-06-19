package testData.inspection.localVariableNameWithMistakes.data.java.src;

class SPITest3 {
  public void method() {
    String <MISSPELLED descr="Word 'Ttest' is misspelled">camelCaseTtest</MISSPELLED> = "she is reading";
    String <MISSPELLED descr="Word 'ttest' is misspelled">ttest</MISSPELLED> = "she is reading";
  }
}
