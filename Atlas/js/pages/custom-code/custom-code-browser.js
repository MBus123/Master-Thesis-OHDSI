define([
	'knockout',
	'text!./custom-code-browser.html',
	'appConfig',
	'./const',
	'services/MomentAPI',
	'./PermissionService',
	'pages/Page',
	'utils/CommonUtils',
    'utils/DatatableUtils',
	'services/CustomCode',
	'services/AuthAPI',
	'services/file',
	'faceted-datatable',
	'components/ac-access-denied',
	'components/heading',
	'components/empty-state',
	'less!./custom-code-browser.less'
], function(
	ko,
	view,
	config,
	constants,
	momentApi,
	PermissionService,
	Page,
	commonUtils,
    datatableUtils,
	CustomCodeService,
	authAPI,
	FileService,
) {
  class CustomCodeBrowser extends Page {
		constructor(params) {
			super(params);
			this.reference = ko.observableArray();
			this.loading = ko.observable(false);
			this.config = config;

			this.canReadCode = PermissionService.isPermittedList;
			this.canCreateCode = PermissionService.isPermittedCreate;

			this.isAuthenticated = authAPI.isAuthenticated;
			this.hasAccess = authAPI.isPermittedReadPlps;

			this.options = {
				Facets: [
                    {
                        'caption': 'Created',
                        'binding': (o) => datatableUtils.getFacetForDate(o.createdDate)
                    },
                    {
                        'caption': 'Updated',
                        'binding': (o) => datatableUtils.getFacetForDate(o.modifiedDate)
                    },
                    {
                        'caption': 'Author',
                        'binding': datatableUtils.getFacetForCreatedBy,
                    },
                    {
                        'caption': 'Designs',
                        'binding': datatableUtils.getFacetForDesign,
                    },
				]
			};

			this.columns = [
				{
					title: 'Id',
					data: 'id'
				},
				{
					title: 'Type',
                    data: d => d.type,
                    visible: false,
				},
				{
					title: 'Name',
					render: datatableUtils.getLinkFormatter(d => ({
						link: constants.paths.analysis(d.id),
						label: d['name']
					})),

				},
				{
					title: 'Created',
					render: datatableUtils.getDateFieldFormatter('createdDate'),
				},
				{
					title: 'Modified',
					render: datatableUtils.getDateFieldFormatter('modifiedDate'),
				},
				{
					title: 'Author',
					render: datatableUtils.getCreatedByFormatter(),
				}
			];
		}

		onPageCreated() {
			if (this.canReadCode()) {
				this.loading(true);
				CustomCodeService.getCustomCodes()
					.then(({ data }) => {
						datatableUtils.coalesceField(data, 'modifiedDate', 'createdDate');
						this.loading(false);
						this.reference(data);
					});
			}
		}

		newCustomCode() {
			document.location = constants.paths.createAnalysis();
		}

		logMe() {
			console.log("I am a  LOG");
		}

		download() {
			FileService.loadZip(config.webAPIRoot + "custom_algorithm/download", "validation-script.zip");
		}
	}

	return commonUtils.build('custom-code-browser', CustomCodeBrowser, view);
});