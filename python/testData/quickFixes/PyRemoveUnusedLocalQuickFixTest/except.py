def f():
    try:
        print('something')
    except Exception as <caret>e:
        # ... Code that does not use 'e'
        print('something else')