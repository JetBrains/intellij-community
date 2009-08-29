package testData.inspection.fieldNameWithMistakes.data.java.src;

class SPITest2 {
 private static final String TEST_<TYPO descr="Word 'CONASTANT' is misspelled">CONASTANT</TYPO> = "Test Constant Value";
  private String <TYPO descr="Word 'ttest' is misspelled">ttest</TYPO>;
  private String camelCase<TYPO descr="Word 'Ttest' is misspelled">Ttest</TYPO>;
}
