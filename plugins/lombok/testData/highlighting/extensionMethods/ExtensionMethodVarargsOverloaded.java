import lombok.experimental.ExtensionMethod;

import lombok.experimental.ExtensionMethod;

@ExtensionMethod(ExtensionMethodVarargsOverloaded.Extensions.class)
class ExtensionMethodVarargsOverloaded {
    void test(Object s) {
        s.extensionMethod<error descr="Ambiguous method call: both 'Object.extensionMethod(Number...)' and 'Object.extensionMethod(String...)' match">()</error>;
    }

    static class Extensions {
        public static <T> void extensionMethod(T receiver, T... value) {
        }
        public static void extensionMethod(String receiver, String... value) {
        }
        public static <T> void extensionMethod(T receiver, Number... value) {
        }
        public static <T> void extensionMethod(T receiver, String... value) {
        }
    }
}