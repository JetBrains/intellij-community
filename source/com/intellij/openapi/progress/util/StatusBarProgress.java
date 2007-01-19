package com.intellij.openapi.progress.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.containers.HashMap;

import javax.swing.*;
import java.util.Iterator;

public class StatusBarProgress extends ProgressIndicatorBase {
  // statusBar -> [textToRestore, MyPreviousText]
  private HashMap<StatusBarEx, Pair<String, String>> myStatusBar2SavedText = new HashMap<StatusBarEx, Pair<String, String>>();

  public void start() {
    super.start();
    SwingUtilities.invokeLater (
      new Runnable() {
        public void run() {
          if (ApplicationManager.getApplication().isDisposed()) return;
          Project[] projects=ProjectManager.getInstance().getOpenProjects();
          if(projects.length==0){
            projects=new Project[]{null};
          }
          for(int i=0;i<projects.length;i++){
            Project project=projects[i];
            final WindowManager windowManager = WindowManager.getInstance();
            if (windowManager != null) {
              final StatusBarEx statusBar = (StatusBarEx) windowManager.getStatusBar(project);
              if (statusBar != null) {
                String info = statusBar.getInfo();
                if (info == null) {
                  info = "";
                }
                myStatusBar2SavedText.put(statusBar, new Pair<String, String>(info, info)); // initial value
              }
            }
          }
        }
      }
    );
  }

  public void stop() {
    super.stop();
    SwingUtilities.invokeLater (
      new Runnable() {
        public void run() {
          for(Iterator<StatusBarEx> i = myStatusBar2SavedText.keySet().iterator();i.hasNext();){
            final StatusBarEx statusBar = i.next();
            final String textToRestore = updateRestoreText(statusBar);
            statusBar.setInfo(textToRestore);
          }
          myStatusBar2SavedText.clear();
        }
      }
    );
  }

  public void setText(String text) {
    super.setText(text);
    update();
  }

  public void setFraction(double fraction) {
    super.setFraction(fraction);
    update();
  }

  private void update(){
    String text;
    if (!isRunning()){
      text = "";
    }
    else{
      text = getText();
      double fraction = getFraction();
      if (fraction > 0) {
        text += " " + (int)(fraction * 100 + 0.5) + "%";
      }
    }
    final String text1 = text;
    SwingUtilities.invokeLater (
      new Runnable() {
        public void run() {
          for(Iterator<StatusBarEx> i = myStatusBar2SavedText.keySet().iterator();i.hasNext();){
            setStatusBarText(i.next(), text1);
          }
        }
      }
    );
  }

  private void setStatusBarText(StatusBarEx statusBar, String text) {
    updateRestoreText(statusBar);
    final Pair<String, String> textsPair = myStatusBar2SavedText.get(statusBar);
    myStatusBar2SavedText.put(statusBar, Pair.create(textsPair.first, text));
    statusBar.setInfo(text);
  }

  private String updateRestoreText(StatusBarEx statusBar) {
    final Pair<String, String> textsPair = myStatusBar2SavedText.get(statusBar);
    // if current status bar info doesn't match the value, that we set, use this value as a restore value
    String info = statusBar.getInfo();
    if (info == null) {
      info = "";
    }
    if (!textsPair.getSecond().equals(info)) {
      myStatusBar2SavedText.put(statusBar, Pair.create(info, textsPair.second));
    }
    return textsPair.getFirst();
  }
}
