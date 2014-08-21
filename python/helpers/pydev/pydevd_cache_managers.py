__author__ = 'novokreshchenov.konstantin'

import pydevd_vars
import sys


def get_signature_info(signature):
    return signature.file, signature.name, ' '.join([arg[1]for arg in signature.args])

def get_frame_info(frame): #return func_name, line_no, filename
    co = frame.f_code
    return co.co_name, frame.f_lineno, co.co_filename # or frame.f_globals["__file__"]


class CacheManager(object):
    def __init__(self, log=None):
        self.cache = {}
        self.log = log

    def get_cache_size(self):
        return sys.getsizeof(self.cache)

    def write_cache_size(self, default=None):
        cache_size = self.get_cache_size()
        if self.log:
            open(self.log, 'w').write(str(cache_size))

    def write_cache(self):
        if self.log is not None:
            open(self.log, "w+").write(self.print_cache())


class CallSignatureCacheManager(CacheManager):
    def __init__(self, log=None):
        CacheManager.__init__(self, log)

    def add(self, signature):
        filename, name, args_type = get_signature_info(signature)

        if not filename in self.cache:
            self.cache[filename] = {}

        calls_from_file = self.cache[filename]

        if not name in calls_from_file:
            calls_from_file[name] = {}

        name_calls = calls_from_file[name]
        name_calls[args_type] = None

    def is_repetition(self, signature):
        filename, name, args_type = get_signature_info(signature)
        if filename in self.cache and name in self.cache[filename] and args_type in self.cache[filename][name]:
            return True
        return False

    def is_first_call(self, signature):
        filename, name = get_signature_info(signature)[:-1]
        if filename in self.cache and name in self.cache[filename]:
            return False
        return True

    def print_cache(self):
        output = ""
        for filename, calls_from_file in self.cache.items():
            for name, args_type in calls_from_file.items():
                output += "filename=%s, name=%s, args_type=%s \n" % (filename, name, args_type)


class ReturnSignatureCacheManager(CacheManager):
    def __init__(self, log=None):
        CacheManager.__init__(self, log)

    def add(self, signature, return_info):
        filename, name = get_signature_info(signature)[:-1]

        if not filename in self.cache:
            self.cache[filename] = {}

        calls_from_file = self.cache[filename]

        if not name in calls_from_file:
            calls_from_file[name] = {}

        returns = calls_from_file[name]
        returns[return_info] = None

    def is_repetition(self, signature, return_info):
        filename, name = get_signature_info(signature)[:-1]
        if filename in self.cache and name in self.cache[filename] and return_info in self.cache[filename][name]:
            return True
        return False

    def print_cache(self):
        output = ""
        for filename, calls_from_file in self.cache.items():
            for name, returns in calls_from_file.items():
                output += 'filename=%s, name=%s, returns=%s \n' % (filename, name, returns)


class CallInfo(object):
    def __init__(self, caller_file, callee_file, caller_name, callee_name, caller_line):
        self.caller_file = caller_file
        self.callee_file = callee_file
        self.caller_name = caller_name
        self.callee_name = callee_name
        self.caller_line = caller_line


class CallHierarchyCacheManager(CacheManager): #stores for every function in file its own callers
    def __init__(self, log=None):
        CacheManager.__init__(self, log)

    def print_cache(self):
        output = ""
        for filename, cache_for_file in self.cache.items():
            for func_name, cache_for_func in cache_for_file.items():
                output += 'file="%s", func="%s":\n' % (filename, func_name)
                for caller_filename, cache_for_caller_file in cache_for_func.items():
                    for caller_func_name, lines_in_caller_func in cache_for_caller_file.items():
                        output += '    file="%s", caller="%s", lines="%s"\n' % (caller_filename, caller_func_name, lines_in_caller_func)
                output += '\n'
        return output

    def add(self, frame): #result is True if it is not repetition otherwise return false
        caller_frame = frame.f_back
        if caller_frame is None:
            return False

        result = False

        func_name, func_def_line_no, func_filename = get_frame_info(frame)
        caller_func_name, func_call_line_no, caller_filename = get_frame_info(caller_frame)

        if not func_filename in self.cache:
            self.cache[func_filename] = {}

        cache_for_file = self.cache[func_filename]

        if not func_name in cache_for_file:
            cache_for_file[func_name] = {}

        cache_for_func = cache_for_file[func_name]

        if not caller_filename in cache_for_func:
            cache_for_func[caller_filename] = {}

        cache_for_caller_file = cache_for_func[caller_filename]

        if not caller_func_name in cache_for_caller_file:
            cache_for_caller_file[caller_func_name] = {}

        cache_for_func_caller = cache_for_caller_file[caller_func_name]

        if not func_call_line_no in cache_for_func_caller:
            cache_for_func_caller[func_call_line_no] = None
            result = True

        if result:
            return CallInfo(caller_filename, func_filename, caller_func_name, func_name, str(func_call_line_no))
        else:
            return None

    def is_repetition(self, frame):
        caller_frame = frame.f_back

        if caller_frame is None:
            return True

        func_name, func_def_line_no, func_filename = get_frame_info(frame)
        caller_func_name, func_call_line_no, caller_filename = get_frame_info(caller_frame)

        if func_filename in self.cache:
            cache_for_file = self.cache[func_filename]
            if func_name in cache_for_file:
                cache_for_func = cache_for_file[func_name]
                if caller_filename in cache_for_func:
                    cache_for_caller_file = cache_for_func[caller_filename]
                    if caller_func_name in cache_for_caller_file:
                        cache_for_func_caller = cache_for_caller_file[caller_func_name]
                        if func_call_line_no in cache_for_func_caller:
                            return True

        return False