public class DataWithGeneric176<T> {

    private final T command;
    private final Runnable callback;

    @java.beans.ConstructorProperties({"command", "callback"})
    private DataWithGeneric176(T command, Runnable callback) {
        this.command = command;
        this.callback = callback;
    }

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

    public static <T> DataWithGeneric176<T> of(T command, Runnable callback) {
        return new DataWithGeneric176<T>(command, callback);
    }

    public T getCommand() {
        return this.command;
    }

    public Runnable getCallback() {
        return this.callback;
    }

    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof DataWithGeneric176)) return false;
        final DataWithGeneric176 other = (DataWithGeneric176) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$command = this.command;
        final Object other$command = other.command;
        if (this$command == null ? other$command != null : !this$command.equals(other$command)) return false;
        final Object this$callback = this.callback;
        final Object other$callback = other.callback;
        if (this$callback == null ? other$callback != null : !this$callback.equals(other$callback)) return false;
        return true;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $command = this.command;
        result = result * PRIME + ($command == null ? 0 : $command.hashCode());
        final Object $callback = this.callback;
        result = result * PRIME + ($callback == null ? 0 : $callback.hashCode());
        return result;
    }

    protected boolean canEqual(Object other) {
        return other instanceof DataWithGeneric176;
    }

    public String toString() {
        return "DataWithGeneric176(command=" + this.command + ", callback=" + this.callback + ")";
    }
}
