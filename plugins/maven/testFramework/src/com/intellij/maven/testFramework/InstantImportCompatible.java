// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.maven.testFramework;


import java.lang.annotation.*;

/**
 * InstantImportCompatible is an annotation that can be applied to methods.
 * It indicates that the annotated test is used during the preimport process and all tests asserts and logic stays in preimport process
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Documented
public @interface InstantImportCompatible {
}
