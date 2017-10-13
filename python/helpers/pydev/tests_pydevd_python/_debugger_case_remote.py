if __name__ == '__main__':
    import os
    import sys
    root_dirname = os.path.dirname(os.path.dirname(__file__))
    
    if root_dirname not in sys.path:
        sys.path.append(root_dirname)
        
    import pydevd
    print('before pydevd.settrace')
    pydevd.settrace(host='127.0.0.1', port=8787)
    print('after pydevd.settrace')
    print('TEST SUCEEDED!')
    