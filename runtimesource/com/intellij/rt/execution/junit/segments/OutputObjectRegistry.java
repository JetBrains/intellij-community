package com.intellij.rt.execution.junit.segments;

import junit.framework.Test;

public interface OutputObjectRegistry {

  String referenceTo(Test object);
}
