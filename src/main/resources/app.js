var app = angular.module('grangerApp', []);

app.controller('MainController', function($q, $http) {

    var ctrl = this;

    ctrl.allPatients = [];

    ctrl.selectedPatient = null;

    ctrl.selectedTooth = null;

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
        ctrl.selectedTooth = null;
    };

    ctrl.birthday = {
        value: new Date(1985, 5, 15)
    };

    ctrl.toothEditMode = false;

    ctrl.enableEditMode = function() {
        ctrl.toothEditMode = true;
    };

    ctrl.toothEditing = {
        medicament: "",
        notes: "",
        nextVisit: ""
    };

    ctrl.rootDetails = {
        rootName: "",
        rootSize: "",
        rootThickness: ""
    };

    function clearEditData() {
        ctrl.toothEditing = {
            medicament: "",
            notes: "",
            nextVisit: ""
        };
        ctrl.rootDetails = {
            rootName: "",
            rootSize: "",
            rootThickness: ""
        };
        ctrl.toothEditMode = false;
    }

    function formatDate(date) {
        var month = date.getMonth() + 1;
        var day = date.getDate();
        var year = date.getFullYear();
        month = month < 10 ? '0'+month : month;
        day = day < 10 ? '0'+day : day;
        return year + '-' + month + '-' + day;
    }

    ctrl.firstName = "";
    ctrl.lastName = "";

    ctrl.addPatient = function() {
        var data = {
            'patientId': 0,
            'firstName': ctrl.firstName,
            'lastName':ctrl.lastName,
            'dateOfBirth': formatDate(ctrl.birthday.value),
            'dentalChart':{'teeth':[]}
        };
        $http({
            method: "post",
            url: '/api',
            data: data
        }).then(function(resp) {
            ctrl.allPatients.push(resp.data)
            ctrl.firstName = "";
            ctrl.lastName = "";
            ctrl.birthday.value = new Date(1985, 5, 15);
        });
    };

    ctrl.deselectTooth = function() {
        ctrl.selectedTooth = null;
    };

    ctrl.selectTooth = function(tooth) {
        ctrl.selectedTooth = tooth;
    };

    ctrl.firstTeethRow = function(criteria) {
        return criteria.number <= 28;
    };

    ctrl.secondTeethRow = function(criteria) {
        return criteria.number > 28;
    };

    ctrl.pushChanges = function() {
        var data = {
            "patientId": ctrl.selectedPatient.patientId,
            "tooth": {
                "number": ctrl.selectedTooth.number,
                "details": {
                    "roots": [
                    ],
                    "medicament": ctrl.toothEditing.medicament,
                    "notes": ctrl.toothEditing.notes,
                    "nextVisit": ctrl.toothEditing.nextVisit
                }
            }
        };

        if (ctrl.rootDetails.rootName != "" && ctrl.rootDetails.rootSize != "" && ctrl.rootDetails.rootThickness != "") {
            data.tooth.details.roots.push(
                {
                    "name": ctrl.rootDetails.rootName,
                    "size": ctrl.rootDetails.rootSize,
                    "thickness": ctrl.rootDetails.rootThickness
                }
            );
        }
        $http({
            method: "post",
            url: '/update',
            data: data
        }).then(function(resp) {
            ctrl.selectedPatient = resp.data;
            clearEditData();
        });
    };

    getAllPatients().then(function(patients) {
       ctrl.allPatients = patients;
    });
});