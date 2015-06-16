/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package testData.inspection.commentsWithMistakes.data.java.src;

/*
@author shkate@jetbrains.com
<li style="color;">some context</li> attribute's
*/
class SPITest1 {
  /* boolean is Java keyword
   <TYPO descr="Typo: In word 'commment'">commment</TYPO>
  */
  // single line <TYPO descr="Typo: In word 'upgade'">upgade</TYPO>
  void method() {
    /*
    <TYPO descr="Typo: In word 'werty'">werty</TYPO> within method
   */
    // single line <TYPO descr="Typo: In word 'newss'">newss</TYPO> within method
  }
}
