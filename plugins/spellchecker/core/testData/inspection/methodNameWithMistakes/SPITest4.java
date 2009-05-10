package testData.inspection.methodNameWithMistakes.data.java.src;

class SPITest4 {
  public void <weak_warning descr="Word 'Ttest' is misspelled">methodTtestWithMistake</weak_warning>() {
  }
  public void <weak_warning descr="Word 'methad' is misspelled">methad</weak_warning>() {
  }
}
