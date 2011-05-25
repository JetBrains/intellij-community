package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.hint.TooltipController;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.impl.TemplateContext;
import com.intellij.codeInsight.template.impl.TemplateEditorUtil;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.plugin.StructuralReplaceAction;
import com.intellij.structuralsearch.plugin.StructuralSearchAction;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Maxim.Mossienko
 * Date: Apr 21, 2004
 * Time: 7:50:48 PM
 */
public class UIUtil {
  static Key<SubstitutionShortInfoHandler> LISTENER_KEY = Key.create("sslistener.key");
  private static final String MODIFY_EDITOR_CONTENT = SSRBundle.message("modify.editor.content.command.name");
  @NonNls private static final String SS_GROUP = "structuralsearchgroup";

  public static Editor createEditor(Document doc, final Project project, boolean editable, @Nullable TemplateContextType contextType) {
    return createEditor(doc, project, editable, false, contextType);
  }

  public static Editor createEditor(@NotNull Document doc,
                                    final Project project,
                                    boolean editable,
                                    boolean addToolTipForVariableHandler,
                                    @Nullable TemplateContextType contextType) {
    final Editor editor =
        editable ? EditorFactory.getInstance().createEditor(doc, project) : EditorFactory.getInstance().createViewer(doc, project);

    EditorSettings editorSettings = editor.getSettings();
    editorSettings.setVirtualSpace(false);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setIndentGuidesShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);

    EditorColorsScheme scheme = editor.getColorsScheme();
    scheme.setColor(EditorColors.CARET_ROW_COLOR, null);
    if (!editable) {
      final EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
      Color c = globalScheme.getColor(EditorColors.READONLY_BACKGROUND_COLOR);

      if (c == null) {
        c = globalScheme.getDefaultBackground();
      }

      ((EditorEx)editor).setBackgroundColor(c);
    }
    else {
      ((EditorEx)editor).setEmbeddedIntoDialogWrapper(true);
    }

    if (contextType != null) {
      TemplateContext context = new TemplateContext();
      context.setEnabled(contextType, true);
      TemplateEditorUtil.setHighlighter(editor, context);
    }

    if (addToolTipForVariableHandler) {
      SubstitutionShortInfoHandler handler = new SubstitutionShortInfoHandler(editor);
      editor.addEditorMouseMotionListener(handler);
      editor.getDocument().addDocumentListener(handler);
      editor.getCaretModel().addCaretListener(handler);
      editor.putUserData(LISTENER_KEY, handler);
    }

    return editor;
  }

  public static JComponent createOptionLine(JComponent[] options) {
    JPanel tmp = new JPanel();

    tmp.setLayout(new BoxLayout(tmp, BoxLayout.X_AXIS));
    for (JComponent option : options) {
      tmp.add(option);
    }
    tmp.add(Box.createHorizontalGlue());

    return tmp;
  }

  public static JComponent createOptionLine(JComponent option) {
    return createOptionLine(new JComponent[]{option});
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static String buildPatternFromElement(final PsiElement element) {
    if (element != null) {
      String text;

      if (element instanceof PsiMethod || element instanceof PsiVariable || element instanceof PsiClass) {
        final StringBuffer buf = new StringBuffer();

        if (element instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)element;
          final PsiClass clazz = method.getContainingClass();

          if (method.hasModifierProperty(PsiModifier.STATIC)) {
            buf.append(clazz.getName()).append(".");
          }
          else if (method.getReturnType() == null) {
            buf.append("new ");
          }
          else {
            buf.append("'instance?:[exprtype( *").append(clazz.getName())
                .append(" )]").append(".");
          }

          buf.append(method.getName()).append('(');
          final PsiParameter[] params = method.getParameterList().getParameters();
          for (int i = 0; i < params.length; ++i) {
            if (i != 0) buf.append(',');
            buf.append("'param").append(i + 1).append(":[exprtype( *").append(params[i].getType().getPresentableText()).append(" )]");
          }
          buf.append(")");
        }
        else if (element instanceof PsiVariable) {
          final PsiVariable var = (PsiVariable)element;
          if (var instanceof PsiField) {
            final PsiClass clazz = (PsiClass)var.getParent();

            if (var.hasModifierProperty(PsiModifier.STATIC)) {
              buf.append(clazz.getName());
            }
            else {
              buf.append("'instance?:[exprtype( *").append(clazz.getName())
                  .append(" )]");
            }

            buf.append('.');
          }

          buf.append(var.getName());
        }
        else {
          PsiClass clazz = (PsiClass)element;
          buf.append("'").append(clazz.getName()).append(":").append(clazz.getName());
        }

        text = buf.toString();
      }
      else {
        text = element.getText();
        if (text == null) return "";
      }

      return text;
    }

    return "";
  }

  public static void setContent(final Editor editor, String val, final int from, final int end, final Project project) {
    final String value = val != null ? val : "";

    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            editor.getDocument().replaceString(from, (end == -1) ? editor.getDocument().getTextLength() : end, value);
          }
        });
      }
    }, MODIFY_EDITOR_CONTENT, SS_GROUP);
  }

  static String getShortParamString(Configuration config, String varname) {
    final MatchOptions options = config.getMatchOptions();


    MatchVariableConstraint constraint = options == null ? null : options.getVariableConstraint(varname);
    NamedScriptableDefinition namedScriptableDefinition = constraint;

    ReplacementVariableDefinition replacementVariableDefinition = config instanceof ReplaceConfiguration ?
                                               ((ReplaceConfiguration)config).getOptions().getVariableDefinition(varname) : null;
    if (replacementVariableDefinition != null) namedScriptableDefinition = replacementVariableDefinition;

    if (constraint == null && replacementVariableDefinition == null) {
      return SSRBundle.message("no.constraints.specified.tooltip.message");
    }

    StringBuffer buf = new StringBuffer();

    if (constraint != null) {
      if (constraint.getRegExp() != null && constraint.getRegExp().length() > 0) {
        append(buf, SSRBundle.message("text.tooltip.message", constraint.isInvertRegExp() ? SSRBundle.message("not.tooltip.message") : "",
                                     constraint.getRegExp(),
                                     constraint.isWithinHierarchy() || constraint.isStrictlyWithinHierarchy() ? SSRBundle
                                         .message("within.hierarchy.tooltip.message") : ""));
      }

      if (constraint.getNameOfExprType() != null && constraint.getNameOfExprType().length() > 0) {
        append(buf, SSRBundle.message("exprtype.tooltip.message",
                                     constraint.isInvertExprType() ? SSRBundle.message("not.tooltip.message") : "",
                                     constraint.getNameOfExprType(),
                                     constraint.isExprTypeWithinHierarchy() ? SSRBundle.message("within.hierarchy.tooltip.message") : ""));
      }

      if (constraint.getMinCount() == constraint.getMaxCount()) {
        append(buf, SSRBundle.message("occurs.tooltip.message", constraint.getMinCount()));
      }
      else {
        append(buf, SSRBundle.message("min.occurs.tooltip.message", constraint.getMinCount(),
                                     constraint.getMaxCount() == Integer.MAX_VALUE ? StringUtil
                                         .decapitalize(SSRBundle.message("editvarcontraints.unlimited")) : constraint.getMaxCount()));
      }
    }

    final String script = namedScriptableDefinition.getScriptCodeConstraint();
    if (script != null && script.length() > 2) {
      final String str = SSRBundle.message("script.tooltip.message", StringUtil.stripQuotesAroundValue(script));
      append(buf, str);
    }

    return buf.toString();
  }

  private static void append(final StringBuffer buf, final String str) {
    if (buf.length() > 0) buf.append(", ");
    buf.append(str);
  }

  public static void navigate(PsiElement result) {
    FileEditorManager.getInstance(result.getProject()).openTextEditor(
        new OpenFileDescriptor(result.getProject(), result.getContainingFile().getVirtualFile(), result.getTextOffset()), true);
  }

  public static void navigate(MatchResult result) {
    final SmartPsiPointer ref = result.getMatchRef();

    FileEditorManager.getInstance(ref.getProject())
        .openTextEditor(new OpenFileDescriptor(ref.getProject(), ref.getFile(), ref.getOffset()), true);
  }

  public static PsiElement getNavigationElement(PsiElement element) {
    if (element instanceof PsiClass && !(element instanceof PsiAnonymousClass)) {
      element = ((PsiClass)element).getNameIdentifier();
    }
    else if (element instanceof PsiMethod) {
      element = ((PsiMethod)element).getNameIdentifier();
    }
    else if (element instanceof PsiVariable) {
      element = ((PsiVariable)element).getNameIdentifier();
    }

    return element;
  }

  public static PsiElement getNavigationElement(MatchResult result) {
    return getNavigationElement(result.getMatchRef().getElement());
  }

  public static void invokeAction(Configuration config, SearchContext context) {
    if (config instanceof SearchConfiguration) {
      StructuralSearchAction.triggerAction(config, context);
    }
    else {
      StructuralReplaceAction.triggerAction(config, context);
    }
  }

  static void showTooltip(final Editor editor, final int start, int end, final String text, TooltipGroup group) {
    Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
    Point top = editor.logicalPositionToXY(editor.offsetToLogicalPosition(start));
    final int documentLength = editor.getDocument().getTextLength();
    if (end >= documentLength) end = documentLength;
    Point bottom = editor.logicalPositionToXY(editor.offsetToLogicalPosition(end));

    Point bestPoint = new Point(top.x, bottom.y + editor.getLineHeight());

    if (!visibleArea.contains(bestPoint)) {
      int defaultOffset = editor.logicalPositionToOffset(editor.xyToLogicalPosition(new Point(0, 0)));
      bestPoint = editor.logicalPositionToXY(editor.offsetToLogicalPosition(defaultOffset));
    }

    Point p = SwingUtilities.convertPoint(editor.getContentComponent(), bestPoint, editor.getComponent().getRootPane().getLayeredPane());
    TooltipController.getInstance().showTooltip(editor, p, text, false, group);
  }

  public static void updateHighlighter(Editor editor, StructuralSearchProfile profile) {
    final TemplateContextType contextType = profile.getTemplateContextType();
    if (contextType != null) {
      TemplateContext context = new TemplateContext();
      context.setEnabled(contextType, true);
      TemplateEditorUtil.setHighlighter(editor, context);
    }
  }
}
