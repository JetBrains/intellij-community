def foo():
    for arg in sys.argv[1:]:
        try:
            f = open(arg, 'r')
        except IOError:
            print('cannot open', arg)
        else:
            <selection>length = len(f.readlines()) #<---extract something from here
            print("hi from else")</selection>
            #anything else you need