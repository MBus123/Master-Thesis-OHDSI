define([
	'knockout',
	'text!./custom-code-manager.html',
	'pages/Page',
	'pages/Router',
	'utils/CommonUtils',
	'assets/ohdsi.util',
    'appConfig',
	'./const',
	'const',
	'atlas-state',
	'./PermissionService',
	'services/Permission',
	'components/security/access/const',
	'services/CustomCode',
	'services/analysis/Cohort',
	'./inputTypes/CustomCodeAnalysis',
	'featureextraction/InputTypes/CovariateSettings',
	'featureextraction/InputTypes/TemporalCovariateSettings',
	'services/analysis/ConceptSet',
	'services/analysis/ConceptSetCrossReference',
	'services/AuthAPI',
	'services/Poll',
	'lodash',
], function (
	ko,
	view,
	Page,
	router,
	commonUtils,
	ohdsiUtil,
	config,
	constants,
	globalConstants,
	sharedState,
	PermissionService,
	GlobalPermissionService,
	{ entityType },
	CustomCodeService,
	Cohort,
	CustomCodeAnalysis,
	CovariateSettings,
	TemporalCovariateSettings,
	ConceptSet,
	ConceptSetCrossReference,
	authAPI,
	{PollService},
	lodash
) {
	const NOT_FOUND = 'NOT FOUND';

	class CustomCodeManager extends Page {
		constructor(params) {
			super(params);
			sharedState.customCodeAnalysis.analysisPath = constants.paths.analysis;

			this.selectTab = this.selectTab.bind(this);
			this.selectedTabKey = ko.observable(router.routerParams().section);

			this.isAuthenticated = authAPI.isAuthenticated;
			this.hasAccess = authAPI.isPermittedReadPlps;

			this.options = constants.options;
			this.config = config;
			this.loading = ko.observable(true);
			this.customCodeAnalysis = sharedState.customCodeAnalysis.current;
			this.selectedAnalysisId = sharedState.customCodeAnalysis.selectedId;
			this.dirtyFlag = sharedState.customCodeAnalysis.dirtyFlag;
			this.managerMode = ko.observable('summary');
			this.tabMode = ko.observable('specification');
			this.utilityPillMode = ko.observable('download');
			this.fullAnalysisList = ko.observableArray();
			this.defaultTemporalCovariateSettings = null;
			this.fullSpecification = ko.observable(null);
			this.packageName = ko.observable().extend({alphaNumeric: null});
			this.isSaving = ko.observable(false);
			this.isCopying = ko.observable(false);
			this.isDeleting = ko.observable(false);
			this.executionTabTitle = config.useExecutionEngine ? "Executions" : "";
			this.isProcessing = ko.computed(() => {
				return this.isSaving() || this.isCopying() || this.isDeleting();
			});
			this.defaultName = "New Algorithm";
			this.canEdit = ko.pureComputed(() => parseInt(this.selectedAnalysisId()) ? PermissionService.isPermittedUpdate(this.selectedAnalysisId()) : PermissionService.isPermittedCreate());

			this.canDelete = ko.pureComputed(() => {
				return PermissionService.isPermittedDelete(this.selectedAnalysisId());
			});

			this.canCopy = ko.pureComputed(() => {
				return PermissionService.isPermittedCopy(this.selectedAnalysisId());
			});

			this.isNewEntity = this.isNewEntityResolver();
			this.predictionCaption = ko.computed(() => {
				if (this.customCodeAnalysis()) {
					if (this.selectedAnalysisId() === '0') {
						return 'New Machine Learning Algorithm';
					} else {
						return `Machine Learning Algorithm #${this.selectedAnalysisId()}`;
					}
				}
			});

			this.isNameFilled = ko.computed(() => {
				return this.customCodeAnalysis() && this.customCodeAnalysis().name() && this.customCodeAnalysis().name().trim();
			});
			this.isNameCharactersValid = ko.computed(() => {
				return this.isNameFilled() && commonUtils.isNameCharactersValid(this.customCodeAnalysis().name());
			});
			this.isNameLengthValid = ko.computed(() => {
				return this.isNameFilled() && commonUtils.isNameLengthValid(this.customCodeAnalysis().name());
			});
			this.isDefaultName = ko.computed(() => {
				return this.isNameFilled() && this.customCodeAnalysis().name().trim() === this.defaultName;
			});
			this.isNameCorrect = ko.computed(() => {
				return this.isNameFilled() && !this.isDefaultName() && this.isNameCharactersValid() && this.isNameLengthValid();
			});

			this.canSave = ko.computed(() => {
				return this.dirtyFlag().isDirty() && this.canEdit();
			});

			const extraExecutionPermissions = ko.computed(() => !this.dirtyFlag().isDirty()
				&& config.api.isExecutionEngineAvailable()
				&& this.canEdit());

			const generationDisableReason = ko.computed(() => {
				if (this.dirtyFlag().isDirty()) return globalConstants.disabledReasons.DIRTY;
				if (!config.api.isExecutionEngineAvailable()) return globalConstants.disabledReasons.ENGINE_NOT_AVAILABLE;
				return globalConstants.disabledReasons.ACCESS_DENIED;
			});
			this.componentParams = ko.observable({
				analysisId: this.selectedAnalysisId,
				customCodeAnalysis: sharedState.customCodeAnalysis.current,
				targetCohorts: sharedState.customCodeAnalysis.targetCohorts,
				outcomeCohorts: sharedState.customCodeAnalysis.outcomeCohorts,
				dirtyFlag: sharedState.customCodeAnalysis.dirtyFlag,
				fullAnalysisList: this.fullAnalysisList,
				packageName: this.packageName,
				fullSpecification: this.fullSpecification,
				loading: this.loading,
				subscriptions: this.subscriptions,
				isEditPermitted: this.canEdit,
				PermissionService,
				extraExecutionPermissions,
				tableColumns: ['Date', 'Status', 'Duration', 'Results'],
				executionResultMode: globalConstants.executionResultModes.DOWNLOAD,
				downloadFileName: 'algorithm-results',
				downloadApiPaths: constants.apiPaths,
				runExecutionInParallel: true,
				PollService: PollService,
				selectedSourceId: this.selectedSourceId,
				generationDisableReason,
				resultsPathPrefix: '/custom_code/',
				afterImportSuccess: this.afterImportSuccess.bind(this),
			});

			GlobalPermissionService.decorateComponent(this, {
				entityTypeGetter: () => entityType.PREDICTION,
				entityIdGetter: () => this.selectedAnalysisId(),
				createdByUsernameGetter: () => this.customCodeAnalysis() && lodash.get(this.customCodeAnalysis(), 'createdBy')
			});
		}

		onPageCreated() {
			const selectedAnalysisId = parseInt(this.selectedAnalysisId());
			if (selectedAnalysisId === 0 && !this.dirtyFlag().isDirty()) {
				this.newAnalysis();
			} else if (selectedAnalysisId > 0 && selectedAnalysisId !== (this.customCodeAnalysis() && this.customCodeAnalysis().id())) {
				this.onAnalysisSelected();
			} else {
				this.loading(false);
			}
		}

		fileSelect(elemet, event) {
			var files =  event.target.files;// FileList object
			console.log(files);
			var f = files[0];
			var reader = new FileReader();

			reader.onload = () => {
				var arrayBuffer = reader.result;
				this.customCodeAnalysis().file(btoa(arrayBuffer));
				console.log(this.customCodeAnalysis().file());	
 			};
			reader.readAsBinaryString(f);
		}

		selectTab(index, { key }) {
				this.selectedTabKey(key);
		return commonUtils.routeTo('/custom-code/' + this.componentParams().analysisId() + '/' + key);
		}

		customCodeAnalysisForWebAPI() {
			let definition = ko.toJS(this.customCodeAnalysis);
			definition = ko.toJSON(definition);
			return JSON.stringify(definition);
		}

		close () {
			if (this.dirtyFlag().isDirty() && !confirm("Algorithm changes are not saved. Would you like to continue?")) {
				return;
			}
			this.loading(true);
			this.customCodeAnalysis(null);
			this.selectedAnalysisId(null);
			this.dirtyFlag(new ohdsiUtil.dirtyFlag(this.customCodeAnalysis()));
			document.location = constants.paths.browser();
		}

		isNewEntityResolver() {
			return ko.computed(() => this.customCodeAnalysis() && this.selectedAnalysisId() === '0');
		}

		async delete() {
			if (!confirm("Delete algorithm? Warning: deletion can not be undone!"))
				return;

			this.isDeleting(true);
			const analysis = CustomCodeService.deleteCustomCode(this.selectedAnalysisId());

			this.loading(true);
			this.customCodeAnalysis(null);
			this.selectedAnalysisId(null);
			this.dirtyFlag(new ohdsiUtil.dirtyFlag(this.customCodeAnalysis()));
			document.location = constants.paths.browser()
		}

		async save() {
			this.isSaving(true);
			this.loading(true);

			let customCodeName = this.customCodeAnalysis().name();
			this.customCodeAnalysis().name(customCodeName.trim());

			// Next check to see that a prediction analysis with this name does not already exist
			// in the database. Also pass the id so we can make sure that the current prediction analysis is excluded in this check.
			try{
				const results = await CustomCodeService.exists(this.customCodeAnalysis().name(), this.customCodeAnalysis().id() == undefined ? 0 : this.customCodeAnalysis().id());
				this.fullAnalysisList.removeAll();
				const payload = this.prepForSave();
				const savedCustomCode = await CustomCodeService.saveCustomCode(payload);
				this.loadAnalysisFromServer(savedCustomCode);
				document.location = constants.paths.analysis(this.customCodeAnalysis().id());
			} catch (e) {
				alert(e);
			} finally {
				this.isSaving(false);
				this.loading(false);
			}
		}

		prepForSave() {

			return {
				id: this.customCodeAnalysis().id(),
				name: this.customCodeAnalysis().name(),
				description: this.customCodeAnalysis().description(),
				code: this.customCodeAnalysis().code(),
				hyperParameters: this.customCodeAnalysis().hyperParameters(), 
				file: this.customCodeAnalysis().file()
			};
		}

		prettyJSON(old_json) {
			var obj = JSON.parse(old_json);
			var p = old_json;
			var p = JSON.stringify(obj, undefined, 4);
			return p;
		}

		newAnalysis() {
			this.loading(true);
			this.customCodeAnalysis(new CustomCodeAnalysis({id: 0, name: this.defaultName}));
			return new Promise(async (resolve, reject) => {
				this.resetDirtyFlag();
				this.loading(false);

				resolve();
			});
		}

		onAnalysisSelected() {
			this.loading(true);
			CustomCodeService.getCustomCode(this.selectedAnalysisId()).then((analysis) => {
				this.loadAnalysisFromServer(analysis);
				this.loading(false);
			});
		}

		resetDirtyFlag() {
			this.dirtyFlag(new ohdsiUtil.dirtyFlag({analysis: this.customCodeAnalysis(), targetCohorts: this.targetCohorts, outcomeCohorts: this.outcomeCohorts}));
		}

		loadAnalysisFromServer(analysis) {
			var header = analysis.json;
			this.loadParsedAnalysisFromServer(header);
		}

		loadParsedAnalysisFromServer(header) {
			const { createdBy, modifiedBy, ...props } = header;
			this.customCodeAnalysis(new CustomCodeAnalysis({
				...props,
				createdBy: createdBy ? createdBy.name : null,
				modifiedBy: modifiedBy ? modifiedBy.name : null
			}));
			console.log(this.customCodeAnalysis().hyperParameters());
			this.resetDirtyFlag();
		}

		setUserInterfaceDependencies() {
			this.targetCohorts.removeAll();
			this.patientLevelPredictionAnalysis().targetIds().forEach(c => {
				let name = NOT_FOUND;
				if (this.patientLevelPredictionAnalysis().cohortDefinitions().filter(a => a.id() === parseInt(c)).length > 0) {
					name = this.patientLevelPredictionAnalysis().cohortDefinitions().filter(a => a.id() === parseInt(c))[0].name();
					this.targetCohorts.push(new Cohort({id: c, name: name}));
				}
			});

			this.outcomeCohorts.removeAll();
			this.patientLevelPredictionAnalysis().outcomeIds().forEach(c => {
				let name = NOT_FOUND;
				if (this.patientLevelPredictionAnalysis().cohortDefinitions().filter(a => a.id() === parseInt(c)).length > 0) {
					name = this.patientLevelPredictionAnalysis().cohortDefinitions().filter(a => a.id() === parseInt(c))[0].name();
					this.outcomeCohorts.push(new Cohort({id: c, name: name}));
				}
			});
		}

		async afterImportSuccess(res) {
			commonUtils.routeTo('/custom_code/' + res.id);
		};

		getAuthorship() {
			const createdDate = commonUtils.formatDateForAuthorship(this.customCodeAnalysis().createdDate);
			const modifiedDate = commonUtils.formatDateForAuthorship(this.customCodeAnalysis().modifiedDate);
			return {
					createdBy: lodash.get(this.customCodeAnalysis(), 'createdBy'),
					createdDate,
					modifiedBy: lodash.get(this.customCodeAnalysis(), 'modifiedBy'),
					modifiedDate,
			}
		}
	}

	return commonUtils.build('custom-code-manager', CustomCodeManager, view);
});