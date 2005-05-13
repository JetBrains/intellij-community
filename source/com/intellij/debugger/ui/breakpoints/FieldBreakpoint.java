/*
 * Class FieldBreakpoint
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.jdi.VirtualMachineProxy;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import com.sun.jdi.*;
import com.sun.jdi.event.AccessWatchpointEvent;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.ModificationWatchpointEvent;
import com.sun.jdi.request.AccessWatchpointRequest;
import com.sun.jdi.request.ModificationWatchpointRequest;
import org.jdom.Element;

import javax.swing.*;
import java.util.List;

public class FieldBreakpoint extends BreakpointWithHighlighter {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.FieldBreakpoint");
  public boolean WATCH_MODIFICATION = true;
  public boolean WATCH_ACCESS       = false;
  private boolean myIsStatic;
  private String myFieldName;

  public static Icon ICON = IconLoader.getIcon("/debugger/db_field_breakpoint.png");
  public static Icon DISABLED_ICON = IconLoader.getIcon("/debugger/db_disabled_field_breakpoint.png");
  public static Icon DISABLED_DEP_ICON = IconLoader.getIcon("/debugger/db_dep_field_breakpoint.png");
  private static Icon ourInvalidIcon = IconLoader.getIcon("/debugger/db_invalid_field_breakpoint.png");
  private static Icon ourVerifiedIcon = IconLoader.getIcon("/debugger/db_verified_field_breakpoint.png");
  public static final String CATEGORY = "field_breakpoints";

  protected FieldBreakpoint(Project project) {
    super(project);
  }

  private FieldBreakpoint(Project project, RangeHighlighter highlighter, String fieldName) {
    super(project, highlighter);
    LOG.assertTrue(fieldName != null);
    myFieldName = fieldName;
  }

  public boolean isStatic() {
    return myIsStatic;
  }

  public String getFieldName() {
    return myFieldName;
  }


  protected Icon getDisabledIcon() {
    final Breakpoint master = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().findMasterBreakpoint(this);
    return master == null? DISABLED_ICON : DISABLED_DEP_ICON;
  }

  protected Icon getSetIcon() {
    return ICON;
  }

  protected Icon getInvalidIcon () {
    return ourInvalidIcon;
  }

  protected Icon getVerifiedIcon() {
    return ourVerifiedIcon;
  }

  public String getCategory() {
    return CATEGORY;
  }

  public PsiField getPsiField() {
    final SourcePosition sourcePosition = getSourcePosition();
    final PsiField field = ApplicationManager.getApplication().runReadAction((Computable<PsiField>)new Computable<PsiField>() {
      public PsiField compute() {
        final PsiClass psiClass = getPsiClassAt(sourcePosition);
        return psiClass != null ? psiClass.findFieldByName(myFieldName, true) : null;
      }
    });
    if (field != null) {
      return field;
    }
    return getPsiFieldAt(sourcePosition);
  }

  protected PsiField getPsiFieldAt(final SourcePosition sourcePosition) {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiField>() {
      public PsiField compute() {
        PsiFile psiFile = sourcePosition.getFile();
        Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);

        if(document == null) {
          return null;
        }

        final int offset = CharArrayUtil.shiftForward(document.getCharsSequence(), sourcePosition.getOffset(), " \t");
        return PsiTreeUtil.getParentOfType(psiFile.findElementAt(offset), PsiField.class, false);
      }
    });
  }

  protected void reload(PsiFile psiFile) {
    super.reload(psiFile);
    PsiField field = getPsiFieldAt(getSourcePosition());
    if(field != null) {
      myFieldName = field.getName();
      myIsStatic = field.hasModifierProperty(PsiModifier.STATIC);
    }
    if (myIsStatic) {
      INSTANCE_FILTERS_ENABLED = false;
    }
  }

  protected boolean moveTo(SourcePosition position) {
    final PsiField field = getPsiFieldAt(position);
    if (field == null) {
      return false;
    }
    return super.moveTo(SourcePosition.createFromElement(field));
  }

  protected ObjectReference getThisObject(SuspendContextImpl context, LocatableEvent event) throws EvaluateException {
    if (event instanceof ModificationWatchpointEvent) {
      ModificationWatchpointEvent modificationEvent = (ModificationWatchpointEvent)event;
      ObjectReference reference = modificationEvent.object();
      if (reference != null) {  // non-static
        return reference;
      }
    }
    else if (event instanceof AccessWatchpointEvent) {
      AccessWatchpointEvent accessEvent = (AccessWatchpointEvent)event;
      ObjectReference reference = accessEvent.object();
      if (reference != null) { // non-static
        return reference;
      }
    }

    return super.getThisObject(context, event);
  }

  public void createRequestForPreparedClass(DebugProcessImpl debugProcess,
                                            ReferenceType refType) {
    VirtualMachineProxy vm = debugProcess.getVirtualMachineProxy();
    try {
      Field field = refType.fieldByName(myFieldName);
      if (field == null) {
        debugProcess.getRequestsManager().setInvalid(this, "Cannot find field " + myFieldName + " in  class " + refType.name());
        return;
      }
      RequestManagerImpl manager = debugProcess.getRequestsManager();
      if (WATCH_MODIFICATION && vm.canWatchFieldModification()) {
        ModificationWatchpointRequest request = manager.createModificationWatchpointRequest(this, field);
        debugProcess.getRequestsManager().enableRequest(request);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Modification request added");
        }
      }
      if (WATCH_ACCESS && vm.canWatchFieldAccess()) {
        AccessWatchpointRequest request = manager.createAccessWatchpointRequest(this, field);
        debugProcess.getRequestsManager().enableRequest(request);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Access request added field = "+field.name() + "; refType = "+refType.name());
        }
      }
    }
    catch (ObjectCollectedException ex) {
      LOG.debug(ex);
    }
    catch (Exception ex) {
      LOG.debug(ex);
    }
  }

  public String getEventMessage(final LocatableEvent event) {
    StringBuffer message = null;
    if (event instanceof ModificationWatchpointEvent) {
      ModificationWatchpointEvent modificationEvent = (ModificationWatchpointEvent)event;
      message = new StringBuffer(64);
      ObjectReference object = modificationEvent.object();
      if (object != null) {
        message.append("{");
      }
      message.append(modificationEvent.field().declaringType().name());
      if (object != null) {
        message.append('@');
        message.append(object.uniqueID());
        message.append("}");
      }
      message.append('.');
      message.append(myFieldName);
      message.append(" will be modified. Current value = '");
      message.append(modificationEvent.valueCurrent());
      message.append("'; New value = '");
      message.append(modificationEvent.valueToBe());
      message.append("'\n");
    }
    else if (event instanceof AccessWatchpointEvent) {
      AccessWatchpointEvent accessEvent = (AccessWatchpointEvent)event;
      message = new StringBuffer(32);
      ObjectReference object = accessEvent.object();
      if (object != null) {
        message.append("{");
      }
      message.append(accessEvent.field().declaringType().name());
      if (object != null) {
        message.append('@');
        message.append(object.uniqueID());
        message.append("}");
      }
      message.append('.');
      message.append(myFieldName);
      message.append(" will be accessed\n");
    }
    return message != null ? message.toString() : null;
  }

  public String getDisplayName() {
    if(!isValid()) {
      return "INVALID";
    }
    return getClassName() + "." + myFieldName;
  }

  public static FieldBreakpoint create(Project project, Document document, int lineIndex, String fieldName) {
    FieldBreakpoint breakpoint = new FieldBreakpoint(project, createHighlighter(project, document, lineIndex), fieldName);
    return (FieldBreakpoint)breakpoint.init();
  }

  public boolean canMoveTo(final SourcePosition position) {
    return super.canMoveTo(position) && getPsiFieldAt(position) != null;
  }

  public boolean isValid() {
    return super.isValid() && getPsiField() != null;
  }

  public boolean isAt(Document document, int offset) {
    PsiField field = findField(myProject, document, offset);
    return field == getPsiField();
  }

  protected static FieldBreakpoint create(Project project, Field field, ObjectReference object) {
    String fieldName = field.name();
    int line = 0;
    Document document = null;
    try {
      List locations = field.declaringType().allLineLocations();
      if(locations.size() > 0) {
        Location location = ((Location) locations.get(0));
        line = location.lineNumber();
        VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(location.sourcePath());
        if(file != null) {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
          if(psiFile != null) {
            document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
          }
        }
      }
    }
    catch (AbsentInformationException e) {
      LOG.debug(e);
    }
    catch (InternalError e) {
      LOG.debug(e);
    }

    if(document == null) return null;

    FieldBreakpoint fieldBreakpoint = new FieldBreakpoint(project, createHighlighter(project, document, line), fieldName);
    if (!fieldBreakpoint.isStatic()) {
      fieldBreakpoint.addInstanceFilter(object.uniqueID());
    }
    return (FieldBreakpoint)fieldBreakpoint.init();
  }

  public static PsiField findField(Project project, Document document, int offset) {
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if(file == null) return null;
    offset = CharArrayUtil.shiftForward(document.getCharsSequence(), offset, " \t");
    PsiElement element = file.findElementAt(offset);
    if(element == null) return null;
    PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class, false);
    int line = document.getLineNumber(offset);
    if(field == null) {
      final PsiField[] fld = new PsiField[] {null};
      DebuggerUtilsEx.iterateLine(project, document, line, new DebuggerUtilsEx.ElementVisitor() {
        public boolean acceptElement(PsiElement element) {
          PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class, false);
          if(field != null) {
            fld[0] = field;
            return true;
          }
          return false;
        }
      });
      field = fld[0];
    }

    return field;
  }

  public void readExternal(Element breakpointNode) throws InvalidDataException {
    super.readExternal(breakpointNode);
    myFieldName = breakpointNode.getAttributeValue("field_name");
    if(myFieldName == null) {
      throw new InvalidDataException("No field name for field breakpoint");
    }
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    super.writeExternal(parentNode);
    parentNode.setAttribute("field_name", getFieldName());
  }

  public PsiElement getEvaluationElement() {
    return getPsiClass();
  }
}