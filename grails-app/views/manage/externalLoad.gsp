<%@ page import="grails.converters.JSON; au.org.ala.collectory.ProviderGroup; au.org.ala.collectory.DataProvider" %>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="breadcrumbParent"
          content="${createLink(action: 'list', controller: 'manage')},${message(code: 'manage.list.title01')}"
    />
    <meta name="layout" content="${grailsApplication.config.skin.layout}" />
    <title>
        <g:message code="manage.extload.title" />
    </title>
    <asset:stylesheet src="application.css" />
</head>
<body>
<h1>
<g:if test="${repatriate}">
    <g:message code="manage.repatriate.title01" />
</g:if>
<g:else>
    <g:message code="manage.extload.title01" />
</g:else>
</h1>

<div class="row">
    <div id="baseForm" class="col-md-8">
        <g:form action="searchForResources" controller="manage">
            <g:hiddenField name="configuration.guid" value="${configuration.guid}"/>

            <div class="mb-3 d-none">
                <label for="adaptorString"><g:message code="manage.extload.label04" /><cl:helpText code="manage.extload.label04.help"/></label>
                <g:select name="adaptorString" class="form-select" from="${adaptors}" optionKey="adaptorString" optionValue="name" value="${configuration.adaptorString}"/>
            </div>
            <div class="mb-3 d-none">
                <label for="endpoint"><g:message code="manage.extload.label05" /><cl:helpText code="manage.extload.label05.help"/></label>
                <g:field name="endpoint" class="form-control" type="url" value="${configuration.endpoint}"/>
            </div>
            <div class="mb-3">
                <label for="country"><g:message code="manage.extload.label06" /><cl:helpText code="manage.extload.label06.help"/></label>
                <g:select name="country" class="form-select" from="${countryMap.entrySet()}" optionKey="key" optionValue="value" values="${configuration.country}"/>
            </div>
            <div class="mb-3 d-none">
                <label for="recordType"><g:message code="manage.extload.label07" /><cl:helpText code="manage.extload.label07.help"/></label>
                <g:select name="recordType" class="form-select" from="${datasetTypeMap.entrySet()}" optionKey="key" optionValue="value" values="${configuration.recordType}"/>
            </div>
            <div class="mb-3">
                <label for="name"><g:message code="manage.extload.label01" /><cl:helpText code="manage.extload.label01.help"/></label>
                <g:field name="name" class="form-control" type="text" value="${configuration.name}"/>
            </div>
            <div class="mb-3 d-none">
                <label for="description"><g:message code="manage.extload.label02" /><cl:helpText code="manage.extload.label02.help"/></label>
                <g:field name="description" class="form-control" type="text" size="64" value="${configuration.description}"/>
            </div>
            <div class="mb-3">
                <label for="dataProviderUid"><g:message code="manage.extload.label03" /><cl:helpText code="manage.extload.label03.help"/></label>
                <g:select name="dataProviderUid"
                          class="form-select"
                          from="${dataProviders}"
                          optionKey="uid"
                          optionValue="name"
                          value="${configuration.dataProviderUid}"
                          noSelection="${['':'Optionally select a provider...']}"
                />
            </div>
            <div class="mb-3">
                <label for="maxNoOfDatasets"><g:message code="manage.extload.label10" /><cl:helpText code="manage.extload.label10.help"/></label>
                <g:field type="text" name="maxNoOfDatasets" class="form-control" value="10" />
            </div>
            <div class="mb-3 d-none">
                <label for="username"><g:message code="manage.extload.label08" /><cl:helpText code="manage.extload.label08.help"/></label>
                <g:field type="text" name="username" class="form-control" value="${grailsApplication.config.gbifApiUser}" />
            </div>
            <div class="mb-3 d-none">
                <label for="password"><g:message code="manage.extload.label09" /><cl:helpText code="manage.extload.label09.help"/></label>
                <g:field type="password" name="password" class="form-control" value="${grailsApplication.config.gbifApiPassword}" />
            </div>
            <div>
                <span class="button"><input type="submit" name="performReview" value="Review" class="save btn btn-outline-dark"></span>
            </div>
        </g:form>
    </div>
    <div class="card card-body col-md-4">
        <p>
            <g:message code="manage.extload.des01" />
            <g:message code="manage.extload.des02" />
        </p>
        <p>
            <g:message code="manage.extload.des03" />
        </p>
    </div>
</div>
</body>
</html>