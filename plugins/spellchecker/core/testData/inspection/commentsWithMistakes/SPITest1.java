package testData.inspection.commentsWithMistakes.data.java.src;

class SPITest1 {
  /* boolean is Java keyword
   <weak_warning descr="Word 'commment' is misspelled">commment</weak_warning>
  */
  // single line <weak_warning descr="Word 'commment' is misspelled">commment</weak_warning>
  void method() {
    /*
    <weak_warning descr="Word 'commment' is misspelled">commment</weak_warning> within method
   */
    // single line <weak_warning descr="Word 'commment' is misspelled">commment</weak_warning> within method
  }
}
