import numpy as np


def split_by_words(X):
    X = np.core.chararray.lower(X)
    return np.core.chararray.split(X)

<selection>DELIMITERS = "!?:;,.\'-+/\\()"

def parse(string):
    return "".join((" " if char in DELIMITERS else char) for char in string).split()
</selection><caret>