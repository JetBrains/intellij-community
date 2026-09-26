// Copyright 2000-2026 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.fixtures;

import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandler;
import com.intellij.lang.parameterInfo.ParameterInfoUIContextEx;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import com.jetbrains.python.PyParameterInfoHandler;
import com.jetbrains.python.codeInsight.parameterInfo.PyParameterInfoUtils;
import com.jetbrains.python.psi.PyArgumentList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for tests that exercise parameter info (^P) at call sites. Extracted from
 * {@code com.jetbrains.python.PyParameterInfoTest} so that framework-specific suites (e.g. the Ultimate-only Pydantic
 * suite) can reuse the same harness without duplicating {@link Collector} and the {@code feignCtrlP} machinery.
 */
public abstract class PyParameterInfoTestCase extends LightMarkedTestCase {

  @Override
  protected Map<String, PsiElement> loadTest() {
    String fname = "/paramInfo/" + getTestName(false) + ".py";
    return configureByFile(fname);
  }

  protected Map<String, PsiElement> loadTest(int expectedMarks) {
    Map<String, PsiElement> marks = loadTest();
    assertEquals("Test data sanity", expectedMarks, marks.size());
    return marks;
  }

  @NotNull
  protected Map<String, PsiElement> loadMultiFileTest(int expectedMarks) {
    final String relativeDirectory = "/paramInfo/" + getTestName(false);
    final String relativeMainFile = relativeDirectory + "/a.py";

    final Map<String, PsiElement> marks = configureByFile(relativeMainFile);
    assertEquals("Test data sanity", marks.size(), expectedMarks);

    final String absoluteDirectory = getTestDataPath() + relativeDirectory;
    final String absoluteMainFile = getTestDataPath() + relativeMainFile;

    Arrays
      .stream(new File(absoluteDirectory).listFiles())
      .map(File::getPath)
      .filter(path -> !path.equals(absoluteMainFile))
      .forEach(path -> myFixture.copyFileToProject(path, new File(path).getName()));

    return marks;
  }

  @NotNull
  protected Collector feignCtrlP(int offset) {
    return feignCtrlP(offset, myFixture.getFile(), true, myFixture.getEditor());
  }

  @NotNull
  protected Collector feignCtrlPWithHintsForHighlightedOnly(int offset) {
    return feignCtrlP(offset, myFixture.getFile(), false, myFixture.getEditor());
  }

  /**
   * Imitates pressing of Ctrl+P; fails if results are not as expected.
   *
   * @param offset offset of 'cursor' where Ctrl+P is pressed.
   * @return a {@link Collector} with collected hint info.
   */
  @NotNull
  protected static Collector feignCtrlP(int offset, @NotNull PsiFile file, boolean showAllHints, Editor editor) {
    boolean oldKeyValue = Registry.is("python.parameter.info.show.all.hints");
    try {
      Registry.get("python.parameter.info.show.all.hints").setValue(showAllHints);
      final PyParameterInfoHandler handler = new PyParameterInfoHandler();
      final Collector collector = new Collector(file, offset, editor);
      collector.setParameterOwner(handler.findElementForParameterInfo(collector));

      if (collector.getParameterOwner() != null) {
        handler.updateParameterInfo((PyArgumentList)collector.getParameterOwner(), collector);

        for (Object itemToShow : collector.getItemsToShow()) {
          PyParameterInfoUtils.CallInfo callInfo = (PyParameterInfoUtils.CallInfo)itemToShow;
          //noinspection unchecked
          handler.updateUI(callInfo, collector);
        }
      }
      return collector;
    }
    finally {
      Registry.get("python.parameter.info.show.all.hints").setValue(oldKeyValue);
    }
  }

  public static void checkParameters(int offset,
                                     @NotNull PsiFile file,
                                     @NotNull String text,
                                     String @NotNull [] highlighted,
                                     Editor editor) {
    Collector collector = feignCtrlP(offset, file, true, editor);
    collector.check(text, highlighted);
  }

  /**
   * Imitates the normal UI contexts to the extent we use it. Collects highlighting.
   */
  public static final class Collector implements ParameterInfoUIContextEx, CreateParameterInfoContext, UpdateParameterInfoContext {

    @NotNull
    private final PsiFile myFile;
    private final int myOffset;

    @NotNull
    private final List<String[]> myListOfTexts;

    @NotNull
    private final List<EnumSet<Flag>[]> myListOfFlags;

    @Nullable
    private PyArgumentList myParameterOwner;

    private Object @NotNull [] myItemsToShow;

    private int myIndex;

    private final Editor myEditor;

    private Collector(@NotNull PsiFile file, int offset, Editor editor) {
      myFile = file;
      myOffset = offset;
      myEditor = editor;
      myListOfTexts = new ArrayList<>();
      myListOfFlags = new ArrayList<>();
      myItemsToShow = ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    @Override
    @NotNull
    public String setupUIComponentPresentation(String @NotNull [] texts, EnumSet<Flag> @NotNull [] flags, @NotNull Color background) {
      assertEquals(texts.length, flags.length);
      myListOfTexts.add(texts);
      myListOfFlags.add(flags);
      return StringUtil.join(texts, "");
    }

    @Override
    public void setEscapeFunction(@Nullable Function<? super String, String> escapeFunction) {
    }

    @Override
    public String setupUIComponentPresentation(String text, int highlightStartOffset, int highlightEndOffset, boolean isDisabled,
                                               boolean strikeout, boolean isDisabledBeforeHighlight, Color background) {
      // nothing, we don't use it
      return text;
    }

    @Override
    public void setupRawUIComponentPresentation(String htmlText) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isUIComponentEnabled() {
      return true;
    }

    @Override
    public boolean isUIComponentEnabled(int index) {
      return true;
    }

    @Override
    public void setUIComponentEnabled(boolean enabled) { }

    @Override
    public void setUIComponentEnabled(int index, boolean enabled) { }

    @Override
    public int getCurrentParameterIndex() {
      return myIndex;
    }

    @Override
    public void removeHint() { }

    @Override
    public void setParameterOwner(@Nullable PsiElement o) {
      assertTrue("Found element is not `null` and not " + PyArgumentList.class.getName(), o == null || o instanceof PyArgumentList);
      myParameterOwner = (PyArgumentList)o;
    }

    @Override
    @Nullable
    public PsiElement getParameterOwner() {
      return myParameterOwner;
    }

    @Override
    public boolean isSingleOverload() {
      return myItemsToShow.length == 1;
    }

    @Override
    public boolean isSingleParameterInfo() {
      return false;
    }

    @Override
    public void setHighlightedParameter(Object parameter) {
      // nothing, we don't use it
    }

    @Override
    public Object getHighlightedParameter() {
      return null;
    }

    @Override
    public void setCurrentParameter(int index) {
      myIndex = index;
    }

    @Override
    @NotNull
    public Color getDefaultParameterColor() {
      return Color.BLACK;
    }

    @Override
    public Object @NotNull [] getItemsToShow() {
      return myItemsToShow;
    }

    @Override
    public void setItemsToShow(Object @NotNull [] items) {
      myItemsToShow = items;
    }

    @Override
    public void showHint(PsiElement element, int offset, ParameterInfoHandler handler) { }

    @Override
    public int getParameterListStart() {
      return 0; // we don't use it
    }

    @Override
    public Object[] getObjectsToView() {
      return null; // we don't use it
    }

    @Override
    public boolean isPreservedOnHintHidden() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setPreservedOnHintHidden(boolean value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isInnermostContext() {
      return false;
    }

    @Override
    public UserDataHolderEx getCustomContext() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement getHighlightedElement() {
      return null;  // we don't use it
    }

    @Override
    public void setHighlightedElement(PsiElement elements) {
      // nothing, we don't use it
    }

    @Override
    public Project getProject() {
      throw new UnsupportedOperationException();
    }

    @Override
    @NotNull
    public PsiFile getFile() {
      return myFile;
    }

    @Override
    public int getOffset() {
      return myOffset;
    }

    @Override
    @NotNull
    public Editor getEditor() {
      return myEditor;
    }

    public void check(@NotNull String text, String @NotNull [] highlighted) {
      check(text, highlighted, ArrayUtilRt.EMPTY_STRING_ARRAY);
    }

    public void check(@NotNull String text, String @NotNull [] highlighted, String @NotNull [] disabled) {
      assertEquals("Number of collected hints is wrong", 1, myItemsToShow.length);
      check(text, highlighted, disabled, 0);
    }

    public void check(@NotNull List<String> texts, @NotNull List<String[]> highlighted, @NotNull List<String[]> disabled) {
      assertEquals("Number of collected hints is wrong", texts.size(), myItemsToShow.length);
      for (int i = 0; i < texts.size(); i++) {
        check(texts.get(i), highlighted.get(i), disabled.get(i), i);
      }
    }

    /**
     * Checks if hint data looks as expected.
     *
     * @param text        expected text of the hint, without formatting
     * @param highlighted expected highlighted substrings of hint
     * @param disabled    expected disabled substrings of hint
     * @param index       hint index
     */
    private void check(@NotNull String text, String @NotNull [] highlighted, String @NotNull [] disabled, int index) {
      final String[] hintText = myListOfTexts.get(index);
      final EnumSet<Flag>[] hintFlags = myListOfFlags.get(index);

      assertEquals("Signature", text, StringUtil.join(hintText, ""));

      final StringBuilder wrongs = new StringBuilder();

      // see if highlighted matches
      final Set<String> highlightSet = Set.of(highlighted);
      for (int i = 0; i < hintText.length; i++) {
        if (hintFlags[i].contains(Flag.HIGHLIGHT) && !highlightSet.contains(hintText[i])) {
          wrongs.append("Highlighted unexpected '").append(hintText[i]).append("'. ");
        }
      }
      for (int i = 0; i < hintText.length; i++) {
        if (!hintFlags[i].contains(Flag.HIGHLIGHT) && highlightSet.contains(hintText[i])) {
          wrongs.append("Not highlighted expected '").append(hintText[i]).append("'. ");
        }
      }

      // see if disabled matches
      final Set<String> disabledSet = Set.of(disabled);
      for (int i = 0; i < hintText.length; i++) {
        if (hintFlags[i].contains(Flag.DISABLE) && !disabledSet.contains(hintText[i])) {
          wrongs.append("Highlighted a disabled '").append(hintText[i]).append("'. ");
        }
      }
      for (int i = 0; i < hintText.length; i++) {
        if (!hintFlags[i].contains(Flag.DISABLE) && disabledSet.contains(hintText[i])) {
          wrongs.append("Not disabled expected '").append(hintText[i]).append("'. ");
        }
      }
      //

      if (wrongs.length() > 0) fail(wrongs.toString());
    }

    public void assertNotFound() {
      assertNull(myParameterOwner);
    }
  }
}
