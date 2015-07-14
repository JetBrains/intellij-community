package de.plushnikov.builder.importbuilder;

import de.plushnikov.builder.BuilderExample;
import de.plushnikov.builder.BuilderExample.BuilderExampleBuilder;
import de.plushnikov.builder.importbuilder.otherpackage.Builder2Import;
import de.plushnikov.builder.importbuilder.otherpackage.Builder2Import.Builder2ImportBuilder;
import de.plushnikov.builder.simple.BuilderSimple;
import de.plushnikov.builder.simple.BuilderSimple.BuilderSimpleBuilder;

public class TestImportingBuilderClass {
    public static void main(String[] args) {
        BuilderSimpleBuilder builderSimple = BuilderSimple.builder();

        BuilderSimple simple = builderSimple.myInt(1).build();
        System.out.println(simple);

        BuilderExampleBuilder builderExampleBuilder = BuilderExample.builder();

        Builder2ImportBuilder builder2ImportBuilder = Builder2Import.builder();
    }
}
