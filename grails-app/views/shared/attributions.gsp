<%@ page import="au.org.ala.collectory.ProviderGroup" %>
<html>
<head>
  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
  <meta name="layout" content="${grailsApplication.config.skin.layout}"/>
  <asset:stylesheet src="application.css"/>
  <g:set var="entityName" value="${command.ENTITY_TYPE}"/>
  <g:set var="entityNameLower" value="${command.ENTITY_TYPE.toLowerCase()}"/>
  <title>Edit ${entityNameLower} metadata</title>
</head>
<body onload="load();">
<div class="nav">
  <h1>Editing: ${fieldValue(bean: command, field: "name")}</h1>
</div>
<div class="body">
  <g:if test="${message}">
    <div class="alert alert-info">${message}</div>
  </g:if>
  <g:hasErrors bean="${command}">
    <div class="errors">
      <g:renderErrors bean="${command}" as="list"/>
    </div>
  </g:hasErrors>
  <g:form method="post" enctype="multipart/form-data" action="editCollection">
    <g:hiddenField name="id" value="${command?.id}"/>
    <g:hiddenField name="uid" value="${command?.uid}" />
    <g:hiddenField name="version" value="${command.version}"/>

    <g:each in="${grailsApplication.config.networkTypes}" var="networkName">
      <div class="mb-3">
        <label for="${networkName}">
          <g:checkBox name="${networkName}" value="${networkName}"/>
          <g:message code="shared.attributes.${networkName}" /><cl:helpText code="providerGroup.attribution.${networkName}"/>
        </label>
      </div>
    </g:each>

    <!-- institution -->
    <div class="mb-3">
      <label for="institution">
        <g:checkBox disabled="true" name="institution" value="${true}"/>
        <g:message code="shared.attributes.institution" /><cl:helpText code="providerGroup.attribution.institution"/>
      </label>
    </div>

    <div class="buttons">
      <span class="button"><input type="submit" name="_action_updateAttributions" value="Update" class="save btn btn-success"></span>
      <span class="button"><input type="submit" name="_action_cancel" value="Cancel" class="cancel btn btn-outline-dark"></span>
    </div>
  </g:form>
</div>

</body>
</html>
