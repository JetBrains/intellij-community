// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.parameterInfo.ParameterFlag;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.components.ActionLink;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import com.jetbrains.python.codeInsight.parameterInfo.ParameterHints;
import com.jetbrains.python.codeInsight.parameterInfo.PyParameterInfoUtils;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.types.PyCallableType;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

public final class PyParameterInfoHandler implements ParameterInfoHandler<PyArgumentList, PyParameterInfoUtils.CallInfo> {
  private static final int MY_PARAM_LENGTH_LIMIT = 50;
  private static final int MAX_PARAMETER_INFO_TO_SHOW = 20;

  private boolean hideOverloads = true;
  private boolean isDisposed = false;
  private int myRealOffset = -1;
  private int numOfSignatures = 0;
  private CreateParameterInfoContext myCreateContext;

  private static final EnumMap<ParameterFlag, ParameterInfoUIContextEx.Flag> PARAM_FLAG_TO_UI_FLAG = new EnumMap<>(Map.of(
    ParameterFlag.HIGHLIGHT, ParameterInfoUIContextEx.Flag.HIGHLIGHT,
    ParameterFlag.DISABLE, ParameterInfoUIContextEx.Flag.DISABLE,
    ParameterFlag.STRIKEOUT, ParameterInfoUIContextEx.Flag.STRIKEOUT
  ));

  @Override
  public @Nullable PyArgumentList findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
    PsiFile file = context.getFile();
    int offset = context.getOffset();
    final PyArgumentList argumentList = PyParameterInfoUtils.findArgumentList(file, offset, -1);

    List<Pair<PyCallExpression, PyCallableType>> parameterInfos = PyParameterInfoUtils.findCallCandidates(argumentList);
    if (parameterInfos != null) {
      if (parameterInfos.size() > MAX_PARAMETER_INFO_TO_SHOW) {
        parameterInfos = parameterInfos.subList(0, MAX_PARAMETER_INFO_TO_SHOW);
      }
      List<PyParameterInfoUtils.CallInfo> infos = new ArrayList<>();

      boolean isFirst = true;
      for (Pair<PyCallExpression, PyCallableType> paramInfo : parameterInfos) {
        infos.add(new PyParameterInfoUtils.CallInfo(paramInfo.first, paramInfo.second, isFirst || hideOverloads));
        isFirst = false;
      }

      Object[] infoArr = infos.toArray();
      setDisplayAllOverloadsState(infoArr, hideOverloads);
      context.setItemsToShow(infoArr);
      numOfSignatures = getNumOfSignatures(infoArr);
      return argumentList;
    }

    return null;
  }

  @Override
  public void showParameterInfo(@NotNull PyArgumentList element, @NotNull CreateParameterInfoContext context) {
    // Show all overloads on second shortcut hit at the same offset
    myCreateContext = context;
    isDisposed = false;
    int actualOffset = getRealCaretOffset(context.getEditor());
    if (actualOffset == myRealOffset) {
      hideOverloads = !hideOverloads;
    }
    context.showHint(element, element.getTextOffset(), this);
  }

  @Override
  public @Nullable PyArgumentList findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
    myRealOffset = getRealCaretOffset(context.getEditor());
    return PyParameterInfoUtils.findArgumentList(context.getFile(), context.getOffset(), context.getParameterListStart());
  }

  /*
   <b>Note: instead of parameter index, we directly store parameter's offset for later use.</b><br/>
   We cannot store an index since we cannot determine what is an argument until we actually map arguments to parameters.
   This is because a tuple in arguments may be a whole argument or map to a tuple parameter.
   */
  @Override
  public void updateParameterInfo(@NotNull PyArgumentList argumentList, @NotNull UpdateParameterInfoContext context) {
    final int allegedCursorOffset = context.getOffset(); // this is already shifted backwards to skip spaces

    if (!argumentList.getTextRange().contains(allegedCursorOffset) && argumentList.getText().endsWith(")")) {
      context.removeHint();
      return;
    }

    final PsiFile file = context.getFile();
    int offset = PyParameterInfoUtils.findCurrentParameter(argumentList, allegedCursorOffset, file);

    setDisplayAllOverloadsState(context.getObjectsToView(), hideOverloads);

    context.setCurrentParameter(offset);
  }

  @Override
  public void updateUI(@NotNull PyParameterInfoUtils.CallInfo description,
                       @NotNull ParameterInfoUIContext context) {
    context.setUIComponentVisible(description.isVisible());
    final int currentParamOffset = context.getCurrentParameterIndex(); // in Python mode, we get an offset here, not an index!
    ParameterHints parameterHints = PyParameterInfoUtils.buildParameterHints(description.getCallandCalleePair(), currentParamOffset);
    if (parameterHints == null) return;

    boolean showAllHints = Registry.is("python.parameter.info.show.all.hints");
    List<PyParameterInfoUtils.ParameterDescription> parameterDescriptions = parameterHints.getParameterDescriptors();
    String[] hintsToShow = new String[parameterDescriptions.size()];
    //noinspection unchecked
    EnumSet<ParameterInfoUIContextEx.Flag>[] flags = new EnumSet[parameterHints.getFlags().size()];
    for (int i = 0; i < flags.length; i++) {
      EnumSet<ParameterFlag> curFlags = parameterHints.getFlags().get(i);
      PyParameterInfoUtils.ParameterDescription representation = parameterDescriptions.get(i);
      hintsToShow[i] = getRepresentationToShow(representation, curFlags.contains(ParameterFlag.HIGHLIGHT), showAllHints);
      flags[i] = StreamEx.of(parameterHints.getFlags().get(i))
        .map(PARAM_FLAG_TO_UI_FLAG::get)
        .collect(MoreCollectors.toEnumSet(ParameterInfoUIContextEx.Flag.class));
    }
    if (context instanceof ParameterInfoUIContextEx) {
      if (parameterDescriptions.isEmpty()) {
        hintsToShow = new String[]{getNoParamsMsg()};
        //noinspection unchecked
        flags = new EnumSet[]{EnumSet.of(ParameterInfoUIContextEx.Flag.DISABLE)};
      }
      ((ParameterInfoUIContextEx)context).setupUIComponentPresentation(hintsToShow, flags, context.getDefaultParameterColor());
    }
    else { // fallback, no highlight
      final StringBuilder signatureBuilder = new StringBuilder();
      if (hintsToShow.length == 0) {
        signatureBuilder.append(getNoParamsMsg());
      }
      else {
        for (String s : hintsToShow) signatureBuilder.append(s);
      }
      context.setupUIComponentPresentation(
        signatureBuilder.toString(), -1, 0, false, false, false, context.getDefaultParameterColor()
      );
    }
  }

  @Override
  public JComponent createBottomComponent() {
    int numOfOverloads = numOfSignatures - 1;

    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
    panel.setBorder(JBUI.Borders.empty(5, 0, 0, 5));
    panel.setOpaque(false);

    String showMoreShortCut = KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_EDITOR_SHOW_PARAMETER_INFO);
    JLabel shortCut = new JLabel(showMoreShortCut);

    ActionLink actionLink = new ActionLink(getActionLinkText(numOfOverloads), event -> {
      if (myCreateContext != null) {
        ReadAction
          .nonBlocking(() -> {
            return findElementForParameterInfo(myCreateContext);
          })
          .finishOnUiThread(ModalityState.defaultModalityState(), argumentList -> {
            if (argumentList != null) {
              showParameterInfo(argumentList, myCreateContext);
            }
          })
          .coalesceBy(myCreateContext, this)
          .expireWhen(() -> isDisposed)
          .submit(AppExecutorUtil.getAppExecutorService());
      }
    });

    actionLink.setBorder(JBUI.Borders.emptyLeft(ExperimentalUI.isNewUI() ? 19 : 3));
    panel.add(actionLink);
    panel.add(shortCut);
    if (numOfOverloads < 1) {
      panel.setVisible(false);
    }
    return panel;
  }

  @Override
  public void updateBottomComponent(@NotNull JComponent component) {
    int numOfOverloads = numOfSignatures - 1;
    if (numOfOverloads >= 1) {
      Component comp = component.getComponent(0);
      if (comp instanceof ActionLink actionLink) {
        actionLink.setText(getActionLinkText(numOfOverloads));
        component.setVisible(true);
      }
    }
  }

  @Nls
  private String getActionLinkText(int numOfOverloads) {
    return hideOverloads
           ? PyBundle.message("param.info.show.more.n.overloads",
                              numOfOverloads,
                              numOfOverloads > 1 ? 0 : 1)
           : PyBundle.message("param.info.show.less");
  }

  private static int getNumOfSignatures(Object[] objectsToShow) {
    if (objectsToShow != null) {
      return objectsToShow.length;
    }
    return 0;
  }

  @Override
  public void dispose(@NotNull DeleteParameterInfoContext context) {
    resetDisplayState();
    myCreateContext = null;
    isDisposed = true;
    ParameterInfoHandler.super.dispose(context);
  }

  private void resetDisplayState() {
    myRealOffset = -1;
    numOfSignatures = 0;
    hideOverloads = true;
  }

  private static String getRepresentationToShow(PyParameterInfoUtils.ParameterDescription description,
                                                boolean isHighlighted,
                                                boolean showHints) {
    String fullRepresentation = description.getFullRepresentation(isHighlighted || showHints);
    if (fullRepresentation.length() > MY_PARAM_LENGTH_LIMIT && !isHighlighted) {
      String annotation = description.getAnnotation();
      if (!annotation.isEmpty() && annotation.length() < fullRepresentation.length()) {
        return annotation;
      }
    }
    return fullRepresentation;
  }

  private static String getNoParamsMsg() {
    return CodeInsightBundle.message("parameter.info.no.parameters");
  }

  /* ParameterInfoContext.getOffset() does not represent the real offset in cases like
   * foo(a, b,     <caret>) as it skips whitespaces
   * The real offset is required for correct folding and unfolding of the overloads
   * when the shortcut is pressed at the same *real* offset
   */
  private static int getRealCaretOffset(@NotNull Editor editor) {
    return editor.getCaretModel().getCurrentCaret().getOffset();
  }

  private static void setDisplayAllOverloadsState(Object[] hintsToShow, boolean hideOverloads) {
    if (hintsToShow != null && hintsToShow.length != 0) {
      ((PyParameterInfoUtils.CallInfo)hintsToShow[0]).setVisible(true);
      for (int i = 1; i < hintsToShow.length; i++) {
        ((PyParameterInfoUtils.CallInfo)hintsToShow[i]).setVisible(!hideOverloads);
      }
    }
  }
}
