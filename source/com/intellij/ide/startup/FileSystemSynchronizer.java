package com.intellij.ide.startup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.*;

/**
 * @author max
 */
public class FileSystemSynchronizer {
  private final static Logger LOG = Logger.getInstance("#com.intellij.ide.startup.FileSystemSynchronizer");

  private ArrayList<CacheUpdater> myUpdaters = new ArrayList<CacheUpdater>();
  private LinkedHashSet<VirtualFile> myFilesToUpdate = new LinkedHashSet<VirtualFile>();
  private Collection/*<VirtualFile>*/[] myUpdateSets;

  private boolean myIsCancelable = false;

  public void registerCacheUpdater(CacheUpdater cacheUpdater) {
    myUpdaters.add(cacheUpdater);
  }

  public void setCancelable(boolean isCancelable) {
    myIsCancelable = isCancelable;
  }

  public void execute() {
    /*
    long time1 = System.currentTimeMillis();
    */

    if (!myIsCancelable) {
      ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null){
        indicator.startNonCancelableSection();
      }
    }

    try {
      if (myUpdateSets == null) { // collectFilesToUpdate() was not executed before
        if (collectFilesToUpdate() == 0) return;
      }

      updateFiles();
    }
    catch(ProcessCanceledException e){
      for (Iterator<CacheUpdater> iterator = myUpdaters.iterator(); iterator.hasNext();) {
        CacheUpdater updater = iterator.next();
        if (updater != null) {
          updater.canceled();
        }
      }
      throw e;
    }
    finally {
      if (!myIsCancelable) {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator != null){
          indicator.finishNonCancelableSection();
        }
      }
    }

    /*
    long time2 = System.currentTimeMillis();
    System.out.println("synchronizer.execute() in " + (time2 - time1) + " ms");
    */
  }

  public int collectFilesToUpdate() {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.pushState();
      indicator.setText("Scanning files...");
    }

    myUpdateSets = new Collection[myUpdaters.size()];
    for (int i = 0; i < myUpdaters.size(); i++) {
      CacheUpdater updater = myUpdaters.get(i);
      try {
        VirtualFile[] updaterFiles = updater.queryNeededFiles();
        Collection<VirtualFile> localSet = new LinkedHashSet<VirtualFile>(Arrays.asList(updaterFiles));
        myFilesToUpdate.addAll(localSet);
        myUpdateSets[i] = localSet;
      }
      catch(ProcessCanceledException e){
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
        myUpdateSets[i] = new ArrayList();
      }
    }

    if (indicator != null) {
      indicator.popState();
    }

    if (myFilesToUpdate.size() == 0) {
      updatingDone();
    }

    return myFilesToUpdate.size();
  }

  private void updateFiles() {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.pushState();
      indicator.setText("Parsing files. Please wait...");
    }

    int totalFiles = myFilesToUpdate.size();
    int count = 0;

    for (Iterator<VirtualFile> iterator = myFilesToUpdate.iterator(); iterator.hasNext();) {
      VirtualFile file = iterator.next();

      if (indicator != null) {
        indicator.setFraction(((double)++count) / totalFiles);
        indicator.setText2(file.getPresentableUrl());
      }

      FileContent content = new FileContent(file);
      for (int i = 0; i < myUpdaters.size(); i++) {
        CacheUpdater updater = myUpdaters.get(i);
        if (myUpdateSets[i].remove(file)) {
          try {
            updater.processFile(content);
          }
          catch(ProcessCanceledException e){
            throw e;
          }
          catch (Exception e) {
            LOG.error(e);
          }

          if (myUpdateSets[i].isEmpty()) {
            try {
              updater.updatingDone();
            }
            catch(ProcessCanceledException e){
              throw e;
            }
            catch (Exception e) {
              LOG.error(e);
            }

            myUpdaters.set(i, null);
          }
        }
      }
    }

    updatingDone();

    if (indicator != null) {
      indicator.popState();
    }
  }

  private void updatingDone() {
    for (int i = 0; i < myUpdaters.size(); i++) {
      CacheUpdater updater = myUpdaters.get(i);
      try {
        if (updater != null) updater.updatingDone();
      }
      catch(ProcessCanceledException e){
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    dropUpdaters();
  }

  private void dropUpdaters() {
    myUpdaters.clear();
    myFilesToUpdate.clear();
    myUpdateSets = null;
  }
}
