public class ValueWithGeneric176<T> {
    private T name;
    private int count;

    @java.beans.ConstructorProperties({"name", "count"})
    private ValueWithGeneric176(T name, int count) {
        this.name = name;
        this.count = count;
    }

    public static void main(String[] args) {
        ValueWithGeneric176<String> valueObject = ValueWithGeneric176.of("thing1", 10);
        System.out.println(valueObject);
    }

    public static <T> ValueWithGeneric176<T> of(T name, int count) {
        return new ValueWithGeneric176<T>(name, count);
    }

    public T getName() {
        return this.name;
    }

    public int getCount() {
        return this.count;
    }

    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ValueWithGeneric176)) return false;
        final ValueWithGeneric176 other = (ValueWithGeneric176) o;
        final Object this$name = this.name;
        final Object other$name = other.name;
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
        if (this.count != other.count) return false;
        return true;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $name = this.name;
        result = result * PRIME + ($name == null ? 0 : $name.hashCode());
        result = result * PRIME + this.count;
        return result;
    }

    public String toString() {
        return "ValueWithGeneric176(name=" + this.name + ", count=" + this.count + ")";
    }
}