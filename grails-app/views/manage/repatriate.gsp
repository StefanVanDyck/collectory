<%@ page import="grails.converters.JSON; au.org.ala.collectory.ProviderGroup; au.org.ala.collectory.DataProvider" %>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="breadcrumbParent"
          content="${createLink(action: 'list', controller: 'manage')},${message(code: 'manage.list.title01')}"
    />
    <meta name="layout" content="${grailsApplication.config.skin.layout}" />
    <title>
        <g:message code="manage.repatriate.title" default="Repatriate datasets" />
    </title>
    <asset:stylesheet src="application.css" />
</head>
<body>
<%
    // Pre-encode your map to JSON in Groovy
    def regionPolygonJson = regionPolygonMap.collectEntries { [(it.key): it.value] } as JSON
%>
<h1>
<g:message code="manage.repatriate.title01" />
</h1>
<div class="row">
    <div id="baseForm" class="col-md-8">
        <g:form action="searchForRepatResources" controller="manage">
            <g:hiddenField name="configuration.guid" value="${configuration.guid}"/>
            <g:hiddenField name="useGeometry" value="${configuration.useGeometry}"/>
            <g:hiddenField name="useGadm1Code" value="${configuration.useGadm1Code}"/>
            <div class="form-group hide">
                <label for="adaptorString"><g:message code="manage.extload.label04" /><cl:helpText code="manage.extload.label04.help"/></label>
                <g:select name="adaptorString" class="form-control" from="${adaptors}" optionKey="adaptorString" optionValue="name" value="${configuration.adaptorString}"/>
            </div>
            <div class="form-group hide">
                <label for="endpoint"><g:message code="manage.extload.label05" /><cl:helpText code="manage.extload.label05.help"/></label>
                <g:field name="endpoint" class="form-control" type="url" value="${configuration.endpoint}"/>
            </div>
            <div class="form-group hide">
                <label for="country"><g:message code="manage.repatriationCountry.label06" /><cl:helpText code="manage.extload.label06.help"/></label>
                <g:field name="country" class="form-control" type="string" value="${configuration.country}"/>
            </div>
            <div class="form-group">
                <label for="repatriationArea"><g:message code="manage.repatriationCountry.label06" /><cl:helpText code="manage.extload.label06.help"/></label>
                <g:select id="repatriationArea" name="repatriationArea" class="form-control" from="${repatriationAreas}" value="${repatriationAreas[0]}" onchange="repatriationAreaUpdated()"/>
            </div>
            <div class="form-group hide">
                <label for="region"><g:message code="manage.repatriationCountry.label07" /><cl:helpText code="manage.extload.label07.help"/></label>
                <g:select name="region" class="form-control" from="${regionMap.entrySet()}" optionKey="key" optionValue="value" values="${configuration.region}" value="${params.region}" onchange="loadPolygon()"/>
            </div>
            <div class="form-group">
                <label for="spatialCriteria"><g:message code="manage.repatriationCountry.label08" /><cl:helpText code="manage.extload.label08.help"/></label>
                <g:select id="spatialCriteria" name="spatialCriteria" class="form-control" from="${['Administrative code', 'WKT polygon']}" value="'select criteria'" onchange="spatialCriteriaUpdated()"/>
            </div>
            <div class="form-group hide">
                <label for="geometry">Polygon WKT (editable)<cl:helpText code="manage.extload.label09.help"/></label>
                <g:textArea name="geometry" cols="50" rows="5" value="${configuration.geometry}" onLoad="loadPolygon()" disabled="true"/>
            </div>
            <div class="form-group hide">
                <label for="recordType"><g:message code="manage.extload.label07" /><cl:helpText code="manage.extload.label07.help"/></label>
                <g:select name="recordType" class="form-control" from="${datasetTypeMap.entrySet()}" optionKey="key" optionValue="value" values="${configuration.recordType}"/>
            </div>
            <div class="form-group hide">
                <label for="name"><g:message code="manage.extload.label01" /><cl:helpText code="manage.extload.label01.help"/></label>
                <g:field name="name" class="form-control" type="text" value="${configuration.name}"/>
            </div>
            <div class="form-group hide">
                <label for="description"><g:message code="manage.extload.label02" /><cl:helpText code="manage.extload.label02.help"/></label>
                <g:field name="description" class="form-control" type="text" size="64" value="${configuration.description}"/>
            </div>
            <div class="form-group hide">
                <label for="dataProviderUid"><g:message code="manage.extload.label03" /><cl:helpText code="manage.extload.label03.help"/></label>
                <g:select name="dataProviderUid"
                          class="form-control"
                          from="${dataProviders}"
                          optionKey="gbifRegistryKey"
                          optionValue="name"
                          value="${configuration.dataProviderUid}"
                          noSelection="${['':'Optionally select a provider...']}"
                />
            </div>
            <div class="form-inline">
                <label for="maxNoOfDatasets"><g:message code="manage.extload.label13" /><cl:helpText code="manage.extload.label13.help"/></label>
                <g:field type="number" name="maxNoOfDatasets" class="form-control form-control-sm" value="25" />
            </div>
            <br/>
            <div class="form-inline">
                <label for="minRecordCount"><g:message code="manage.extload.label11" /><cl:helpText code="manage.extload.label11.help"/></label>
                <g:field type="number" name="minRecordCount" class="form-control form-control-sm" value="10000" />
            </div>
            <br/>
            <div class="form-inline">
                <label for="maxRecordCount"><g:message code="manage.extload.label12" /><cl:helpText code="manage.extload.label12.help"/></label>
                <g:field type="number" name="maxRecordCount" class="form-control form-control-sm" value="1000000" />
            </div>
            <div>
                <span class="button"><input type="submit" name="performReview" value="Review" class="save btn btn-default"></span>
            </div>
        </g:form>
    </div>
    <div class="well col-md-4">
        <p>
            <g:message code="manage.repatriate.des01" />
            <g:message code="manage.repatriate.des02" />
        </p>
        <p>
            <g:message code="manage.repatriate.des03" />
        </p>
    </div>
</div>
<script type="text/javascript">
  function loadPolygon() {
    var region = document.getElementById("region").value;
    var regionPolygonMap = ${raw(regionPolygonJson.toString())};
    var polygon = regionPolygonMap[region];
    var spatialCriteria = document.getElementById("spatialCriteria").value;
    var useGeometry = spatialCriteria === 'WKT polygon';
    if (polygon && useGeometry) {
      document.getElementById("geometry").value = polygon;
    } else {
      document.getElementById("geometry").value = "";
    }
    $("input[name='useGeometry']").val(useGeometry);
  }

  function repatriationAreaUpdated() {
    var repatriationArea = document.getElementById("repatriationArea").value;
    var useGadm1Code = false;
    var $regionsDiv = $("div.form-group").has("select[name='region']");
    if(repatriationArea === 'Belgium'){
        $regionsDiv.addClass("hide");
    }else{
        $regionsDiv.removeClass("hide");
        useGadm1Code = true;
    }
    $("input[name='useGadm1Code']").val(useGadm1Code);
  }

  function spatialCriteriaUpdated() {
    var spatialCriteria = document.getElementById("spatialCriteria").value;
    var $geometryDiv = $("div.form-group").has("textArea[name='geometry']");
    if(spatialCriteria === 'Administrative code'){
      $geometryDiv.addClass("hide");
    }else if(spatialCriteria === 'WKT polygon'){
      $geometryDiv.removeClass("hide");
      loadPolygon();
    }
  }
</script>
</body>
</html>
