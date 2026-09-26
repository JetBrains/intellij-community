from joblib import Parallel, delayed


def f(i):
    print(i)
    return i*i

Parallel(n_jobs=2, backend='loky')(delayed(f)(i) for i in range(3))
