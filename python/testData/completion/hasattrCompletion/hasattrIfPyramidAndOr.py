def foo(x):
    if hasattr(x, 'foo'):
      if hasattr(x, 'bar') or hasattr(x, 'baz'):
        if hasattr(x, 'qux') and (hasattr(x, 'quux') or hasattr(x, 'quuz')) and hasattr(x, 'corge'):
          print(x.<caret>)