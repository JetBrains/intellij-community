package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.jsp.JspAction;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspToken;
import com.intellij.psi.jsp.JspTokenType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.ui.LightweightHint;
import com.intellij.util.Alarm;
import com.intellij.util.text.CharArrayUtil;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Iterator;

class ParameterInfoController {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.hint.ParameterInfoController");

  public static final int TYPE_XML_ATTRS = 1;

  private Project myProject;
  private Editor myEditor;
  private int myType;
  private String myParameterCloseChars;
  private RangeMarker myLbraceMarker;
  private LightweightHint myHint;
  private ParameterInfoComponent myComponent;

  private CaretListener myEditorCaretListener;
  private DocumentListener myEditorDocumentListener;
  private PropertyChangeListener myLookupListener;

  private Alarm myAlarm = new Alarm();
  private static final int DELAY = 200;

  private boolean myDisposed = false;

  /**
   * Keeps Vector of ParameterInfoController's in Editor
   */
  private static final Key<ArrayList<ParameterInfoController>> ALL_CONTROLLERS_KEY = Key.create("ParameterInfoController.ALL_CONTROLLERS_KEY");
  public static final String DEFAULT_PARAMETER_CLOSE_CHARS = ",){}";

  private static ArrayList<ParameterInfoController> getAllControllers(Editor editor) {
    ArrayList<ParameterInfoController> array = editor.getUserData(ALL_CONTROLLERS_KEY);
    if (array == null){
      array = new ArrayList<ParameterInfoController>();
      editor.putUserData(ALL_CONTROLLERS_KEY, array);
    }
    return array;
  }

  public static boolean isAlreadyShown(Editor editor, int lbraceOffset) {
    ArrayList allControllers = getAllControllers(editor);
    for(int i = 0; i < allControllers.size(); i++){
      ParameterInfoController controller = (ParameterInfoController)allControllers.get(i);
      if (!controller.myHint.isVisible()){
        controller.dispose();
        continue;
      }
      if (controller.myLbraceMarker.getStartOffset() == lbraceOffset) return true;
    }
    return false;
  }

  public ParameterInfoController(
      Project project,
      Editor editor,
      int lbraceOffset,
      LightweightHint hint,
      int type) {
    this (project, editor, lbraceOffset, hint, type, DEFAULT_PARAMETER_CLOSE_CHARS);
  }
  
  
  public ParameterInfoController(
      Project project,
      Editor editor,
      int lbraceOffset,
      LightweightHint hint,
      int type,
      String parameterCloseChars) {
    myProject = project;
    myEditor = editor;
    myType = type;
    myParameterCloseChars = parameterCloseChars;
    myLbraceMarker = (editor.getDocument()).createRangeMarker(lbraceOffset, lbraceOffset);
    myHint = hint;
    myComponent = (ParameterInfoComponent)myHint.getComponent();

    ArrayList<ParameterInfoController> allControllers = getAllControllers(myEditor);
    allControllers.add(this);

    myEditorCaretListener = new CaretListener(){
      public void caretPositionChanged(CaretEvent e) {
        if (myType == TYPE_XML_ATTRS) {
          myAlarm.cancelAllRequests();
          addAlarmRequest();
          return;
        }

        int oldOffset = myEditor.logicalPositionToOffset(e.getOldPosition());
        int newOffset = myEditor.logicalPositionToOffset(e.getNewPosition());
        if (newOffset <= myLbraceMarker.getStartOffset()){
          myAlarm.cancelAllRequests();
          addAlarmRequest();
          return;
        }
        int offset1 = Math.min(oldOffset, newOffset);
        int offset2 = Math.max(oldOffset, newOffset);
        CharSequence chars = myEditor.getDocument().getCharsSequence();
        int offset = CharArrayUtil.shiftForwardUntil(chars, offset1, myParameterCloseChars);
        if (offset < offset2){
          myAlarm.cancelAllRequests();
          addAlarmRequest();
        }
        else{
          if (myAlarm.cancelAllRequests() > 0){
            addAlarmRequest();
          }
        }
      }
    };
    myEditor.getCaretModel().addCaretListener(myEditorCaretListener);

    myEditorDocumentListener = new DocumentAdapter(){
      public void documentChanged(DocumentEvent e) {
        CharSequence oldS = e.getOldFragment();
        if (CharArrayUtil.shiftForwardUntil(oldS, 0, myParameterCloseChars) < oldS.length()){
          myAlarm.cancelAllRequests();
          addAlarmRequest();
          return;
        }
        CharSequence newS = e.getNewFragment();
        if (CharArrayUtil.shiftForwardUntil(newS, 0, myParameterCloseChars) < newS.length()){
          myAlarm.cancelAllRequests();
          addAlarmRequest();
          return;
        }
        if (myAlarm.cancelAllRequests() > 0){
          addAlarmRequest();
        }
      }
    };
    myEditor.getDocument().addDocumentListener(myEditorDocumentListener);

    myLookupListener = new PropertyChangeListener() {
      public void propertyChange(PropertyChangeEvent evt) {
        if (LookupManager.PROP_ACTIVE_LOOKUP.equals(evt.getPropertyName())){
          final Lookup lookup = (Lookup)evt.getNewValue();
          if (lookup != null){
            adjustPositionForLookup(lookup);
          }
        }
      }
    };
    LookupManager.getInstance(project).addPropertyChangeListener(myLookupListener);

    updateComponent();
  }

  private void dispose(){
    if (myDisposed) return;
    myDisposed = true;

    ArrayList allControllers = getAllControllers(myEditor);
    allControllers.remove(this);
    myEditor.getCaretModel().removeCaretListener(myEditorCaretListener);
    myEditor.getDocument().removeDocumentListener(myEditorDocumentListener);
    LookupManager.getInstance(myProject).removePropertyChangeListener(myLookupListener);
  }

  private void adjustPositionForLookup(Lookup lookup) {
    if (!myHint.isVisible()){
      dispose();
      return;
    }

    HintManager hintManager = HintManager.getInstance();
    short constraint = lookup.isPositionedAboveCaret() ? HintManager.UNDER : HintManager.ABOVE;
    Point p = hintManager.getHintPosition(myHint, myEditor, constraint);
    Dimension hintSize = myHint.getComponent().getPreferredSize();
    JLayeredPane layeredPane = myEditor.getComponent().getRootPane().getLayeredPane();
    p.x = Math.min(p.x, layeredPane.getWidth() - hintSize.width);
    p.x = Math.max(p.x, 0);
    myHint.setBounds(p.x, p.y,hintSize.width,hintSize.height);
  }

  private void addAlarmRequest(){
    Runnable request = new Runnable(){
      public void run(){
        updateComponent();
      }
    };
    myAlarm.addRequest(request, DELAY);
  }

  private void updateComponent(){
    if (!myHint.isVisible()){
      dispose();
      return;
    }

    PsiDocumentManager.getInstance(myProject).commitAllDocuments();

    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    CharSequence chars = myEditor.getDocument().getCharsSequence();
    int offset = CharArrayUtil.shiftBackward(chars, myEditor.getCaretModel().getOffset() - 1, " \t") + 1;

    PsiExpressionList list = findArgumentList(file, offset, myLbraceMarker.getStartOffset());
    if (list != null){
      updateMethodInfo(list, offset);
    }
    else{
      final XmlTag tag = findXmlTag(file, offset);
      if (tag != null) {
        myComponent.setCurrentItem(tag);
      }
      else{
        final JspAction action = findJspAction(file, offset);
        if (action != null) {
          myComponent.setCurrentItem(action);
        }
        else{
          if (findParentOfType(file, offset, PsiAnnotation.class) != null) {
            int offset1 = CharArrayUtil.shiftForward(chars, myEditor.getCaretModel().getOffset(), " \t");
            if (chars.charAt(offset1) == ',') {
              offset1 = CharArrayUtil.shiftBackward(chars, offset1 - 1, " \t");
            }
            myComponent.setHighlightedMethod(findAnnotationMethod(file, offset1));
          }
          else {
            final PsiReferenceParameterList refParamList = findParentOfType(file, offset, PsiReferenceParameterList.class);
            if (refParamList != null) {
              updateTypeParameterInfo(refParamList, offset);
            }
            else {
              myHint.hide();
              dispose();
            }
          }
        }
      }
    }

    myComponent.update();
  }

  private void updateTypeParameterInfo(PsiReferenceParameterList list, int offset) {
    int index = getCurrentParameterIndex(list, offset);
    myComponent.setCurrentParameter(index);
  }

  public static void nextParameter (Editor editor, int lbraceOffset) {
    ArrayList controllers = getAllControllers(editor);
    for (Iterator iterator = controllers.iterator(); iterator.hasNext();) {
      ParameterInfoController controller = (ParameterInfoController)iterator.next();
      if (!controller.myHint.isVisible()){
        controller.dispose();
        continue;
      }
      if (controller.myLbraceMarker.getStartOffset() == lbraceOffset) {
        controller.prevOrNextParameter(true);
        return;
      }
    }
  }

  public static void prevParameter (Editor editor, int lbraceOffset) {
    ArrayList controllers = getAllControllers(editor);
    for (Iterator iterator = controllers.iterator(); iterator.hasNext();) {
      ParameterInfoController controller = (ParameterInfoController)iterator.next();
      if (!controller.myHint.isVisible()){
        controller.dispose();
        continue;
      }
      if (controller.myLbraceMarker.getStartOffset() == lbraceOffset) {
        controller.prevOrNextParameter(false);
        return;
      }
    }
  }

  private void prevOrNextParameter(boolean isNext) {
    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(myEditor.getDocument());
    CharSequence chars = myEditor.getDocument().getCharsSequence();
    int offset = CharArrayUtil.shiftBackward(chars, myEditor.getCaretModel().getOffset() - 1, " \t") + 1;

    int lbraceOffset = myLbraceMarker.getStartOffset();
    if (lbraceOffset < offset) {
      PsiExpressionList argList = findArgumentList(file, offset, lbraceOffset);
      if (argList != null) {
        int currentParameterIndex = getCurrentParameterIndex(argList, offset);
        PsiExpression currentParameter = null;
        if (currentParameterIndex > 0 && !isNext) {
          currentParameter = argList.getExpressions()[currentParameterIndex - 1];
        }
        else if (currentParameterIndex < argList.getExpressions().length - 1 && isNext) {
          currentParameter = argList.getExpressions()[currentParameterIndex + 1];
        }

        if (currentParameter != null) {
          offset = currentParameter.getTextRange().getStartOffset();
          myEditor.getCaretModel().moveToOffset(offset);
          myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          myEditor.getSelectionModel().removeSelection();
          updateMethodInfo(argList, offset);
        }
      }
    }
  }

  private int getCurrentParameterIndex(PsiElement argList, int offset) {
    int curOffset = argList.getTextRange().getStartOffset();
    PsiElement[] children = argList.getChildren();
    int index = 0;
    for(int i = 0; i < children.length; i++) {
      PsiElement child = children[i];
      curOffset += child.getTextLength();
      if (offset < curOffset) break;
      if (child instanceof PsiJavaToken && ((PsiJavaToken)child).getTokenType() == JavaTokenType.COMMA) {
        index++;
      }
    }

    return index;
  }

  private void updateMethodInfo(PsiExpressionList list, final int offset) {
    int index = getCurrentParameterIndex(list, offset);
    myComponent.setCurrentParameter(index);

    Object[] candidates = myComponent.getObjects();
    PsiExpression[] args = list.getExpressions();
    for(int i = 0; i < candidates.length; i++) {
      boolean enabled = true;
      CandidateInfo candidate = (CandidateInfo) candidates[i];
      PsiMethod method = (PsiMethod) candidate.getElement();
      PsiSubstitutor substitutor = candidate.getSubstitutor();
      LOG.assertTrue(substitutor != null);
      if (!method.isValid() || !substitutor.isValid()){ // this may sometimes happen e,g, when editing method call in field initializer candidates in the same file get invalidated
        myComponent.setEnabled(i, false);
        continue;
      }

      PsiParameter[] parms = method.getParameterList().getParameters();
      if (parms.length <= index){
        if (parms.length > 0){
          if (method.isVarArgs()) {
            for (int j = 0; j < parms.length - 1; j++) {
              PsiType parmType = substitutor.substitute(parms[j].getType());
              PsiType argType = args[j].getType();
              if (argType != null && !parmType.isAssignableFrom(argType)) {
                enabled = false;
                break;
              }
            }

            if (enabled) {
              PsiArrayType lastParmType = (PsiArrayType)substitutor.substitute(parms[parms.length - 1].getType());
              PsiType componentType = lastParmType.getComponentType();

              if (parms.length == args.length) {
                PsiType lastArgType = args[args.length - 1].getType();
                if (lastArgType != null && !lastParmType.isAssignableFrom(lastArgType) &&
                    !componentType.isAssignableFrom(lastArgType)) {
                  enabled = false;
                }
              }
              else {
                for (int j = parms.length; j <= index; j++) {
                  PsiExpression arg = args[j];
                  PsiType argType = arg.getType();
                  if (argType != null && !componentType.isAssignableFrom(argType)) {
                    enabled = false;
                    break;
                  }
                }
              }
            }
          } else {
            enabled = false;
          }
        }
        else{
          enabled = index == 0;
        }
      }
      else{
        for(int j = 0; j < index; j++){
          PsiParameter parm = parms[j];
          PsiExpression arg = args[j];
          LOG.assertTrue(parm.isValid());
          LOG.assertTrue(arg.isValid());
          PsiType parmType = substitutor.substitute(parm.getType());
          PsiType argType = arg.getType();
          if (argType != null && !parmType.isAssignableFrom(argType)){
            enabled = false;
            break;
          }
        }
      }
      myComponent.setEnabled(i, enabled);
    }
  }

  public static PsiExpressionList findArgumentList(PsiFile file, int offset, int lbraceOffset){
    char[] chars = file.textToCharArray();
    if (chars == null) return null;
    if (offset == chars.length) offset--;
    int offset1 = CharArrayUtil.shiftBackward(chars, offset, " \t\n\r");
    if (offset1 < 0) return null;
    boolean acceptRparenth = true;
    boolean acceptLparenth = false;
    if (offset1 != offset){
      offset = offset1;
      acceptRparenth = false;
      acceptLparenth = true;
    }

    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    PsiElement parent = element.getParent();
    while(true){
      if (parent instanceof PsiExpressionList) {
        TextRange range = parent.getTextRange();
        if (!acceptRparenth){
          if (offset == range.getEndOffset() - 1){
            PsiElement[] children = parent.getChildren();
            PsiElement last = children[children.length - 1];
            if (last instanceof PsiJavaToken && ((PsiJavaToken)last).getTokenType() == JavaTokenType.RPARENTH){
              parent = parent.getParent();
              continue;
            }
          }
        }
        if (!acceptLparenth){
          if (offset == range.getStartOffset()){
            parent = parent.getParent();
            continue;
          }
        }
        if (lbraceOffset >= 0 && range.getStartOffset() != lbraceOffset){
          parent = parent.getParent();
          continue;
        }
        break;
      }
      if (parent instanceof PsiFile) return null;
      parent = parent.getParent();
    }
    PsiExpressionList list = (PsiExpressionList)parent;
    PsiElement listParent = list.getParent();
    if (listParent instanceof PsiMethodCallExpression
      || listParent instanceof PsiNewExpression
      || listParent instanceof PsiAnonymousClass
      || listParent instanceof PsiEnumConstant){
      return list;
    }
    else{
      return null;
    }
  }

  public static XmlTag findXmlTag(PsiFile file, int offset){
    if (!(file instanceof XmlFile)) return null;

    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    element = element.getParent();

    while (element != null) {
      if (element instanceof XmlTag) {
        XmlTag tag = (XmlTag)element;

        final PsiElement[] children = tag.getChildren();

        if (offset <= children[0].getTextRange().getStartOffset()) return null;

        for (int i = 0; i < children.length; i++) {
          PsiElement child = children[i];

          final TextRange range = child.getTextRange();
          if (range.getStartOffset() <= offset && range.getEndOffset() > offset) return tag;

          if (child instanceof XmlToken) {
            XmlToken token = (XmlToken)child;
            if (token.getTokenType() == XmlTokenType.XML_TAG_END) return null;
          }
        }

        return null;
      }

      element = element.getParent();
    }

    return null;
  }

  public static PsiCall getCall(PsiExpressionList list){
    if (list.getParent() instanceof PsiMethodCallExpression){
      return (PsiCallExpression)list.getParent();
    }
    else if (list.getParent() instanceof PsiNewExpression){
      return (PsiCallExpression)list.getParent();
    }
    else if (list.getParent() instanceof PsiAnonymousClass){
      return (PsiCallExpression)list.getParent().getParent();
    }
    else if (list.getParent() instanceof PsiEnumConstant){
      return (PsiCall)list.getParent();
    }
    else{
      return null;
    }
  }

  public static JspAction findJspAction(PsiFile file, int offset) {
    if (!(file instanceof JspFile)) return null;

    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    element = element.getParent();

    while (element != null) {
      if (element instanceof JspAction) {
        JspAction action = (JspAction)element;

        final PsiElement[] children = action.getChildren();

        if (offset <= children[0].getTextRange().getStartOffset()) return null;

        for (int i = 0; i < children.length; i++) {
          PsiElement child = children[i];

          final TextRange range = child.getTextRange();
          if (range.getStartOffset() <= offset && range.getEndOffset() > offset) return action;

          if (child instanceof JspToken) {
            JspToken token = (JspToken)child;
            if (token.getTokenType() == JspTokenType.JSP_ACTION_END) return null;
          }
        }

        return null;
      }

      element = element.getParent();
    }

    return null;
  }

  public static PsiAnnotationMethod findAnnotationMethod(PsiFile file, int offset) {
    PsiNameValuePair pair = findParentOfType(file, offset, PsiNameValuePair.class);
    if (pair == null) return null;
    final PsiElement resolved = pair.getReference().resolve();
    return resolved instanceof PsiAnnotationMethod ? ((PsiAnnotationMethod)resolved) : null;
  }
  public static <T extends PsiElement> T findParentOfType (PsiFile file, int offset, Class<T> parentClass) {
    if (!(file instanceof PsiJavaFile)) return null;

    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    return PsiTreeUtil.getParentOfType(element, parentClass);
  }
}
