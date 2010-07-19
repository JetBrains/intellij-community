/*
 * Created on 13/10/2005
 */
package com.jetbrains.python.console.pydev;

public interface ICallback<Ret, Arg> {

    Ret call(Arg arg);
}