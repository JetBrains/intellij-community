class JustAClass:
    def __init__(self):
        # Non-mobile videos: for the old pages
        self.nonMobileVideoItemRegex = 'src="(?<Image>[^"]+)"\W+>(?<Premium><div class="not-' \
                                       'available-image-overlay">)?[\w\W]{0,500}?</a></div>\W*' \
                                       '</div>\W*<div[^>]*>\W*<a href="(?<Url>[^"]+/(?<Day>\d+)-' \
                                       '(?<Month>\d+)-(?<Year>\d+)/(?<WhatsOnId>[^/"]+))"[^>]*>' \
                                       '<h4>(?<Title>[^<]+)<[\W\w]{0,600}?<p[^>]+>(?<Description>' \
                                       '[^<]*)'.replace('(?<', '(?P<')

        # Non-mobile videos: for the new pages
        self.nonMobileVideoItemRege2 = 'src="(?<Image>[^"]+)"[^>]+>\W*</a></div>\W*<div[^>]*>\W*<h3><a href="' \
                                       '(?<Url>[^"]+/(?<Day>\d+)-(?<Month>\d+)-(?<Year>\d+)/(?<WhatsOnId>[^/"]+))"' \
                                       '[^>]*>(?<Title>[^<]+)<[\W\w]{0,600}?<p[^>]*>' \
                                       '(?<Description>[^<]*)'.replace('(?<', '(?P<')