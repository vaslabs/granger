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

    ctrl.publicKey = null;

    ctrl.requestPublicKey = function() {
        return $http({
            method: "get",
            url: '/pub_key',
        }).then(function(resp) {
            ctrl.publicKey = resp.data.value;
        });
    };

    ctrl.repo = null;

    ctrl.initialiseRepo = function() {
        var data = {
            uri: ctrl.repo
        };
        $http({
            method: "post",
            url: '/init',
            data: data
        }).then(function(resp) {
            ctrl.repoReady = true;
        });
    };


    ctrl.selectPatient = function(patient) {
      ctrl.selectedPatient = patient;
      getLatestActivity(ctrl.selectedPatient.patientId)
    };

    function getLatestActivity(patientId) {
        return $http({
            method: "get",
            url: '/api/latestActivity/'+patientId,
        }).then(function(resp) {
            if (ctrl.selectedPatient != null) {
                ctrl.selectedPatient.latestActivity = resp.data;
            }
        });
    }

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

    var today = new Date();

    ctrl.toothEditing = {
        medicament: "",
        notes: "",
        nextVisit: "",
        nextVisitDate: (new Date(today.getFullYear(), today.getMonth(), today.getDate(), today.getHours(), today.getMinutes()))
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
        var now = (new Date()).toISOString()
        var data = {
            "patientId": ctrl.selectedPatient.patientId,
            "toothNumber": ctrl.selectedTooth.number,
            "medicament": {
                "name":ctrl.toothEditing.medicament,
                "date":now
            },
            "nextVisit":{
                "notes": ctrl.toothEditing.nextVisit,
                "dateOfNextVisit": ctrl.toothEditing.nextVisitDate.toISOString(),
                "dateOfNote": now
            },
            "toothNote": {
                "note": ctrl.toothEditing.notes,
                "dateOfNote": now
            }
        };
        console.log(data);

        if (ctrl.rootDetails.rootName != "" && ctrl.rootDetails.rootSize != "" && ctrl.rootDetails.rootThickness != "") {
            data.roots = [
                {
                    "name": ctrl.rootDetails.rootName,
                    "size": ctrl.rootDetails.rootSize,
                    "thickness": ctrl.rootDetails.rootThickness
                }
            ];
        }

        $http({
            method: "post",
            url: '/update',
            data: data
        }).then(function(resp) {
            ctrl.selectedPatient = resp.data;
            ctrl.allPatients = ctrl.allPatients.filter(function(patient) { return patient.patientId != ctrl.selectedPatient.patientId;});
            ctrl.allPatients.push(ctrl.selectedPatient);
            ctrl.selectedTooth = ctrl.selectedPatient.dentalChart.teeth.find(function(tooth) { return tooth.number == ctrl.selectedTooth.number;});
            clearEditData();
        });
    };

    ctrl.repoReady = true;


    getAllPatients().then(function(patients) {
       if ("error" in patients) {
        ctrl.repoReady = false;
       } else {
        ctrl.repoReady = true;
        ctrl.allPatients = patients;
       }
    });
});