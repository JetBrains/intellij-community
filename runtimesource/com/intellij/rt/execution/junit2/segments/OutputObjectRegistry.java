package com.intellij.rt.execution.junit2.segments;

import junit.framework.Test;

public interface OutputObjectRegistry {

  String referenceTo(Test object);
}
