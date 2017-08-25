var exec = require('cordova/exec');

exports.encode = function (data, success, error) {
    console.log("in......encode");
    exec(success, error, "QrScanner", "encode", [data]);
};

exports.scan = function (data, success, error) {
    console.log("in......scan");
    exec(success, error, "QrScanner", "scan", [data]);
};
