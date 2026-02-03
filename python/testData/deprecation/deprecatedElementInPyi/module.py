import warnings
warnings.warn("the deprecated module is deprecated; use a non-deprecated module instead",
                DeprecationWarning, 2)

def bar():
    import warnings
    warnings.warn("this is deprecated", DeprecationWarning, 2)
