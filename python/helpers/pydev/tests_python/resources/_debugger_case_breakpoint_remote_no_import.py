if __name__ == '__main__':
    import os
    import sys
    port = int(sys.argv[1])
    root_dirname = os.path.dirname(os.path.dirname(__file__))
    
    if root_dirname not in sys.path:
        sys.path.append(root_dirname)
        
    print('before pydevd.settrace')
    from _pydev_bundle import pydev_localhost
    breakpoint(host=pydev_localhost.get_localhost(), port=port)  # Set up through custom sitecustomize.py
    print('after pydevd.settrace')
    print('TEST SUCEEDED!')
    