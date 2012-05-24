mod.wizards {
	newContentElement.wizardItems {
		forms.elements {
			mailform {
				tt_content_defValues {
					bodytext (
enctype = multipart/form-data
method = post
prefix = tx_form
					)
				}
			}
		}
	}
	form {
		defaults {
			showTabs = elements, options, form
			tabs {
				elements {
					showAccordions = basic, predefined, content
					accordions {
						basic {
							showButtons = checkbox, fieldset, fileupload, hidden, password, radio, reset, select, submit, textarea, textline
						}
						predefined {
							showButtons = email, radiogroup, checkboxgroup, name
						}
						content {
							showButtons = header, textblock
						}
					}
				}
				options {
					showAccordions = legend, label, attributes, options, validation, filters, various
					accordions {
						attributes {
							showProperties = accept, acceptcharset, accesskey, action, alt, checked, class, cols, dir, disabled, enctype, id, label, lang, maxlength, method, multiple, name, readonly, rows, selected, size, src, style, tabindex, title, type, value
						}
						label {
							showProperties = label
						}
						validation {
							showRules = alphabetic, alphanumeric, between, date, digit, email, equals, fileallowedtypes, filemaximumsize, fileminimumsize, float, greaterthan, inarray, integer, ip, length, lessthan, regexp, required, uri
							rules {
								alphabetic {
									showProperties = message, error, breakOnError, showMessage, allowWhiteSpace
								}
								alphanumeric {
									showProperties = message, error, breakOnError, showMessage, allowWhiteSpace
								}
								between {
									showProperties = message, error, breakOnError, showMessage, minimum, maximum, inclusive
								}
								date {
									showProperties = message, error, breakOnError, showMessage, format
								}
								digit {
									showProperties = message, error, breakOnError, showMessage
								}
								email {
									showProperties = message, error, breakOnError, showMessage
								}
								equals {
									showProperties = message, error, breakOnError, showMessage, field
								}
								fileallowedtypes {
									showProperties = message, error, breakOnError, showMessage, types
								}
								filemaximumsize {
									showProperties = message, error, breakOnError, showMessage, maximum
								}
								fileminimumsize {
									showProperties = message, error, breakOnError, showMessage, minimum
								}
								float {
									showProperties = message, error, breakOnError, showMessage
								}
								greaterthan {
									showProperties = message, error, breakOnError, showMessage, minimum
								}
								inarray {
									showProperties = message, error, breakOnError, showMessage, array, strict
								}
								integer {
									showProperties = message, error, breakOnError, showMessage
								}
								ip {
									showProperties = message, error, breakOnError, showMessage
								}
								length {
									showProperties = message, error, breakOnError, showMessage, minimum, maximum
								}
								lessthan {
									showProperties = message, error, breakOnError, showMessage, maximum
								}
								regexp {
									showProperties = message, error, breakOnError, showMessage, expression
								}
								required {
									showProperties = message, error, breakOnError, showMessage
								}
								uri {
									showProperties = message, error, breakOnError, showMessage
								}
							}
						}
						filtering {
							showFilters = alphabetic, alphanumeric, currency, digit, integer, lowercase, regexp, removexss, stripnewlines, titlecase, trim, uppercase
							filters {
								alphabetic {
									showProperties = allowWhiteSpace
								}
								alphanumeric {
									showProperties = allowWhiteSpace
								}
								currency {
									showProperties = decimalPoint, thousandSeparator
								}
								digit {
									showProperties =
								}
								integer {
									showProperties =
								}
								lowercase {
									showProperties =
								}
								regexp {
									showProperties = expression
								}
								removexss {
									showProperties =
								}
								stripnewlines {
									showProperties =
								}
								titlecase {
									showProperties =
								}
								trim {
									showProperties = characterList
								}
								uppercase {
									showProperties =
								}
							}
						}
					}
				}
				form {
					showAccordions = prefix, attributes, postProcessor
					accordions {
						attributes {
							showProperties = accept, acceptcharset, action, class, dir, enctype, id, lang, method, name, style, title
						}
						postProcessor {
							showPostProcessors = mail
							postProcessors {
								mail {
									showProperties = recipientEmail, senderEmail
								}
							}
						}
					}
				}
			}
		}
		elements {
			button {
				showAccordions = attributes
				accordions {
					attributes {
						showProperties = value
					}
				}
			}
			checkbox {
				showAccordions = label, attributes
				accordions {
					attributes {
						showProperties = name, value
					}
				}
			}
			fieldset {
				showAccordions = legend
			}
			fileupload {
				showAccordions = label, attributes, validation
				accordions {
					attributes {
						showProperties = name
					}
					validation {
						showRules = required, fileallowedtypes, filemaximumsize, fileminimumsize
					}
				}
			}
			hidden {
				showAccordions = attributes
				accordions {
					attributes {
						showProperties = name, value
					}
				}
			}
			password {
				showAccordions = label, attributes, validation
				accordions {
					attributes {
						showProperties = name
					}
					validation {
						showRules = required, equals
					}
				}
			}
			radio < .checkbox
			reset < .button
			select {
				showAccordions = label, attributes, options, validation
				accordions {
					attributes {
						showProperties = name, multiple
					}
					validation {
						showRules = required
					}
				}
			}
			submit < .button
			textarea {
				showAccordions = label, attributes, validation, filters
				accordions {
					attributes {
						showProperties = name, cols, rows
					}
					filtering {
						showFilters = alphabetic, alphanumeric, lowercase, regexp, stripnewlines, titlecase, trim, uppercase
					}
					validation {
						showRules = alphabetic, alphanumeric, length, regexp, required
					}
				}
			}
			textline {
				showAccordions = label, attributes, validation, filters
				accordions {
					attributes {
						showProperties = name
					}
					validation {
						showRules = alphabetic, alphanumeric, between, date, digit, email, equals, float, greaterthan, inarray, integer, ip, length, lessthan, regexp, required, uri
					}
					filtering {
						showFilters = alphabetic, alphanumeric, currency, digit, integer, lowercase, regexp, titlecase, trim, uppercase
					}
				}
			}
			checkboxgroup {
				showAccordions = legend, options, various, validation
				accordions {
					validation {
						showRules = required
					}
				}
			}
			email < .textline
			header {
				showAccordions = various
			}
			textblock {
				showAccordions = various
			}
			name {
				showAccordions = legend, various
			}
			radiogroup < .checkboxgroup
		}
	}
}
