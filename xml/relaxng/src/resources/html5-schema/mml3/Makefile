BASE=http://www.w3.org/Math/RelaxNG/mathml3
FILES=\
mathml3-common.rnc \
mathml3-content.rnc \
mathml3-presentation.rnc \
mathml3-strict-content.rnc \
mathml3.rnc
CURL=curl
CURLFLAGS=-s
PATCH=patch
PATCHFLAGS=

all:
	for file in $(FILES); do $(CURL) $(CURLFLAGS) $(BASE)/$$file > $$file; done
	$(PATCH) $(PATCHFLAGS) < patch-vnu

clean:
	$(RM) $(FILES)
