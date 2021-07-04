define(
	(require, factory) => {
    const { AuthorizedRoute } = require('pages/Route');
    const atlasState = require('atlas-state');
    function routes(router) {

      const customCodeViewEdit = new AuthorizedRoute((analysisId, section, sourceId) => {
        require([
          './custom-code-manager',
        ], function() {
          atlasState.customCodeAnalysis.selectedId(analysisId);
          router.setCurrentView('custom-code-manager', {
            id: analysisId,
            section: section || 'specification',
            sourceId: section === 'executions' ? sourceId : null,
          });
        });
      });

      return {
        '/custom_code': new AuthorizedRoute(() => {
          require(['./custom-code-browser'], function() {
            router.setCurrentView('custom-code-browser');
          })
        }),
        '/custom_code/:codeId:': customCodeViewEdit,
        '/custom_code/:codeId:/:section:': customCodeViewEdit,
        '/custom_code/:Id:/:section:/:sourceId:': customCodeViewEdit,
      };
    }

    return routes;
  }
);