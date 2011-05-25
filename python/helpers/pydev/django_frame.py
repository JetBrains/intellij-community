def read_file(filename):
    f = open(filename, "r")
    s =  f.read()
    f.close()
    return s

def offset_to_line_number(text, offset):
    curLine = 1
    curOffset = 0
    while curOffset < offset:
        if curOffset == len(text):
            return -1
        c = text[curOffset]
        if c == '\n':
            curLine += 1
        elif c == '\r':
            curLine += 1
            if curOffset < len(text) and text[curOffset + 1] == '\n':
                curOffset += 1

        curOffset += 1

    return curLine

def get_source(frame):
    try:
        return frame.f_locals['self'].source
    except:
        return None

def get_template_file_name(frame):
    try:
        source = get_source(frame)
        return source[0].name
    except:
        return None

def get_template_line(frame):
    source = get_source(frame)
    file_name = get_template_file_name(frame)
    try:
        return offset_to_line_number(read_file(file_name), source[1][0])
    except:
        return None

class DjangoTemplateFrame:
    def __init__(self, frame):
        file_name = get_template_file_name(frame)
        context = frame.f_locals['context']
        self.f_code = FCode('Django Template', file_name)
        self.f_lineno = get_template_line(frame)
        self.f_back = frame
        self.f_globals = {}
        self.f_locals = self.collect_context(context)
        self.f_trace = None

    def collect_context(self, context):
        res = {}
        for d in context.dicts:
            for k,v in d.items():
                res[k] = v
        return res

    def changeVariable(self, name, value):
        context = self.f_back.f_locals['context']
        for d in context.dicts:
            for k,v in d.items():
                if k == name:
                    d[k] = value

class FCode:
    def __init__(self, name, filename):
        self.co_name = name
        self.co_filename = filename
  