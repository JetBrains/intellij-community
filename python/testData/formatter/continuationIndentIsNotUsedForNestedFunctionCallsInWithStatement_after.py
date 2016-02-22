with raises_assertion(
        has_string('Missing download_urls: {}, {}'.format(
            self.other_download_url, self.another_download_url))):
    fixture.assert_detail_page_yields_expected()
