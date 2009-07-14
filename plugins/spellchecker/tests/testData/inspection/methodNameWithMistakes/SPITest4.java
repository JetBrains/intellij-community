package testData.inspection.methodNameWithMistakes.data.java.src;

class SPITest4 {
  public void method<TYPO descr="Word 'Ttest' is misspelled">Ttest</TYPO>WithMistake() {
  }
  public void <TYPO descr="Word 'methad' is misspelled">methad</TYPO>() {
  }
}
