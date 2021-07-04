define([
	'knockout', 
	'databindings',
], function (
	ko
) {
	class CustomCodeSettings {
		constructor(data = {}) {
			for (const key of Object.keys(data)) {
				this[key] = data[key];
			}
		}
    }
	
	return CustomCodeSettings;
});