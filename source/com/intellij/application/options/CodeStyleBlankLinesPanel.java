package com.intellij.application.options;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.OptionGroup;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;

public class CodeStyleBlankLinesPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.CodeStyleBlankLinesPanel");

  private JTextField myKeepBlankLinesInDeclarations;
  private JTextField myKeepBlankLinesInCode;
  private JTextField myBlankLinesBeforePackage;
  private JTextField myBlankLinesAfterPackage;
  private JTextField myBlankLinesBeforeImports;
  private JTextField myBlankLinesAfterImports;
  private JTextField myBlankLinesAroundClass;
  private JTextField myBlankLinesAroundField;
  private JTextField myBlankLinesAroundMethod;
  private JTextField myBlankLinesAfterClassHeader;
  private JTextField myKeepBlankLinesBeforeRBrace;
  private Editor myEditor;
  private boolean toUpdatePreview = true;
  private CodeStyleSettings mySettings;

  public CodeStyleBlankLinesPanel(CodeStyleSettings settings){
    super(new GridBagLayout());
    mySettings = settings;

    add(createKeepBlankLinesPanel(), new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 4, 0, 4), 0, 0));
    add(createBlankLinesPanel(), new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL, new Insets(0, 4, 0, 4), 0, 0));

    add(createPreviewPanel(), new GridBagConstraints(1, 0, 1, 2, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(0, 0, 0, 4), 0, 0));
  }

  public void dispose() {
    EditorFactory.getInstance().releaseEditor(myEditor);
  }

  private JPanel createBlankLinesPanel() {
    OptionGroup optionGroup = new OptionGroup("Blank Lines");

    myBlankLinesBeforePackage = createTextField();
    optionGroup.add(new JLabel("Before package statement:"), myBlankLinesBeforePackage);

    myBlankLinesAfterPackage = createTextField();
    optionGroup.add(new JLabel("After package statement:"), myBlankLinesAfterPackage);

    myBlankLinesBeforeImports = createTextField();
    optionGroup.add(new JLabel("Before imports:"), myBlankLinesBeforeImports);

    myBlankLinesAfterImports = createTextField();
    optionGroup.add(new JLabel("After imports:"), myBlankLinesAfterImports);

    myBlankLinesAroundClass = createTextField();
    optionGroup.add(new JLabel("Around class:"), myBlankLinesAroundClass);

    myBlankLinesAroundField = createTextField();
    optionGroup.add(new JLabel("Around field:"), myBlankLinesAroundField);

    myBlankLinesAroundMethod = createTextField();
    optionGroup.add(new JLabel("Around method:"), myBlankLinesAroundMethod);

    myBlankLinesAfterClassHeader = createTextField();
    optionGroup.add(new JLabel("After class header:  "), myBlankLinesAfterClassHeader);

    return optionGroup.createPanel();
  }

  private JPanel createKeepBlankLinesPanel() {
    OptionGroup optionGroup = new OptionGroup("Keep Blank Lines");

    myKeepBlankLinesInDeclarations = createTextField();
    optionGroup.add(new JLabel("In declarations:"), myKeepBlankLinesInDeclarations);

    myKeepBlankLinesInCode = createTextField();
    optionGroup.add(new JLabel("In code:"), myKeepBlankLinesInCode);

    myKeepBlankLinesBeforeRBrace = createTextField();
    optionGroup.add(new JLabel("Before '}': "), myKeepBlankLinesBeforeRBrace);

    return optionGroup.createPanel();
  }

  private JPanel createPreviewPanel() {
    JPanel panel = new JPanel();
    panel.setBorder(IdeBorderFactory.createTitledBorder("Preview"));
    panel.setLayout(new BorderLayout());
    myEditor = createEditor();
    panel.add(myEditor.getComponent(), BorderLayout.CENTER);
    panel.setPreferredSize(new Dimension(200, 0));
    return panel;
  }

  private static Editor createEditor() {
    EditorFactory editorFactory = EditorFactory.getInstance();
    Document editorDocument = editorFactory.createDocument("");
    EditorEx editor = (EditorEx) editorFactory.createViewer(editorDocument);

    EditorSettings editorSettings = editor.getSettings();
    editorSettings.setWhitespacesShown(true);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);
    editorSettings.setAdditionalColumnsCount(0);
    editorSettings.setAdditionalLinesCount(1);

    EditorColorsScheme scheme = editor.getColorsScheme();
    scheme.setColor(EditorColors.CARET_ROW_COLOR, null);

    editor.setHighlighter(HighlighterFactory.createJavaHighlighter(scheme, LanguageLevel.HIGHEST));
    return editor;
  }

  private void updatePreview() {
    if(!toUpdatePreview) {
      return;
    }
    final String text =
       "/*\n" +
       " * This is a sample file.\n" +
       " */\n" +
       "package com.intellij.samples;\n" +
       "import com.intellij.idea.Main;\n" +
       "import javax.swing.*;\n" +
       "import java.util.Vector;\n" +
       "public class Foo {\n" +
       "  private int field1;\n" +
       "  private int field2;\n" +
       "  public void foo1() {\n\n" +
       "  }\n" +
       "  public void foo2() {\n" +
       "  }\n\n" +
       "}";

    final Project project = ProjectManager.getInstance().getDefaultProject();
    final PsiManager manager = PsiManager.getInstance(project);
    ApplicationManager.getApplication().runWriteAction(
      new Runnable(){
        public void run () {
          PsiElementFactory factory = manager.getElementFactory();
          try{
            PsiFile psiFile = factory.createFileFromText("a.java", text);

            CodeStyleSettings saved = mySettings;
            mySettings = (CodeStyleSettings)mySettings.clone();
            apply();

            CodeStyleSettingsManager.getInstance(project).setTemporarySettings(mySettings);
            CodeStyleManager.getInstance(project).reformat(psiFile);
            CodeStyleSettingsManager.getInstance(project).dropTemporarySettings();

            myEditor.getSettings().setTabSize(mySettings.getTabSize(StdFileTypes.JAVA));
            Document document = myEditor.getDocument();
            document.replaceString(0, document.getTextLength(), psiFile.getText());

            mySettings = saved;
          }
          catch(IncorrectOperationException e){
            LOG.error(e);
          }
        }
      }
    );
  }

  public void reset() {
    toUpdatePreview = false;
    myKeepBlankLinesInDeclarations.setText(""+mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS);
    myKeepBlankLinesInCode.setText(""+mySettings.KEEP_BLANK_LINES_IN_CODE);
    myKeepBlankLinesBeforeRBrace.setText(""+mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE);
    myBlankLinesBeforePackage.setText(""+mySettings.BLANK_LINES_BEFORE_PACKAGE);
    myBlankLinesAfterPackage.setText(""+mySettings.BLANK_LINES_AFTER_PACKAGE);
    myBlankLinesBeforeImports.setText(""+mySettings.BLANK_LINES_BEFORE_IMPORTS);
    myBlankLinesAfterImports.setText(""+mySettings.BLANK_LINES_AFTER_IMPORTS);
    myBlankLinesAroundClass.setText(""+mySettings.BLANK_LINES_AROUND_CLASS);
    myBlankLinesAroundField.setText(""+mySettings.BLANK_LINES_AROUND_FIELD);
    myBlankLinesAroundMethod.setText(""+mySettings.BLANK_LINES_AROUND_METHOD);
    myBlankLinesAfterClassHeader.setText(""+mySettings.BLANK_LINES_AFTER_CLASS_HEADER);
    toUpdatePreview = true;
    updatePreview();
 }

  public void apply() {
    mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS = getValue(myKeepBlankLinesInDeclarations);
    mySettings.KEEP_BLANK_LINES_IN_CODE = getValue(myKeepBlankLinesInCode);
    mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE = getValue(myKeepBlankLinesBeforeRBrace);
    mySettings.BLANK_LINES_BEFORE_PACKAGE = getValue(myBlankLinesBeforePackage);
    mySettings.BLANK_LINES_AFTER_PACKAGE = getValue(myBlankLinesAfterPackage);
    mySettings.BLANK_LINES_BEFORE_IMPORTS = getValue(myBlankLinesBeforeImports);
    mySettings.BLANK_LINES_AFTER_IMPORTS = getValue(myBlankLinesAfterImports);
    mySettings.BLANK_LINES_AROUND_CLASS = getValue(myBlankLinesAroundClass);
    mySettings.BLANK_LINES_AROUND_FIELD = getValue(myBlankLinesAroundField);
    mySettings.BLANK_LINES_AROUND_METHOD = getValue(myBlankLinesAroundMethod);
    mySettings.BLANK_LINES_AFTER_CLASS_HEADER = getValue(myBlankLinesAfterClassHeader);
  }

  public boolean isModified() {
    boolean isModified;
    isModified = mySettings.KEEP_BLANK_LINES_IN_DECLARATIONS != getValue(myKeepBlankLinesInDeclarations);
    isModified |= mySettings.KEEP_BLANK_LINES_IN_CODE != getValue(myKeepBlankLinesInCode);
    isModified |= mySettings.KEEP_BLANK_LINES_BEFORE_RBRACE != getValue(myKeepBlankLinesBeforeRBrace);
    isModified |= mySettings.BLANK_LINES_BEFORE_PACKAGE != getValue(myBlankLinesBeforePackage);
    isModified |= mySettings.BLANK_LINES_AFTER_PACKAGE != getValue(myBlankLinesAfterPackage);
    isModified |= mySettings.BLANK_LINES_BEFORE_IMPORTS != getValue(myBlankLinesBeforeImports);
    isModified |= mySettings.BLANK_LINES_AFTER_IMPORTS != getValue(myBlankLinesAfterImports);
    isModified |= mySettings.BLANK_LINES_AROUND_CLASS != getValue(myBlankLinesAroundClass);
    isModified |= mySettings.BLANK_LINES_AROUND_FIELD != getValue(myBlankLinesAroundField);
    isModified |= mySettings.BLANK_LINES_AROUND_METHOD != getValue(myBlankLinesAroundMethod);
    isModified |= mySettings.BLANK_LINES_AFTER_CLASS_HEADER != getValue(myBlankLinesAfterClassHeader);
    return isModified;
  }


  private static int getValue(JTextField textField) {
    int ret = 0;
    try{
      ret = Integer.parseInt(textField.getText());
      if(ret < 0) {
        ret = 0;
      }
      if(ret > 10) {
        ret = 10;
      }
    }
    catch(NumberFormatException e){
    }
    return ret;
  }

  private JTextField createTextField() {
    JTextField textField = new JTextField(6);

    textField.getDocument().addDocumentListener(new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        updatePreview();
      }
    });

    return textField;
  }

}