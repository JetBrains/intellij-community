/*
 * Copyright (C) 2010-2018 The Project Lombok Authors.
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

/**
 * Use {@code var} as the type of any local variable declaration (even in a {@code for} statement), and the type will be inferred from the initializing expression
 * (any further assignments to the variable are not involved in this type inference).
 * <p>
 * For example: {@code var x = 10.0;} will infer {@code double}, and {@code var y = new ArrayList<String>();} will infer {@code ArrayList<String>}.
 * <p>
 * Note that this is an annotation type because {@code var x = 10;} will be desugared to {@code @var int x = 10;}
 * <p>
 * Complete documentation is found at <a href="https://projectlombok.org/features/var">the project lombok features page for &#64;var</a>.
 */
public @interface var {
}
