/*
 * Copyright © 2009 Reinier Zwitserloot and Roel Spilker.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ensures the variable declaration that you annotate will be cleaned up by calling its close method, regardless
 * of what happens. Implemented by wrapping all statements following the local variable declaration to the
 * end of your scope into a try block that, as a finally action, closes the resource.
 * <p/>
 * Example:
 * <pre>
 * public void copyFile(String in, String out) throws IOException {
 *     &#64;Cleanup FileInputStream inStream = new FileInputStream(in);
 *     &#64;Cleanup FileOutputStream outStream = new FileOutputStream(out);
 *     byte[] b = new byte[65536];
 *     while (true) {
 *         int r = inStream.read(b);
 *         if (r == -1) break;
 *         outStream.write(b, 0, r);
 *     }
 * }
 * </pre>
 * <p/>
 * Will generate:
 * <pre>
 * public void copyFile(String in, String out) throws IOException {
 *     &#64;Cleanup FileInputStream inStream = new FileInputStream(in);
 *     try {
 *         &#64;Cleanup FileOutputStream outStream = new FileOutputStream(out);
 *         try {
 *             byte[] b = new byte[65536];
 *             while (true) {
 *                 int r = inStream.read(b);
 *                 if (r == -1) break;
 *                 outStream.write(b, 0, r);
 *             }
 *         } finally {
 *             out.close();
 *         }
 *     } finally {
 *         in.close();
 *     }
 * }
 * </pre>
 * <p/>
 * Note that the final close method call, if it throws an exception, will overwrite any exception thrown
 * in the main body of the generated try block. You should NOT rely on this behaviour - future versions of
 * lombok intend to silently swallow any exception thrown by the cleanup method <i>_IF</i> the main body
 * throws an exception as well, as the earlier exception is usually far more useful.
 * <p/>
 * However, in java 1.6, generating the code to do this is prohibitively complicated.
 */
@Target(ElementType.LOCAL_VARIABLE)
@Retention(RetentionPolicy.SOURCE)
public @interface Cleanup {
  /**
   * The name of the method that cleans up the resource. By default, 'close'. The method must not have any parameters.
   */
  String value() default "close";
}
