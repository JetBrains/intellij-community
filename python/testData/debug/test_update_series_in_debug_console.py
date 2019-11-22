import pandas as pd


class C:
    @staticmethod
    def f():

        def g():
            return pd.Series(index=[40, 50, 60], data=[1, 2, 3])

        a = g()
        a  # breakpoint


C.f()
