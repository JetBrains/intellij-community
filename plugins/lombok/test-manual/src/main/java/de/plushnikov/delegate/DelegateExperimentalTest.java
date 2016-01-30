package de.plushnikov.delegate;

import lombok.experimental.Delegate;

import java.util.ArrayList;
import java.util.List;

public class DelegateExperimentalTest {
    public static void main(final String... args) {
        MyQ myQ = new MyQ();
        X meinX = new X(myQ);
        meinX.nothing();
        meinX.add("Hallo World");
    }

    public interface Q extends List<String> {
        void nothing();
    }

    public static final class X implements Q {
        @Delegate(types = {Q.class})
        private final Q q;

        public X(final Q q) {
            this.q = q;
        }
    }

    private static class MyQ implements Q {
        @Delegate
        private final List<String> list = new ArrayList<String>();

        @Override
        public void nothing() {
            System.out.println("LombokMain.nothing");
        }
    }
}
