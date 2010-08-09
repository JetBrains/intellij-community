import java.util.concurrent.atomic.AtomicInteger;

// "Convert to atomic" "true"
class Test {
 AtomicInteger i = new AtomicInteger(0);

 int j = i.get() + 5;
 String s = "i = " + i;
}