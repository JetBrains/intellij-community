package testData.inspection.fieldNameWithMistakes.data.java.src;

class SPITest2 {
 private static final String <weak_warning descr="Word 'CONASTANT' is misspelled">TEST_CONASTANT</weak_warning> = "Test Constant Value";
  private String <weak_warning descr="Word 'ttest' is misspelled">ttest</weak_warning>;
  private String <weak_warning descr="Word 'Ttest' is misspelled">camelCaseTtest</weak_warning>;
}
