import java.io.Serializable;

public class Example {
    private final Object field = "hello";

    public void twoLevels(Object o) {
        if (o instanceof CharSequence cs) {
            if (cs instanceof String s) {
                System.out.println(s.length());
            }
        }
    }

    public void threeLevels(Object o) {
        if (o instanceof Serializable ser) {
            if (ser instanceof Number n) {
                if (n instanceof Integer i) {
                    System.out.println(i + 1);
                }
            }
        }
    }

    public void nestedInSameCondition(Object o) {
        if (o instanceof CharSequence cs && cs instanceof String s) {
            System.out.println(s.length());
        }
    }

    public Object compute() {
        return "computed";
    }

    public void methodCallSubject() {
        if (compute() instanceof String s) {
            System.out.println("matched");
        }
    }

    public void fieldSubject() {
        if (field instanceof CharSequence cs) {
            if (cs instanceof String s) {
                System.out.println(s.length());
            }
        }
    }
}