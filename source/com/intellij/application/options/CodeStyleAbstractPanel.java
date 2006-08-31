/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.UserActivityListener;
import com.intellij.ui.UserActivityWatcher;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

public abstract class CodeStyleAbstractPanel {
  private static Logger LOG = Logger.getInstance("#com.intellij.application.options.CodeStyleXmlPanel");
  private final Editor myEditor;
  private final CodeStyleSettings mySettings;
  private boolean myShouldUpdatePreview;
  protected final static int[] ourWrappings = new int[]{CodeStyleSettings.DO_NOT_WRAP,
    CodeStyleSettings.WRAP_AS_NEEDED,
    CodeStyleSettings.WRAP_ON_EVERY_ITEM,
    CodeStyleSettings.WRAP_ALWAYS};
  private long myLastDocumentModificationStamp;
  private String myTextToReformat = null;
  private UserActivityWatcher myUserActivityWatcher = new UserActivityWatcher();

  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  public CodeStyleAbstractPanel(CodeStyleSettings settings) {
    mySettings = settings;
    myEditor = createEditor();
    myUserActivityWatcher.addUserActivityListener(new UserActivityListener() {
      public void stateChanged() {
        somethingChanged();
        myUpdateAlarm.addRequest(new Runnable() {
          public void run() {
            myUpdateAlarm.cancelAllRequests();
            updatePreview();
          }
        }, 300);

      }
    });
  }

  protected void somethingChanged() {

  }

  protected void addPanelToWatch(Component component) {
    myUserActivityWatcher.register(component);
  }

  private Editor createEditor() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    myTextToReformat = getPreviewText();
    Document editorDocument = editorFactory.createDocument(myTextToReformat);
    EditorEx editor = (EditorEx)editorFactory.createEditor(editorDocument);

    myLastDocumentModificationStamp = editor.getDocument().getModificationStamp();

    EditorSettings editorSettings = editor.getSettings();
    fillEditorSettings(editorSettings);

    EditorColorsScheme scheme = editor.getColorsScheme();
    scheme.setColor(EditorColors.CARET_ROW_COLOR, null);

    editor.setHighlighter(createHighlighter(scheme));

    return editor;
  }

  protected abstract LexerEditorHighlighter createHighlighter(final EditorColorsScheme scheme);

  protected void fillEditorSettings(final EditorSettings editorSettings) {
    editorSettings.setWhitespacesShown(true);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);
    editorSettings.setAdditionalColumnsCount(0);
    editorSettings.setAdditionalLinesCount(1);
    final int rightMargin = getRightMargin();
    if (rightMargin > 0) {
      editorSettings.setRightMargin(rightMargin);
    }
  }

  protected abstract int getRightMargin();

  protected final void updatePreview() {
    if (!myShouldUpdatePreview) {
      return;
    }

    if (myLastDocumentModificationStamp != myEditor.getDocument().getModificationStamp()) {
      myTextToReformat = myEditor.getDocument().getText();
    }

    Project project = /*(Project)DataManager.getInstance().getDataContext().getData(DataConstants.PROJECT); //todo uncomment - do not load default project
    if (project == null) {
      project =*/ ProjectManager.getInstance().getDefaultProject();
    //}
    final Project finalProject = project;
    CommandProcessor.getInstance().executeCommand(finalProject,
                                                  new Runnable() {
                                                    public void run() {
                                                      replaceText(finalProject);
                                                    }
                                                  }, null, null);
    myEditor.getSettings().setRightMargin(getRightMargin());
    myLastDocumentModificationStamp = myEditor.getDocument().getModificationStamp();
  }

  private void replaceText(final Project project) {
    final PsiManager manager = PsiManager.getInstance(project);
    final LanguageLevel effectiveLanguageLevel = manager.getEffectiveLanguageLevel();
    manager.setEffectiveLanguageLevel(LanguageLevel.HIGHEST);
    try {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          PsiElementFactory factory = manager.getElementFactory();
          try {
            PsiFile psiFile = factory.createFileFromText("a." + getFileType().getDefaultExtension(), myTextToReformat);

            apply(mySettings);
            CodeStyleSettings clone = (CodeStyleSettings)mySettings.clone();
            if (getRightMargin() > 0) {
              clone.RIGHT_MARGIN = getRightMargin();
            }


            CodeStyleSettingsManager.getInstance(project).setTemporarySettings(clone);
            CodeStyleManager.getInstance(project).reformat(psiFile);
            CodeStyleSettingsManager.getInstance(project).dropTemporarySettings();

            myEditor.getSettings().setTabSize(clone.getTabSize(getFileType()));
            Document document = myEditor.getDocument();
            document.replaceString(0, document.getTextLength(), psiFile.getText());
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }

      });
    }
    finally {
      manager.setEffectiveLanguageLevel(effectiveLanguageLevel);
    }
  }

  @NotNull
  protected abstract FileType getFileType();

  @NonNls
  protected abstract String getPreviewText();

  public abstract void apply(CodeStyleSettings settings);

  public final void reset(final CodeStyleSettings settings) {
    myShouldUpdatePreview = false;
    try {
      resetImpl(settings);
    }
    finally {
      myShouldUpdatePreview = true;
      updatePreview();
    }
  }

  protected static int getIndexForWrapping(int value) {
    for (int i = 0; i < ourWrappings.length; i++) {
      int ourWrapping = ourWrappings[i];
      if (ourWrapping == value) return i;
    }
    LOG.assertTrue(false);
    return 0;
  }

  public abstract boolean isModified(CodeStyleSettings settings);

  public abstract JComponent getPanel();

  public final void dispose() {
    myUpdateAlarm.cancelAllRequests();
    EditorFactory.getInstance().releaseEditor(myEditor);
  }

  protected abstract void resetImpl(final CodeStyleSettings settings);

  protected static void fillWrappingCombo(final JComboBox wrapCombo) {
    wrapCombo.addItem(ApplicationBundle.message("combobox.codestyle.do.not.wrap"));
    wrapCombo.addItem(ApplicationBundle.message("combobox.codestyle.wrap.if.long"));
    wrapCombo.addItem(ApplicationBundle.message("combobox.codestyle.chop.down.if.long"));
    wrapCombo.addItem(ApplicationBundle.message("combobox.codestyle.wrap.always"));
  }

  protected String readFromFile(@NonNls final String fileName) {
    try {
      //noinspection HardCodedStringLiteral
      final InputStream stream = getClass().getClassLoader().getResourceAsStream("codeStyle/preview/" + fileName);
      final InputStreamReader reader = new InputStreamReader(stream);
      final LineNumberReader lineNumberReader = new LineNumberReader(reader);
      final StringBuffer result;
      try {
        String line;
        result = new StringBuffer();
        while ((line = lineNumberReader.readLine()) != null) {
          result.append(line);
          result.append("\n");
        }
      }
      finally {
        lineNumberReader.close();
      }

      return result.toString();
    }
    catch (IOException e) {
      return "";
    }
  }

  protected void installPreviewPanel(final JPanel previewPanel) {
    previewPanel.setLayout(new BorderLayout());
    previewPanel.add(myEditor.getComponent(), BorderLayout.CENTER);
  }
}
