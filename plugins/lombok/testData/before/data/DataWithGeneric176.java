import lombok.Data;

@Data(staticConstructor = "of")
public class DataWithGeneric176<T> {

    private final T command;
    private final Runnable callback;

    public static void main(String[] args) {
        DataWithGeneric176<Integer> test = new DataWithGeneric176<Integer>(123, new Runnable() {
            @Override
            public void run() {
                System.out.println("Run");
            }
        });

        test.getCallback();
        System.out.println(test.getCommand());

        DataWithGeneric176<String> foo = DataWithGeneric176.of("fooqwqww", new Runnable() {
            public void run() {
            }
        });

        foo.getCallback();
        System.out.println(foo.getCommand());
    }
}
