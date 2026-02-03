// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng;

import java.util.Collection;
import java.util.List;

public final class TestNGCommonClassNames {
  public static final String ORG_TESTNG_ANNOTATIONS_TEST = "org.testng.annotations.Test";

  public static final String ORG_TESTNG_ANNOTATIONS_FACTORY = "org.testng.annotations.Factory";

  public static final String ORG_TESTNG_ANNOTATIONS_AFTERCLASS = "org.testng.annotations.AfterClass";

  public static final String ORG_TESTNG_ANNOTATIONS_AFTERGROUPS = "org.testng.annotations.AfterGroups";

  public static final String ORG_TESTNG_ANNOTATIONS_AFTERSUITE = "org.testng.annotations.AfterSuite";

  public static final String ORG_TESTNG_ANNOTATIONS_AFTERTEST = "org.testng.annotations.AfterTest";

  public static final String ORG_TESTNG_ANNOTATIONS_BEFORECLASS = "org.testng.annotations.BeforeClass";

  public static final String ORG_TESTNG_ANNOTATIONS_BEFOREGROUPS = "org.testng.annotations.BeforeGroups";

  public static final String ORG_TESTNG_ANNOTATIONS_BEFORESUITE = "org.testng.annotations.BeforeSuite";

  public static final String ORG_TESTNG_ANNOTATIONS_BEFORETEST = "org.testng.annotations.BeforeTest";

  public static final Collection<String> LIFE_CYCLE_CLASSES = List.of(
    ORG_TESTNG_ANNOTATIONS_AFTERCLASS,
    ORG_TESTNG_ANNOTATIONS_AFTERGROUPS,
    ORG_TESTNG_ANNOTATIONS_AFTERSUITE,
    ORG_TESTNG_ANNOTATIONS_AFTERTEST,
    ORG_TESTNG_ANNOTATIONS_BEFORECLASS,
    ORG_TESTNG_ANNOTATIONS_BEFOREGROUPS,
    ORG_TESTNG_ANNOTATIONS_BEFORESUITE,
    ORG_TESTNG_ANNOTATIONS_BEFORETEST
  );
}
