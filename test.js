if (Meteor.isClient) {
  // counter starts at 0

  document.addEventListener("deviceready", onDeviceReady, false);


  function onDeviceReady() {

    console.log('dir jest: ' + cordova.file.dataDirectory);
    var httpd = cordova && cordova.plugins && cordova.plugins.CordovaUpdate;
    httpd.setAdditionalDataPath(cordova.file.dataDirectory.substr("file://".length), function(msg) {
      alert("additional data ok: " + msg);
    }, function(msg) { alert("additional data err: " + msg); });

    httpd.registerMimeType("lol", "application/dupa", function(msg) {
      alert("mime ok: " + msg);
    }, function(msg) { alert("mime err: " + msg); });


    httpd.setAdditionalDataUrlPrefix("lol", function(msg) {
      alert("prefix ok: " + msg);
    }, function(msg) { alert("prefix err: " + msg); });


    var fileTransfer = new FileTransfer();
    var uri = encodeURI("http://dane.omegait.pl/000_0521.jpg");

    fileTransfer.download(
        uri,
        cordova.file.dataDirectory + '1.jpg',
        function(entry) {
          console.log("download complete: " + entry.toURL());
          document.getElementById('test').src = 'http://meteor.local/lol/1.jpg';
        },
        function(error) {
          console.log("download error source " + error.source);
          console.log("download error target " + error.target);
          console.log("upload error code" + error.code);
        },
        false,
        {
          headers: {

          }
        }
    );

  }


  Session.setDefault('counter', 0);

  Template.hello.helpers({
    counter: function () {
      return Session.get('counter');
    }
  });

  Template.hello.events({
    'click button': function () {
      // increment the counter when button is clicked
      Session.set('counter', Session.get('counter') + 1);
	Meteor.call('msg', 'lol', function() {
	});
    }
  });
}

if (Meteor.isServer) {
  Meteor.startup(function () {
    // code to run on server at startup
  });
  Meteor.methods({
  test: function(msg) {
    console.log(msg);
    return true;
  }
  });
}
