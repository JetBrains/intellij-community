import pandas as pd
import numpy as np

dim = [521, 378, 107, 194, 278]
dict_var = dict()


def main():
    for i in range(len(dim)):
        temp_dict = dict()
        for j in range(dim[i]):
            df = pd.DataFrame(np.random.rand(8, 20))
            temp_dict[str(j)] = df
        dict_var[str(i)] = temp_dict
    print('TEST SUCEEDED')  # breakpoint


if __name__ == '__main__':
    main()
