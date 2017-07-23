if __name__ == '__main__':
    import os
    import sys
    root_dirname = os.path.dirname(os.path.dirname(__file__))
    
    if root_dirname not in sys.path:
        sys.path.append(root_dirname)
        
    import pydevd
    print('before pydevd.settrace')
    pydevd.settrace(port=8787)
    print('after pydevd.settrace')
    print('TEST SUCEEDED!')
    