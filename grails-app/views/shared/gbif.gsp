<%@ page import="au.org.ala.collectory.DataResource" %>
<%@ page import="au.org.ala.collectory.IsoCodeService" %>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
        <meta name="layout" content="${grailsApplication.config.skin.layout}" />
        <asset:stylesheet src="application.css"/>
        <title><g:message code="dataResource.base.label" default="Edit data resource metadata" /></title>
        <link rel="stylesheet" href="${resource(dir:'css/smoothness',file:'jquery-ui-1.12.1.min.css')}" type="text/css" media="screen"/>
        <r:require modules="jquery_ui_custom, debug"/>
    </head>
    <body>
        <div class="nav">
        <h1><g:message code="collection.title.editing" />: ${command.name}</h1>
        </div>
        <div id="baseForm" class="body">
            <g:if test="${message}">
            <div class="alert alert-info">${message}</div>
            </g:if>
            <g:hasErrors bean="${command}">
            <div class="errors">
                <g:renderErrors bean="${command}" as="list" />
            </div>
            </g:hasErrors>
            <g:form method="post" name="baseForm" action="base">
                <g:hiddenField name="id" value="${command?.id}" />
                <g:hiddenField name="uid" value="${command?.uid}" />
                <g:hiddenField name="version" value="${command.version}" />

                <!-- GBIF registration key-->
                <div class="mb-3">
                    <label for="gbifRegistryKey"><g:message code="dataResource.gbif.registrationkey.label" default="GBIF registration key" /><cl:helpText code="providerGroup.gbifregistrationkey"/></label>
                    <g:textField name="gbifRegistryKey" class="form-control" value="${command?.gbifRegistryKey}" />
                </div>

                <div class="mb-3">
                    <label for="gbifCountryToAttribute">*<g:message code="dataResource.gbif.countryattribute.label" default="GBIF country to attribute data to" /><cl:helpText code="providerGroup.gbifCountryToAttribute"/></label>
                    <g:countrySelect id="country"  class="form-control" name="gbifCountryToAttribute" value="${command?.gbifCountryToAttribute?.toLowerCase()}"
                                     noSelection="['ZZZ':'-Use ZZZ for international organisations-']" required="true"/>
                </div>

                <div class="buttons">
                    <span class="button"><input type="submit" name="_action_updateGBIFDetails" value="${message(code:"collection.button.update")}" class="save btn btn-success"></span>
                    <span class="button"><input type="submit" name="_action_cancel" value="${message(code:"dataresource.gbifupload.btn.cancel")}" class="cancel btn btn-outline-dark"></span>
                </div>
            </g:form>
        </div>
    </body>
</html>
