package testData.inspection.commentsWithMistakes.data.java.src;

/*
@author shkate@jetbrains.com
<li style="color;">some context</li>
*/
class SPITest1 {
  /* boolean is Java keyword
   <TYPO descr="Word 'commment' is misspelled">commment</TYPO>
  */
  // single line <TYPO descr="Word 'upgade' is misspelled">upgade</TYPO>
  void method() {
    /*
    <TYPO descr="Word 'werty' is misspelled">werty</TYPO> within method
   */
    // single line <TYPO descr="Word 'newss' is misspelled">newss</TYPO> within method
  }
}
