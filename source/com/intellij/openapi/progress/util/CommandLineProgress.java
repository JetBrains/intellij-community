package com.intellij.openapi.progress.util;


public class CommandLineProgress extends ProgressIndicatorBase{
  public void setText(String text) {
    if (getText().equals(text)) return;
    super.setText(text);
    System.out.println(getTextToPrint());
  }

  public void setFraction(double fraction) {
    String oldText = getTextToPrint();
    super.setFraction(fraction);
    String newText = getTextToPrint();
    if (!newText.equals(oldText)){
      System.out.println(newText);
    }
  }

  private String getTextToPrint(){
    if (getFraction() > 0){
      return getText() + " " + (int)(getFraction() * 100 + 0.5) + "%";
    }
    else{
      return getText();
    }
  }
}
