import lombok.Synchronized;

public abstract class SynchronizedTest {
    private final Object fooLock = new Object();
    private final static Object foobarLock = new Object();

    @Synchronized
    public static void hello() {
        System.out.println("hello");
    }

    @Synchronized
    public int answerToLife() {
        return 42;
    }

    @Synchronized("fooLock")
    public void foo() {
        System.out.println("foo");
    }

    @Synchronized("foobarLock")
    public static void foobar() {
        System.out.println("foobar");
    }

    <error descr="The field 'fooLock' is non-static and this cannot be used on this static method">@Synchronized("fooLock")</error>
    public static void errorStatic() {
        System.out.println("errorStatic");
    }

    <error descr="The field 'doesnExists1' does not exist.">@Synchronized("doesnExists1")</error>
    public void error1() {
        System.out.println("error1");
    }

    <error descr="The field 'doesnExists2' does not exist.">@Synchronized("doesnExists2")</error>
    public static void error2() {
        System.out.println("error2");
    }

    <error descr="@Synchronized is legal only on concrete methods.">@Synchronized</error>
    abstract void abstractMethod();
}
