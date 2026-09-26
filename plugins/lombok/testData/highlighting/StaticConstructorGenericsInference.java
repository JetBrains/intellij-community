package IDEA_383054_Lombok_AllArgsConstructor_staticName_generic_type_inference_error;

import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor(staticName = "of")
class WithT<T> {
    private T element;

    public T getElement(){
        return element;
    }
}

@AllArgsConstructor(staticName = "of")
class WithTAndBounds<T extends Number> {
    private T element;

    public T getElement(){
        return element;
    }
}

@AllArgsConstructor(staticName = "of")
class WithList<T> {
    private List<T> list;

    public List<T> getList(){
        return list;
    }
}

@AllArgsConstructor(staticName = "of")
class WithListAndBounds<T extends Number> {
    private List<T> list;

    public List<T> getList(){
        return list;
    }
}

@AllArgsConstructor(staticName = "of")
class WithDependentBounds<T extends Number, U extends T> {
    private T first;
    private U second;

    public T getFirst() { return first; }
    public U getSecond() { return second; }
}

@SuppressWarnings("unused")
class StaticConstructorGenericsInference {
    static void test(
            List<? extends Number> listExtendsNumber, List<? extends Integer> listExtendsInteger, Number number) {
        WithT<String> withT1 = WithT.of("abc");
        System.out.print(withT1);
        System.out.println(withT1.getElement());
        WithT<? extends String> withT2 = WithT.of("abc");
        System.out.print(withT2);
        WithT<String> withT3 = WithT.of(null);
        System.out.print(withT3);
        WithT<? extends String> withT4 = WithT.of(null);
        System.out.print(withT4);

        WithTAndBounds<Number> withTAndBounds0 = WithTAndBounds.of(number);
        System.out.print(withTAndBounds0);
        System.out.println(withTAndBounds0.getElement());
        WithTAndBounds<? extends Number> withTAndBounds1 = WithTAndBounds.of(number);
        System.out.print(withTAndBounds1);
        WithTAndBounds<? extends Number> withTAndBounds2 = WithTAndBounds.of(4);
        System.out.print(withTAndBounds2);
        WithTAndBounds<? extends Integer> withTAndBounds3 = WithTAndBounds.of(4);
        System.out.print(withTAndBounds3);
        WithTAndBounds<Integer> withTAndBounds4 = WithTAndBounds.of(4);
        System.out.print(withTAndBounds4);

        WithList<String> withList1 = WithList.of(List.<String>of());
        System.out.print(withList1);
        System.out.print(withList1.getList());
        WithList<String> withList2 = WithList.of(List.of());
        System.out.print(withList2);
        WithList<String> withList3 = WithList.of(null);
        System.out.print(withList3);

        WithListAndBounds<Number> number1 = WithListAndBounds.of(List.<Number>of());
        System.out.print(number1);
        System.out.print(number1.getList());
        WithListAndBounds<Number> number2 = WithListAndBounds.of(List.of());
        System.out.print(number2);
        WithListAndBounds<Number> number3 = WithListAndBounds.of(null);
        System.out.print(number3);
        WithListAndBounds<Number> number4 = WithListAndBounds.of(List.of(number));
        System.out.print(number4);
        WithListAndBounds<Number> number5 = WithListAndBounds.of(List.of(4));
        System.out.print(number5);
        WithListAndBounds<? extends Number> number6 = WithListAndBounds.of(listExtendsNumber);
        System.out.print(number6);

        WithListAndBounds<Integer> integer1 = WithListAndBounds.of(List.<Integer>of());
        System.out.print(integer1);
        WithListAndBounds<Integer> integer2 = WithListAndBounds.of(List.of());
        System.out.print(integer2);
        WithListAndBounds<Integer> integer3 = WithListAndBounds.of(null);
        System.out.print(integer3);
        WithListAndBounds<Integer> integer4 = WithListAndBounds.of(List.of(4));
        System.out.print(integer4);
        WithListAndBounds<? extends Integer> integer5 = WithListAndBounds.of(List.of(4));
        System.out.print(integer5);
        WithListAndBounds<? extends Number> integer6 = WithListAndBounds.of(listExtendsInteger);
        System.out.print(integer6);
        WithListAndBounds<? extends Integer> integer7 = WithListAndBounds.of(listExtendsInteger);
        System.out.print(integer7);

        // Counter-examples that protect the bounds-copying code in
        // AbstractConstructorClassProcessor#createStaticFactoryMethod. Without that code the
        // method type parameters lose their upper bounds (and bounds that reference other
        // type parameters), so the Java highlighter no longer reports the violations below.

        // Simple bound: the explicit type argument violates `T extends Number`.
        WithTAndBounds<?> stringBoundViolation = WithTAndBounds.<<error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Number'">String</error>>of("abc");
        System.out.print(stringBoundViolation);

        // Bound referencing another type parameter: String does not extend Integer (U extends T).
        // The 'should extend Integer' text proves the substitutor was applied: the class bound
        // `U extends T` is transferred to the method with method-T (bound to Integer) substituted in.
        WithDependentBounds<Integer, ?> dependentBoundViolation = WithDependentBounds.<Integer, <error descr="Type parameter 'java.lang.String' is not within its bound; should extend 'java.lang.Integer'">String</error>>of(1, "x");
        System.out.print(dependentBoundViolation);

        // Sanity checks that the new class works for valid calls.
        WithDependentBounds<Number, Integer> dependentOk = WithDependentBounds.of(1, 2);
        System.out.print(dependentOk.getFirst());
        System.out.print(dependentOk.getSecond());
    }
}