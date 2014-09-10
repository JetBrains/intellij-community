package de.plushnikov.builder;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Builder;

public class BuilderStatic {
    @Getter
    @Setter
    private String duh;

    private BuilderStatic(String duh) {
        this.duh = duh;
    }

    @Builder
    public static BuilderStatic createTest(String duh) {
        return new BuilderStatic(duh);
    }

    public static void main(String[] args) {
        BuilderStatic.BuilderStaticBuilder audjjwajtj;
        BuilderStatic.builder().duh("duh").build();
    }

    public static class InnerTest {
        public void test() {

        }
    }
}
