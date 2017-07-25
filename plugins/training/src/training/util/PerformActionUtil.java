package training.util;

import com.intellij.ide.DataManager;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Created by karashevich on 02/02/15.
 */
public class PerformActionUtil {

    public static InputEvent getInputEvent(String actionName) {
        final Shortcut[] shortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionName);
        KeyStroke keyStroke = null;
        for (Shortcut each : shortcuts) {
            if (each instanceof KeyboardShortcut) {
                keyStroke = ((KeyboardShortcut) each).getFirstKeyStroke();
                if (keyStroke != null) break;
            }
        }

        if (keyStroke != null) {
            return new KeyEvent(JOptionPane.getRootFrame(),
                    KeyEvent.KEY_PRESSED,
                    System.currentTimeMillis(),
                    keyStroke.getModifiers(),
                    keyStroke.getKeyCode(),
                    keyStroke.getKeyChar(),
                    KeyEvent.KEY_LOCATION_STANDARD);
        } else {
            return new MouseEvent(JOptionPane.getRootFrame(), MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, 1, false, MouseEvent.BUTTON1);
        }
    }

    public static void performAction(final String actionName, final Editor editor, final Project project, final Runnable runnable) throws InterruptedException, ExecutionException {

        final ActionManager am = ActionManager.getInstance();
        final AnAction targetAction = am.getAction(actionName);
        final InputEvent inputEvent = getInputEvent(actionName);

        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                    @Override
                    public void run() {
                        am.tryToExecute(targetAction, inputEvent, editor.getContentComponent(), null, true).doWhenDone(runnable);
                    }
                });
            }
        });
    }

    public static void performAction(final String actionName, final Editor editor, final Project project) throws InterruptedException, ExecutionException {

        final ActionManager am = ActionManager.getInstance();
        final AnAction targetAction = am.getAction(actionName);
        final InputEvent inputEvent = getInputEvent(actionName);

        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                WriteCommandAction.runWriteCommandAction(project, new Runnable() {
                    @Override
                    public void run() {
                        am.tryToExecute(targetAction, inputEvent, editor.getContentComponent(), null, true);
                    }
                });
            }
        });
    }


    public static void performAction(final String actionName, final Editor editor, final AnActionEvent e, final Runnable runnable) throws InterruptedException, ExecutionException {

        final ActionManager am = ActionManager.getInstance();
        final AnAction targetAction = am.getAction(actionName);
        final InputEvent inputEvent = getInputEvent(actionName);

        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                WriteCommandAction.runWriteCommandAction(e.getProject(), new Runnable() {
                    @Override
                    public void run() {
                        am.tryToExecute(targetAction, inputEvent, editor.getContentComponent(), null, true).doWhenDone(runnable);
                    }
                });
            }
        });
    }

    public static void performActionDisabledPresentation(final Editor editor, String actionClassName){
        performActionDisabledPresentation(editor, actionClassName, null);
    }

    public static void performActionDisabledPresentation(final Editor editor, String actionClassName, @Nullable final FileEditor fileEditor) {
        final Class<AnAction> aClass;
        try {
            aClass = (Class<AnAction>) ClassLoader.getSystemClassLoader().loadClass(actionClassName);
            final AnAction action = aClass.newInstance();

            final ActionManagerEx amEx = ActionManagerEx.getInstanceEx();
            final String actionName = ActionManager.getInstance().getId(action);

            Object hostEditor = editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor;
            Map<String, Object> map = ((fileEditor != null) ? ContainerUtil.newHashMap(Pair.create(CommonDataKeys.HOST_EDITOR.getName(), hostEditor), Pair.createNonNull(CommonDataKeys.EDITOR.getName(), editor),
                    Pair.createNonNull(PlatformDataKeys.FILE_EDITOR.getName(), fileEditor)):
                    ContainerUtil.newHashMap(Pair.create(CommonDataKeys.HOST_EDITOR.getName(), hostEditor),
                            Pair.createNonNull(CommonDataKeys.EDITOR.getName(), editor)));
            DataContext parent = DataManager.getInstance().getDataContext(editor.getContentComponent());
            final DataContext context = SimpleDataContext.getSimpleContext(map, parent);
            final AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "", context);


            amEx.fireBeforeActionPerformed(action, context, event);
            ActionUtil.performActionDumbAware(action, event);
            amEx.queueActionPerformedEvent(action, context, event);

        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void performActionDisabledPresentation(String actionName, final Editor editor){
        performActionDisabledPresentation(actionName, editor, null);
    }


    public static void performActionDisabledPresentation(String actionName, final Editor editor, @Nullable final FileEditor fileEditor){

        final ActionManagerEx amEx = ActionManagerEx.getInstanceEx();
        final AnAction action = amEx.getAction(actionName);
        final InputEvent inputEvent = getInputEvent(actionName);

        final Presentation presentation = action.getTemplatePresentation().clone();

        Object hostEditor = editor instanceof EditorWindow ? ((EditorWindow)editor).getDelegate() : editor;
        Map<String, Object> map = ((fileEditor != null) ? ContainerUtil.newHashMap(Pair.create(CommonDataKeys.HOST_EDITOR.getName(), hostEditor), Pair.createNonNull(CommonDataKeys.EDITOR.getName(), editor),
                Pair.createNonNull(PlatformDataKeys.FILE_EDITOR.getName(), fileEditor)):
                ContainerUtil.newHashMap(Pair.create(CommonDataKeys.HOST_EDITOR.getName(), hostEditor),
                        Pair.createNonNull(CommonDataKeys.EDITOR.getName(), editor)));
        DataContext parent = DataManager.getInstance().getDataContext(editor.getContentComponent());
        final DataContext context = SimpleDataContext.getSimpleContext(map, parent);
        final AnActionEvent event = AnActionEvent.createFromAnAction(action, null, "", context);


        amEx.fireBeforeActionPerformed(action, context, event);
        ActionUtil.performActionDumbAware(action, event);
        amEx.queueActionPerformedEvent(action, context, event);
    }

    public static void performEditorAction(String actionId, Editor editor){
        final ActionManagerEx amEx = ActionManagerEx.getInstanceEx();
        final AnAction action = amEx.getAction(actionId);
        final InputEvent inputEvent = getInputEvent(actionId);

        final Presentation presentation = action.getTemplatePresentation().clone();
        final DataManager dataManager = DataManager.getInstance();
        Component contextComponent = editor.getContentComponent();
        final DataContext context = (contextComponent != null ? dataManager.getDataContext(contextComponent) : dataManager.getDataContext());



        AnActionEvent event = new AnActionEvent(
                inputEvent, context,
                ActionPlaces.UNKNOWN,
                presentation, amEx,
                inputEvent.getModifiersEx()
        );
        amEx.fireBeforeActionPerformed(action, context, event);

        ActionUtil.performActionDumbAware(action, event);
        amEx.queueActionPerformedEvent(action, context, event);

    }
}
