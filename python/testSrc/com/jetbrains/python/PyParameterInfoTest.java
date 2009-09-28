package com.jetbrains.python;

import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContextEx;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Tests parameter info available via ^P at call sites.
 * <br/>User: dcheryasov
 * Date: Jul 14, 2009 3:42:44 AM
 */
public class PyParameterInfoTest extends MarkedTestCase {
  protected String getTestDataPath() {
    return PythonTestUtil.getTestDataPath()+ "/paramInfo/";
  }

  public void testSimpleFunction() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 3);

    PsiElement arg1 = marks.get("<arg1>");
    feignCtrlP(arg1.getTextOffset()).check("a,b,c", new String[]{"a,"});
    feignCtrlP(arg1.getTextOffset()+1).check("a,b,c", new String[]{"a,"});
    feignCtrlP(arg1.getTextOffset()-3).assertNotFound(); // ^P before arglist gives nothing

    PsiElement arg2 = marks.get("<arg2>");
    feignCtrlP(arg2.getTextOffset()).check("a,b,c", new String[]{"b,"});
    feignCtrlP(arg2.getTextOffset()+1).check("a,b,c", new String[]{"b,"});
    feignCtrlP(arg2.getTextOffset()+2).check("a,b,c", new String[]{"c"}); // one too far after arg2, and we came to arg3

    PsiElement arg3 = marks.get("<arg3>");
    feignCtrlP(arg3.getTextOffset()).check("a,b,c", new String[]{"c"});
    feignCtrlP(arg3.getTextOffset()+1).check("a,b,c", new String[]{"c"});
    feignCtrlP(arg3.getTextOffset()-1).check("a,b,c", new String[]{"c"}); // space before arg goes to that arg
    feignCtrlP(arg3.getTextOffset()+2).assertNotFound(); // ^P on a ")" gives nothing
  }

  public void testStarredFunction() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 4);

    PsiElement arg1 = marks.get("<arg1>");
    feignCtrlP(arg1.getTextOffset()).check("a,b,*c", new String[]{"a,"});
    feignCtrlP(arg1.getTextOffset()+1).check("a,b,*c", new String[]{"a,"});
    
    PsiElement arg2 = marks.get("<arg2>");
    feignCtrlP(arg2.getTextOffset()).check("a,b,*c", new String[]{"b,"});
    feignCtrlP(arg2.getTextOffset()+1).check("a,b,*c", new String[]{"b,"});
    
    PsiElement arg3 = marks.get("<arg3>");
    feignCtrlP(arg3.getTextOffset()).check("a,b,*c", new String[]{"*c"});
    feignCtrlP(arg3.getTextOffset()+1).check("a,b,*c", new String[]{"*c"});

    PsiElement arg4 = marks.get("<arg4>");
    feignCtrlP(arg4.getTextOffset()).check("a,b,*c", new String[]{"*c"});
    feignCtrlP(arg4.getTextOffset()+1).check("a,b,*c", new String[]{"*c"});
    feignCtrlP(arg4.getTextOffset()+2).assertNotFound();
  }

  public void testKwdFunction() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 4);

    PsiElement arg1 = marks.get("<arg1>");
    feignCtrlP(arg1.getTextOffset()).check("a,b,**c", new String[]{"a,"});
    feignCtrlP(arg1.getTextOffset()+1).check("a,b,**c", new String[]{"a,"});

    PsiElement arg2 = marks.get("<arg2>");
    feignCtrlP(arg2.getTextOffset()).check("a,b,**c", new String[]{"b,"});
    feignCtrlP(arg2.getTextOffset()+1).check("a,b,**c", new String[]{"b,"});


    PsiElement arg3 = marks.get("<arg3>");
    feignCtrlP(arg3.getTextOffset()).check("a,b,**c", new String[]{"**c"});
    feignCtrlP(arg3.getTextOffset()+1).check("a,b,**c", new String[]{"**c"});

    PsiElement arg4 = marks.get("<arg4>");
    feignCtrlP(arg4.getTextOffset()).check("a,b,**c", new String[]{"**c"});
    feignCtrlP(arg4.getTextOffset()+1).check("a,b,**c", new String[]{"**c"});
  }

  public void testKwdOutOfOrder() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 4);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b,**c", new String[]{"**c"});

    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,b,**c", new String[]{"b,"});

    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a,b,**c", new String[]{"a,"});

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b,**c", new String[]{"**c"});
  }

  public void testStarArg() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b,c", new String[]{"a,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,b,c", new String[]{"b,","c"});
    feignCtrlP(marks.get("<arg2a>").getTextOffset()).check("a,b,c", new String[]{"b,","c"});
  }

  public void testKwdArg() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b,c", new String[]{"a,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,b,c", new String[]{"b,","c"});
    feignCtrlP(marks.get("<arg2a>").getTextOffset()).check("a,b,c", new String[]{"b,","c"});
  }

  public void testKwdArgOutOfOrder() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b,c", new String[]{"b,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,b,c", new String[]{"a,","c"});
    feignCtrlP(marks.get("<arg2a>").getTextOffset()).check("a,b,c", new String[]{"a,","c"});
  }

  public void testStarredAndKwdFunction() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 6);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b,*c,**d", new String[]{"a,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,b,*c,**d", new String[]{"b,"});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a,b,*c,**d", new String[]{"*c,"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("a,b,*c,**d", new String[]{"*c,"});
    feignCtrlP(marks.get("<arg5>").getTextOffset()).check("a,b,*c,**d", new String[]{"**d"});
    feignCtrlP(marks.get("<arg6>").getTextOffset()).check("a,b,*c,**d", new String[]{"**d"});
  }

  public void testNestedArg() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 4);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,(b,c),d", new String[]{"a,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,(b,c),d", new String[]{"b,"});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a,(b,c),d", new String[]{"c"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("a,(b,c),d", new String[]{"d"});

    feignCtrlP(marks.get("<arg2>").getTextOffset()-2).check("a,(b,c),d", new String[]{}); // before nested tuple: no arg matches
  }

  public void testDoubleNestedArg() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 5);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,(b,(c,d)),e", new String[]{"a,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,(b,(c,d)),e", new String[]{"b,"});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a,(b,(c,d)),e", new String[]{"c,"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("a,(b,(c,d)),e", new String[]{"d"});
    feignCtrlP(marks.get("<arg5>").getTextOffset()).check("a,(b,(c,d)),e", new String[]{"e"});
  }

  public void testNestedMultiArg() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 3);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,(b,c),d", new String[]{"a,"});
    feignCtrlP(marks.get("<arg23>").getTextOffset()).check("a,(b,c),d", new String[]{"b,","c"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("a,(b,c),d", new String[]{"d"});
  }

  public void testStarredParam() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b,c", new String[]{"a,"});
    feignCtrlP(marks.get("<arg23>").getTextOffset()).check("a,b,c", new String[]{"b,","c"});
  }

  public void testStarredParamAndArg() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 4);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("a,b,*c", new String[]{"a,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("a,b,*c", new String[]{"b,"});
    feignCtrlP(marks.get("<arg3>").getTextOffset()).check("a,b,*c", new String[]{"*c"});
    feignCtrlP(marks.get("<arg4>").getTextOffset()).check("a,b,*c", new String[]{"*c"});
  }


  public void testSimpleMethod() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 1);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self,a", new String[]{"a"}, new String[]{"self,"});
  }

  public void testSimpleClassFunction() throws Exception {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", marks.size(), 2);

    feignCtrlP(marks.get("<arg1>").getTextOffset()).check("self,a", new String[]{"self,"});
    feignCtrlP(marks.get("<arg2>").getTextOffset()).check("self,a", new String[]{"a"});
  }

  // TODO: add method tests with decorators when a mock SDK is available 

  /**
   * Imitates pressing of Ctrl+P; fails if results are not as expected.
   * @param offset offset of 'cursor' where ^P is pressed.
   * @return a {@link Collector} with collected hint info.
   * @throws Exception if it fails
   */
  private Collector feignCtrlP(int offset) throws Exception {
    Collector collector = new Collector(getProject(), getFile(), offset);
    PyParameterInfoHandler handler = new PyParameterInfoHandler();
    collector.setParameterOwner(handler.findElementForParameterInfo(collector)); // finds arglist, sets items to show
    if (collector.getParameterOwner() != null) {
      assertEquals("Collected one analysis result", 1, collector.myItems.length);
      handler.updateParameterInfo((PyArgumentList)collector.getParameterOwner(), collector); // moves offset to correct parameter
      handler.updateUI((PyArgumentList.AnalysisResult)collector.getItemsToShow()[0], collector); // sets hint text and flags
    }
    return collector;
  }

  /**
   * Imitates the normal UI contexts to the extent we use it. Collects highlighting.
   */
  private static class Collector implements ParameterInfoUIContextEx, CreateParameterInfoContext, UpdateParameterInfoContext {

    private PsiFile myFile;
    private int myOffset;
    private int myIndex;
    private Object[] myItems;
    private Project myProject;
    private Editor myEditor;
    private PyArgumentList myParamOwner;
    private String[] myTexts;
    private EnumSet<Flag>[] myFlags;

    private Collector(Project project, PsiFile file, int offset) {
      myProject = project;
      myEditor = null;
      myFile = file;
      myOffset = offset;
    }

    public void setupUIComponentPresentation(String[] texts, EnumSet<Flag>[] flags, Color background) {
      assert texts.length == flags.length;
      myTexts = texts;
      myFlags = flags;
    }

    public void setupUIComponentPresentation(String text, int highlightStartOffset, int highlightEndOffset, boolean isDisabled,
                                             boolean strikeout, boolean isDisabledBeforeHighlight, Color background) {
      // nothing, we don't use it
    }

    public boolean isUIComponentEnabled() {
      return true;
    }

    public boolean isUIComponentEnabled(int index) {
      return true;
    }

    public void setUIComponentEnabled(boolean enabled) { }

    public void setUIComponentEnabled(int index, boolean b) { }

    public int getCurrentParameterIndex() {
      return myIndex;
    }

    public void removeHint() { }

    public void setParameterOwner(PsiElement o) {
      assertTrue("Found element is a python arglist", o == null || o instanceof PyArgumentList);
      myParamOwner = (PyArgumentList)o;
    }

    public PsiElement getParameterOwner() {
      return myParamOwner;
    }

    public void setHighlightedParameter(Object parameter) {
      // nothing, we don't use it
    }

    public void setCurrentParameter(int index) {
      myIndex = index;
    }

    public Color getDefaultParameterColor() {
      return java.awt.Color.BLACK;
    }

    public Object[] getItemsToShow() {
      return myItems;
    }

    public void setItemsToShow(Object[] items) {
      myItems = items;
    }

    public void showHint(PsiElement element, int offset, ParameterInfoHandler handler) { }

    public int getParameterListStart() {
      return 0; // we don't use it
    }

    public Object[] getObjectsToView() {
      return null; // we don't use it
    }

    public PsiElement getHighlightedElement() {
      return null;  // we don't use it
    }

    public void setHighlightedElement(PsiElement elements) {
      // nothing, we don't use it
    }

    public Project getProject() {
      return myProject;
    }

    public PsiFile getFile() {
      return myFile;
    }

    public int getOffset() {
      return myOffset;
    }

    @NotNull
    public Editor getEditor() {
      return myEditor;
    }

    /**
     * Checks if hint data look as expected.
     * @param text expected text of the hint, without formatting
     * @param highlighted expected highlighted substrings of hint
     * @param disabled expected disabled substrings of hint
     */
    public void check(String text, String[] highlighted, String[] disabled) {
      assertEquals("Signature", text, PyUtil.joinSubarray(myTexts, 0, 1000, "", new StringBuilder()).toString());
      StringBuilder wrongs = new StringBuilder();
      // see if highlighted matches
      Set<String> highlight_set = new HashSet<String>();
      highlight_set.addAll(Arrays.asList(highlighted));
      for (int i=0; i < myTexts.length; i += 1) {
        if (myFlags[i].contains(Flag.HIGHLIGHT) && !highlight_set.contains(myTexts[i])) {
          wrongs.append("Highlighted unexpected '").append(myTexts[i]).append("'. ");
        }
      }
      for (int i=0; i < myTexts.length; i += 1) {
        if (!myFlags[i].contains(Flag.HIGHLIGHT) && highlight_set.contains(myTexts[i])) {
          wrongs.append("Not highlighted expected '").append(myTexts[i]).append("'. ");
        }
      }
      // see if disabled matches
      Set<String> disabled_set = new HashSet<String>();
      disabled_set.addAll(Arrays.asList(disabled));
      for (int i=0; i < myTexts.length; i += 1) {
        if (myFlags[i].contains(Flag.DISABLE) && !disabled_set.contains(myTexts[i])) {
          wrongs.append("Highlighted unexpected '").append(myTexts[i]).append("'. ");
        }
      }
      for (int i=0; i < myTexts.length; i += 1) {
        if (!myFlags[i].contains(Flag.DISABLE) && disabled_set.contains(myTexts[i])) {
          wrongs.append("Not disabled expected '").append(myTexts[i]).append("'. ");
        }
      }
      //
      if (wrongs.length() > 0) fail(wrongs.toString());
    }

    public void check(String text, String[] highlighted) {
      check(text, highlighted, new String[0]);
    }

    public void assertNotFound() {
      assertNull(myParamOwner);
    }
  }
}
