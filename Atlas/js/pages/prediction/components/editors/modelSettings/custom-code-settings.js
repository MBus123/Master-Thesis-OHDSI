define([
	'knockout', 
	'text!./custom-code-settings.html',	
	'./ModelSettingsEditorComponent',
	'utils/CommonUtils',
    'services/CustomCode',
], function (
	ko, 
	view, 
	ModelSettingsEditorComponent,
	commonUtils,
    CustomCodeService,
) {
	class CustomCodeSettings extends ModelSettingsEditorComponent {
		constructor(params) {
			super(params);
            this.loading = ko.observable(true);
            this.myItems = ko.observableArray([]);
            for (const obj in Object.keys(params.modelSettings.CustomCodeSettings)) {
                this.modelSettings[obj] = ko.observable(params.modelSettings.CustomCodeSettings[obj]);
            }
            if (params.modelSettings.CustomCodeSettings.selectedItem != null && typeof params.modelSettings.CustomCodeSettings.selectedItem === 'function') {
                this.modelSettings.selectedItem = params.modelSettings.CustomCodeSettings.selectedItem;
                this.tmpId = this.modelSettings.selectedItem();
            } else {
                if (params.modelSettings.CustomCodeSettings.selectedItem != null) {
                    this.modelSettings.selectedItem = ko.observable(params.modelSettings.CustomCodeSettings.selectedItem);
                this.tmpId = params.modelSettings.CustomCodeSettings.selectedItem;
                } else {
                    this.modelSettings.selectedItem = ko.observable(null);
                    this.tmpId = null;
                }
            }
                
            this.currentHyperParameterList = ko.observableArray([]);
            CustomCodeService.getCustomCodes().then( (result) => {
                this.myItems(result.data.map(x => x.id));
                this.loading(false);
                if (this.tmpId != null) {
                    this.modelSettings.selectedItem(this.tmpId);
                    this.test();
                }
            });
		}

        getCaption() {
            caption  = "";
            CustomCodeService.getCustomCode(id).then( (result) => {
                    
                caption = result.name;
            });
            return caption;
        }

        test() {
            const sel_item = this.modelSettings.selectedItem;
            const id = this.modelSettings.selectedItem();
            for (const param in this.currentHyperParameterList) {
                delete this.modelSettings[param];
            }
            //this.modelSettings.selectedItem = ko.observable(sel_item);
            this.currentHyperParameterList([]);
            CustomCodeService.getCustomCode(id).then( (result) => {
                var h = JSON.parse(result.data.hyperParameters);
                const hyperParameters = result.data.hyperParameters.split(",");
                for (const param of h) {
                    if (this.modelSettings[param.name] != null) {
                        this.modelSettings[param.name] = ko.observableArray([param.default]);
                    } else {
                        this.modelSettings[param.name] = ko.observable(null);
                    }
                    var newParam = {
                        name: ko.observable(param.name),
                        value: this.modelSettings[param.name],
                        valueLabel: ko.observable(param.name),
                        default: ko.observableArray([param.default]),
                    };
                    this.currentHyperParameterList.push(newParam);
                }
                //this.currentHyperParameterList(h);
                //this.currentHyperParameterList(result.data.hyperParameters.split(","));
            });
        }
        
	}

    class CodeComponent extends ModelSettingsEditorComponent {
        constructor(params) {
            super(params);
            const components = [];
            for (const [key, value] of Object.entries(params)) {
                console.log(`${key}: ${value}`);
                const clazz = null;
                if (value.type === "list") {
                    clazz = CodeComponentList;
                } else if (value.type === "float") {
                    clazz = CodeComponentFloat;
                } else if (value.type === "int") {
                    clazz = CodeComponentInt;
                } else if (value.type === "bool") {
                    clazz = CodeComponentBool;
                }

                this.components.push({
                    name: settings.key,
                    value: this.modelSettings.key,
                    valueLabel: this.utils.getDefaultModelSettingName(this.defaultModelSettings, settings.key),
                    default: this.utils.getDefaultModelSettingValue(this.defaultModelSettings, settings.key),
                });
              }
        }
    }

    class CodeComponentList extends CodeComponent {

    }

    class CodeComponentFloat extends CodeComponent {

    }

    class CodeComponentInt extends CodeComponent {

    }

    class CodeComponentBool extends CodeComponent {

    }

	return commonUtils.build('custom-code-settings', CustomCodeSettings, view);
});