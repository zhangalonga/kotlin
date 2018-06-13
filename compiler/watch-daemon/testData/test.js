if (typeof kotlin === 'undefined') {
  throw new Error("Error loading module 'test'. Its dependency 'kotlin' was not found. Please, check whether 'kotlin' is loaded prior to 'test'.");
}
var test = function (_, Kotlin) {
  'use strict';
  var Kind_CLASS = Kotlin.Kind.CLASS;
  var println = Kotlin.kotlin.io.println_s8jyv4$;
  function MyProject() {
  }
  MyProject.prototype.render = function () {
    return 'test';
  };
  MyProject.$metadata$ = {
    kind: Kind_CLASS,
    simpleName: 'MyProject',
    interfaces: []
  };
  function main(args) {
    println('- 1');
    println('- 2');
  }
  _.MyProject = MyProject;
  _.main_kand9s$ = main;
  main([]);
  Kotlin.defineModule('test', _);
  return _;
}(typeof test === 'undefined' ? {} : test, kotlin);
