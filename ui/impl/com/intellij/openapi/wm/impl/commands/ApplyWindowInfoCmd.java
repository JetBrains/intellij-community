/**
 * @author Vladimir Kondratyev
 */
package com.intellij.openapi.wm.impl.commands;

import com.intellij.openapi.wm.impl.commands.FinalizableCommand;
import com.intellij.openapi.wm.impl.*;

/**
 * Apply <code>info</code> to the corresponded tool button and decarator.
 * Command uses freezed copy of passed <code>info</code> object.
 */
public final class ApplyWindowInfoCmd extends FinalizableCommand{
  private final WindowInfoImpl myInfo;
  private final StripeButton myButton;
  private final InternalDecorator myDecorator;

  public ApplyWindowInfoCmd(
    final WindowInfoImpl info,
    final StripeButton button,
    final InternalDecorator decorator,
    final Runnable finishCallBack
  ){
    super(finishCallBack);
    myInfo=info.copy();
    myButton=button;
    myDecorator=decorator;
  }

  public final void run(){
    try{
      myButton.apply(myInfo);
      myDecorator.apply(myInfo);
    }finally{
      finish();
    }
  }
}
