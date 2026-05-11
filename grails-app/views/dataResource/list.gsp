<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
        <meta name="breadcrumbParent"
              content="${createLink(action: 'list', controller: 'manage')},${message(code: 'manage.list.title01')}"
        />
        <meta name="layout" content="${grailsApplication.config.skin.layout}" />
        <g:set var="entityName" value="${entityType}" />
        <g:set var="entityNameLower" value="${cl.controller(type: entityType)}"/>
        <title><g:message code="default.list.label" args="[entityName]" /></title>
        <asset:stylesheet src="application.css" />
    </head>
    <body>
        <div class="btn-toolbar">
            <ul class="btn-group">
                <li class="btn btn-outline-dark"><cl:homeLink/></li>
                <li class="btn btn-outline-dark"><span class="fa fa-list"></span><g:link class="list" action="list" params="[max:10000]"> <g:message code="default.list.label" args="[entityName]"/></g:link></li>
                <li class="btn btn-outline-dark"><span class="fa fa-plus"></span><g:link class="create" action="create"> <g:message code="default.new.label" args="[entityName]"/></g:link></li>
                <li class="btn btn-outline-dark"><span class="fa fa-filter"></span><g:link class="gb" action="list" controller="dataResource" params="[sort:'name',max:10000, resourceType:'records', order:'asc']"> <g:message code="records.datasets.only" default="Record datasets only"/></g:link></li>
                <li class="btn btn-outline-dark"><span class="fa fa-download"></span><g:link class="gb" action="downloadCSV" controller="gbif"> <g:message code="download.datasets.list" default="Download CSV"/></g:link></li>
            </ul>
        </div>
        <div class="body content">
            <h1><g:message code="default.list.label" args="[entityName]" /></h1>
            <g:if test="${flash.message}">
              <div class="alert alert-warning">${flash.message}</div>
            </g:if>

            <div class="list">
                <table class="table table-bordered table-striped">
                    <thead>
                        <tr>
                            <g:sortableColumn property="name" title="${message(code: 'dataResource.name.label', default: 'Name')}" />
                            <g:sortableColumn property="uid" title="${message(code: 'providerGroup.uid.label', default: 'UID')}" />
                            <g:sortableColumn property="resourceType" title="${message(code: 'dataResource.resourceType.label', default: 'Type')}" />
                            <th>Is private</th>
                            <th>Licence</th>
                            <th>Verified</th>
                        </tr>
                    </thead>
                    <tbody>
                    <g:each in="${instanceList}" status="i" var="instance">
                      <tr class="${(i % 2) == 0 ? 'odd' : 'even'}">

                        <td><g:link action="show" id="${instance.uid}">${fieldValue(bean: instance, field: "name")}</g:link>
                        <br/>
                        <small>
                        ${fieldValue(bean: instance.dataProvider, field: "name")}
                        ${fieldValue(bean: instance.institution, field: "name")}
                        </small>
                        </td>

                        <td>${fieldValue(bean: instance, field: "uid")}</td>

                        <td>${fieldValue(bean: instance, field: "resourceType")}</td>

                        <td>
                          ${instance.isPrivate}
                        </td>
                        <td>
                            ${instance.licenseType}
                        </td>
                        <td>
                              ${instance.isVerified() ? "yes" : "no"}
                        </td>
                      </tr>
                    </g:each>
                    </tbody>
                </table>
            </div>

            <div>
                <tb:paginate controller="dataResource" action="list" total="${instanceTotal}" />
            </div>
        </div>
    </body>
</html>
