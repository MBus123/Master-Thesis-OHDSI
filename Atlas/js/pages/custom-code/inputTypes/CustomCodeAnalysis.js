define([
	'knockout',
    '../../../components/cohortbuilder/CohortDefinition',
    'conceptsetbuilder/InputTypes/ConceptSet',
    'services/analysis/ConceptSetCrossReference',
    './PredictionCovariateSettings',
    "./CreateStudyPopulationArgs",
    './GetDbPlpDataArgs',
    './RunPlpArgs',
], function (
	ko,
    CohortDefinition,
    ConceptSet,
    ConceptSetCrossReference,
    CovariateSettings,
    CreateStudyPopulationArgs,
    GetDbPlpDataArgs,
    RunPlpArgs
) {
	class CustomCodeAnalysis {
        constructor(data = {}) {
            console.log(data);
            this.id = ko.observable(data.id || null);
            this.name = ko.observable(data.name || null);
            this.description = ko.observable(data.description || null);
            this.createdBy = data.createdBy || null;
            this.createdDate = data.createdDate || null;
            this.modifiedBy = data.modifiedBy || null;
            this.modifiedDate = data.modifiedDate || null;
            this.hyperParameters = ko.observable(data.hyperParameters || null);
            this.code = ko.observable(data.code || null);
            this.file = ko.observable(data.file || null);
        }
	}
	
	return CustomCodeAnalysis;
});