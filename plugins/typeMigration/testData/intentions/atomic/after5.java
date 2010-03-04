// "Convert to atomic" "true"

import java.util.concurrent.atomic.AtomicInteger;

class Test {
 AtomicInteger i = new AtomicInteger(0 + 8);

 int j = i.get() + 5;
 String s = "i = " + i;
}