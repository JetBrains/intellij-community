package com.jetbrains.numpy.codeInsight;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class NumpyUfuncs {
  private static final List<String> UFUNC_LIST = new ArrayList<>();
  public static final List<String> UFUNC_METHODS = new ArrayList<>();

  public static boolean isUFunc(@Nullable final String name) {
    return UFUNC_LIST.contains(name);
  }

  static {
    //Math operations
    UFUNC_LIST.add("add");
    UFUNC_LIST.add("subtract");
    UFUNC_LIST.add("multiply");
    UFUNC_LIST.add("divide");
    UFUNC_LIST.add("logaddexp");
    UFUNC_LIST.add("logaddexp2");
    UFUNC_LIST.add("true_divide");
    UFUNC_LIST.add("floor_divide");
    UFUNC_LIST.add("negative");
    UFUNC_LIST.add("power");
    UFUNC_LIST.add("remainder");
    UFUNC_LIST.add("mod");
    UFUNC_LIST.add("fmod");
    UFUNC_LIST.add("absolute");
    UFUNC_LIST.add("rint");
    UFUNC_LIST.add("sign");
    UFUNC_LIST.add("conj");
    UFUNC_LIST.add("exp");
    UFUNC_LIST.add("exp2");
    UFUNC_LIST.add("log");
    UFUNC_LIST.add("log2");
    UFUNC_LIST.add("log10");
    UFUNC_LIST.add("expm1");
    UFUNC_LIST.add("log1p");
    UFUNC_LIST.add("sqrt");
    UFUNC_LIST.add("square");
    UFUNC_LIST.add("reciprocal");
    UFUNC_LIST.add("ones_like");

    //Trigonometric
    UFUNC_LIST.add("sin");
    UFUNC_LIST.add("cos");
    UFUNC_LIST.add("tan");
    UFUNC_LIST.add("arcsin");
    UFUNC_LIST.add("arccos");
    UFUNC_LIST.add("arctan");
    UFUNC_LIST.add("arctan2");
    UFUNC_LIST.add("hypot");
    UFUNC_LIST.add("sinh");
    UFUNC_LIST.add("cosh");
    UFUNC_LIST.add("tanh");
    UFUNC_LIST.add("arcsinh");
    UFUNC_LIST.add("arccosh");
    UFUNC_LIST.add("arctanh");
    UFUNC_LIST.add("deg2rad");
    UFUNC_LIST.add("rad2deg");

    //Bit-twiddling functions
    UFUNC_LIST.add("bitwise_and");
    UFUNC_LIST.add("bitwise_or");
    UFUNC_LIST.add("bitwise_xor");
    UFUNC_LIST.add("invert");
    UFUNC_LIST.add("left_shift");
    UFUNC_LIST.add("right_shift");

    //Comparison functions
    UFUNC_LIST.add("greater");
    UFUNC_LIST.add("greater_equal");
    UFUNC_LIST.add("less");
    UFUNC_LIST.add("less_equal");
    UFUNC_LIST.add("not_equal");
    UFUNC_LIST.add("equal");
    UFUNC_LIST.add("logical_and");
    UFUNC_LIST.add("logical_or");
    UFUNC_LIST.add("logical_xor");
    UFUNC_LIST.add("logical_not");
    UFUNC_LIST.add("maximum");
    UFUNC_LIST.add("minimum");
    UFUNC_LIST.add("fmax");
    UFUNC_LIST.add("fmin");

    ///Floating functions
    UFUNC_LIST.add("isreal");
    UFUNC_LIST.add("iscomplex");
    UFUNC_LIST.add("isfinite");
    UFUNC_LIST.add("isinf");
    UFUNC_LIST.add("isnan");
    UFUNC_LIST.add("signbit");
    UFUNC_LIST.add("copysign");
    UFUNC_LIST.add("nextafter");
    UFUNC_LIST.add("modf");
    UFUNC_LIST.add("ldexp");
    UFUNC_LIST.add("frexp");
    UFUNC_LIST.add("fmod");
    UFUNC_LIST.add("floor");
    UFUNC_LIST.add("ceil");
    UFUNC_LIST.add("trunc");

    UFUNC_LIST.add("fabs");

    UFUNC_METHODS.add("nin");
    UFUNC_METHODS.add("nout");
    UFUNC_METHODS.add("nargs");
    UFUNC_METHODS.add("identity");
    UFUNC_METHODS.add("ntypes");
    UFUNC_METHODS.add("accumulate");
    UFUNC_METHODS.add("reduce");
    UFUNC_METHODS.add("reduceat");
    UFUNC_METHODS.add("outer");
    UFUNC_METHODS.add("at");
  }
}
