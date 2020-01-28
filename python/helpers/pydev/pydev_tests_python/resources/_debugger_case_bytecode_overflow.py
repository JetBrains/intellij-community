import sys


class Dataset:
    def __init__(self, arg):
        pass

    def set_maintainer(self, arg):
        pass

    def set_organization(self, arg):
        pass

    def set_subnational(self, arg):
        pass

    def add_country_location(self, arg):
        pass


class Response:
    @staticmethod
    def json():
        return [{'total': 1, 'pages': 1}]


class Downloader:
    def download(self, url):
        return Response()


def mock_parameters():
    topic = {'id': '17', 'value': 'Gender & Science',
             'sourceNote': 'Gender equality is a core development objective...', 'tags': ['gender', 'science'],
             'sources': {'2': [
                 {'id': 'SH.STA.MMRT', 'name': 'Maternal mortality ratio (modeled estimate, per 100, 000 live births)',
                  'unit': '', 'source': {'id': '2', 'value': 'World Development Indicators'},
                  'sourceNote': 'Maternal mortality ratio is ...',
                  'sourceOrganization': 'WHO, UNICEF, UNFPA, World Bank Group, and the United Nations Population Division. Trends in Maternal Mortality:  2000 to 2017. Geneva, World Health Organization, 2019',
                  'topics': [{'id': '8', 'value': 'Health '}, {'id': '17', 'value': 'Gender'},
                             {'id': '2', 'value': 'Aid Effectiveness '}]},
                 {'id': 'SG.LAW.CHMR', 'name': 'Law prohibits or invalidates child or early marriage (1=yes; 0=no)',
                  'unit': '', 'source': {'id': '2', 'value': 'World Development Indicators'},
                  'sourceNote': 'Law prohibits or invalidates...',
                  'sourceOrganization': 'World Bank: Women, Business and the Law.',
                  'topics': [{'id': '13', 'value': 'Public Sector '}, {'id': '17', 'value': 'Gender'}]},
                 {'id': 'SP.ADO.TFRT', 'name': 'Adolescent fertility rate (births per 1,000 women ages 15-19)',
                  'unit': '', 'source': {'id': '2', 'value': 'World Development Indicators'},
                  'sourceNote': 'Adolescent fertility rate is...',
                  'sourceOrganization': 'United Nations Population Division,  World Population Prospects.',
                  'topics': [{'id': '8', 'value': 'Health '}, {'id': '17', 'value': 'Gender'},
                             {'id': '15', 'value': 'Social Development '}]},
                 {'id': 'SH.MMR.RISK', 'name': 'Lifetime risk of maternal death (1 in: rate varies by country)',
                  'unit': '', 'source': {'id': '2', 'value': 'World Development Indicators'},
                  'sourceNote': 'Life time risk of maternal death is...',
                  'sourceOrganization': 'WHO, UNICEF, UNFPA, World Bank Group, and the United Nations Population Division. Trends in Maternal Mortality:  2000 to 2017. Geneva, World Health Organization, 2019',
                  'topics': [{'id': '8', 'value': 'Health '}, {'id': '17', 'value': 'Gender'}]}]}}

    generate_dataset_and_showcase("http://papa/", Downloader(), "AFG", "Afghanistan", topic, 60, 25)


def generate_dataset_and_showcase(base_url, downloader, countryiso, countryname, topic,
                                  indicator_limit, character_limit):
    topicname = topic['value'].replace('&', 'and')
    title = '%s - %s' % (countryname, topicname)

    dataset = Dataset({
        'name': 'wb',
        'title': title,
    })
    dataset.set_maintainer('196196be-6037-4488-8b71-d786adf4c081')
    dataset.set_organization('hdx')
    dataset.set_subnational(False)
    try:
        dataset.add_country_location(countryiso)
    except ValueError as e:
        print('%s has a problem! %s' % (countryname, e))
        return

    start_url = '%sv2/en/country/%s/indicator/' % (base_url, countryiso)
    for source_id in topic['sources']:
        indicator_list = topic['sources'][source_id]
        indicator_list_len = len(indicator_list)
        i = 0
        while i < indicator_list_len:
            ie = min(i + indicator_limit, indicator_list_len)
            indicators_string = ';'.join([x['id'] for x in indicator_list[i:ie]])
            if len(indicators_string) > character_limit:
                indicators_string = ';'.join([x['id'] for x in indicator_list[i:ie - 2]])  # break 1
                i -= 2
            url = '%s%s?source=%s&format=json&per_page=10000' % (start_url, indicators_string, source_id)
            response = downloader.download(url)
            json = response.json()
            if json[0]['total'] == 0:
                i += indicator_limit
                continue
            if json[0]['pages'] != 1:
                raise ValueError('Not expecting more than one page!')
            i += indicator_limit


mock_parameters()
sys.stdout.write('TEST SUCEEDED!')
