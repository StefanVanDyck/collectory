/*
 * Mapping - plot collection locations
 */

/************************************************************\
 * i18n
 \************************************************************/
jQuery.i18n.properties({
   name: 'messages',
   path: COLLECTORY_CONF.contextPath + '/messages/i18n/',
   mode: 'map',
   language: COLLECTORY_CONF.locale // default is to use browser specified locale
});
/************************************************************/

/* some globals */
// the map
var map;

// the data layer
var vectors;

// the server base url
var baseUrl;

// the ajax url for getting filtered features
var featuresUrl;

// flag to make sure we only apply the url initial filter once
var firstLoad = true;

if (altMap == undefined) {
    var altMap = false;
}

//var extent = new OpenLayers.Bounds(-20037508.34, -20037508.34, 20037508.34, 20037508.34);

// centre point for map of Australia - this value is transformed
// to the map projection once the map is created.
var centrePoint;

var defaultZoom;

// represents the number in 'all' collections - used in case the total number changes on an ajax request
var maxCollections = 0;

// num of filtered collections;
var collectionsCount = 0

var geoJsonLayer;

var clusterMarkers;

var myStyle = {
    "color": "#ff7800",
    "weight": 3,
    "opacity": 0.65
};

function onEachFeature(feature, layer) {
    // does this feature have a property named popupContent?
    if (feature.properties) {
        layer.bindPopup(feature.properties.popupContent, {maxWidth : 600 });
    }
}

/************************************************************\
* initialise the map
* note this must be called from body.onload() not jQuery document.ready() as the latter is too early
\************************************************************/
function initMap(mapOptions) {

    centrePoint = [mapOptions.centreLon, mapOptions.centreLat];
    defaultZoom = mapOptions.defaultZoom;
    // serverUrl is the base url for the site eg https://collections.ala.org.au in production
    // cannot use relative url as the context path varies with environment
    baseUrl = mapOptions.serverUrl;
    featuresUrl = mapOptions.serverUrl + "/public/mapFeatures";
    var featureGraphicUrl = mapOptions.serverUrl + "/static/images/map/orange-dot.png";
    var clusterGraphicUrl = mapOptions.serverUrl + "/static/images/map/orange-dot-multiple.png";
    var cartodbPattern = COLLECTORY_CONF.cartodbPattern;
    var patterns = ['a', 'b', 'c', 'd'].map(function(s) { return cartodbPattern.replace('${s}', s)});
    var map = L.map('map_canvas').setView([mapOptions.centreLat, mapOptions.centreLon], defaultZoom);

    L.tileLayer('https://api.mapbox.com/styles/v1/{id}/tiles/{z}/{x}/{y}?access_token='+ COLLECTORY_CONF.mapboxAccessToken, {
        maxZoom: 18,
        attribution: 'Map data &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors, ' +
            'Imagery © <a href="https://www.mapbox.com/">Mapbox</a>',
        id: 'mapbox/light-v9',
        tileSize: 512,
        zoomOffset: -1
    }).addTo(map);


    $.getJSON(featuresUrl, function(data) {
        clusterMarkers = L.markerClusterGroup({maxClusterRadius:10});
        geoJsonLayer = L.geoJson(data,
            {
                style: myStyle,
                onEachFeature: onEachFeature
            }
        );
        clusterMarkers.addLayer(geoJsonLayer);
        map.addLayer(clusterMarkers);
        setLabels(data);
        updateList(data.features);
    });
}

/************************************************************\
*   handler for loading features
\************************************************************/
function dataRequestHandler(data) {
    clusterMarkers.clearLayers();
    geoJsonLayer.clearLayers();
    geoJsonLayer.addData(data);
    clusterMarkers.addLayer(geoJsonLayer);
    setLabels(data);
    updateList(data.features);
}

function setLabels(data){

    // remove non-mappable collections
    var unMappable = new Array();
    var unMappableCount = 0
    for (var i = 0; i < data.features.length; i++) {
        if (!data.features[i].properties.isMappable) {
            unMappable.push(data.features[i]);
            if (data.features[i].properties.entityType == "Institution") {
                unMappableCount = unMappableCount + data.features[i].properties.collectionCount;
            }

        }
    }

    // update number of unmappable collections
    var unMappedText = "";
    switch (unMappable.length) {
        case 0: unMappedText = ""; break;
        case 1: unMappedText = "1 " + jQuery.i18n.prop('map.js.collectioncannotbemapped'); break;
        default: unMappedText = unMappableCount + " " + jQuery.i18n.prop('map.js.collectionscannotbemapped'); break;
    }
    $('span#numUnMappable').html(unMappedText);

    // update display of number of features
    var selectedFilters = getSelectedFiltersAsString();

    collectionsCount = 0;

    $.each(data.features, function( index, entity ) {
        if (entity.properties.entityType == "Institution") {
            collectionsCount = collectionsCount + entity.properties.collectionCount;
        }
    });

    var selectedFrom = jQuery.i18n.prop('map.js.collectionstotalarg', collectionsCount);
    if (selectedFilters != 'all') {
        selectedFrom = jQuery.i18n.prop("map.js.totalcollectionsfiltered", collectionsCount, jQuery.i18n.prop(selectedFilters))
    }
    var innerFeatures = "";

    switch (collectionsCount) {
        //case 0: innerFeatures = "No collections are selected."; break;
        //case 1: innerFeatures = "One collection is selected."; break;
        case 0: innerFeatures = jQuery.i18n.prop('map.js.nocollectionsareselected'); break;
        case 1: innerFeatures = jQuery.i18n.prop('map.js.onecollectionisselected'); break;
        default: innerFeatures = selectedFrom; break;
    }
    $('span#numFeatures').html(innerFeatures);

    // first time only: select the filter if one is specified in the url
    if (firstLoad) {
        selectInitialFilter();
        firstLoad = false;
    }
}

/************************************************************\
*   build human-readable string from selected filter list
\************************************************************/
function getSelectedFiltersAsString() {
    var list;
    //alert(altMap);
    if (altMap) {
        // new style
        list = getSelectedFilters();
    } else {
        // old style
        list = getAll();
    }
    // transform some
    list = list.replace(/plants/,"plant");
    list = list.replace(/microbes/,"microbial");

    // remove trailing comma
    if (list.substr(list.length - 1) == ',') {
        list = list.substring(0,list.length - 1);
    }
    // replace last with 'and'
    var last = list.lastIndexOf(',');
    if (last > 0) {
        list = list.substr(0,last) + " and " + list.substr(last + 1);
    }
    // insert space after remaining commas
    list = list.replace(/,/g,", ");
    return list;
}

/************************************************************\
*   regenerate list of collections - update total number
\************************************************************/
function updateList(features) {
    // update the potential total
    maxCollections = Math.max(collectionsCount, maxCollections);
    if (!$('div#all').hasClass('inst')) {  // don't change text if showing institutions
        $('span#allButtonTotal').html(jQuery.i18n.prop('show.all') + " " + maxCollections + " " + jQuery.i18n.prop('collections') + ".")
    }
    // update display of number of features
    var innerFeatures = "";
    switch (collectionsCount) {
        case 0: innerFeatures = jQuery.i18n.prop('map.js.nocollectionsareselected'); break;
        case 1: innerFeatures = collectionsCount + " " + jQuery.i18n.prop('map.js.collectionislisted'); break;
        default: innerFeatures = collectionsCount + " " + jQuery.i18n.prop('map.js.collectionsarelistedalphabetically'); break;
    }
    $('span#numFilteredCollections').html(innerFeatures);

    // group by institution
    // var sortedParents = groupByParent(features, true);
    var sortedParents = features;

    var innerHtml = "";
    var orphansHtml = "";
    for (var i = 0; i < sortedParents.length; i++) {
        var institution = sortedParents[i];
        // show institution - use name of institution from first collection
        var content = "<li><a class='highlight' href='" + baseUrl + "/public/show/" + institution.properties.uid + "'>" +
            institution.properties.name + "</a><ul>";

        if (institution.properties.collections !== undefined){
            // add collections
            for (var c = 0; c < institution.properties.collections.length; c++) {
                var collection = institution.properties.collections[c];
                content += "<li>";
                content += "<a href=" + collection.url + ">" + collection.name + "</a>";
                content += "</li>";
            }
        }

        content += "</ul></li>"
        innerHtml += content;
    }
    innerHtml += orphansHtml;
    $('ul#filtered-list').html(innerHtml);
}


/************************************************************\
*   grab name from institution
\************************************************************/
function getName(obj) {

    if ($.isArray(obj) && obj[0].attributes && obj[0].attributes.name && obj[0].attributes.entityType != "Collection") {
        return obj[0].attributes.name;
    } else if (!$.isArray(obj) && obj.attributes && obj.attributes.name && obj.attributes.entityType != "Collection") {
        return obj.attributes.name;
    }

    var name;
    if ($.isArray(obj)) {
        name = obj[0].attributes.instName;
    } else {
        name = obj.attributes.instName;
    }
    // remove leading 'The ' so the institutions sort by first significant letter
    if (name !== null && name.length > 4 && name.substr(0,4) === "The ") {
        name = name.substr(4);
    }
    return name;
}

/*
 * Helpers for managing Filter checkboxes
 */
/************************************************************\
*   set all boxes checked and trigger change handler
\************************************************************/
function setAll() {
    $('input[name=filter]').attr('checked', $('input#all').is(':checked'));
    filterChange();
}


/************************************************************\
*   build comma-separated string representing all selected boxes
\************************************************************/
function getAll() {
    if ($('input#all').is(':checked')) {
        return "all";
    }
    var checked = "";
    $('input[name=filter]').each(function(index, element){
        if (element.checked) {
            checked += element.value + ",";
        }
    });

    return checked;
}


/************************************************************\
*   need separate handler for ento change because we need to know which checkbox changed
*   to manage the ento-fauna paradigm
\************************************************************/
function entoChange() {
    // set state of faunal box
    if ($('input#fauna').is(':checked') && !$('input#ento').is(':checked')) {
        $('input#fauna').attr('checked', false);
    }
    filterChange();
}


/************************************************************\
*   handler for filter selection change
\************************************************************/
function filterChange() {
    // set ento based on faunal
    // set state of faunal box
    if ($('input#fauna').is(':checked') && !$('input#ento').is(':checked')) {
        $('input#ento').attr('checked', true);
    }
    // find out if they are all checked
    var all = true;
    $('input[name=filter]').each(function(index, element){
        if (!element.checked) {
            all = false;
        }
    });
    // set state of 'select all' box
    if ($('input#all').is(':checked') && !all) {
        $('input#all').attr('checked', false);
    } else if (!$('input#all').is(':checked') && all) {
        $('input#all').attr('checked', true);
    }

    // reload features based on new filter selections
    reloadData();
}
/* END filter checkboxes */

/*
 * Helpers for managing Filter buttons
 */
/************************************************************\
*   handle filter button click
\************************************************************/
function toggleButton(button) {
    // if already selected do nothing
    if ($(button).hasClass('selected')) {
        return;
    }

    // de-select all
    $('div.filter-buttons div').toggleClass('selected',false);

    // select the one that was clicked
    $(button).toggleClass("selected", true);

    // reloadData
    var filters = button.id;
    if (filters == 'fauna') {filters = 'fauna,entomology'}
    $.get(featuresUrl, {filters: filters}, dataRequestHandler);
    
}

/************************************************************\
 *   select filter if one is specified in the url
 \************************************************************/
function selectInitialFilter() {
    var params = $.deparam.querystring(),
        start = params.start,
        filter;
    if (start) {
        if (start === 'insects') { start = 'entomology'; }
        filter = $("#" + start);
        if (filter.length > 0) {
            toggleButton(filter[0]);
        }
    }
}

/************************************************************\
*   build comma separated string of selected buttons - NOT USED
\************************************************************/
function getSelectedFilters() {
    var checked = "";
    $('div.filter-buttons div').each(function(index, element){
        if ($(element).hasClass('selected')) {
            checked += element.id + ",";
        }
    });
    if (checked == 'fauna,entomology,microbes,plants,') {
        checked = 'all';
    }

    return checked;
}

/* END filter buttons */
