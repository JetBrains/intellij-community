package testData.inspection.fieldNameWithMistakes.data.java.src;

class SPITest2 {
 private static final String <TYPO descr="Word 'CONASTANT' is misspelled">TEST_CONASTANT</TYPO> = "Test Constant Value";
  private String <TYPO descr="Word 'ttest' is misspelled">ttest</TYPO>;
  private String <TYPO descr="Word 'Ttest' is misspelled">camelCaseTtest</TYPO>;
}
