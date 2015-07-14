var argscheck = require('cordova/argscheck'),
exec = require('cordova/exec');

var corhttpd_exports = {};

corhttpd_exports.startServer = function(options, success, error) {
  var defaults = {
    'www_root': '',
    'cordovajs_root': null
  };

  // Merge optional settings into defaults.
  for (var key in defaults) {
    if (typeof options[key] !== 'undefined') {
      defaults[key] = options[key];
    }
  }

  exec(success, error, "CordovaUpdate", "startServer", [ defaults['www_root'], defaults['cordovajs_root'] ]);
};

corhttpd_exports.setLocalPath = function (path, success, error) {
  exec(success, error, "CordovaUpdate", "setLocalPath", [path]);
};

corhttpd_exports.setAdditionalDataPath = function (path, success, error) {
  exec(success, error, "CordovaUpdate", "setAdditionalDataPath", [path]);
};

corhttpd_exports.setAdditionalDataUrlPefix = function (prefix, success, error) {
  exec(success, error, "CordovaUpdate", "setAdditionalDataUrlPrefix", [prefix]);
};

corhttpd_exports.getCordovajsRoot = function (success, error) {
  exec(success, error, "CordovaUpdate", "getCordovajsRoot", []);
};

module.exports = corhttpd_exports;

