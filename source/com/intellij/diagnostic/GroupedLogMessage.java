/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.diagnostic;

import java.util.List;

public class GroupedLogMessage extends AbstractMessage {

  private List<AbstractMessage> myMessages;

  public GroupedLogMessage(List<AbstractMessage> messages) {
    myMessages = messages;
  }

  public List<AbstractMessage> getMessages() {
    return myMessages;
  }

  public String getThrowableText() {
    StringBuffer result = new StringBuffer();
    for (int i = 0; i < myMessages.size(); i++) {
      AbstractMessage each = myMessages.get(i);
      result.append(each.getThrowableText() + "\n\n\n");
    }
    return result.toString();
  }

  public Throwable getThrowable() {
    return myMessages.get(0).getThrowable();
  }

  public String getMessage() {
    return myMessages.get(0).getMessage();
  }

}
