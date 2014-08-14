def execute():
    import os
    import sys
    
    files = None
    if 'combine' not in sys.argv:
    
        if '--pydev-analyze' in sys.argv:
                
            #Ok, what we want here is having the files passed through stdin (because
            #there may be too many files for passing in the command line -- we could
            #just pass a dir and make the find files here, but as that's already 
            #given in the java side, let's just gather that info here).
            sys.argv.remove('--pydev-analyze')
            try:
                s = raw_input()
            except:
                s = input()
            s = s.replace('\r', '')
            s = s.replace('\n', '')
            files = s.split('|')
            files = [v for v in files if len(v) > 0]
            
            #Note that in this case we'll already be in the working dir with the coverage files, so, the
            #coverage file location is not passed.
            
        else:
            #For all commands, the coverage file is configured in pydev, and passed as the first argument
            #in the command line, so, let's make sure this gets to the coverage module.            
            os.environ['COVERAGE_FILE'] = sys.argv[1]
            del sys.argv[1]
        
    try:
        import coverage #@UnresolvedImport
    except:
        sys.stderr.write('Error: coverage module could not be imported\n')
        sys.stderr.write('Please make sure that the coverage module (http://nedbatchelder.com/code/coverage/)\n')
        sys.stderr.write('is properly installed in your interpreter: %s\n' % (sys.executable,))
        
        import traceback;traceback.print_exc()
        return
    
    #print(coverage.__version__) TODO: Check if the version is a version we support (should be at least 3.4) -- note that maybe the attr is not there.
    from coverage.cmdline import main #@UnresolvedImport

    if files is not None:        
        sys.argv.append('-r')
        sys.argv.append('-m')
        sys.argv += files
        
    main()

if __name__ == '__main__':
    execute()