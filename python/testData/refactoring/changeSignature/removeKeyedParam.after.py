def retry(tries, delay=3, backoff=1, exceptions_to_check=Exception, retry_for_lambda=None):
    print "tries", tries
    print "log", log
    print "delay", delay
    print "backoff", backoff
    print "exceptions_to_check", exceptions_to_check
    print "retry_for_lambda", retry_for_lambda


retry(3, 2, retry_for_lambda=lambda x: not x)