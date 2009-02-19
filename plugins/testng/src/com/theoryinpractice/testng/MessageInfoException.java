package com.theoryinpractice.testng;

import com.intellij.openapi.ui.ex.MessagesEx;

/**
 * @author Hani Suleiman
 *         Date: Jul 20, 2005
 *         Time: 2:07:53 PM
 */
public class MessageInfoException extends Exception
{
    private final MessagesEx.MessageInfo info;

    public MessageInfoException(MessagesEx.MessageInfo info) {
        this.info = info;
    }

    public MessagesEx.MessageInfo getMessageInfo() {
        return info;
    }
}
