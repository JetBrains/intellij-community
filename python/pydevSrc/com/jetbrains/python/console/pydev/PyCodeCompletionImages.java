package com.jetbrains.python.console.pydev;

import com.intellij.util.Icons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PyCodeCompletionImages {

    /**
     * Returns an image for the given type
     * @param type
     * @return
     */
    @Nullable
    public static Icon getImageForType(int type){
      switch (type) {
        case IToken.TYPE_CLASS:
          return Icons.CLASS_ICON;
        case IToken.TYPE_FUNCTION:
          return Icons.METHOD_ICON;
        default:
          return null;
      }
    }

}