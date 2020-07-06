def f():
    try:
        print('something')
    except Exception, <caret>e:
        # ... Code that does not use 'e'
        print('something else')