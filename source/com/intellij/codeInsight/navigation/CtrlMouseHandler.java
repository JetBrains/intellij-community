package com.intellij.codeInsight.navigation;

import com.intellij.ant.PsiAntElement;
import com.intellij.ant.impl.dom.impl.PsiAntTarget;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.javadoc.JavaDocUtil;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.jsp.JspToken;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.ui.LightweightHint;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class CtrlMouseHandler implements ProjectComponent {
  private Project myProject;
  private static TextAttributes ourReferenceAttributes;
  private RangeHighlighter myHighlighter;
  private Editor myHighlighterView;
  private Cursor myStoredCursor;
  private Info myStoredInfo;
  private TooltipProvider myTooltipProvider = null;

  private KeyListener myEditorKeyListener = new KeyAdapter() {
    public void keyPressed(final KeyEvent e) {
      handleKey(e);
    }

    public void keyReleased(final KeyEvent e) {
      handleKey(e);
    }

    private void handleKey(final KeyEvent e) {
      int modifiers = e.getModifiers();

      if (isControlShiftMask(modifiers)) {
        if (myTooltipProvider != null) {
          if (!myTooltipProvider.isTypeBrowsed()) {
            disposeHighlighter();
          }
          myTooltipProvider.execute(true);
        }
      } else if (isControlMask(modifiers)) {
        if (myTooltipProvider != null) {
          if (myTooltipProvider.isTypeBrowsed()) {
            disposeHighlighter();
          }
          myTooltipProvider.execute(false);
        }
      } else {
        disposeHighlighter();
        myTooltipProvider = null;
      }
    }
  };

  private FileEditorManagerListener myFileEditorManagerListener = new FileEditorManagerAdapter() {
    public void selectionChanged(FileEditorManagerEvent e) {
      disposeHighlighter();
      myTooltipProvider = null;
    }
  };

  private VisibleAreaListener myVisibleAreaListener = new VisibleAreaListener() {
    public void visibleAreaChanged(VisibleAreaEvent e) {
      disposeHighlighter();
      myTooltipProvider = null;
    }
  };

  private EditorMouseAdapter myEditorMouseAdapter = new EditorMouseAdapter() {
    public void mouseReleased(EditorMouseEvent e) {
      disposeHighlighter();
      myTooltipProvider = null;
    }
  };

  private EditorMouseMotionListener myEditorMouseMotionListener = new EditorMouseMotionAdapter() {
    public void mouseMoved(final EditorMouseEvent e) {
      if (e.isConsumed()) {
        return;
      }

      MouseEvent mouseEvent = e.getMouseEvent();

      Editor editor = e.getEditor();
      Point point = mouseEvent.getPoint();
      LogicalPosition pos = editor.xyToLogicalPosition(new Point(point.x, point.y));
      int offset = editor.logicalPositionToOffset(pos);
      int selStart = editor.getSelectionModel().getSelectionStart();
      int selEnd = editor.getSelectionModel().getSelectionEnd();

      int modifiers = mouseEvent.getModifiers();

      if ((!isControlMask(modifiers) && !isControlShiftMask(modifiers)) || offset >= selStart && offset < selEnd) {
        disposeHighlighter();
        myTooltipProvider = null;
        return;
      }

      myTooltipProvider = new TooltipProvider(editor, pos);
      myTooltipProvider.execute(isControlShiftMask(modifiers));
    }
  };

  static {
    ourReferenceAttributes = new TextAttributes();
    ourReferenceAttributes.setForegroundColor(Color.blue);
    ourReferenceAttributes.setEffectColor(Color.blue);
    ourReferenceAttributes.setEffectType(EffectType.LINE_UNDERSCORE);
  }

  public CtrlMouseHandler(Project project) {
    myProject = project;
  }

  public String getComponentName() {
    return "CtrlMouseHandler";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public void projectOpened() {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    eventMulticaster.addEditorMouseListener(myEditorMouseAdapter);
    eventMulticaster.addEditorMouseMotionListener(myEditorMouseMotionListener);
  }

  public void projectClosed() {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    eventMulticaster.removeEditorMouseListener(myEditorMouseAdapter);
    eventMulticaster.removeEditorMouseMotionListener(myEditorMouseMotionListener);
  }

  private static boolean isControlMask(int modifiers) {
    int mask = SystemInfo.isMac ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK;
    return modifiers == mask;
  }

  private static boolean isControlShiftMask(int modifiers) {
    int mask = (SystemInfo.isMac ? KeyEvent.META_MASK : KeyEvent.CTRL_MASK) | KeyEvent.SHIFT_MASK;
    return modifiers == mask;
  }

  private static class JavaInfoGenerator {
    private static void newLine(StringBuffer buffer) {
      // Don't know why space has to be added after newline for good text alignment...
      buffer.append("\n ");
    }

    private static void generateType(StringBuffer buffer, PsiType type, PsiElement context) {
      if (type instanceof PsiPrimitiveType) {
        buffer.append(type.getCanonicalText());

        return;
      }

      if (type instanceof PsiWildcardType) {
        PsiWildcardType wc = ((PsiWildcardType) type);
        PsiType bound = wc.getBound();

        buffer.append("?");

        if (bound != null) {
          buffer.append(wc.isExtends() ? " extends " : " super ");
          generateType(buffer, bound, context);
        }
      }

      if (type instanceof PsiArrayType) {
        generateType(buffer, ((PsiArrayType) type).getComponentType(), context);
        buffer.append("[]");

        return;
      }

      if (type instanceof PsiClassType) {
        PsiClassType.ClassResolveResult result = ((PsiClassType) type).resolveGenerics();
        PsiClass psiClass = result.getElement();
        PsiSubstitutor psiSubst = result.getSubstitutor();

        if (psiClass == null || psiClass instanceof PsiTypeParameter) {
          buffer.append(type.getPresentableText());
          return;
        }

        buffer.append(JavaDocUtil.getShortestClassName(psiClass, context));

        if (psiClass.hasTypeParameters()) {
          StringBuffer subst = new StringBuffer();
          boolean goodSubst = true;

          PsiTypeParameter[] params = psiClass.getTypeParameterList().getTypeParameters();

          subst.append("<");
          for (int i = 0; i < params.length; i++) {
            PsiType t = psiSubst.substitute(params[i]);

            if (t == null) {
              goodSubst = false;
              break;
            }

            generateType(subst, t, context);

            if (i < params.length - 1) {
              subst.append(", ");
            }
          }

          if (goodSubst) {
            subst.append(">");
            String text = subst.toString();

            buffer.append(text);
          }
        }
      }

      return;
    }

    private static void generateInitializer(StringBuffer buffer, PsiVariable variable) {
      PsiExpression initializer = variable.getInitializer();
      if (initializer != null) {
        String text = initializer.getText().trim();
        int index1 = text.indexOf('\n');
        if (index1 < 0) index1 = text.length();
        int index2 = text.indexOf('\r');
        if (index2 < 0) index2 = text.length();
        int index = Math.min(index1, index2);
        boolean trunc = index < text.length();
        text = text.substring(0, index);
        buffer.append(" = ");
        buffer.append(text);
        if (trunc) {
          buffer.append("...");
        }
      }
    }

    private static void generateModifiers(StringBuffer buffer, PsiElement element) {
      String modifiers = PsiFormatUtil.formatModifiers(element, PsiFormatUtil.JAVADOC_MODIFIERS_ONLY);

      if (modifiers.length() > 0) {
        buffer.append(modifiers);
        buffer.append(" ");
      }
    }

    private static String generatePackageInfo(PsiPackage aPackage) {
      return aPackage.getQualifiedName();
    }

    private static String generateAttributeValueInfo(PsiAntElement antElement) {
      if (antElement instanceof PsiAntTarget) return null;

      PsiElement navigationElement = antElement.getNavigationElement();
      if (navigationElement instanceof XmlAttributeValue) {
        return PsiTreeUtil.getParentOfType(navigationElement, XmlTag.class).getText();
      }

      return null;
    }

    private static String generateClassInfo(PsiClass aClass) {
      StringBuffer buffer = new StringBuffer();

      if (aClass instanceof PsiAnonymousClass) return "anonymous class";

      PsiFile file = aClass.getContainingFile();

      if (file instanceof PsiJavaFile) {
        String packageName = ((PsiJavaFile) file).getPackageName();
        if (packageName.length() > 0) {
          buffer.append(packageName);
          newLine(buffer);
        }
      }

      generateModifiers(buffer, aClass);

      buffer.append(aClass.isInterface() ? "interface " : aClass instanceof PsiTypeParameter ? "class parameter " : "class ");

      buffer.append(JavaDocUtil.getShortestClassName(aClass, aClass));

      if (aClass.hasTypeParameters()) {
        PsiTypeParameter[] parms = aClass.getTypeParameterList().getTypeParameters();

        buffer.append("<");

        for (int i = 0; i < parms.length; i++) {
          PsiTypeParameter p = parms[i];

          buffer.append(p.getName());

          PsiClassType[] refs = p.getExtendsList().getReferencedTypes();

          if (refs.length > 0) {
            buffer.append(" extends ");

            for (int j = 0; j < refs.length; j++) {
              generateType(buffer, refs[j], aClass);

              if (j < refs.length - 1) {
                buffer.append(" & ");
              }
            }
          }

          if (i < parms.length - 1) {
            buffer.append(", ");
          }
        }

        buffer.append(">");
      }

      PsiReferenceList extendsList = aClass.getExtendsList();
      PsiClassType[] refs = extendsList == null ? PsiClassType.EMPTY_ARRAY : extendsList.getReferencedTypes();
      if (refs.length > 0 || !aClass.isInterface() && !"java.lang.Object".equals(aClass.getQualifiedName())) {
        buffer.append(" extends ");
        if (refs.length == 0) {
          buffer.append("Object");
        } else {
          for (int i = 0; i < refs.length; i++) {
            generateType(buffer, refs[i], aClass);

            if (i < refs.length - 1) {
              buffer.append(", ");
            }
          }
        }
      }

      refs = aClass.getImplementsList().getReferencedTypes();
      if (refs.length > 0) {
        newLine(buffer);
        buffer.append("implements ");
        for (int i = 0; i < refs.length; i++) {
          generateType(buffer, refs[i], aClass);

          if (i < refs.length - 1) {
            buffer.append(", ");
          }
        }
      }

      return buffer.toString();
    }

    private static String generateMethodInfo(PsiMethod method) {
      StringBuffer buffer = new StringBuffer();

      PsiClass parentClass = method.getContainingClass();

      if (parentClass != null) {
        buffer.append(JavaDocUtil.getShortestClassName(parentClass, method));
        newLine(buffer);
      }

      generateModifiers(buffer, method);

      PsiTypeParameter[] params = method.getTypeParameterList().getTypeParameters();

      if (params.length > 0) {
        buffer.append("<");
        for (int i = 0; i < params.length; i++) {
          PsiTypeParameter param = params[i];

          buffer.append(param.getName());

          PsiClassType[] extendees = param.getExtendsList().getReferencedTypes();

          if (extendees.length > 0) {
            buffer.append(" extends ");

            for (int j = 0; j < extendees.length; j++) {
              generateType(buffer, extendees[j], method);

              if (j < extendees.length - 1) {
                buffer.append(" & ");
              }
            }
          }

          if (i < params.length - 1) {
            buffer.append(", ");
          }
        }
        buffer.append("> ");
      }

      if (method.getReturnType() != null) {
        generateType(buffer, method.getReturnType(), method);
        buffer.append(" ");
      }

      buffer.append(method.getName());

      buffer.append(" (");
      PsiParameter[] parms = method.getParameterList().getParameters();
      for (int i = 0; i < parms.length; i++) {
        PsiParameter parm = parms[i];
        generateType(buffer, parm.getType(), method);
        buffer.append(" ");
        if (parm.getName() != null) {
          buffer.append(parm.getName());
        }
        if (i < parms.length - 1) {
          buffer.append(", ");
        }
      }

      buffer.append(")");

      PsiClassType[] refs = method.getThrowsList().getReferencedTypes();
      if (refs.length > 0) {
        newLine(buffer);
        buffer.append(" throws ");
        for (int i = 0; i < refs.length; i++) {
          PsiClass throwsClass = refs[i].resolve();

          if (throwsClass != null) {
            buffer.append(JavaDocUtil.getShortestClassName(throwsClass, method));
          } else {
            buffer.append(refs[i].getPresentableText());
          }

          if (i < refs.length - 1) {
            buffer.append(", ");
          }
        }
      }

      return buffer.toString();
    }

    private static String generateFieldInfo(PsiField field) {
      StringBuffer buffer = new StringBuffer();
      PsiClass parentClass = field.getContainingClass();

      if (parentClass != null) {
        buffer.append(JavaDocUtil.getShortestClassName(parentClass, field));
        newLine(buffer);
      }

      generateModifiers(buffer, field);

      generateType(buffer, field.getType(), field);
      buffer.append(" ");
      buffer.append(field.getName());

      generateInitializer(buffer, field);

      return buffer.toString();
    }

    private static String generateVariableInfo(PsiVariable variable) {
      StringBuffer buffer = new StringBuffer();

      generateModifiers(buffer, variable);

      generateType(buffer, variable.getType(), variable);

      buffer.append(" ");

      buffer.append(variable.getName());
      generateInitializer(buffer, variable);

      return buffer.toString();
    }

    private static String generateFileInfo(PsiFile file) {
      return file.getVirtualFile().getPresentableUrl();
    }

    public static String generateInfo(PsiElement element) {
      if (element instanceof PsiClass) {
        return generateClassInfo((PsiClass) element);
      } else if (element instanceof PsiMethod) {
        return generateMethodInfo((PsiMethod) element);
      } else if (element instanceof PsiField) {
        return generateFieldInfo((PsiField) element);
      } else if (element instanceof PsiVariable) {
        return generateVariableInfo((PsiVariable) element);
      } else if (element instanceof PsiFile) {
        return generateFileInfo((PsiFile) element);
      } else if (element instanceof PsiPackage) {
        return generatePackageInfo((PsiPackage) element);
      } else if (element instanceof PsiAntElement) {
        return generateAttributeValueInfo(((PsiAntElement) element));
      } else {
        return null;
      }
    }
  }

  private static class Info {
    public final PsiElement myTargetElement;
    public final PsiElement myElementAtPointer;
    public final int myStartOffset;
    public final int myEndOffset;

    public Info(PsiElement targetElement, PsiElement elementAtPointer) {
      myTargetElement = targetElement;
      myElementAtPointer = elementAtPointer;
      myStartOffset = elementAtPointer.getTextOffset();
      myEndOffset = myStartOffset + elementAtPointer.getTextLength();
    }

    public Info(PsiElement targetElement, PsiElement elementAtPointer, int startOffset, int endOffset) {
      myTargetElement = targetElement;
      myElementAtPointer = elementAtPointer;
      myStartOffset = startOffset;
      myEndOffset = endOffset;
    }
  }

  private Info getInfoAt(final Editor editor, LogicalPosition pos, boolean browseType) {
    Document document = editor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (file == null) return null;
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    final int offset = editor.logicalPositionToOffset(pos);

    int selStart = editor.getSelectionModel().getSelectionStart();
    int selEnd = editor.getSelectionModel().getSelectionEnd();

    if (offset >= selStart && offset < selEnd) return null;


    PsiElement targetElement;
    if (browseType) {
      targetElement = GotoTypeDeclarationAction.findSymbolType(myProject, editor, offset);
    } else {
      PsiReference ref = TargetElementUtil.findReference(editor, offset);
      if (ref != null) {
        PsiElement resolvedElement = ref.resolve();

        if (resolvedElement != null) {
          PsiElement e = ref.getElement();
          return new Info(resolvedElement,
                  e,
                  e.getTextRange().getStartOffset() + ref.getRangeInElement().getStartOffset(),
                  e.getTextRange().getStartOffset() + ref.getRangeInElement().getEndOffset());
        }
      }
      targetElement = GotoDeclarationAction.findTargetElement(myProject, editor, offset);
    }
    if (targetElement != null && targetElement.isPhysical()) {
      PsiElement elementAtPointer = file.findElementAt(offset);
      if (elementAtPointer instanceof PsiIdentifier
              || elementAtPointer instanceof PsiKeyword
              || elementAtPointer instanceof JspToken
              || elementAtPointer instanceof PsiDocToken
              || elementAtPointer instanceof XmlToken) {
        return new Info(targetElement, elementAtPointer);
      }
    }

    return null;
  }

  private void disposeHighlighter() {
    if (myHighlighter != null) {
      myHighlighterView.getMarkupModel().removeHighlighter(myHighlighter);
      Component internalComponent = myHighlighterView.getContentComponent();
      internalComponent.setCursor(myStoredCursor);
      internalComponent.removeKeyListener(myEditorKeyListener);
      myHighlighterView.getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
      FileEditorManager.getInstance(myProject).removeFileEditorManagerListener(myFileEditorManagerListener);
      HintManager hintManager = HintManager.getInstance();
      hintManager.hideAllHints();
      myHighlighter = null;
      myHighlighterView = null;
      myStoredCursor = null;
    }
    myStoredInfo = null;
  }

  private class TooltipProvider {
    private final Editor myEditor;
    private final LogicalPosition myPosition;
    private boolean myBrowseType;

    public TooltipProvider(Editor editor, LogicalPosition pos) {
      myEditor = editor;
      myPosition = pos;
    }

    public boolean isTypeBrowsed() {
      return myBrowseType;
    }

    public void execute(boolean browseType) {
      myBrowseType = browseType;
      Info info = getInfoAt(myEditor, myPosition, myBrowseType);
      if (info == null) return;

      Component internalComponent = myEditor.getContentComponent();
      if (myHighlighter != null) {
        if (!Comparing.equal(info.myElementAtPointer, myStoredInfo.myElementAtPointer) ||
                info.myStartOffset != myStoredInfo.myStartOffset ||
                info.myEndOffset != myStoredInfo.myEndOffset) {
          disposeHighlighter();
        } else {
          // highlighter already set
          internalComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
          return;
        }
      }

      if (info.myTargetElement != null && info.myElementAtPointer != null) {
        //if (info.myTargetElement.isPhysical()) {
        installLinkHighlighter(info);
        //}

        internalComponent.addKeyListener(myEditorKeyListener);
        myEditor.getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
        myStoredCursor = internalComponent.getCursor();
        myStoredInfo = info;
        internalComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        FileEditorManager.getInstance(myProject).addFileEditorManagerListener(myFileEditorManagerListener);

        String text = JavaInfoGenerator.generateInfo(info.myTargetElement); //JavaDocManager.getInstance(myProject).getDocInfo(info.myTargetElement);

        if (text == null) return;

        JLabel label = HintUtil.createInformationLabel(text);
        label.setUI(new MultiLineLabelUI());
        Font FONT = UIManager.getFont("Label.font");
        label.setFont(FONT);
        final LightweightHint hint = new LightweightHint(label);
        final HintManager hintManager = HintManager.getInstance();
        label.addMouseMotionListener(new MouseMotionAdapter() {
          public void mouseMoved(MouseEvent e) {
            hintManager.hideAllHints();
          }
        });
        Point p = hintManager.getHintPosition(hint, myEditor, myPosition, HintManager.ABOVE);
        hintManager.showEditorHint(hint, myEditor, p,
                HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE |
                HintManager.HIDE_BY_SCROLLING,
                0, false);
      }
    }

    private void installLinkHighlighter(Info info) {
      int startOffset = info.myStartOffset;
      int endOffset = info.myEndOffset;
      myHighlighter =
              myEditor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, HighlighterLayer.SELECTION + 1,
                      ourReferenceAttributes, HighlighterTargetArea.EXACT_RANGE);
      myHighlighterView = myEditor;
    }
  }

}