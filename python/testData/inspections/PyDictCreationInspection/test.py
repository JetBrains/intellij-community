<weak_warning descr="This dictionary creation could be rewritten as a dictionary literal">dict = {"n": "n"}</weak_warning>
dict["a"], dict["b"] = "a", "b"
dict["k"] = "k"

dict, a = {"n": "n"}
dict["a"], a["b"] = "a", "b"
dict["k"] = "k"

d = {}
foo()
d["a"] = 3

def someiter():
    yield 1
    yield 2
dikt = { 'results' : list(someiter()) }
dikt['num_results'] = len(dikt['results'])
