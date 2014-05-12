try:
    import inspect
except:
    try:
        import _pydev_inspect as inspect # for older versions
    except:
        import traceback;traceback.print_exc() #Ok, no inspect available (search will not work)

try:
    import re
except:
    try:
        import _pydev_re as re # for older versions @UnresolvedImport
    except:
        import traceback;traceback.print_exc() #Ok, no inspect available (search will not work)



def DoFind(f, mod):
    import linecache
    if inspect.ismodule(mod):
        return f, 0, 0
    
    lines = linecache.getlines(f)
    
    if inspect.isclass(mod):
        name = mod.__name__
        pat = re.compile(r'^\s*class\s*' + name + r'\b')
        for i in range(len(lines)):
            if pat.match(lines[i]): 
                return f, i, 0
            
        return f, 0, 0

    if inspect.ismethod(mod):
        mod = mod.im_func
        
    if inspect.isfunction(mod):
        try:
            mod = mod.func_code
        except AttributeError:
            mod = mod.__code__ #python 3k
            
    if inspect.istraceback(mod):
        mod = mod.tb_frame
        
    if inspect.isframe(mod):
        mod = mod.f_code

    if inspect.iscode(mod):
        if not hasattr(mod, 'co_filename'):
            return None, 0, 0
        
        if not hasattr(mod, 'co_firstlineno'):
            return mod.co_filename, 0, 0
        
        lnum = mod.co_firstlineno
        pat = re.compile(r'^(\s*def\s)|(.*(?<!\w)lambda(:|\s))|^(\s*@)')
        while lnum > 0:
            if pat.match(lines[lnum]): 
                break
            lnum -= 1
            
        return f, lnum, 0

    raise RuntimeError('Do not know about: ' + f + ' ' + str(mod))
