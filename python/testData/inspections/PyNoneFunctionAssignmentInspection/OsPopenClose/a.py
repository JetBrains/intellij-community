from os import popen
ret = popen("non-existent-command").close()