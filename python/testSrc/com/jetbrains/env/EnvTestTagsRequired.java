/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.env;

import com.jetbrains.TestEnv;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tag {@link PyEnvTestCase} (or inheritor) if you need specific tag to run this test.
 *
 * @author Ilya.Kazakevich
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface EnvTestTagsRequired {
  /**
   * @return tags should exist on interpreter for this test not to be skipped
   */
  String[] tags();
  TestEnv[] skipOnOSes() default {};
  Class<? extends PythonSdkFlavor>[] skipOnFlavors() default {};
}
