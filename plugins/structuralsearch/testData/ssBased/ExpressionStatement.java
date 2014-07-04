import java.io.File;
import java.io.IOException;

class Test{
   void foo() throws IOException {
     File f = <warning descr="Forbid File.createTempFile">File.createTempFile("", "")</warning>;
   }
}