/*
 * @author: Eugene Zhuravlev
 * Date: Jul 4, 2003
 * Time: 7:39:27 PM
 */
package com.intellij.uiDesigner.make;

import com.intellij.compiler.impl.StateCache;
import com.intellij.openapi.compiler.CompilerPaths;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.util.containers.StringInterner;
import org.jetbrains.annotations.NonNls;

import java.io.*;

final class BindingsCache {
  @NonNls
  private static final String BINDINGS_FILE_NAME = "formbinding.dat";
  private final StateCache<MyState> myCache;

  public BindingsCache(final Project project) {
    final File cacheStoreDirectory = CompilerPaths.getCacheStoreDirectory(project);
    myCache = cacheStoreDirectory != null ? new StateCache<MyState>(cacheStoreDirectory + File.separator + BINDINGS_FILE_NAME, new StringInterner()) {
      public MyState read(final DataInputStream stream) throws IOException {
        return new MyState(stream.readLong(), stream.readUTF());
      }

      public void write(final MyState myState, final DataOutputStream stream) throws IOException {
        stream.writeLong(myState.getFormTimeStamp());
        stream.writeUTF(myState.getClassName());
      }
    } : null;
  }

  public String getBoundClassName(final VirtualFile formFile) throws Exception {
    String classToBind = getSavedBinding(formFile);
    if (classToBind == null) {
      final Document doc = FileDocumentManager.getInstance().getDocument(formFile);
      final LwRootContainer rootContainer = Utils.getRootContainer(doc.getText(), null);
      classToBind = rootContainer.getClassToBind();
    }
    if (classToBind != null) {
      updateCache(formFile, classToBind);
    }
    return classToBind;
  }

  public void save() {
    if (myCache != null) {
      myCache.save();
    }
  }

  private String getSavedBinding(final VirtualFile formFile) {
    if (myCache != null) {
      final String formUrl = formFile.getUrl();
      final MyState state = myCache.getState(formUrl);
      if (state != null) {
        if (formFile.getTimeStamp() == state.getFormTimeStamp()) {
          return state.getClassName();
        }
      }
    }
    return null;
  }
    
  private void updateCache(final VirtualFile formFile, final String classToBind) {
    if (myCache != null) {
      myCache.update(formFile.getUrl(), new MyState(formFile.getTimeStamp(), classToBind));
    }
  }

  private static final class MyState implements Serializable{
    private final long myFormTimeStamp;
    private final String myClassName;

    public MyState(final long formTimeStamp, final String className){
      myFormTimeStamp = formTimeStamp;
      myClassName = className;
    }

    public long getFormTimeStamp(){
      return myFormTimeStamp;
    }

    public String getClassName(){
      return myClassName;
    }
  }
}
