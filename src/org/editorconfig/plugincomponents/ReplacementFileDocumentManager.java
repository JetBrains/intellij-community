package org.editorconfig.plugincomponents;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public class ReplacementFileDocumentManager extends FileDocumentManagerImpl {
    private static final Logger LOG = Logger.getInstance("#org.editorconfig.plugincomponents.ReplacementFileDocumentManager");

    private final MessageBus bus;

    // This is basically copied from FileDocumentManagerImpl
    private final DoneSavingListener multiCaster;

    public ReplacementFileDocumentManager(@NotNull VirtualFileManager virtualFileManager, @NotNull ProjectManager projectManager) {
        super(virtualFileManager, projectManager);
        bus = ApplicationManager.getApplication().getMessageBus();
        InvocationHandler handler = new InvocationHandler() {
            @Nullable
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                multiCast(method, args);
                return null;
            }
        };

        final ClassLoader loader = DoneSavingListener.class.getClassLoader();
        multiCaster = (DoneSavingListener)Proxy.newProxyInstance(loader, new Class[]{DoneSavingListener.class}, handler);

    }

    private void multiCast(@NotNull Method method, Object[] args) {
        try {
            method.invoke(bus.syncPublisher(DoneSavingTopic.DONE_SAVING), args);
        }
        catch (InvocationTargetException e) {
            LOG.error(e.getTargetException());
        }
        catch (ClassCastException e) {
            LOG.error("Arguments: "+ Arrays.toString(args), e);
        }
        catch (Exception e) {
            LOG.error(e);
        }
    }

    @Override
    public void saveAllDocuments() {
        super.saveAllDocuments();
        multiCaster.doneSavingAllDocuments();
    }
    
    @Override
    public void saveDocument(@NotNull Document document) {
        super.saveDocument(document);    //To change body of overridden methods use File | Settings | File Templates.
        multiCaster.doneSavingDocument(document);
    }
}
