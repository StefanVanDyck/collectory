<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="breadcrumbParent"
          content="${createLink(action: 'list', controller: 'manage')},${message(code: 'manage.list.title01')}"
    />
    <meta name="layout" content="${grailsApplication.config.skin.layout}" />
    <g:set var="entityName" value="${entityType}" />
    <g:set var="entityNameLower" value="${cl.controller(type: entityType)}"/>
    <title>GBIF Syncing Healthcheck</title>
</head>
<body>
<div class="body content">
    <h1>GBIF Sync - sync results</h1>
    <div class="float-end">
        <g:link class="btn btn-primary" action="syncAllResources"
                onclick="return confirm('${message(code: 'default.button.updateall.confirm.message', default: 'Are you sure you want to sync all ? This will take some time to complete.')}');">
            <i class="ui-icon-arrow-1-s"></i> Sync resources
        </g:link>
    </div>
    <g:if test="${errorMessage}">
        <span class="alert alert-warning">${errorMessage}</span>
    </g:if>
    <g:else>
        <p class="lead">Syncing has started for providers/organisations</p>
        <p>This will continue in the background for several minutes.</p>
    </g:else>
</div>
</body>
</html>
