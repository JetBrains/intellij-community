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

/*some <TYPO descr="Word 'commments' is misspelled">commments</TYPO> */
function  test<TYPO descr="Word 'Fuunction' is misspelled">Fuunction</TYPO>(){
  var <TYPO descr="Word 'upddate' is misspelled">upddate</TYPO> = "test variable";
}

var obj = {

};
obj.<TYPO descr="Word 'ttest' is misspelled">ttest</TYPO> = function(){

};