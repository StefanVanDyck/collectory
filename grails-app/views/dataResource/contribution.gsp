<%@ page import="grails.converters.JSON; au.org.ala.collectory.resources.DarwinCoreFields; au.org.ala.collectory.DataHub; au.org.ala.collectory.ProviderGroup; au.org.ala.collectory.DataResource; au.org.ala.collectory.resources.Profile" %>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="layout" content="${grailsApplication.config.skin.layout}" />
    <title><g:message code="dataResource.base.label" default="Edit data resource metadata" /></title>
    <asset:stylesheet src="application.css"/>
    <script type="text/javascript">
        var COLLECTORY_CONF = {
            contextPath: "${request.contextPath}",
            locale: "${(org.springframework.web.servlet.support.RequestContextUtils.getLocale(request).toString())?:request.locale}",
            cartodbPattern: "${grailsApplication.config.cartodb.pattern}"
        };
    </script>
    <asset:javascript src="application-pages.js"/>
    <asset:javascript src="contribution.js"/>
</head>
<body>
<div class="nav">
    <h1><g:message code="collection.title.editing" />: ${command.name}</h1>
</div>
<div id="baseForm" class="row">
    <g:if test="${message}">
        <div class="alert alert-info">${message}</div>
    </g:if>
    <g:hasErrors bean="${command}">
        <div class="errors">
            <g:renderErrors bean="${command}" as="list" />
        </div>
    </g:hasErrors>
    <g:form method="post" name="contributionForm" action="contribution">
        <g:hiddenField name="id" value="${command?.id}" />
        <g:hiddenField name="uid" value="${command?.uid}" />
        <g:hiddenField name="version" value="${command.version}" />
        <table >
            <tbody>

            <!-- status -->
            <div class="mb-3">
                    <label for="status"><g:message code="dataResource.status.label" default="Status" /><cl:helpText code="dataResource.status"/></label>
                    <g:select name="status" class="form-select" from="${grailsApplication.config.dataResource.statusList}" value="${command.status}"/>
            </div>

            <!-- provenance -->
            <div class="mb-3">
                    <label for="provenance"><g:message code="dataResource.provenance.label" default="Provenance" /><cl:helpText code="dataResource.provenance"/></label>
                     <g:select name="provenance" class="form-select" from="${grailsApplication.config.dataResource.provenanceTypesList}" value="${command.provenance}" noSelection="${['':'none']}"/>
            </div>

            <!-- last checked -->
            <div class="mb-3">
                    <label for="lastChecked"><g:message code="dataResource.lastChecked.label" default="Last checked" /><cl:helpText code="dataResource.lastChecked"/></label>
                    <g:textField name="lastChecked" class="form-control" pattern="\\d\\d\\d\\d-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d" value="${command.lastChecked}"/>
            </div>

            <!-- data currency -->
            <div class="mb-3">
                <label for="dataCurrency"><g:message code="dataResource.dataCurrency.label" default="Data currency" /><cl:helpText code="dataResource.dataCurrency"/></label>
                <g:textField name="dataCurrency" class="form-control" pattern="\\d\\d\\d\\d-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d" value="${command.dataCurrency}"/>
            </div>

            <!-- harvest frequency -->
            <div class="mb-3">
                <label for="harvestFrequency"><g:message code="dataResource.harvestFrequency.label" default="Harvest frequency" /><cl:helpText code="dataResource.harvestFrequency"/></label>
                <g:textField name="harvestFrequency" class="form-control" value="${command.harvestFrequency}"/>
            </div>

            <!-- mob notes -->
            <div class="mb-3">
                <label for="mobilisationNotes"><g:message code="dataResource.mobilisationNotes.label" default="Mobilisation notes" /><cl:helpText code="dataResource.mobilisationNotes"/></label>
                <g:textArea name="mobilisationNotes" class="form-control" rows="${cl.textAreaHeight(text:command.mobilisationNotes)}" value="${command?.mobilisationNotes}" />
                    <p class="form-text text-muted"><g:message code="dataresource.contribution.des01" />.</p>
            </div>

            <!-- harvest notes -->
            <div class="mb-3">
                <label for="harvestingNotes"><g:message code="dataResource.harvestingNotes.label" default="Harvesting notes" /> <cl:helpText code="dataResource.harvestingNotes"/></label>
                <g:textArea name="harvestingNotes" class="form-control" rows="${cl.textAreaHeight(text:command.harvestingNotes)}" value="${command?.harvestingNotes}" />
            </div>

            <!-- public archive -->
            <div class="mb-3">
                <label for="publicArchiveAvailable">
                    <g:checkBox name="publicArchiveAvailable" value="${command?.publicArchiveAvailable}" />
                    <g:message code="dataResource.publicArchiveAvailable.label" default="Public archive available" /><cl:helpText code="dataResource.publicArchiveAvailable"/>
                </label>
            </div>

            <!-- harvest parameters -->
            <div class="card card-body">
                <h3><g:message code="dataresource.contribution.table0101" /></h3>
                <cl:connectionParameters bean="command" connectionParameters="${command.connectionParameters}"/>
            </div>

            <g:if test="${command.resourceType == 'records'} || ${command.resourceType == 'events'}">
                <!-- darwin core defaults -->
                <div><h3><g:message code="dataresource.contribution.table0201" /></h3></div>
                <div><g:message code="dataresource.contribution.table0301" />.</div>
                <g:set var="dwc" value="${command.defaultDarwinCoreValues ? JSON.parse(command.defaultDarwinCoreValues) : [:]}"/>
                <!-- add fields for each of the important terms -->
                <g:each in="${DarwinCoreFields.getImportant()}" var="dwcf">
                    <div class="mb-3">
                            <label for="${dwcf.name}"><g:message code="dataResource.DwC.${dwcf.name}.label" default="${dwcf.name}" /> <cl:helpText code="dataResource.${dwcf.name}"/></label>
                            <g:if test="${dwcf.values}">
                                <!-- pick list -->
                                <g:select name="${dwcf.name}" class="form-select" from="${dwcf.values}" value="${dwc[dwcf.name]}"/>
                            </g:if>
                            <g:else>
                                <!-- text field -->
                                <g:textField name="${dwcf.name}" class="form-control" value="${dwc[dwcf.name]}"/>
                            </g:else>
                    </div>
                </g:each>
                <!-- add fields for any other terms that have values -->
                <g:each var="dwcf" in="${dwc.entrySet()}">
                    <g:if test="${dwcf.key in DarwinCoreFields.getLessImportant().collect({it.name})}">
                        <div class="mb-3">
                            <label for="${dwcf.key}"><g:message code="dataResource.DwC.${dwcf.key}.label" default="${dwcf.key}" /></label>
                            <g:textField name="${dwcf.key}" class="form-control" value="${dwcf.value}"/>
                        </div>
                    </g:if>
                </g:each>

                <!-- add a blank field so other DwC terms can be added -->
                <div id="add-another"><g:message code="dataresource.contribution.table0401" />.</div>
                <div class="mb-3">
                        <g:select name="otherKey" class="form-select" from="${DarwinCoreFields.getLessImportant().collect({it.name})}"/>
                        <button id="more-terms" type="button" class="btn btn-outline-dark"><g:message code="dataresource.contribution.table.button" /></button>
                </div>

            </g:if>

            </tbody>
        </table>
    %{--</div>--}%

        <div class="buttons">
            <span class="button"><input type="submit" name="_action_updateContribution" value="${message(code:"collection.button.update")}" class="save btn btn-success"></span>
            <span class="button"><input type="submit" name="_action_cancel" value="${message(code:"collection.button.cancel")}" class="cancel btn btn-outline-dark"></span>
        </div>
    </g:form>
</div>
</body>
</html>
