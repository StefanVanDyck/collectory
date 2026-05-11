<%@ page import="grails.converters.JSON; au.org.ala.collectory.ProviderGroup; au.org.ala.collectory.Institution" %>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
        <meta name="layout" content="${grailsApplication.config.skin.layout}" />
        <title><g:message code="collection.base.label" default="Edit taxonomy hints" args="['taxonomy hints']"/></title>
        <asset:javascript src="application.css"/>
    </head>
    <body>
        <div class="title-bar">
            <h1><g:message code="shared.title.editing" />: ${command.name}</h1>
        </div>
        <div >
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
                <label for="range">
                    Please supply a comma separated list of higher taxa. This will be displayed on
                    public metadata pages.
                    Examples: "flora", "mammals, amphibians"
                 </label>
                <g:textField class="form-control" name="range" value="${command.listTaxonomicRange() ? command.listTaxonomicRange().join(',') : ''}" />

                <span class="button"><input type="submit" name="_action_updateTaxonomicRange" value="${message(code:"shared.button.update")}" class="save btn btn-outline-dark"></span>
                <span class="button"><input type="submit" name="_action_cancel" value="${message(code:"shared.button.cancel")}" class="cancel btn btn-outline-dark"></span>
            </g:form>

        </div>
    </body>
</html>
