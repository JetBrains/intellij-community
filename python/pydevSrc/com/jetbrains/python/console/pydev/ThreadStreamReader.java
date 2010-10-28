/*
 * Created on 03/08/2005
 */
package com.jetbrains.python.console.pydev;

import com.intellij.openapi.vfs.CharsetToolkit;

import java.io.InputStream;
import java.io.InputStreamReader;

public class ThreadStreamReader extends Thread {

    /**
     * Input stream read.
     */
    private final InputStream is;

    /**
     * Buffer with the contents gotten.
     */
    private final FastStringBuffer contents;

    /**
     * Access to the buffer should be synchronized.
     */
    private final Object lock = new Object();

    /**
     * Keeps the next unique identifier.
     */
    private static int next=0;

    /**
     * Get a unique identifier for this thread.
     */
    private static synchronized int next(){
        next ++;
        return next;
    }

    public ThreadStreamReader(InputStream is) {
        this.setName("ThreadStreamReader: "+next());
        this.setDaemon(true);
        contents = new FastStringBuffer();
        this.is = is;
    }

    public void run() {
        try {
            InputStreamReader in = new InputStreamReader(is, CharsetToolkit.UTF8);
            int c;
            while ((c = in.read()) != -1) {
                synchronized(lock){
                    contents.append((char) c);
                }
            }
        } catch (Exception e) {
            //that's ok
        }
    }

    /**
     * @return the contents that were obtained from this instance since it was started or since
     * the last call to this method.
     */
    public String getAndClearContents() {
        synchronized(lock){
            String string = contents.toString();
            contents.clear();
            return string;
        }
    }

    public String getContents() {
        synchronized(lock){
            return contents.toString();
        }
    }
}