
package com.intellij.psi;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.testFramework.LightIdeaTestCase;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;

public abstract class ParsingTestCase extends LightIdeaTestCase {
  private final String myDataPath;
  protected String myFileExt;
  private final String myFullDataPath;

  public ParsingTestCase(String dataPath, String fileExt) {
    myDataPath = dataPath;
    myFullDataPath = PathManagerEx.getTestDataPath() + "/psi/" + myDataPath;
    myFileExt = fileExt;
  }

  protected void doTest(boolean checkResult) throws Exception{
    String name = getTestName(false);
    String text = loadFile(name + "." + myFileExt);
    PsiFile file = createFile(name + "." + myFileExt, text);
    if (checkResult){
      checkResult(name + ".txt", file);
    }
    else{
      DebugUtil.treeToString(com.intellij.psi.impl.source.SourceTreeToPsiMap.psiElementToTree(file), false);
    }
  }

  protected void checkResult(String targetDataName, final PsiFile file) throws Exception {
    String treeText = DebugUtil.treeToString(com.intellij.psi.impl.source.SourceTreeToPsiMap.psiElementToTree(file), false).trim();
    try{
      String expectedText = loadFile(targetDataName);
      /*
      if (!expectedText.equals(treeText)){
        int length = Math.min(expectedText.length(), treeText.length());
        for(int i = 0; i < length; i++){
          char c1 = expectedText.charAt(i);
          char c2 = treeText.charAt(i);
          if (c1 != c2){
            System.out.println("i = " + i);
            System.out.println("c1 = " + c1);
            System.out.println("c2 = " + c2);
          }
        }
      }
      */
      assertEquals(expectedText, treeText);
    }
    catch(FileNotFoundException e){
      String fullName = myFullDataPath + File.separatorChar + targetDataName;
      FileWriter writer = new FileWriter(fullName);
      writer.write(treeText);
      writer.close();
      assertTrue("No output file found. Created.", false);
    }
  }

  protected String loadFile(String name) throws Exception {
    String fullName = myFullDataPath + File.separatorChar + name;
    String text = new String(FileUtil.loadFileText(new File(fullName))).trim();
    text = StringUtil.convertLineSeparators(text);
    return text;
  }
}