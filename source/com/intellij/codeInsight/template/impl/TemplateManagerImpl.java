package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.TemplateStateListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspDeclaration;
import com.intellij.psi.jsp.JspExpression;
import com.intellij.psi.jsp.JspUtil;

public class TemplateManagerImpl extends TemplateManager implements ProjectComponent {
  protected Project myProject;
  private EditorFactoryListener myEditorFactoryListener;

  private static final Key TEMPLATE_STATE_KEY = Key.create("TEMPLATE_STATE_KEY");

  public TemplateManagerImpl(Project project) {
    myProject = project;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectClosed() {
    EditorFactory.getInstance().removeEditorFactoryListener(myEditorFactoryListener);
  }

  public void projectOpened() {
    myEditorFactoryListener = new EditorFactoryAdapter() {
      public void editorReleased(EditorFactoryEvent event) {
        Editor removedEditor = event.getEditor();
        TemplateState tState = getTemplateState(removedEditor);
        if (tState != null) {
          tState.dispose();
        }
        removedEditor.putUserData(TEMPLATE_STATE_KEY, null);
      }
    };
    EditorFactory.getInstance().addEditorFactoryListener(myEditorFactoryListener);
  }

  public Template createTemplate(String key, String group) {
    return new TemplateImpl(key, group);
  }

  public Template createTemplate(String key, String group, String text) {
    return new TemplateImpl(key, text, group);
  }

  public static TemplateState getTemplateState(Editor editor) {
    return (TemplateState) editor.getUserData(TEMPLATE_STATE_KEY);
  }

  private TemplateState initTemplateState(final Editor editor) {
    TemplateState prevState = getTemplateState(editor);
    if (prevState != null) {
      prevState.dispose();
    }
    TemplateState state = new TemplateState(myProject, editor);
    editor.putUserData(TEMPLATE_STATE_KEY, state);
    return state;
  }

  public boolean startTemplate(Editor editor, char shortcutChar) {
    return startTemplate(this, editor, shortcutChar);
  }

  public void startTemplate(final Editor editor, Template template) {
    startTemplate(editor, template, null);
  }

  public void startTemplate(Editor editor, String selectionString, Template template) {
    startTemplate(editor, selectionString, template, null);
  }

  private void startTemplate(final Editor editor, final String selectionString, final Template template, TemplateStateListener listener) {
    final TemplateState templateState = initTemplateState(editor);

    templateState.getProperties().put(ExpressionContext.SELECTION, selectionString);

    if (listener != null) {
      templateState.addTemplateStateListener(listener);
    }
    CommandProcessor.getInstance().executeCommand(
      myProject, new Runnable() {
        public void run() {
          if (selectionString != null) {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                EditorModificationUtil.deleteSelectedText(editor);
              }
            });
          } else {
            editor.getSelectionModel().removeSelection();
          }
          templateState.start((TemplateImpl) template);
        }
      },
      "Insert Code Template", null
    );

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      if (!templateState.isFinished()) templateState.gotoEnd();
    }
  }

  public void startTemplate(final Editor editor, final Template template, TemplateStateListener listener) {
    startTemplate(editor, null, template, listener);
  }

  public boolean startTemplate(TemplateManagerImpl templateManager, final Editor editor, char shortcutChar) {
    final Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(templateManager.myProject).getPsiFile(document);
    if (file == null) return false;

    TemplateSettings templateSettings = TemplateSettings.getInstance();
    CharSequence text = document.getCharsSequence();
    final int caretOffset = editor.getCaretModel().getOffset();
    TemplateImpl template = null;
    int wordStart = 0;
    for (int i = 1; i <= templateSettings.getMaxKeyLength(); i++) {
      wordStart = caretOffset - i;
      if (wordStart < 0) {
        break;
      }
      String key = text.subSequence(wordStart, caretOffset).toString();
      template = templateSettings.getTemplate(key);
      if (template != null && template.isDeactivated()) {
        template = null;
      }
      if (template != null) {
        if (Character.isJavaIdentifierStart(key.charAt(0))) {
          if (wordStart > 0 && Character.isJavaIdentifierPart(text.charAt(wordStart - 1))) {
            template = null;
            continue;
          }
        }
        break;
      }
    }

    if (template == null) return false;

    if (shortcutChar != 0 && getShortcutChar(template) != shortcutChar) {
      return false;
    }

    if (template.isSelectionTemplate()) return false;

    CommandProcessor.getInstance().executeCommand(
      myProject, new Runnable() {
        public void run() {
          PsiDocumentManager.getInstance(myProject).commitDocument(document);
        }
      },
      "", null
    );
    if (!templateManager.checkContext(template, file, caretOffset - template.getKey().length())) {
      return false;
    }
    if (!editor.getDocument().isWritable()) {
      editor.getDocument().fireReadOnlyModificationAttempt();
      return false;
    }
    final int wordStart0 = wordStart;
    final TemplateImpl template0 = template;
    final TemplateState templateState0 = templateManager.initTemplateState(editor);
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(
      myProject, new Runnable() {
        public void run() {
          editor.getDocument().deleteString(wordStart0, caretOffset);
          editor.getCaretModel().moveToOffset(wordStart0);
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          editor.getSelectionModel().removeSelection();
          templateState0.start(template0);
        }
      },
      "Insert Code Template", null
    );
    return true;
  }

  private static char getShortcutChar(TemplateImpl template) {
    char c = template.getShortcutChar();
    if (c == TemplateSettings.DEFAULT_CHAR) {
      return TemplateSettings.getInstance().getDefaultShortcutChar();
    }
    else {
      return c;
    }
  }

  private boolean checkContext(final TemplateImpl template, final PsiFile file, final int offset) {
    int contextType = getContextType(file, offset);
    TemplateContext templateContext = template.getTemplateContext();
    return templateContext.isInContext(contextType);
  }

  public int getContextType(PsiFile file, int offset) {
    VirtualFile vFile = file.getVirtualFile();
    FileType fileType;
    if (file instanceof PsiCodeFragment) {
      fileType = StdFileTypes.JAVA;
    }
    else {
      fileType = FileTypeManager.getInstance().getFileTypeByFile(vFile);
    }

    if (fileType == StdFileTypes.XML) {
      return TemplateContext.XML_CONTEXT;
    }
    if (fileType == StdFileTypes.HTML) {
      return TemplateContext.HTML_CONTEXT;
    }
    if (fileType == StdFileTypes.PLAIN_TEXT) {
      return TemplateContext.OTHER_CONTEXT;
    }
    PsiElement element = file.findElementAt(offset);
    if (fileType == StdFileTypes.JSP) {
      PsiElement parent = element;
      while (parent != null && !(parent instanceof JspExpression) && !(parent instanceof JspDeclaration)) {
        parent = parent.getParent();
      }
      if (parent == null && JspUtil.getContainingScriptletStart(element) == null) {
        return TemplateContext.JSP_CONTEXT;
      }
    }
    if (isInComment(element)) {
      return TemplateContext.JAVA_COMMENT_CONTEXT;
    }
    if (element instanceof PsiJavaToken) {
      if (((PsiJavaToken)element).getTokenType() == JavaTokenType.STRING_LITERAL) {
        return TemplateContext.JAVA_STRING_CONTEXT;
      }
    }
    return TemplateContext.JAVA_CODE_CONTEXT;
  }

  private boolean isInComment(PsiElement element) {
    if (element == null) {
      return false;
    }
    if (element instanceof PsiComment) {
      return true;
    }
    return isInComment(element.getParent());
  }

  public String getComponentName() {
    return "TemplateManager";
  }

  public Template getActiveTemplate(Editor editor) {
    final TemplateState templateState = getTemplateState(editor);
    return templateState != null ? templateState.getTemplate() : null;
  }
}
