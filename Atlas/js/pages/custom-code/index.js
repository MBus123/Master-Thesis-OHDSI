define(
  (require, exports) => {
	  const ko = require('knockout');  	
    const buildRoutes = require('./routes');
    const appState = require('atlas-state');
    const constants = require('./const');

    const statusCss = ko.pureComputed(function () {
      return "";
    });

    const navUrl = ko.pureComputed(function () {
      let url = constants.paths.browser();
      if (appState.customCodeAnalysis.current()) {
        if (appState.customCodeAnalysis.current().id() != null && appState.customCodeAnalysis.current().id() > 0) {
          url = appState.customCodeAnalysis.analysisPath(appState.customCodeAnalysis.current().id());
        } else {
          url = constants.paths.createAnalysis();
        }
      }
      return url;
    });
  
    return {
      title: 'Custom Algorithms',
      buildRoutes,
      navUrl: navUrl,
      icon: '',
			statusCss: statusCss,
    };
  }
);