import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class J {
    void foo(ArrayList<@Nullable String> list) {
        for (String s : list) {
            System.out.println(s.length());
            System.out.println(s.length());
            System.out.println(s.length());
        }
    }
}