def f():
    try:
        print('something')
    except Exception:
        # ... Code that does not use 'e'
        print('something else')