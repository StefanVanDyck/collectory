<%@ page import="au.org.ala.collectory.CollectoryTagLib" %>
<div class="public-metadata">
    <g:set var="facet" value="${new CollectoryTagLib().getFacetForEntity(instance)}"/>
    <h3 id="totalRecordCountLinkHdr">
        <a id="totalRecordCountLink" href="${grailsApplication.config.biocacheUiURL}/occurrences/search?q=${facet}:${instance.uid}">
        </a>
    </h3>
    <h3 id="eventRecordsWrapper" style="display:none">
        <a id="totalEventCount" href="${grailsApplication.config.eventsUiURL}${instance.uid}">
        </a>
    </h3>

    <h4><g:message code="dataAccess.title"/></h4>
    <div class="dataAccess btn-group-vertical">

        <a href="${grailsApplication.config.biocacheUiURL}/occurrences/search?q=${facet}:${instance.uid}" class="btn btn-outline-dark"><i class="fa fa-list"></i> <g:message code="dataAccess.view.records"/></a>
        <g:if test="${!grailsApplication.config.disableLoggerLinks.toBoolean() && grailsApplication.config.loggerURL}">
            <a href="${grailsApplication.config.loggerURL}/reasonBreakdownCSV?eventId=1002&entityUid=${instance.uid}" class="btn btn-outline-dark"><i class="fa fa-download"></i> <g:message code="dataAccess.download.stats"/></a>
        </g:if>

        <cl:createNewRecordsAlertsLink query="${facet}:${instance.uid}" displayName="${instance.name}"
            linkText="${g.message(code:'dataAccess.alert.records.alt')}" altText="${g.message(code:'dataAccess.alert.records')} ${instance.name}"/>

        <cl:createNewAnnotationsAlertsLink query="${facet}:${instance.uid}" displayName="${instance.name}"
            linkText="${g.message(code:'dataAccess.alert.annotations.alt')}" altText="${g.message(code:'dataAccess.alert.annotations')} ${instance.name}"/>
    </div>
</div>
