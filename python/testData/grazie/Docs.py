# coding=utf-8
"""Module description <TYPO descr="Typo: In word 'eror'">eror</TYPO>"""


class ExampleClassWithNoTypos:
    """A group of *members*.

    This class has no useful logic; it's just a documentation example.
    This class is useful for mocking requests.Response (the class that comes back from requests.get and friends).

    Args:
        name (str): the name of this group. And another sentence.

    Attributes:
        name (str): the name of this group. <GRAMMAR_ERROR descr="UPPERCASE_SENTENCE_START">and</GRAMMAR_ERROR> another sentence. And here are some correct English words to make the language detector work.

    """

    def __init__(self, name):
        self.name = name

    def good_function(self, member):
        """
        Adds a [member] to this group.

        Args:
            member (str): member to add to the group.

        Returns:
            int: the new size of the group.

        """
        return 1  # no error comment


class ExampleClassWithTypos:
    """It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> friend there. And here are some correct English words to make the language detector work.

    Args:
        name (str): the <GRAMMAR_ERROR descr="COMMA_WHICH">name which</GRAMMAR_ERROR> group and some other English text

    Attributes:
        name (str): the <GRAMMAR_ERROR descr="COMMA_WHICH">name which</GRAMMAR_ERROR> group and some other English text

    """

    def __init__(self, name):
        self.name = name

    def bad_function(self, member):
        """
        It <GRAMMAR_ERROR descr="IT_VBZ">add</GRAMMAR_ERROR> a [member] to this <TYPO descr="Typo: In word 'grooup'">grooup</TYPO>. And here are some correct English words to make the language detector work.

        Args:
            member (str): member to add to the group.

        Returns:
            int: the new size of <GRAMMAR_ERROR descr="DT_DT">a the</GRAMMAR_ERROR> group. And here are some correct English words to make the language detector work.

        """
        return 1  # It <GRAMMAR_ERROR descr="IT_VBZ">are</GRAMMAR_ERROR> <TYPO descr="Typo: In word 'eror'">eror</TYPO> comment. And here are some correct English words to make the language detector work.


class ForMultiLanguageSupport:
    """
    В коробке лежало <GRAMMAR_ERROR descr="Sklonenije_NUM_NN">пять карандаша</GRAMMAR_ERROR>.
    А <GRAMMAR_ERROR descr="grammar_vse_li_noun">все ли ошибка</GRAMMAR_ERROR> найдены?
    Это случилось <GRAMMAR_ERROR descr="INVALID_DATE">31 ноября</GRAMMAR_ERROR> 2014 г.
    За весь вечер она <GRAMMAR_ERROR descr="ne_proronila_ni">не проронила и слово</GRAMMAR_ERROR>.
    Собрание состоится в <GRAMMAR_ERROR descr="RU_COMPOUNDS">конференц зале</GRAMMAR_ERROR>.
    <GRAMMAR_ERROR descr="WORD_REPEAT_RULE">Он он</GRAMMAR_ERROR> ошибка.
    """

    def __init__(self):
        """
        Er überprüfte die Rechnungen noch <TYPO descr="Typo: In word 'einal'">einal</TYPO>, um ganz <GRAMMAR_ERROR descr="COMPOUND_INFINITIV_RULE">sicher zu gehen</GRAMMAR_ERROR>.
        Das ist <GRAMMAR_ERROR descr="FUEHR_FUER">führ</GRAMMAR_ERROR> Dich!
        Das <TYPO descr="Typo: In word 'daert'">daert</TYPO> geschätzt fünf <GRAMMAR_ERROR descr="MANNSTUNDE">Mannstunden</GRAMMAR_ERROR>.
        """
        pass
