define([
	'knockout',
], function (
	ko
) {
	class CustomCode {
        constructor(data = {}) {
            this.id = data.id || null;
            this.name = data.name || null;
            this.description = data.description ||null;
            this.code = data.code || null;
            this.hyperParameters = data.hyperParameters || null;
            this.file = data.file || null;
        }
	}
	
	return CustomCode;
});