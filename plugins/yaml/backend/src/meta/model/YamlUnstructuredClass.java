// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml.meta.model;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Like {@link YamlDictionaryClass} but allows arbitrary level of unstructured nesting
 */
@ApiStatus.Internal
public class YamlUnstructuredClass extends YamlMetaClass {
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private static YamlUnstructuredClass ourInstance;
  private static final Object ourLock = new Object();

  public static YamlMetaClass getInstance() {
    if(ourInstance != null)
      return ourInstance;

    synchronized(ourLock) {
      if (ourInstance == null) {
        ourInstance = new YamlUnstructuredClass();
        addUnstructuredFeature(ourInstance);
      }
    }
    return ourInstance;
  }

  /**
   * convenience method for adding an all-permissive unstructured field
   * @param metaClass target metaclass
   * @return the added field
   */
  public static @NotNull Field addUnstructuredFeature(@NotNull YamlMetaClass metaClass) {
    return metaClass.addFeature(new Field("anything:<any-key>", getInstance()))
      .withAnyName()
      .withRelationSpecificType(Field.Relation.SEQUENCE_ITEM, getInstance())
      .withRelationSpecificType(Field.Relation.SCALAR_VALUE, YamlAnyScalarType.getInstance())
      .withEmptyValueAllowed(true);
  }

  public YamlUnstructuredClass() {
    super("yaml:anyobject");
  }
}
