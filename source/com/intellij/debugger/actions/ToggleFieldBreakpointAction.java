package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.InstanceFilter;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.FieldBreakpoint;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;

/**
 * User: lex
 * Date: Sep 4, 2003
 * Time: 8:59:30 PM
 */
public class ToggleFieldBreakpointAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.actions.ToggleFieldBreakpointAction");

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) return;
    SourcePosition place = getPlace(e);

    if(place != null) {
      Document document = PsiDocumentManager.getInstance(project).getDocument(place.getFile());
      if (document != null) {
        DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
        BreakpointManager manager = debuggerManager.getBreakpointManager();
        Breakpoint breakpoint = manager.findFieldBreakpoint(document, place.getOffset());

        if(breakpoint == null) {
          FieldBreakpoint fieldBreakpoint = manager.addFieldBreakpoint(document, place.getOffset());
          if (fieldBreakpoint != null) {
            if(DebuggerAction.isContextView(e)) {
              DebuggerTreeNodeImpl selectedNode = DebuggerAction.getSelectedNode(e.getDataContext());
              LOG.assertTrue(selectedNode != null);
              ObjectReference object = ((FieldDescriptorImpl)selectedNode.getDescriptor()).getObject();
              if(object != null) {
                long id = object.uniqueID();
                InstanceFilter[] instanceFilters = new InstanceFilter[] { InstanceFilter.create(Long.toString(id))};
                fieldBreakpoint.setInstanceFilters(instanceFilters);
                fieldBreakpoint.INSTANCE_FILTERS_ENABLED = true;
              }
            }

            RequestManagerImpl.createRequests(fieldBreakpoint);
            DialogWrapper dialog = manager.createConfigurationDialog(fieldBreakpoint, null);
            dialog.show();
          }
        } else {
          manager.removeBreakpoint(breakpoint);
        }
      }
    }
  }

  public void update(AnActionEvent event){
    SourcePosition place = getPlace(event);
    boolean toEnable = place != null;

    Presentation presentation = event.getPresentation();
    if(ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace()) ||
       ActionPlaces.STRUCTURE_VIEW_POPUP.equals(event.getPlace())) {
      presentation.setVisible(toEnable);
    }
    else if(DebuggerAction.isContextView(event)) {
      presentation.setText(presentation.getText().replaceFirst("Toggle", "Add"));
      Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
      if(place != null) {
        Document document = PsiDocumentManager.getInstance(project).getDocument(place.getFile());
        if (document != null) {
          Breakpoint fieldBreakpoint = (DebuggerManagerEx.getInstanceEx(project)).getBreakpointManager().findFieldBreakpoint(
                    document,
                    place.getOffset());
          if(fieldBreakpoint != null) {
            presentation.setEnabled(false);
            return;
          }
        }
      }
    }
    presentation.setVisible(toEnable);
  }

  public static SourcePosition getPlace(AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if(project == null) return null;
    if (ActionPlaces.PROJECT_VIEW_POPUP.equals(event.getPlace()) ||
        ActionPlaces.STRUCTURE_VIEW_POPUP.equals(event.getPlace())) {
      final PsiElement psiElement = (PsiElement)dataContext.getData(DataConstants.PSI_ELEMENT);
      if(psiElement instanceof PsiField) {
        return SourcePosition.createFromElement(psiElement);
      }
      return null;
    }

    if(DebuggerAction.isContextView(event)) {
      DebuggerTree tree = (DebuggerTree)event.getDataContext().getData(DebuggerActions.DEBUGGER_TREE);
      if(tree != null && tree.getSelectionPath() != null) {
        DebuggerTreeNodeImpl node = ((DebuggerTreeNodeImpl)tree.getSelectionPath().getLastPathComponent());
        if(node != null && node.getDescriptor() instanceof FieldDescriptorImpl) {
          Field field = ((FieldDescriptorImpl)node.getDescriptor()).getField();
          PsiClass psiClass = DebuggerUtilsEx.findClass(field.declaringType().name(), project);
          if(psiClass != null) {
            psiClass = (PsiClass) psiClass.getNavigationElement();
            final PsiField psiField = psiClass.findFieldByName(field.name(), true);
            if (psiField != null) {
              return SourcePosition.createFromElement(psiField);
            }
          }
        }
      }
    }

    DebuggerTreeNodeImpl selectedNode = DebuggerAction.getSelectedNode(dataContext);

    if(selectedNode != null && selectedNode.getDescriptor() instanceof FieldDescriptorImpl) {
      FieldDescriptorImpl descriptor = (FieldDescriptorImpl)selectedNode.getDescriptor();
      return descriptor.getSourcePosition(project, DebuggerAction.getDebuggerContext(dataContext));
    }


    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if(editor == null) {
      editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    }
    if (editor != null) {
      final Document document = editor.getDocument();
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file != null) {
        FileTypeManager fileTypeManager = FileTypeManager.getInstance();
        FileType fileType = fileTypeManager.getFileTypeByFile(file.getVirtualFile());
        if (StdFileTypes.JAVA == fileType || StdFileTypes.CLASS  == fileType) {
          final PsiField field = FieldBreakpoint.findField(project, document, editor.getCaretModel().getOffset());
          if(field != null){
            return SourcePosition.createFromElement(field);
          }
        }
      }
    }
    return null;
  }

}