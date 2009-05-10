package testData.inspection.localVariableNameWithMistakes.data.java.src;

class SPITest3 {
  public void method() {
    String <weak_warning descr="Word 'Ttest' is misspelled">camelCaseTtest</weak_warning> = "she is reading";
    String <weak_warning descr="Word 'ttest' is misspelled">ttest</weak_warning> = "she is reading";
  }
}
