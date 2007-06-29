package com.intellij.ide;

import com.intellij.Patches;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.copy.CopyHandler;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandler;
import com.intellij.ui.UIHelper;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class CopyPasteManagerEx extends CopyPasteManager implements ClipboardOwner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.CopyPasteManagerEx");

  private final ArrayList<Transferable> myDatas;
  private MyData myRecentData;

//  private static long ourWastedMemory = 0;
//  private static long ourLastPrintedMemory = 0;
//  private static long ourLastPrintTime = 0;
//  private static long ourInvokationCounter = 0;

  private final EventDispatcher<ContentChangedListener> myDispatcher = EventDispatcher.create(ContentChangedListener.class);
  private static final int DELAY_UNTIL_ABORT_CLIPBOARD_ACCESS = 2000;
  private boolean myIsWarningShown = false;

  public CopyPasteManagerEx() {
    myDatas = new ArrayList<Transferable>();
  }

  public PsiElement[] getElements(boolean[] isCopied) {
    try {
      Transferable content = getSystemClipboardContents();
      Object transferData;
      try {
        transferData = content.getTransferData(ourDataFlavor);
      } catch (UnsupportedFlavorException e) {
        return null;
      } catch (IOException e) {
        return null;
      }

      if (!(transferData instanceof MyData)) {
        return null;
      }
      MyData dataProxy = (MyData) transferData;
      if (!Comparing.equal(dataProxy, myRecentData)) {
        return null;
      }
      if (isCopied != null) {
        isCopied[0] = myRecentData.isCopied();
      }
      return myRecentData.getElements();
    } catch (Exception e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(e);
      }
      return null;
    }
  }

  private Transferable getSystemClipboardContents() {
    final Transferable[] contents = new Transferable[] {null};
    final boolean[] success = new boolean[] {false};
    Runnable accessor = new Runnable() {
      public void run() {
        try {
          for (int i = 0; i < 3; i++) {
            try {
              contents[0] = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(CopyPasteManagerEx.this);
            }
            catch (IllegalStateException e) {
              try {
                Thread.sleep(50);
              }
              catch (InterruptedException e1) {
              }
              continue;
            }
            break;
          }

          success[0] = true;
        }
        catch (Throwable e) {
          // No luck
        }        
        finally {
          Thread.interrupted(); // reset interrupted status
        }
      }
    };

    if (Patches.SUN_BUG_ID_4818143) {
      final Future<?> accessorFuture = ApplicationManager.getApplication().executeOnPooledThread(accessor);

      try {
        accessorFuture.get(DELAY_UNTIL_ABORT_CLIPBOARD_ACCESS, TimeUnit.MILLISECONDS);
      }
      catch (Exception e) { /*  no luck */ }

      if (success[0]) return contents[0];
      accessorFuture.cancel(true);
      showWorkaroundMessage();

      return null;
    }
    else {
      accessor.run();
      return contents[0];
    }
  }

  private void showWorkaroundMessage() {
    if (myIsWarningShown) return;
    final String productName = ApplicationNamesInfo.getInstance().getProductName();
    Messages.showErrorDialog(IdeBundle.message("error.paste.bug.workaround", productName, productName), IdeBundle.message("title.system.error"));
    myIsWarningShown = true;
  }

  static PsiElement[] getElements(final Transferable content) {
    if (content == null) return null;
    Object transferData;
    try {
      transferData = content.getTransferData(ourDataFlavor);
    } catch (UnsupportedFlavorException e) {
      return null;
    } catch (IOException e) {
      return null;
    }

    return transferData instanceof MyData ? ((MyData)transferData).getElements() : null;
  }

//  private long getUsedMemory() {
//    Runtime runtime = Runtime.getRuntime();
//    long usedMemory = runtime.totalMemory() - runtime.freeMemory();
//    return usedMemory;
//  }
//
  public void clear() {
    Transferable old = getContents();
    myRecentData = null;
    setSystemClipboardContent(new StringSelection(""));
    fireContentChanged(old);
  }

  private void setElements(PsiElement[] elements, boolean copied) {
    Transferable old = getContents();
    myRecentData = new MyData(elements, copied);
    setSystemClipboardContent(new MyTransferable(myRecentData));
    fireContentChanged(old);
  }

  public boolean isCutElement(Object element) {
    if (myRecentData == null) return false;
    if (myRecentData.isCopied()) return false;
    PsiElement[] elements = myRecentData.getElements();
    if (elements == null) return false;
    for (PsiElement aElement : elements) {
      if (aElement == element) return true;
    }
    return false;
  }

  public abstract static class CopyPasteDelegator implements UIHelper.CopyPasteSupport {
    private final Project myProject;
    private final JComponent myKeyReceiver;
    private final MyEditable myEditable;

    public CopyPasteDelegator(Project project, JComponent keyReceiver) {
      myProject = project;
      myKeyReceiver = keyReceiver;
      myEditable = new MyEditable();
    }

    @NotNull
    protected abstract PsiElement[] getSelectedElements();

    @NotNull protected final PsiElement[] getValidSelectedElements() {
      PsiElement[] selectedElements = getSelectedElements();
      for (PsiElement element : selectedElements) {
        if (!element.isValid()) {
          return PsiElement.EMPTY_ARRAY;
        }
      }
      return selectedElements;
    }

    private void updateView() {
      myKeyReceiver.repaint();
    }

    public CopyProvider getCopyProvider() {
      return myEditable;
    }

    public CutProvider getCutProvider() {
      return myEditable;
    }

    public PasteProvider getPasteProvider() {
      return myEditable;
    }

    private class MyEditable implements CutProvider, CopyProvider, PasteProvider {
      public void performCopy(DataContext dataContext) {
        PsiElement[] elements = getValidSelectedElements();
        ((CopyPasteManagerEx)CopyPasteManager.getInstance()).setElements(elements, true);
        updateView();
      }

      public boolean isCopyEnabled(DataContext dataContext) {
        PsiElement[] elements = getValidSelectedElements();
        return CopyHandler.canCopy(elements);
      }

      public void performCut(DataContext dataContext) {
        PsiElement[] elements = getValidSelectedElements();
        if (MoveHandler.adjustForMove(myProject, elements, null) == null) {
          return;
        }
        // 'elements' passed instead of result of 'adjustForMove' because otherwise ProjectView would
        // not recognize adjusted elements when graying them
        ((CopyPasteManagerEx)CopyPasteManager.getInstance()).setElements(elements, false);
        updateView();
      }

      public boolean isCutEnabled(DataContext dataContext) {
        final PsiElement[] elements = getValidSelectedElements();
        return elements.length != 0 && MoveHandler.canMove(elements, null);
      }

      public void performPaste(DataContext dataContext) {
        final boolean[] isCopied = new boolean[1];
        final PsiElement[] elements = ((CopyPasteManagerEx)CopyPasteManager.getInstance()).getElements(isCopied);
        if (elements == null) return;
        try {
          PsiElement target = (PsiElement)dataContext.getData(DataConstantsEx.PASTE_TARGET_PSI_ELEMENT);
          if (isCopied[0]) {
            PsiDirectory targetDirectory = target instanceof PsiDirectory ? (PsiDirectory)target : null;
            PsiPackage targetPackage = target instanceof PsiPackage ? (PsiPackage)target : null;
            if (targetDirectory == null & target instanceof PsiPackage) {
              final PsiDirectory[] directories = ((PsiPackage)target).getDirectories();
              if (directories.length > 0) {
                targetDirectory = directories[0];
              }
            }
            if (CopyHandler.canCopy(elements)) {
              CopyHandler.doCopy(elements, targetPackage, targetDirectory);
            }
          }
          else {
            if (MoveHandler.canMove(elements, target)) {
              MoveHandler.doMove(myProject, elements, target, new MoveCallback() {
                public void refactoringCompleted() {
                  ((CopyPasteManagerEx)CopyPasteManager.getInstance()).clear();
                }
              });
            }
          }
        }
        catch (RuntimeException ex) {
          throw ex;
        }
        finally {
          updateView();
        }
      }

      public boolean isPastePossible(DataContext dataContext) {
        return true;
      }

      public boolean isPasteEnabled(DataContext dataContext){
        Project project = (Project)dataContext.getData(DataConstants.PROJECT);
        if (project == null) {
          return false;
        }

        Object target = dataContext.getData(DataConstantsEx.PASTE_TARGET_PSI_ELEMENT);
        if (target == null) {
          return false;
        }
        PsiElement[] elements = ((CopyPasteManagerEx)CopyPasteManager.getInstance()).getElements(new boolean[]{false});
        if (elements == null) {
          return false;
        }

        // disable cross-project paste
        for (PsiElement element : elements) {
          PsiManager manager = element.getManager();
          if (manager == null || manager.getProject() != project) {
            return false;
          }
        }

        return true;
      }
    }
  }

  private static final DataFlavor ourDataFlavor;
  static {
    try {
      ourDataFlavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=" + MyData.class.getName());
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private static class MyData {
    private PsiElement[] myElements;
    private boolean myIsCopied;

    public MyData(PsiElement[] elements, boolean copied) {
      myElements = elements;
      myIsCopied = copied;
    }

    public PsiElement[] getElements() {
      if (myElements == null) return PsiElement.EMPTY_ARRAY;

      int validElementsCount = 0;

      for (PsiElement element : myElements) {
        if (element.isValid()) {
          validElementsCount++;
        }
      }

      if (validElementsCount == myElements.length) {
        return myElements;
      }

      PsiElement[] validElements = new PsiElement[validElementsCount];
      int j=0;
      for (PsiElement element : myElements) {
        if (element.isValid()) {
          validElements[j++] = element;
        }
      }

      myElements = validElements;
      return myElements;
    }

    public boolean isCopied() {
      return myIsCopied;
    }
  }

  public static class MyTransferable implements Transferable {
    private MyData myDataProxy;
    private static final DataFlavor[] DATA_FLAVOR_ARRAY = new DataFlavor[]{ourDataFlavor};

    public MyTransferable(MyData data) {
      myDataProxy = data;
    }

    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
      if (!ourDataFlavor.equals(flavor)) {
        return null;
      }
      return myDataProxy;
    }

    public DataFlavor[] getTransferDataFlavors() {
      return DATA_FLAVOR_ARRAY;
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
      return flavor.equals(ourDataFlavor);
    }

    public PsiElement[] getElements() {
      return myDataProxy.getElements();
    }
  }

  public void lostOwnership(Clipboard clipboard, Transferable contents) {
    fireContentChanged(null);
  }

  private void fireContentChanged(final Transferable oldTransferable) {
    myDispatcher.getMulticaster().contentChanged(oldTransferable, getContents());
  }

  public void addContentChangedListener(ContentChangedListener listener) {
    myDispatcher.addListener(listener);
  }

  public void addContentChangedListener(final ContentChangedListener listener, Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  public void removeContentChangedListener(ContentChangedListener listener) {
    myDispatcher.removeListener(listener);
  }

  public void setContents(Transferable content) {
    Transferable old = getContents();
    addNewContentToStack(content);

    setSystemClipboardContent(content);

    fireContentChanged(old);
  }

  private void setSystemClipboardContent(final Transferable content) {
    final boolean[] success = new boolean[]{false};
    final Runnable accessor = new Runnable() {
      public void run() {
        try {
          for (int i = 0; i < 3; i++) {
            try {
              Toolkit.getDefaultToolkit().getSystemClipboard().setContents(content, CopyPasteManagerEx.this);
            }
            catch (IllegalStateException e) {
              try {
                Thread.sleep(50);
              }
              catch (InterruptedException e1) {
              }
              continue;
            }
            break;
          }
          success[0] = true;
        }
        finally {
          Thread.interrupted(); // reset interrupted status
        }
      }
    };

    if (Patches.SUN_BUG_ID_4818143) {
      Future<?> accessorFuture = ApplicationManager.getApplication().executeOnPooledThread(accessor);

      try {
        accessorFuture.get(DELAY_UNTIL_ABORT_CLIPBOARD_ACCESS, TimeUnit.MILLISECONDS);
      }
      catch (Exception e) { /* no luck */ }

      if (!success[0]) {
        showWorkaroundMessage();
        accessorFuture.cancel(true);
      }
    }
    else {
      accessor.run();
    }
  }

  private void addNewContentToStack(Transferable content) {
    try {
      String clipString = getStringContent(content);
      if (clipString != null) {
        Transferable same = null;
        for (Transferable old : myDatas) {
          if (clipString.equals(getStringContent(old))) {
            same = old;
            break;
          }
        }

        if (same == null) {
          myDatas.add(0, content);
          deleteAfterAllowedMaximum();
        }
        else {
          moveContentTopStackTop(same);
        }
      }
    } catch (UnsupportedFlavorException e) {
    } catch (IOException e) {
    }
  }

  private static String getStringContent(Transferable content) throws UnsupportedFlavorException, IOException {
    return (String) content.getTransferData(DataFlavor.stringFlavor);
  }

  private void deleteAfterAllowedMaximum() {
    int max = UISettings.getInstance().MAX_CLIPBOARD_CONTENTS;
    for (int i = myDatas.size() - 1; i >= max; i--) {
      myDatas.remove(i);
    }
  }

  public Transferable getContents() {
    return getSystemClipboardContents();
  }

  public Transferable[] getAllContents() {
    deleteAfterAllowedMaximum();

    Transferable content = getSystemClipboardContents();
    if (content != null) {
      try {
        String clipString = getStringContent(content);
        String datasString = null;

        if (!myDatas.isEmpty()) {
          datasString = getStringContent(myDatas.get(0));
        }

        if (clipString != null && clipString.length() > 0 && !Comparing.equal(clipString, datasString)) {
          myDatas.add(0, content);
        }
      } catch (UnsupportedFlavorException e) {
      } catch (IOException e) {
      }
    }

    return myDatas.toArray(new Transferable[myDatas.size()]);
  }

  public void removeContent(Transferable t) {
    Transferable old = getContents();
    boolean isCurrentClipboardContent = myDatas.indexOf(t) == 0;
    myDatas.remove(t);
    if (isCurrentClipboardContent) {
      if (!myDatas.isEmpty()) {
        setSystemClipboardContent(myDatas.get(0));
      }
      else {
        setSystemClipboardContent(new StringSelection(""));
      }
    }
    fireContentChanged(old);
  }

  public void moveContentTopStackTop(Transferable t) {
    setSystemClipboardContent(t);
    myDatas.remove(t);
    myDatas.add(0, t);
  }
}
