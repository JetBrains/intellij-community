package testData.inspection.commentsWithMistakes.data.java.src;

/*
@author shkate@jetbrains.com
<li style="color;">some context</li>
*/
class SPITest1 {
  /* boolean is Java keyword
   <MISSPELLED descr="Word 'commment' is misspelled">commment</MISSPELLED>
  */
  // single line <MISSPELLED descr="Word 'commment' is misspelled">commment</MISSPELLED>
  void method() {
    /*
    <MISSPELLED descr="Word 'commment' is misspelled">commment</MISSPELLED> within method
   */
    // single line <MISSPELLED descr="Word 'commment' is misspelled">commment</MISSPELLED> within method
  }
}
