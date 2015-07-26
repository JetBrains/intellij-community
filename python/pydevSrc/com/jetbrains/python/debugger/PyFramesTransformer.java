package com.jetbrains.python.debugger;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface PyFramesTransformer {
  ExtensionPointName<PyFramesTransformer> EP_NAME = ExtensionPointName.create("Pythonid.pyFramesTransformer");

  @Nullable
  List<PyStackFrameInfo> transformFrames(@Nullable List<PyStackFrameInfo> frames);
}
