define(function (require, exports) {

	const config = require('appConfig');
	const authApi = require('services/AuthAPI');
    const httpService = require('services/http');
    const customCodeEndpoint = "custom_algorithm/";

    function getCustomCodes() {
        return httpService.doGet(config.webAPIRoot + customCodeEndpoint).catch(authApi.handleAccessDenied);
    }

    function saveCustomCode(code) {
        const url = config.webAPIRoot + customCodeEndpoint + (code.id || "");
        let promise;
        if (code.id) {
            promise = httpService.doPut(url, code);
        } else {
            promise = httpService.doPost(url, code);
        }

		promise.catch((error) => {
			console.log("Error: " + error);
			authApi.handleAccessDenied(error);
		});

		return promise;

    }

    function deleteCustomCode(id) {
		return httpService.doDelete(config.webAPIRoot + customCodeEndpoint + (id || ""))
			.catch((error) => {
				console.log("Error: " + error);
				authApi.handleAccessDenied(error);
			});
    }
    
    function getCustomCode(id) {
		return httpService.doGet(config.webAPIRoot + customCodeEndpoint + id)
			.catch((error) => {
				console.log("Error: " + error);
				authApi.handleAccessDenied(error);
			});
	}

	function exists(name, id) {
		return httpService
			.doGet(`${config.webAPIRoot}${customCodeEndpoint}${id}/exists?name=${name}`)
			.then(res => res.data)
			.catch(error => authApi.handleAccessDenied(error));
	}

	function download() {
		return httpService.doGet(config.webAPIRoot + customCodeEndpoint + "download").catch((error) => {
			console.log("Error: " + error);
			authApi.handleAccessDenied(error);
		});
	}

	return {
		getCustomCodes: getCustomCodes,
		exists: exists,
		saveCustomCode: saveCustomCode,
		deleteCustomCode: deleteCustomCode,
		getCustomCode: getCustomCode,
		download: download
	};
});