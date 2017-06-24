var app = angular.module('grangerApp', []);

app.directive('ngEnter', function() {
        return function(scope, element, attrs) {
            element.bind("keydown keypress", function(event) {
                if(event.which === 13) {
                        scope.$apply(function(){
                                scope.$eval(attrs.ngEnter);
                        });

                        event.preventDefault();
                }
            });
        };
});

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
        ctrl.deselectTooth();
    };

    ctrl.birthday = {
        value: new Date(1985, 5, 15)
    };

    ctrl.toothEditMode = false;

    ctrl.enableEditMode = function() {
        ctrl.toothEditMode = true;
        ctrl.rootDetails = ctrl.selectedTooth.roots.concat(ctrl.rootDetails);
    };

    var today = new Date();

    ctrl.toothEditing = {
        medicament: "",
        notes: "",
        nextVisit: "",
        nextVisitDate: (new Date(today.getFullYear(), today.getMonth(), today.getDate(), today.getHours(), today.getMinutes()))
    };

    ctrl.rootDetails = [{
        name: "",
        size: "",
        thickness: ""
    }];

    function clearEditData() {
        ctrl.toothEditing = {
            medicament: "",
            notes: "",
            nextVisit: ""
        };
        ctrl.rootDetails = {
            name: "",
            size: "",
            thickness: ""
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
        ctrl.rootDetails = [{
           name: "",
           size: "",
           thickness: ""
        }];
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

    ctrl.toLocalDateTime = function(dateString) {
        var date = new Date(dateString);
        var localeDateTime = new Date(date.getFullYear(), date.getMonth(), date.getDate(), date.getHours(), date.getMinutes());
        return localeDateTime.toLocaleString();
    };

    ctrl.flatActivity = function(activityMap) {
        if (activityMap == null)
            return [];
        console.log(activityMap);
        var flatActivities = [];
        $.each(activityMap, function (key, activities) {
            if (activities.length > 0)
                flatActivities.push(activities[0]);
        });
        console.log(flatActivities);
        return flatActivities;
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

        data.roots = ctrl.rootDetails.filter(function(item) {return item.name != "" && item.size != "" && item.thickness != "";});


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

    ctrl.addRootRow = function() {
        var emptyRows = ctrl.rootDetails.filter(function(item) {return item.name == "" || item.size == "" || item.thickness == "";});
        if (emptyRows.length == 0) {
            ctrl.rootDetails.push({
              name: "",
              size: "",
              thickness: ""
            });
        }
    };
});