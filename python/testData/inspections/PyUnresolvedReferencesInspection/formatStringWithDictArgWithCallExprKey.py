def foo():
    return 'foo'
print("{foo}".format(**{'bar': 10, foo(): 20}))