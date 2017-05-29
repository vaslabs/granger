var app = angular.module('grangerApp', []);

app.controller('MainController', function($q, $http) {

    var ctrl = this;

    ctrl.allPatients = [];

    ctrl.selectedPatient = null;

    function getAllPatients() {
        return $http({
            method: "get",
            url: '/api',
        }).then(function(resp) {
            return resp.data;
        });
    }

    ctrl.selectPatient = function(patient) {
      ctrl.selectedPatient = patient;
    };

    ctrl.deselectPatient = function() {
        ctrl.selectedPatient = null;
    };

    ctrl.firstTeethRow = function(criteria) {
        return criteria.number <= 28;
    };

    ctrl.secondTeethRow = function(criteria) {
        return criteria.number > 28;
    };

    getAllPatients().then(function(patients) {
       ctrl.allPatients = patients;
    });
});