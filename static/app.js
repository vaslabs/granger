var app = angular.module('grangerApp', []);

app.directive('scrollOnClick', function() {
  return {
    restrict: 'A',
    link: function(scope, $elm, attrs) {
      var idToScroll = attrs.href;
      $elm.on('click', function() {
        var $target;
        if (idToScroll) {
          $target = $(idToScroll);
        } else {
          $target = $elm;
        }
        $("body").animate({scrollTop: $target.offset().top}, "slow");
      });
    }
  }
});

app.directive('ngConfirmClick', [
  function(){
    return {
      priority: -1,
      restrict: 'A',
      link: function(scope, element, attrs){
        element.bind('click', function(e){
          var message = attrs.ngConfirmClick;
          if(message && !confirm(message)){
            e.stopImmediatePropagation();
            e.preventDefault();
          }
        });
      }
    }
  }
]);

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

app.directive('focusMe', function() {
  return {
    link: function(scope, element, attrs) {
      scope.$watch(attrs.focusMe, function(value) {
        if(value === true) {
          console.log('value=',value);
            element[0].focus();
            scope[attrs.focusMe] = false;
        }
      });
    }
  };
});

app.controller('MainController', function($q, $http) {

    var ctrl = this;

    ctrl.allPatients = [];

    ctrl.selectedPatient = null;

    ctrl.selectedTooth = null;

    function simpleGet(endpoint) {
        return $http({
            method: "get",
            url: endpoint,
        }).then(function(resp) {
            return resp.data;
        });
    }

    function getAllPatients() {
        return simpleGet('/api');
    }


    function getRememberData() {
        return simpleGet('/api/remember');
    }

    function getNotifications() {
        return simpleGet('/treatment/notifications/' + new Date().toISOString());
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
            grangerInit();
        });
    };

    ctrl.searchText = null;

    ctrl.selectPatient = function(patient) {
      ctrl.selectedPatient = patient;
      getLatestActivity(ctrl.selectedPatient.patientId)
    };

    ctrl.treatmentCategory = null;

    ctrl.notifications = [];

    ctrl.newTreatment = function() {
        if (ctrl.treatmentCategory == null || ctrl.treatmentCategory == "")
            return;
        var data = {
            "patientId": ctrl.selectedPatient.patientId,
            "toothId": ctrl.selectedTooth.number,
            "category": ctrl.treatmentCategory
        };

        return $http({
            method: "post",
            url: '/treatment/start',
            data: data
        }).then(function(resp) {
            updatePatient(resp);
            ctrl.treatmentCategory = null;
        });
    };

    function completedTreatmentsCount(treatments) {
        return treatments.filter(function (t) { return t.dateCompleted != null}).length;
    }

    ctrl.treatmentsCompletedClass = function(tooth) {
        if (tooth.treatments == null || tooth.treatments.length == 0)
            return "";
        if (tooth.treatments[0].dateCompleted == null)
            return "orange";
        else if (completedTreatmentsCount(tooth.treatments) > 1)
            return "red";
        else
            return "green";
    };

    ctrl.completeTreatment = function() {
            var data = {
                "patientId": ctrl.selectedPatient.patientId,
                "toothId": ctrl.selectedTooth.number,
            };

            return $http({
                method: "post",
                url: '/treatment/finish',
                data: data
            }).then(function(resp) {
                updatePatient(resp);
                ctrl.treatmentCategory = null;
            });
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

    ctrl.deleteTreatment = function(treatment) {
        var deleteTreatmentCommand = {
            toothId: ctrl.selectedTooth.number,
            patientId: ctrl.selectedPatient.patientId,
            createdOn: treatment.dateStarted
        };

        return $http({
            method: "post",
            url: '/treatment/delete',
            data: deleteTreatmentCommand
        }).then(function(resp) {
            updatePatient(resp);
            ctrl.treatmentCategory = null;
        });

    };

    ctrl.birthday = {
        value: new Date(1985, 5, 15)
    };
    ctrl.toothEditMode = false;

    ctrl.enableEditMode = function() {
        ctrl.toothEditMode = true;
        ctrl.rootDetails = ctrl.selectedTreatment.roots.concat(ctrl.rootDetails);
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
        length: ""
    }];

    ctrl.obturationDetails = [{
        name: "",
        size: "",
        length: ""
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
            length: ""
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
        resetEditData();
    };

    function resetEditData() {
       ctrl.selectedTooth = null;
       ctrl.selectedTreatment = null;
       ctrl.allowRootFocus = false;
       ctrl.allowObturationFocus = false;
       ctrl.rootDetails = [{
          name: "",
          size: "",
          length: ""
       }];
       ctrl.obturationDetails = [{
         name: "",
         size: "",
         length: ""
      }];
    }

    ctrl.init_treatments_ui = function() {
        $('select').material_select();
    };


    ctrl.selectTooth = function(tooth) {
        resetEditData();
        ctrl.selectedTooth = tooth;
    };

    ctrl.firstTeethRow = function(criteria) {
        return criteria.number <= 28;
    };

    ctrl.secondTeethRow = function(criteria) {
        return criteria.number > 28;
    };

    ctrl.toLocalDateTime = function(dateString) {
        if (dateString == null || dateString == "")
            return "";
        var date = new Date(dateString);
        var localeDateTime = new Date(date.getFullYear(), date.getMonth(), date.getDate(), date.getHours(), date.getMinutes());
        return localeDateTime.toLocaleString();
    };

    ctrl.flatActivity = function(activityMap) {
        if (activityMap == null)
            return [];
        var flatActivities = [];
        $.each(activityMap, function (key, activities) {
            if (activities.length > 0)
                flatActivities.push(activities[0]);
        });
        return flatActivities;
    };

    function medicament() {
        if (ctrl.toothEditing.medicament == null || ctrl.toothEditing.medicament == "")
            return null;
        var med = ctrl.toothEditing.medicament;
        if (ctrl.medicamentSuggestions.indexOf(med) < 0)
            ctrl.medicamentSuggestions.push(med);
        return {
            "name":med,
            "date":now()
        };
    }

    function nextVisit() {
        if (ctrl.toothEditing.nextVisit == null || ctrl.toothEditing.nextVisit == "")
            return null;
        return {
           "notes": ctrl.toothEditing.nextVisit,
           "dateOfNextVisit": ctrl.toothEditing.nextVisitDate.toISOString(),
           "dateOfNote": now()
        }
    }

    function now() {
        return (new Date()).toISOString();
    }

    function toothNote() {
        if (ctrl.toothEditing.notes == null || ctrl.toothEditing.notes == "")
            return null;
        return {
            "note": ctrl.toothEditing.notes,
            "dateOfNote": now()
        };
    }

    ctrl.selectedTreatment = null;

    ctrl.selectTreatment = function(treatment) {
        ctrl.allowObturationFocus = false;
        ctrl.allowRootFocus = false;
        ctrl.selectedTreatment = treatment;
    };

    var filterOutEmptyRoots = function(item) {return item.name != "" && item.size != "" && item.length != "";};

    ctrl.pushChanges = function() {
        var data = {
            "patientId": ctrl.selectedPatient.patientId,
            "toothNumber": ctrl.selectedTooth.number,
            "medicament": medicament(),
            "nextVisit": nextVisit(),
            "toothNote": toothNote(),
            "treatmentStarted": ctrl.selectedTreatment.dateStarted
        };

        data.roots = ctrl.rootDetails.filter(filterOutEmptyRoots);

        data.obturation = ctrl.obturationDetails.filter(filterOutEmptyRoots);

        $http({
            method: "post",
            url: '/update',
            data: data
        }).then(function(resp) {
            updatePatient(resp);
            clearEditData();
        });
    };

    function updatePatient(resp) {
        ctrl.selectedPatient = resp.data;
        ctrl.allPatients = ctrl.allPatients.filter(function(patient) { return patient.patientId != ctrl.selectedPatient.patientId;});
        ctrl.allPatients.push(ctrl.selectedPatient);
        ctrl.selectedTooth = ctrl.selectedPatient.dentalChart.teeth.find(function(tooth) { return tooth.number == ctrl.selectedTooth.number;});
        ctrl.selectedTreatment = ctrl.selectedTooth.treatments[0];
    }

    ctrl.repoReady = true;


    function grangerInit() {
        getAllPatients().then(function(patients) {
           if ("EmptyRepo" in patients) {
            ctrl.repoReady = false;
           } else {
            ctrl.repoReady = true;
            ctrl.allPatients = patients;
            fetchNotifications();
            setTimeout(fetchNotifications(), 5*60*1000)
           }
        });
    }

    getRememberData().then(function(medicaments) {
        ctrl.medicamentSuggestions = medicaments.medicamentNames;
    });

    function fetchNotifications() {
        getNotifications().then(function(notify) {
            ctrl.notifications = notify.notifications;
        });
    }


    ctrl.allowRootFocus = false;
    ctrl.allowObturationFocus = false;

    ctrl.addRootRow = function() {
        var emptyRows = ctrl.rootDetails.filter(filterOutEmptyRoots);
        ctrl.allowRootFocus = true;
        ctrl.allowObturationFocus = false;
        ctrl.rootDetails.push({
          name: "",
          size: "",
          length: ""
        });
    };

    ctrl.medicamentSuggestions = [
    ];

    ctrl.addObturationRow = function() {
            var emptyRows = ctrl.obturationDetails.filter(filterOutEmptyRoots);
            ctrl.allowObturationFocus = true;
            ctrl.allowRootFocus = false;
            ctrl.obturationDetails.push({
              name: "",
              size: "",
              length: ""
            });
    };

    grangerInit();

    ctrl.displayFriendlyTime = function(utcTime) {
        return new Date(utcTime).toLocaleDateString()
    }
});