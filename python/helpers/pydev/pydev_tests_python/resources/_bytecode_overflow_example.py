import re

en_lang_symbols = r'[^\w!@#$%\^-_+=|\}{][\"\';:?\/><.,&)(*\s`\u2019]'
en_words_basic = []
en_words = []

TRACE_MESSAGE = "Trace called"


def tracing():
    print(TRACE_MESSAGE)


def call_tracing():
    tracing()


class Dummy:
    non_en_words_limit = 3

    @staticmethod
    def fun(text):
        words = tuple(w[0].lower() for w in re.finditer(r'[a-zA-Z]+', text))
        non_en_pass = []
        for i, word in enumerate(words):
            non_en = []
            if not (word in en_words_basic
                    or (word.endswith('s') and word[:-1] in en_words_basic)
                    or (word.endswith('ed') and word[:-2] in en_words_basic)
                    or (word.endswith('ing') and word[:-3] in en_words_basic)
                    or word in en_words
                    or (word.endswith('s') and word[:-1] in en_words)
                    or (word.endswith('ed') and word[:-2] in en_words)
                    or (word.endswith('ing') and word[:-3] in en_words)
                    ):

                non_en.append(word)
                non_en_pass.append(word)
                for j in range(1, Dummy.non_en_words_limit):
                    if i + j >= len(words):
                        break
                    word = words[i + j]

                    if (word in en_words_basic
                        or (word.endswith('s') and word[:-1] in en_words_basic)
                        or (word.endswith('ed') and word[:-2] in en_words_basic)
                        or (word.endswith('ing') and word[:-3] in en_words_basic)
                        or word in en_words
                        or (word.endswith('s') and word[:-1] in en_words)
                        or (word.endswith('ed') and word[:-2] in en_words)
                        or (word.endswith('ing') and word[:-3] in en_words)
                    ):
                        break
                    else:
                        non_en.append(word)
                        non_en_pass.append(word)


class DummyTracing:
    non_en_words_limit = 3

    @staticmethod
    def fun(text):
        words = tuple(w[0].lower() for w in re.finditer(r'[a-zA-Z]+', text))
        tracing()
        non_en_pass = []
        for i, word in enumerate(words):
            non_en = []
            if not (word in en_words_basic
                    or (word.endswith('s') and word[:-1] in en_words_basic)
                    or (word.endswith('ed') and word[:-2] in en_words_basic)
                    or (word.endswith('ing') and word[:-3] in en_words_basic)
                    or word in en_words
                    or (word.endswith('s') and word[:-1] in en_words)
                    or (word.endswith('ed') and word[:-2] in en_words)
                    or (word.endswith('ing') and word[:-3] in en_words)
                    ):

                non_en.append(word)
                non_en_pass.append(word)
                for j in range(1, Dummy.non_en_words_limit):
                    if i + j >= len(words):
                        break
                    word = words[i + j]
                    if (word in en_words_basic
                        or (word.endswith('s') and word[:-1] in en_words_basic)
                        or (word.endswith('ed') and word[:-2] in en_words_basic)
                        or (word.endswith('ing') and word[:-3] in en_words_basic)
                        or word in en_words
                        or (word.endswith('s') and word[:-1] in en_words)
                        or (word.endswith('ed') and word[:-2] in en_words)
                        or (word.endswith('ing') and word[:-3] in en_words)
                        ):
                        break
                    else:
                        non_en.append(word)
                        non_en_pass.append(word)

