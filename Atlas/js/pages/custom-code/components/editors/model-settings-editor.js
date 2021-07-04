define([
	'knockout', 
	'text!./model-settings-editor.html',	
	'components/Component',
    'utils/CommonUtils',
], function (
	ko, 
	view, 
	Component,
    commonUtils,
) {
	class ModelSettingsEditor extends Component {
		constructor(params) {
            super(params);

            this.editor = params.editor;
            this.editorSettings = params;
		}
	}

	return commonUtils.build('model-settings-editor', ModelSettingsEditor, view);
});